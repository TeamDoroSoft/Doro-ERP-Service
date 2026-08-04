package com.dorosoft.erp.identity.infrastructure.persistence;

import com.dorosoft.erp.identity.application.port.IdentitySecurityEventRepository;
import com.dorosoft.erp.identity.domain.securityevent.IdentitySecurityEvent;
import com.dorosoft.erp.identity.infrastructure.persistence.repository.IdentitySecurityEventSpringDataRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class JpaIdentitySecurityEventRepositoryAdapter implements IdentitySecurityEventRepository {
    private final IdentitySecurityEventSpringDataRepository repository;

    public JpaIdentitySecurityEventRepositoryAdapter(IdentitySecurityEventSpringDataRepository repository) {
        this.repository = repository;
    }

    @Override
    public void appendIfAbsent(IdentitySecurityEvent event) {
        repository.insertIfAbsent(
                event.securityEventId().toString(),
                event.accountId() == null ? null : event.accountId().toString(),
                event.eventType().name(),
                event.outcome().name(),
                event.failureClass() == null ? null : event.failureClass().name(),
                event.requestId(),
                event.occurredAt()
        );
    }
}
