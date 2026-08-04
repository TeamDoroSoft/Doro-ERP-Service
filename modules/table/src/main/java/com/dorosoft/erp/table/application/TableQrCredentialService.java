package com.dorosoft.erp.table.application;

import com.dorosoft.erp.audit.application.api.AuditContext;
import com.dorosoft.erp.audit.application.api.AuditRecordCommand;
import com.dorosoft.erp.audit.application.api.AuditWriter;
import com.dorosoft.erp.audit.domain.AuditAction;
import com.dorosoft.erp.audit.domain.AuditDomain;
import com.dorosoft.erp.audit.domain.AuditPrimaryTarget;
import com.dorosoft.erp.audit.domain.AuditTargetType;
import com.dorosoft.erp.table.application.TableQrTokenFactory.GeneratedQrToken;
import com.dorosoft.erp.table.application.dto.QrCredentialIssueResponse;
import com.dorosoft.erp.table.infrastructure.persistence.StoreTableEntity;
import com.dorosoft.erp.table.infrastructure.persistence.StoreTableJpaRepository;
import com.dorosoft.erp.table.infrastructure.persistence.TableQrCredentialEntity;
import com.dorosoft.erp.table.infrastructure.persistence.TableQrCredentialJpaRepository;
import com.dorosoft.erp.table.infrastructure.persistence.TableQrCredentialStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TableQrCredentialService {

    private final StoreTableJpaRepository tableRepository;
    private final TableQrCredentialJpaRepository credentialRepository;
    private final TableQrTokenFactory tokenFactory;
    private final AuditWriter auditWriter;
    private final Clock clock;
    private final String publicBaseUrl;

    public TableQrCredentialService(
            StoreTableJpaRepository tableRepository,
            TableQrCredentialJpaRepository credentialRepository,
            TableQrTokenFactory tokenFactory,
            AuditWriter auditWriter,
            Clock clock,
            @Value("${doro.erp.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this.tableRepository = tableRepository;
        this.credentialRepository = credentialRepository;
        this.tokenFactory = tokenFactory;
        this.auditWriter = auditWriter;
        this.clock = clock;
        this.publicBaseUrl = normalizeBaseUrl(publicBaseUrl);
    }

    @Transactional
    public QrCredentialIssueResponse issue(UUID tableId, TableQrOperationContext context) {
        StoreTableEntity table = lockExistingTable(tableId);
        if (credentialRepository.findActiveByTableIdForUpdate(table.getTableId()).isPresent()) {
            throw new TableManagementException(
                    HttpStatus.CONFLICT,
                    TableErrorCode.QR_CREDENTIAL_ALREADY_ACTIVE,
                    "Active QR credential already exists for table.");
        }
        return issueNewCredential(
                table.getTableId(), null, AuditAction.TABLE_QR_ISSUED, context, clock.instant());
    }

    @Transactional
    public QrCredentialIssueResponse reissue(UUID tableId, TableQrOperationContext context) {
        StoreTableEntity table = lockExistingTable(tableId);
        Instant occurredAt = clock.instant();
        UUID predecessorCredentialId =
                credentialRepository
                        .findActiveByTableIdForUpdate(table.getTableId())
                        .map(active -> {
                            active.revoke(context.actorId(), occurredAt);
                            return active.getCredentialId();
                        })
                        .orElse(null);
        if (predecessorCredentialId != null) {
            credentialRepository.flush();
        }

        return issueNewCredential(
                table.getTableId(), predecessorCredentialId, AuditAction.TABLE_QR_ROTATED, context, occurredAt);
    }

    private StoreTableEntity lockExistingTable(UUID tableId) {
        return tableRepository
                .findByIdForUpdate(tableId)
                .orElseThrow(
                        () ->
                                new TableManagementException(
                                        HttpStatus.NOT_FOUND,
                                        TableErrorCode.TABLE_NOT_FOUND,
                                        "Table not found. tableId=" + tableId));
    }

    private QrCredentialIssueResponse issueNewCredential(
            UUID tableId,
            UUID predecessorCredentialId,
            AuditAction action,
            TableQrOperationContext context,
            Instant occurredAt) {
        UUID credentialId = UUID.randomUUID();
        GeneratedQrToken token = tokenFactory.generate();
        TableQrCredentialEntity credential =
                TableQrCredentialEntity.issue(
                        credentialId,
                        tableId,
                        token.digest(),
                        predecessorCredentialId,
                        context.actorId(),
                        occurredAt);
        try {
            credentialRepository.saveAndFlush(credential);
            recordAudit(credential, action, context);
        } catch (DataIntegrityViolationException exception) {
            throw new TableManagementException(
                    HttpStatus.CONFLICT,
                    TableErrorCode.QR_CREDENTIAL_ALREADY_ACTIVE,
                    "Active QR credential could not be created.");
        }

        return new QrCredentialIssueResponse(
                credential.getCredentialId(),
                tableId,
                predecessorCredentialId,
                TableQrCredentialStatus.ACTIVE.name(),
                credential.getIssuedAt(),
                accessUrl(token.token()));
    }

    private void recordAudit(
            TableQrCredentialEntity credential,
            AuditAction action,
            TableQrOperationContext context) {
        Map<String, Object> afterValue = new LinkedHashMap<>();
        afterValue.put("credentialId", credential.getCredentialId().toString());
        afterValue.put(
                "predecessorCredentialId",
                credential.getPredecessorId() == null ? null : credential.getPredecessorId().toString());
        afterValue.put("status", credential.getStatus().name());

        AuditRecordCommand command =
                AuditRecordCommand.builder()
                        .domain(AuditDomain.STORE)
                        .action(action)
                        .operationId(credential.getCredentialId())
                        .eventSequence(0)
                        .primaryTarget(
                                new AuditPrimaryTarget(
                                        AuditTargetType.TABLE_QR_CREDENTIAL,
                                        credential.getCredentialId()))
                        .beforeValue(Map.of())
                        .afterValue(afterValue)
                        .valueSchemaVersion(1)
                        .build();
        auditWriter.record(
                command,
                AuditContext.identityUser(
                        context.tenantId(),
                        context.actorId(),
                        context.actorRoleCode(),
                        context.actorDisplayName(),
                        context.requestId(),
                        credential.getIssuedAt()));
    }

    private String accessUrl(String token) {
        return publicBaseUrl + "/qr#token=" + token;
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl == null || baseUrl.isBlank() ? "http://localhost:8080" : baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    public record TableQrOperationContext(
            UUID actorId,
            String actorRoleCode,
            String actorDisplayName,
            String tenantId,
            String requestId) {}
}
