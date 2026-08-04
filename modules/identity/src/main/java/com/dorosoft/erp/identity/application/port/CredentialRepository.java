package com.dorosoft.erp.identity.application.port;

import com.dorosoft.erp.identity.domain.credential.Credential;
import com.dorosoft.erp.identity.domain.credential.PasswordHistoryEntry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CredentialRepository {
    Optional<Credential> findByAccountId(UUID accountId);

    Optional<Credential> findByAccountIdForUpdate(UUID accountId);

    Credential save(Credential credential);

    List<PasswordHistoryEntry> findRecentHistory(UUID accountId);

    void addCurrentHashToHistoryAndKeepLatestFour(PasswordHistoryEntry entry);
}
