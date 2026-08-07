package com.dorosoft.erp.audit.infrastructure.mongodb.index;

import java.time.Duration;
import org.bson.Document;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

@Component
public class AuditMongoIndexBootstrap implements ApplicationRunner {

    public static final String COLLECTION = "audit_records";

    private final MongoTemplate mongoTemplate;

    public AuditMongoIndexBootstrap(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        var indexOperations = mongoTemplate.indexOps(COLLECTION);
        indexOperations.createIndex(compoundIndex("ux_source", new Document("source.service", 1)
                .append("source.eventId", 1)).unique());
        indexOperations.createIndex(compoundIndex("ix_tenant_time", new Document("tenantId", 1)
                .append("occurredAt", -1)
                .append("_id", -1)));
        indexOperations.createIndex(compoundIndex("ix_tenant_action_time", new Document("tenantId", 1)
                .append("action", 1)
                .append("occurredAt", -1)
                .append("_id", -1)));
        indexOperations.createIndex(compoundIndex("ix_tenant_target_time", new Document("tenantId", 1)
                .append("target.type", 1)
                .append("target.id", 1)
                .append("occurredAt", -1)
                .append("_id", -1)));
        indexOperations.createIndex(new Index()
                .on("expiresAt", Direction.ASC)
                .expire(Duration.ZERO)
                .named("ttl_expires"));
    }

    private Index compoundIndex(String name, Document keys) {
        return new CompoundIndexDefinition(keys).named(name);
    }
}
