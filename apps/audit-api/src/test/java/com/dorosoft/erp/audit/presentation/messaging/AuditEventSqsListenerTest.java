package com.dorosoft.erp.audit.presentation.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.dorosoft.erp.audit.application.api.ingest.IngestAuditEventUseCase;
import com.dorosoft.erp.audit.application.api.ingest.IngestOutcome;
import com.dorosoft.erp.platform.messaging.audit.AuditActor;
import com.dorosoft.erp.platform.messaging.audit.AuditEventEnvelope;
import com.dorosoft.erp.platform.messaging.audit.AuditTarget;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sqs.model.Message;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

class AuditEventSqsListenerTest {

    private final JsonMapper jsonMapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    @Test
    void savedOutcomeIsAcknowledgedAndCounted() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RecordingAcknowledgement acknowledgement = new RecordingAcknowledgement(false);
        AuditEventSqsListener listener = listener(event -> IngestOutcome.SAVED, registry);

        listener.receive(jsonMapper.writeValueAsString(event()), acknowledgement, message());

        assertThat(acknowledgement.acknowledged).isTrue();
        assertThat(registry.get("audit.consumer.processed").tag("outcome", "saved").counter().count())
                .isEqualTo(1);
    }

    @Test
    void alreadyExistingOutcomeIsAlsoAcknowledged() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RecordingAcknowledgement acknowledgement = new RecordingAcknowledgement(false);
        AuditEventSqsListener listener = listener(event -> IngestOutcome.ALREADY_EXISTS, registry);

        listener.receive(jsonMapper.writeValueAsString(event()), acknowledgement, message());

        assertThat(acknowledgement.acknowledged).isTrue();
        assertThat(registry.get("audit.consumer.processed")
                        .tag("outcome", "already_exists")
                        .counter()
                        .count())
                .isEqualTo(1);
    }

    @Test
    void invalidJsonIsNotAcknowledgedAndPayloadIsNotLogged() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RecordingAcknowledgement acknowledgement = new RecordingAcknowledgement(false);
        AuditEventSqsListener listener = listener(event -> IngestOutcome.SAVED, registry);
        Logger logger = (Logger) LoggerFactory.getLogger(AuditEventSqsListener.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            assertThatThrownBy(() -> listener.receive(
                            "{\"password\":\"do-not-log-this\"",
                            acknowledgement,
                            message()))
                    .isInstanceOf(AuditEventProcessingException.class)
                    .hasMessage("Audit event deserialization failed");
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(acknowledgement.acknowledged).isFalse();
        assertThat(registry.get("audit.consumer.failed")
                        .tag("category", "deserialization")
                        .counter()
                        .count())
                .isEqualTo(1);
        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .allSatisfy(message -> assertThat(message).doesNotContain("password", "do-not-log-this"));
    }

    @Test
    void contractFailureIsNotAcknowledged() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RecordingAcknowledgement acknowledgement = new RecordingAcknowledgement(false);
        AuditEventSqsListener listener = listener(event -> {
            throw new IllegalArgumentException("invalid contract");
        }, registry);

        assertThatThrownBy(() -> listener.receive(
                        jsonMapper.writeValueAsString(event()),
                        acknowledgement,
                        message()))
                .isInstanceOf(AuditEventProcessingException.class)
                .hasMessage("Audit event contract validation failed");

        assertThat(acknowledgement.acknowledged).isFalse();
        assertThat(registry.get("audit.consumer.failed").tag("category", "contract").counter().count())
                .isEqualTo(1);
    }

    @Test
    void persistenceFailureIsNotAcknowledged() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RecordingAcknowledgement acknowledgement = new RecordingAcknowledgement(false);
        RuntimeException failure = new RuntimeException("mongodb unavailable");
        AuditEventSqsListener listener = listener(event -> {
            throw failure;
        }, registry);

        assertThatThrownBy(() -> listener.receive(
                        jsonMapper.writeValueAsString(event()),
                        acknowledgement,
                        message()))
                .isSameAs(failure);

        assertThat(acknowledgement.acknowledged).isFalse();
        assertThat(registry.get("audit.consumer.failed")
                        .tag("category", "persistence_or_internal")
                        .counter()
                        .count())
                .isEqualTo(1);
    }

    @Test
    void acknowledgementFailureIsRetriedAndCountedSeparately() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RecordingAcknowledgement acknowledgement = new RecordingAcknowledgement(true);
        AuditEventSqsListener listener = listener(event -> IngestOutcome.SAVED, registry);

        assertThatThrownBy(() -> listener.receive(
                        jsonMapper.writeValueAsString(event()),
                        acknowledgement,
                        message()))
                .isInstanceOf(AuditEventProcessingException.class)
                .hasMessage("Audit event acknowledgement failed");

        assertThat(registry.get("audit.consumer.failed")
                        .tag("category", "acknowledgement")
                        .counter()
                        .count())
                .isEqualTo(1);
    }

    private AuditEventSqsListener listener(IngestAuditEventUseCase useCase, SimpleMeterRegistry registry) {
        return new AuditEventSqsListener(jsonMapper, useCase, new AuditConsumerMetrics(registry));
    }

    private Message message() {
        return Message.builder().messageId("message-123").build();
    }

    private AuditEventEnvelope event() {
        return new AuditEventEnvelope(
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                "AuditRecorded",
                1,
                "commerce",
                UUID.fromString("20000000-0000-0000-0000-000000000002"),
                null,
                new AuditActor(
                        "EMPLOYEE",
                        UUID.fromString("40000000-0000-0000-0000-000000000004"),
                        "MANAGER"),
                "ORDER_ACCEPTED",
                new AuditTarget(
                        "ORDER",
                        UUID.fromString("50000000-0000-0000-0000-000000000005")),
                "SUCCESS",
                null,
                Map.of("orderNumber", "A-001"),
                "req-123",
                Instant.parse("2026-08-07T00:00:00Z"));
    }

    private static final class RecordingAcknowledgement implements Acknowledgement {

        private final boolean fail;
        private boolean acknowledged;

        private RecordingAcknowledgement(boolean fail) {
            this.fail = fail;
        }

        @Override
        public void acknowledge() {
            if (fail) {
                throw new RuntimeException("ack failed");
            }
            acknowledged = true;
        }

        @Override
        public CompletableFuture<Void> acknowledgeAsync() {
            acknowledge();
            return CompletableFuture.completedFuture(null);
        }
    }
}
