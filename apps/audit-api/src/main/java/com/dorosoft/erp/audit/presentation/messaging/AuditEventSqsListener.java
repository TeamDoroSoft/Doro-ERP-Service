package com.dorosoft.erp.audit.presentation.messaging;

import com.dorosoft.erp.audit.application.api.ingest.IngestAuditEventUseCase;
import com.dorosoft.erp.audit.application.api.ingest.IngestOutcome;
import com.dorosoft.erp.platform.messaging.audit.AuditEventEnvelope;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.model.Message;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class AuditEventSqsListener {

    private static final Logger log = LoggerFactory.getLogger(AuditEventSqsListener.class);

    private final ObjectMapper objectMapper;
    private final IngestAuditEventUseCase useCase;
    private final AuditConsumerMetrics metrics;

    public AuditEventSqsListener(
            ObjectMapper objectMapper,
            IngestAuditEventUseCase useCase,
            AuditConsumerMetrics metrics) {
        this.objectMapper = objectMapper;
        this.useCase = useCase;
        this.metrics = metrics;
    }

    @SqsListener(
            queueNames = "${audit.sqs.queue-name}",
            factory = "auditSqsListenerContainerFactory",
            id = "auditEventSqsListener")
    public void receive(String payload, Acknowledgement acknowledgement, Message originalMessage) {
        AuditEventEnvelope event = null;
        try {
            event = objectMapper.readValue(payload, AuditEventEnvelope.class);
            IngestOutcome outcome = useCase.handle(event);
            acknowledge(acknowledgement);
            metrics.processed(outcome);
            log.info(
                    "Audit event processed: traceId={}, sourceService={}, eventId={}, outcome={}",
                    event.traceId(),
                    event.sourceService(),
                    event.eventId(),
                    outcome);
        } catch (JacksonException exception) {
            fail(originalMessage, null, AuditConsumerMetrics.FailureCategory.DESERIALIZATION);
            throw new AuditEventProcessingException("Audit event deserialization failed", exception);
        } catch (IllegalArgumentException exception) {
            fail(originalMessage, event, AuditConsumerMetrics.FailureCategory.CONTRACT);
            throw new AuditEventProcessingException("Audit event contract validation failed", exception);
        } catch (AuditAcknowledgementException exception) {
            fail(originalMessage, event, AuditConsumerMetrics.FailureCategory.ACKNOWLEDGEMENT);
            throw exception;
        } catch (RuntimeException exception) {
            fail(originalMessage, event, AuditConsumerMetrics.FailureCategory.PERSISTENCE_OR_INTERNAL);
            throw exception;
        }
    }

    private void acknowledge(Acknowledgement acknowledgement) {
        try {
            acknowledgement.acknowledge();
        } catch (RuntimeException exception) {
            throw new AuditAcknowledgementException("Audit event acknowledgement failed", exception);
        }
    }

    private void fail(
            Message originalMessage,
            AuditEventEnvelope event,
            AuditConsumerMetrics.FailureCategory category) {
        metrics.failed(category);
        log.warn(
                "Audit event processing failed: messageId={}, eventId={}, category={}",
                originalMessage == null ? null : originalMessage.messageId(),
                event == null ? null : event.eventId(),
                category);
    }

    private static final class AuditAcknowledgementException extends AuditEventProcessingException {

        private AuditAcknowledgementException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
