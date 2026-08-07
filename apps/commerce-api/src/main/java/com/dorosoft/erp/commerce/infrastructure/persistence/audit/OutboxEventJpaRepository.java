package com.dorosoft.erp.commerce.infrastructure.persistence.audit;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, UUID> {

    List<OutboxEventEntity> findByTenantIdAndAggregateIdOrderByOccurredAtAsc(UUID tenantId, UUID aggregateId);

    List<OutboxEventEntity> findByTenantIdOrderByOccurredAtAsc(UUID tenantId);
}
