package com.dorosoft.erp.storeaccess.application.api.identity;

import com.dorosoft.erp.platform.web.ProblemAwareException;
import com.dorosoft.erp.storeaccess.application.port.identity.EmployeeSecurityHistoryRepository;
import com.dorosoft.erp.storeaccess.application.port.identity.SecurityHistoryQuery;
import com.dorosoft.erp.storeaccess.domain.identity.EmployeeSecurityHistory;
import com.dorosoft.erp.storeaccess.domain.identity.Role;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Local security history lookup (ADR-02-015): OWNER sees every Tenant employee/Kiosk record; MANAGER sees
 * only their own actions, {@code STAFF}-targeted records, and Kiosk records; {@code STAFF} may not query at
 * all. {@link ActorContext} stands in for the authenticated Session until feature 02 step 2's login is
 * complete; a future Controller will build it from the Session and expose this as
 * {@code GET /api/v1/security-history}.
 */
@Service
public class SecurityHistoryQueryService {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Duration MAX_RANGE = Duration.ofDays(90);

    private final EmployeeSecurityHistoryRepository repository;

    public SecurityHistoryQueryService(EmployeeSecurityHistoryRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<EmployeeSecurityHistory> search(ActorContext actor, SecurityHistorySearchRequest request) {
        requireCanQuery(actor);
        validateRange(request.from(), request.to());
        int size = normalizeSize(request.size());
        UUID restrictToActorId = actor.role() == Role.MANAGER ? actor.employeeId() : null;

        SecurityHistoryQuery query = new SecurityHistoryQuery(
                actor.tenantId(), request.from(), request.to(), request.eventType(), request.targetType(),
                request.targetId(), request.result(), request.cursorOccurredAt(), request.cursorId(), size,
                restrictToActorId);
        return repository.search(query);
    }

    private void requireCanQuery(ActorContext actor) {
        if (actor.role() == Role.STAFF) {
            throw new ProblemAwareException(
                    SecurityHistoryProblemCode.SECURITY_HISTORY_ACCESS_FORBIDDEN, "보안 이력을 조회할 권한이 없습니다.");
        }
    }

    private void validateRange(Instant from, Instant to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new ProblemAwareException(
                    SecurityHistoryProblemCode.INVALID_SECURITY_HISTORY_RANGE, "조회 기간이 올바르지 않습니다.");
        }
        if (Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
            throw new ProblemAwareException(
                    SecurityHistoryProblemCode.INVALID_SECURITY_HISTORY_RANGE, "조회 기간은 최대 90일입니다.");
        }
    }

    private int normalizeSize(Integer requestedSize) {
        if (requestedSize == null) {
            return DEFAULT_PAGE_SIZE;
        }
        if (requestedSize < 1 || requestedSize > MAX_PAGE_SIZE) {
            throw new ProblemAwareException(
                    SecurityHistoryProblemCode.INVALID_SECURITY_HISTORY_PAGE_SIZE, "조회 건수는 1~100 사이여야 합니다.");
        }
        return requestedSize;
    }
}
