package com.dorosoft.erp.table.application;

import com.dorosoft.erp.table.application.dto.QrTableAccessResponse;
import com.dorosoft.erp.table.application.port.TableOpenSessionReader;
import com.dorosoft.erp.table.infrastructure.persistence.StoreTableJpaRepository;
import com.dorosoft.erp.table.infrastructure.persistence.TableQrCredentialJpaRepository;
import com.dorosoft.erp.table.infrastructure.persistence.TableQrCredentialStatus;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TableQrAccessService {

    private static final String DENIED_MESSAGE = "QR access is not available.";

    private final TableQrTokenFactory tokenFactory;
    private final TableQrCredentialJpaRepository credentialRepository;
    private final StoreTableJpaRepository tableRepository;
    private final TableOpenSessionReader openSessionReader;
    private final String tenantId;

    public TableQrAccessService(
            TableQrTokenFactory tokenFactory,
            TableQrCredentialJpaRepository credentialRepository,
            StoreTableJpaRepository tableRepository,
            TableOpenSessionReader openSessionReader,
            @Value("${doro.erp.tenant-id:local-store}") String tenantId) {
        this.tokenFactory = tokenFactory;
        this.credentialRepository = credentialRepository;
        this.tableRepository = tableRepository;
        this.openSessionReader = openSessionReader;
        this.tenantId = tenantId;
    }

    @Transactional(readOnly = true)
    public QrTableAccessResponse verify(String token) {
        byte[] digest = digestToken(token);
        try {
            var credential =
                    credentialRepository
                            .findByTokenDigestAndStatus(digest, TableQrCredentialStatus.ACTIVE)
                            .orElseThrow(TableQrAccessService::denied);
            var table =
                    tableRepository
                            .findById(credential.getTableId())
                            .orElseThrow(TableQrAccessService::denied);
            if (!table.isActive()) {
                throw denied();
            }
            var session =
                    openSessionReader
                            .findOpenSession(table.getTableId())
                            .orElseThrow(TableQrAccessService::denied);

            return new QrTableAccessResponse(
                    true,
                    new QrTableAccessResponse.Store(tenantId),
                    new QrTableAccessResponse.Table(table.getTableNumber(), table.getDisplayName()),
                    new QrTableAccessResponse.Session(session.sessionId()));
        } finally {
            Arrays.fill(digest, (byte) 0);
        }
    }

    private byte[] digestToken(String token) {
        try {
            return tokenFactory.digestToken(token);
        } catch (IllegalArgumentException exception) {
            throw denied();
        }
    }

    private static TableManagementException denied() {
        return new TableManagementException(
                HttpStatus.FORBIDDEN,
                TableErrorCode.QR_ACCESS_DENIED,
                DENIED_MESSAGE);
    }
}
