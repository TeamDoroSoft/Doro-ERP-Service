package com.dorosoft.erp.storeaccess.infrastructure.identity.persistence;

import com.dorosoft.erp.storeaccess.application.port.identity.EmployeeSecurityHistoryRepository;
import com.dorosoft.erp.storeaccess.application.port.identity.SecurityHistoryQuery;
import com.dorosoft.erp.storeaccess.domain.identity.EmployeeSecurityHistory;
import com.dorosoft.erp.storeaccess.domain.identity.Role;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

@Repository
class EmployeeSecurityHistoryRepositoryAdapter implements EmployeeSecurityHistoryRepository {

    private final EmployeeSecurityHistoryJpaRepository jpaRepository;
    private final Clock clock;

    EmployeeSecurityHistoryRepositoryAdapter(EmployeeSecurityHistoryJpaRepository jpaRepository, Clock clock) {
        this.jpaRepository = jpaRepository;
        this.clock = clock;
    }

    @Override
    public Optional<EmployeeSecurityHistory> findById(UUID id) {
        return jpaRepository.findById(id).map(EmployeeSecurityHistoryEntity::toDomain);
    }

    @Override
    public EmployeeSecurityHistory save(EmployeeSecurityHistory history) {
        EmployeeSecurityHistoryEntity saved = jpaRepository.saveAndFlush(EmployeeSecurityHistoryEntity.fromDomain(history));
        return saved.toDomain();
    }

    @Override
    public List<EmployeeSecurityHistory> search(SecurityHistoryQuery query) {
        Sort sort = Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"));
        return jpaRepository.findAll(toSpecification(query, clock.instant()), PageRequest.of(0, query.size(), sort))
                .stream()
                .map(EmployeeSecurityHistoryEntity::toDomain)
                .toList();
    }

    private static Specification<EmployeeSecurityHistoryEntity> toSpecification(SecurityHistoryQuery query, Instant now) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), query.tenantId()));
            predicates.add(cb.greaterThan(root.get("expiresAt"), now));
            predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), query.from()));
            predicates.add(cb.lessThanOrEqualTo(root.get("occurredAt"), query.to()));
            if (query.eventType() != null) {
                predicates.add(cb.equal(root.get("eventType"), query.eventType()));
            }
            if (query.targetType() != null) {
                predicates.add(cb.equal(root.get("targetType"), query.targetType()));
            }
            if (query.targetId() != null) {
                predicates.add(cb.equal(root.get("targetId"), query.targetId()));
            }
            if (query.result() != null) {
                predicates.add(cb.equal(root.get("result"), query.result()));
            }
            if (query.cursorOccurredAt() != null && query.cursorId() != null) {
                predicates.add(cb.or(
                        cb.lessThan(root.get("occurredAt"), query.cursorOccurredAt()),
                        cb.and(
                                cb.equal(root.get("occurredAt"), query.cursorOccurredAt()),
                                cb.lessThan(root.get("id"), query.cursorId()))));
            }
            if (query.restrictToActorId() != null) {
                Subquery<UUID> staffIds = criteriaQuery.subquery(UUID.class);
                var staffRoot = staffIds.from(EmployeeAccountEntity.class);
                staffIds.select(staffRoot.get("id"))
                        .where(
                                cb.equal(staffRoot.get("tenantId"), query.tenantId()),
                                cb.equal(staffRoot.get("role"), Role.STAFF));
                predicates.add(cb.or(
                        cb.and(
                                cb.equal(root.get("targetType"), "EMPLOYEE"),
                                cb.equal(root.get("targetId"), query.restrictToActorId())),
                        cb.and(cb.equal(root.get("targetType"), "EMPLOYEE"), root.get("targetId").in(staffIds)),
                        cb.equal(root.get("targetType"), "KIOSK_DEVICE")));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Override
    public int deleteExpiredBatch(Instant now, int batchSize) {
        return jpaRepository.deleteExpiredBatch(now, batchSize);
    }
}
