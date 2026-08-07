package com.dorosoft.erp.audit.infrastructure.mongodb;

import com.dorosoft.erp.audit.application.port.AuditRecordRepositoryPort;
import com.dorosoft.erp.audit.application.port.SaveOutcome;
import com.dorosoft.erp.audit.domain.record.AuditRecord;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MongoAuditRecordRepository implements AuditRecordRepositoryPort {

    static final String COLLECTION = "audit_records";

    private final MongoTemplate mongoTemplate;

    public MongoAuditRecordRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public SaveOutcome save(AuditRecord record) {
        try {
            mongoTemplate.insert(AuditRecordDocumentMapper.toDocument(record), COLLECTION);
            return SaveOutcome.SAVED;
        } catch (DuplicateKeyException exception) {
            return SaveOutcome.DUPLICATE;
        }
    }
}
