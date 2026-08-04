package com.dorosoft.erp.identity.infrastructure.persistence;

import com.dorosoft.erp.identity.application.port.CredentialRepository;
import com.dorosoft.erp.identity.domain.credential.Credential;
import com.dorosoft.erp.identity.domain.credential.PasswordHistoryEntry;
import com.dorosoft.erp.identity.infrastructure.persistence.entity.CredentialJpaEntity;
import com.dorosoft.erp.identity.infrastructure.persistence.entity.PasswordHistoryJpaEntity;
import com.dorosoft.erp.identity.infrastructure.persistence.repository.CredentialSpringDataRepository;
import com.dorosoft.erp.identity.infrastructure.persistence.repository.PasswordHistorySpringDataRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class JpaCredentialRepositoryAdapter implements CredentialRepository {
    private final CredentialSpringDataRepository credentialRepository;
    private final PasswordHistorySpringDataRepository historyRepository;

    public JpaCredentialRepositoryAdapter(
            CredentialSpringDataRepository credentialRepository,
            PasswordHistorySpringDataRepository historyRepository
    ) {
        this.credentialRepository = credentialRepository;
        this.historyRepository = historyRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Credential> findByAccountId(UUID accountId) {
        return credentialRepository.findById(accountId.toString()).map(CredentialJpaEntity::toDomain);
    }

    @Override
    public Optional<Credential> findByAccountIdForUpdate(UUID accountId) {
        return credentialRepository.findByAccountIdForUpdate(accountId.toString())
                .map(CredentialJpaEntity::toDomain);
    }

    @Override
    public Credential save(Credential credential) {
        return credentialRepository.saveAndFlush(CredentialJpaEntity.fromDomain(credential)).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PasswordHistoryEntry> findRecentHistory(UUID accountId) {
        return historyRepository.findByAccountIdOrderByChangedAtDesc(accountId.toString()).stream()
                .limit(4)
                .map(PasswordHistoryJpaEntity::toDomain)
                .toList();
    }

    @Override
    public void addCurrentHashToHistoryAndKeepLatestFour(PasswordHistoryEntry entry) {
        historyRepository.save(PasswordHistoryJpaEntity.fromDomain(entry));
        List<PasswordHistoryJpaEntity> ordered = historyRepository
                .findByAccountIdOrderByChangedAtDesc(entry.employeeAccountId().toString());
        if (ordered.size() > 4) {
            historyRepository.deleteAllInBatch(ordered.subList(4, ordered.size()));
        }
    }
}
