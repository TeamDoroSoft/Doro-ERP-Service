package com.dorosoft.erp.identity.application.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dorosoft.erp.audit.application.api.PrivacyAccessCommand;
import com.dorosoft.erp.audit.application.api.PrivacyAccessContext;
import com.dorosoft.erp.audit.application.api.PrivacyAccessLogger;
import com.dorosoft.erp.audit.application.api.PrivacyAccessResult;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DefaultIdentityPrivacyAccessDeniedRecorderTest {
    @Test
    void deniedEmployeeReadUsesNoSubjectsAndAnEncryptedContext() {
        PrivacyAccessLogger logger = mock(PrivacyAccessLogger.class);
        PrivacyAccessContext encrypted = new PrivacyAccessContext(
                "tenant-a", "EMPLOYEE", UUID.randomUUID(), "EMPLOYEE", "req-1",
                "Y2lwaGVydGV4dA==", "v1", Instant.parse("2026-08-04T04:00:00Z"));
        when(logger.append(any(), any())).thenReturn(
                PrivacyAccessResult.success(UUID.randomUUID(), Instant.parse("2026-08-04T04:00:01Z")));

        new DefaultIdentityPrivacyAccessDeniedRecorder(logger)
                .record(DeniedPrivacyOperation.EMPLOYEE_ACCOUNT_READ, encrypted);

        ArgumentCaptor<PrivacyAccessCommand> command = ArgumentCaptor.forClass(PrivacyAccessCommand.class);
        verify(logger).append(command.capture(), org.mockito.ArgumentMatchers.same(encrypted));
        assertThat(command.getValue().subjects()).isEmpty();
        assertThat(command.getValue().resultCount()).isZero();
        assertThat(command.getValue().resultCode().name()).isEqualTo("DENIED");
    }
}
