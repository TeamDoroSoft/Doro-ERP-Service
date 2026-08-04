package com.dorosoft.erp.identity.infrastructure.ratelimit;

import com.dorosoft.erp.identity.application.ratelimit.ClientIpAddress;
import com.dorosoft.erp.identity.application.ratelimit.LoginRateLimitDecision;
import com.dorosoft.erp.identity.application.session.AuthenticationUnavailableException;
import java.net.InetAddress;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisLoginRateLimitAdapterTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-04T10:00:00Z"), ZoneOffset.UTC);
    private static final ClientIpAddress CLIENT_IP = ClientIpAddress.of(InetAddress.ofLiteral("192.0.2.10"));

    @Test
    void hashesLoginAndIpBeforeSendingOnlyNamespacedKeysToRedis() {
        RecordingExecutor executor = new RecordingExecutor(List.of(1L, 0L, 0L, ""));
        RedisLoginRateLimitAdapter adapter = adapter(executor, false);

        LoginRateLimitDecision decision = adapter.evaluate(" Staff01 ", CLIENT_IP);

        assertThat(decision.allowed()).isTrue();
        assertThat(executor.keys).allSatisfy(key -> {
            assertThat(key).startsWith("erp:prod:store-a:identity:rate-limit:");
            assertThat(key).doesNotContain("staff01", "192.0.2.10");
        });
        assertThat(executor.arguments.getFirst()).isEqualTo("EVAL");
        assertThat(executor.arguments.get(3)).isEqualTo("10");
        assertThat(executor.arguments.get(4)).isEqualTo("30000");
        assertThat(executor.arguments.get(5)).isEqualTo("30");
        assertThat(executor.arguments.get(6)).isEqualTo("2000");
    }

    @Test
    void mapsLimitedResultToRetryAfterAndDeterministicEventWindow() {
        RecordingExecutor first = new RecordingExecutor(List.of(0L, 30L, 1L, "window-1"));
        RecordingExecutor second = new RecordingExecutor(List.of(0L, 30L, 1L, "window-1"));

        LoginRateLimitDecision one = adapter(first, false).evaluate("staff01", CLIENT_IP);
        LoginRateLimitDecision two = adapter(second, false).evaluate("staff01", CLIENT_IP);

        assertThat(one.allowed()).isFalse();
        assertThat(one.retryAfter()).isEqualTo(java.time.Duration.ofSeconds(30));
        assertThat(one.securityEventRequired()).isTrue();
        assertThat(one.limitedWindowId()).isEqualTo(two.limitedWindowId());
    }

    @Test
    void dualKeyModeEvaluatesActiveAndPreviousBucketsWithPreviousCanonicalMarker() {
        RecordingExecutor executor = new RecordingExecutor(List.of(1L, 0L, 0L, ""));
        RedisLoginRateLimitAdapter adapter = adapter(executor, true);

        adapter.evaluate("staff01", CLIENT_IP);

        assertThat(executor.arguments.get(2)).isEqualTo("1");
        assertThat(executor.keys.subList(0, 4)).doesNotHaveDuplicates();

        RecordingExecutor oldExecutor = new RecordingExecutor(List.of(1L, 0L, 0L, ""));
        RedisLoginRateLimitAdapter oldAdapter = new RedisLoginRateLimitAdapter(oldExecutor,
                new RateLimitKeyRing(RateLimitKeyRingTest.bytes((byte) 2), null),
                new IdentityRedisNamespace("erp:prod:store-a:identity"), CLOCK);
        oldAdapter.evaluate("staff01", CLIENT_IP);

        assertThat(executor.keys.get(4)).isEqualTo(oldExecutor.keys.get(4));
    }

    @Test
    void acknowledgesOnlyTheSameDeterministicPendingWindow() {
        SequencedExecutor executor = new SequencedExecutor(
                List.of(0L, 20L, 1L, "window-42"),
                List.of(1L, 0L, 0L, "window-42"),
                List.of(1L, 0L, 0L, "window-42"));
        RedisLoginRateLimitAdapter adapter = adapter(executor, false);
        LoginRateLimitDecision decision = adapter.evaluate("staff01", CLIENT_IP);

        adapter.markSecurityEventRecorded("staff01", CLIENT_IP, decision.limitedWindowId());

        assertThat(executor.modes).containsExactly("EVAL", "INSPECT", "ACK");
    }

    @Test
    void failsClosedWhenRedisOrLuaFails() {
        RedisLoginRateLimitAdapter adapter = adapter((keys, arguments) -> {
            throw new IllegalStateException("script failure contains internal details");
        }, false);

        assertThatThrownBy(() -> adapter.evaluate("staff01", CLIENT_IP))
                .isInstanceOf(AuthenticationUnavailableException.class)
                .hasMessageNotContaining("internal details");
    }

    private RedisLoginRateLimitAdapter adapter(
            RedisLoginRateLimitAdapter.ScriptExecutor executor,
            boolean dualKey
    ) {
        return new RedisLoginRateLimitAdapter(executor,
                new RateLimitKeyRing(RateLimitKeyRingTest.bytes((byte) 1),
                        dualKey ? RateLimitKeyRingTest.bytes((byte) 2) : null),
                new IdentityRedisNamespace("erp:prod:store-a:identity"), CLOCK);
    }

    private static class RecordingExecutor implements RedisLoginRateLimitAdapter.ScriptExecutor {
        private final List<?> result;
        private List<String> keys;
        private List<String> arguments;

        private RecordingExecutor(List<?> result) {
            this.result = result;
        }

        @Override
        public List<?> execute(List<String> keys, List<String> arguments) {
            this.keys = List.copyOf(keys);
            this.arguments = List.copyOf(arguments);
            return result;
        }
    }

    private static final class SequencedExecutor implements RedisLoginRateLimitAdapter.ScriptExecutor {
        private final List<List<?>> results;
        private final List<String> modes = new ArrayList<>();
        private int index;

        @SafeVarargs
        private SequencedExecutor(List<?>... results) {
            this.results = List.of(results);
        }

        @Override
        public List<?> execute(List<String> keys, List<String> arguments) {
            modes.add(arguments.getFirst());
            return results.get(index++);
        }
    }
}
