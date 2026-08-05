package com.dorosoft.erp.table.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dorosoft.erp.table.application.TableQrTokenFactory.GeneratedQrToken;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * TABLE-10 인수·보안 검증: QR Token 생성·Digest 변환 로직에 대한 순수 단위 테스트.
 *
 * <p>Spring Context, DB, Docker가 필요 없는 {@link TableQrTokenFactory} 자체 로직만 검증한다. 목적은 (1) Token
 * 추측 공격에 대비한 엔트로피·형식, (2) 원문이 아닌 Digest만으로 재조회가 가능해야 하는 계약, (3) 위·변조되었거나
 * 무효한 Token 입력에 대한 거부를 코드 레벨에서 보장하는 데 있다.
 */
class TableQrTokenFactoryTest {

    private final TableQrTokenFactory factory = new TableQrTokenFactory();

    @Test
    @DisplayName("생성된 Token은 32byte 난수를 Padding 없는 Base64url 43자로 인코딩한다")
    void generate_producesUrlSafeTokenWithoutPadding() {
        GeneratedQrToken generated = factory.generate();

        assertEquals(43, generated.token().length(), "32byte -> Base64url without padding must be 43 chars");
        assertTrue(generated.token().matches("^[A-Za-z0-9_-]+$"), "token must be URL-safe Base64 without padding");
        assertTrue(generated.token().indexOf('=') < 0, "token must not contain Base64 padding");
    }

    @Test
    @DisplayName("생성된 Digest는 Token 원문의 SHA-256이며 32byte이다")
    void generate_digestMatchesSha256OfDecodedToken() throws Exception {
        GeneratedQrToken generated = factory.generate();
        byte[] decoded = Base64.getUrlDecoder().decode(generated.token());

        byte[] expectedDigest = MessageDigest.getInstance("SHA-256").digest(decoded);

        assertEquals(32, generated.digest().length);
        assertTrue(java.util.Arrays.equals(expectedDigest, generated.digest()));
    }

    @Test
    @DisplayName("반복 생성한 Token은 서로 다르며 추측 불가능한 무작위성을 가진다")
    void generate_isNotPredictable() {
        Set<String> tokens = new HashSet<>();
        Set<String> digests = new HashSet<>();
        IntStream.range(0, 200)
                .mapToObj(ignored -> factory.generate())
                .forEach(generated -> {
                    tokens.add(generated.token());
                    digests.add(Base64.getEncoder().encodeToString(generated.digest()));
                });

        assertEquals(200, tokens.size(), "generated tokens must not collide");
        assertEquals(200, digests.size(), "generated digests must not collide");
    }

    @Test
    @DisplayName("digestToken은 발급 시 반환된 Digest와 동일한 값을 재산출한다 (조회 계약 검증)")
    void digestToken_reproducesSameDigestAsGeneratedToken() {
        GeneratedQrToken generated = factory.generate();

        byte[] recomputed = factory.digestToken(generated.token());

        assertTrue(java.util.Arrays.equals(generated.digest(), recomputed));
    }

    @Test
    @DisplayName("null 또는 공백 Token은 즉시 거부된다")
    void digestToken_rejectsNullOrBlank() {
        assertThrows(IllegalArgumentException.class, () -> factory.digestToken(null));
        assertThrows(IllegalArgumentException.class, () -> factory.digestToken(""));
        assertThrows(IllegalArgumentException.class, () -> factory.digestToken("   "));
    }

    @Test
    @DisplayName("과도하게 긴 문자열은 디코딩을 시도하지 않고 즉시 거부된다")
    void digestToken_rejectsOverlyLongInput() {
        String overlyLong = "A".repeat(129);

        assertThrows(IllegalArgumentException.class, () -> factory.digestToken(overlyLong));
    }

    @Test
    @DisplayName("Base64url 형식이 아닌 Token은 거부된다 (형식 추측 공격 방어)")
    void digestToken_rejectsMalformedBase64() {
        assertThrows(IllegalArgumentException.class, () -> factory.digestToken("not-base64-!!!@@@###"));
    }

    @Test
    @DisplayName("32byte로 디코딩되지 않는 Token은 거부된다 (길이 위·변조 방어)")
    void digestToken_rejectsWrongDecodedLength() {
        String shortToken = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(new byte[16]);
        String longToken = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(new byte[48]);

        assertThrows(IllegalArgumentException.class, () -> factory.digestToken(shortToken));
        assertThrows(IllegalArgumentException.class, () -> factory.digestToken(longToken));
    }

    @Test
    @DisplayName("서로 다른 두 Token은 서로 다른 Digest로 변환된다")
    void digestToken_differentTokensProduceDifferentDigests() {
        GeneratedQrToken first = factory.generate();
        GeneratedQrToken second = factory.generate();

        byte[] firstDigest = factory.digestToken(first.token());
        byte[] secondDigest = factory.digestToken(second.token());

        assertNotEquals(Base64.getEncoder().encodeToString(firstDigest),
                Base64.getEncoder().encodeToString(secondDigest));
    }
}
