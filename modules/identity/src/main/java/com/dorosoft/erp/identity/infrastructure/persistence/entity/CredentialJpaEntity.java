package com.dorosoft.erp.identity.infrastructure.persistence.entity;

import com.dorosoft.erp.identity.domain.credential.Credential;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "credential")
public class CredentialJpaEntity {
    @Id
    @Column(name = "employee_account_id", nullable = false, columnDefinition = "CHAR(36)")
    private String accountId;
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;
    @Version
    @Column(name = "credential_version", nullable = false)
    private long credentialVersion;
    @Column(name = "password_changed_at", nullable = false)
    private Instant passwordChangedAt;

    protected CredentialJpaEntity() {
    }

    public static CredentialJpaEntity fromDomain(Credential credential) {
        CredentialJpaEntity entity = new CredentialJpaEntity();
        entity.accountId = credential.employeeAccountId().toString();
        entity.passwordHash = credential.passwordHash();
        entity.mustChangePassword = credential.mustChangePassword();
        entity.credentialVersion = credential.credentialVersion();
        entity.passwordChangedAt = credential.passwordChangedAt();
        return entity;
    }

    public Credential toDomain() {
        return new Credential(
                UUID.fromString(accountId), passwordHash, mustChangePassword,
                credentialVersion, passwordChangedAt
        );
    }
}
