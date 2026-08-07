package com.dorosoft.erp.audit.infrastructure.mongodb;

import com.dorosoft.erp.audit.domain.record.AuditRecord;
import java.util.Date;
import org.bson.Document;

final class AuditRecordDocumentMapper {

    private AuditRecordDocumentMapper() {}

    static Document toDocument(AuditRecord record) {
        Document source = new Document("service", record.source().service())
                .append("eventId", record.source().eventId().toString());
        Document actor = new Document("type", record.actor().type())
                .append("id", record.actor().id().toString())
                .append("role", record.actor().role());
        Document target = new Document("type", record.target().type())
                .append("id", record.target().id().toString());

        return new Document("source", source)
                .append("eventVersion", record.eventVersion())
                .append("tenantId", record.tenantId().toString())
                .append("storeId", record.storeId() == null ? null : record.storeId().toString())
                .append("actor", actor)
                .append("action", record.action())
                .append("target", target)
                .append("result", record.result())
                .append("reasonCode", record.reasonCode())
                .append("metadata", new Document(record.metadata()))
                .append("traceId", record.traceId())
                .append("occurredAt", Date.from(record.occurredAt()))
                .append("receivedAt", Date.from(record.receivedAt()))
                .append("expiresAt", Date.from(record.expiresAt()));
    }
}
