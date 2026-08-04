package com.dorosoft.erp.identity.application.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dorosoft.erp.identity.application.error.IdentityErrorCode;
import com.dorosoft.erp.identity.application.error.IdentityException;
import com.dorosoft.erp.identity.domain.account.AccountStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EmployeeAccountApplicationServiceTest {
    private final EmployeeAccountTransactions transactions = mock(EmployeeAccountTransactions.class);
    private final IdentityAuditRecorder recorder = mock(IdentityAuditRecorder.class);
    private final EmployeeAccountApplicationService service =
            new EmployeeAccountApplicationService(transactions, recorder);
    private final UUID actorId = UUID.randomUUID();
    private final UUID targetId = UUID.randomUUID();
    private final IdentityOperationContext context = new IdentityOperationContext(
            "tenant-a", actorId, "ADMIN", "req-1", "Y2lwaGVydGV4dA==", "v1", Instant.now());

    @Test
    void missingIfMatchIsDeniedBeforeTargetLookupAndHasNoSubject() {
        assertThatThrownBy(() -> service.changeStatus(
                targetId, null,
                new EmployeeAccountApplicationService.ChangeStatusCommand("INACTIVE"), context))
                .isInstanceOfSatisfying(IdentityException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo(IdentityErrorCode.PRECONDITION_REQUIRED));

        verify(recorder).privacyUpdate(context, false, List.of());
        verify(transactions, never()).changeStatus(
                targetId, 0L,
                AccountStatus.INACTIVE, context);
    }

    @Test
    void versionMismatchAfterLookupRecordsTheConfirmedTarget() {
        var command = new EmployeeAccountApplicationService.ChangeStatusCommand("INACTIVE");
        when(transactions.changeStatus(targetId, 4L, AccountStatus.INACTIVE, context))
                .thenThrow(new IdentityException(IdentityErrorCode.PRECONDITION_FAILED));

        assertThatThrownBy(() -> service.changeStatus(targetId, "\"4\"", command, context))
                .isInstanceOf(IdentityException.class);

        verify(recorder).privacyUpdate(context, false, List.of(targetId));
    }
}
