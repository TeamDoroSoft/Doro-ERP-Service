package com.dorosoft.erp.audit.application.usecase;

import com.dorosoft.erp.audit.application.api.AuditContractException;
import com.dorosoft.erp.audit.application.api.AuditErrorCode;
import com.dorosoft.erp.audit.application.api.PrivacyAccessCommand;
import com.dorosoft.erp.audit.application.api.PrivacyAccessContext;
import com.dorosoft.erp.audit.application.api.PrivacyAccessSubject;
import com.dorosoft.erp.audit.application.port.PrivacyAccessAppendPort;
import com.dorosoft.erp.audit.application.model.PrivacyAccessRecord;
import com.dorosoft.erp.audit.domain.PrivacyAccessAction;
import com.dorosoft.erp.audit.domain.PrivacyPurposeCode;
import com.dorosoft.erp.audit.domain.PrivacyResourceType;
import com.dorosoft.erp.audit.domain.PrivacyResultCode;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultPrivacyAccessLoggerTest {
    private static final Instant ACCESSED_AT = Instant.parse("2026-08-04T11:00:00Z");

    @Test
    void appendsSubjectsAndCalculatesTwoYearRetention() {
        CapturingPort port = new CapturingPort();
        DefaultPrivacyAccessLogger logger = new DefaultPrivacyAccessLogger(
                port, Clock.fixed(ACCESSED_AT.plusSeconds(1), ZoneOffset.UTC));
        UUID subjectId = UUID.randomUUID();

        var result = logger.append(command(PrivacyResultCode.SUCCESS,
                List.of(new PrivacyAccessSubject("EMPLOYEE", subjectId)), 1), context());

        assertTrue(result.accepted());
        assertEquals(ACCESSED_AT.atZone(ZoneOffset.UTC).plusYears(2).toInstant(), port.record.retentionUntil());
        assertEquals(subjectId, port.record.command().subjects().getFirst().subjectId());
    }

    @Test
    void deniedResultMayRecordAResolvedSubjectButCountMustMatch() {
        CapturingPort port = new CapturingPort();
        DefaultPrivacyAccessLogger logger = new DefaultPrivacyAccessLogger(
                port, Clock.fixed(ACCESSED_AT, ZoneOffset.UTC));
        UUID subjectId = UUID.randomUUID();

        logger.append(command(PrivacyResultCode.DENIED,
                List.of(new PrivacyAccessSubject("EMPLOYEE", subjectId)), 1), context());
        AuditContractException exception = assertThrows(AuditContractException.class,
                () -> logger.append(command(PrivacyResultCode.DENIED,
                        List.of(new PrivacyAccessSubject("EMPLOYEE", subjectId)), 0), context()));

        assertEquals(subjectId, port.record.command().subjects().getFirst().subjectId());
        assertEquals(AuditErrorCode.PRIVACY_ACCESS_LOG_UNAVAILABLE, exception.code());
    }

    private PrivacyAccessCommand command(PrivacyResultCode result, List<PrivacyAccessSubject> subjects, int count) {
        return new PrivacyAccessCommand(PrivacyResourceType.EMPLOYEE_ACCOUNT, PrivacyAccessAction.READ,
                PrivacyPurposeCode.STORE_OPERATION, result, subjects, count);
    }

    private PrivacyAccessContext context() {
        return new PrivacyAccessContext(
                "local-store", "ADMIN", UUID.randomUUID(), "ADMIN", "req-privacy",
                Base64.getEncoder().encodeToString(new byte[]{1, 2, 3, 4}), "v1", ACCESSED_AT);
    }

    private static final class CapturingPort implements PrivacyAccessAppendPort {
        private PrivacyAccessRecord record;

        @Override
        public void append(PrivacyAccessRecord record) {
            this.record = record;
        }
    }
}
