package com.dorosoft.erp.audit.infrastructure.mongodb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.dorosoft.erp.audit.application.port.SaveOutcome;
import com.dorosoft.erp.audit.domain.record.AuditActor;
import com.dorosoft.erp.audit.domain.record.AuditRecord;
import com.dorosoft.erp.audit.domain.record.AuditSource;
import com.dorosoft.erp.audit.domain.record.AuditTarget;
import com.dorosoft.erp.audit.infrastructure.mongodb.index.AuditMongoIndexBootstrap;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

@Testcontainers
class MongoAuditRecordRepositoryIntegrationTest {

    @Container
    private static final MongoDBContainer MONGODB = new MongoDBContainer("mongo:8.0.12");

    private static MongoClient mongoClient;
    private static MongoTemplate mongoTemplate;
    private static MongoAuditRecordRepository repository;
    private static AuditMongoIndexBootstrap indexBootstrap;

    @BeforeAll
    static void connect() {
        mongoClient = MongoClients.create(MONGODB.getReplicaSetUrl());
        mongoTemplate = new MongoTemplate(mongoClient, "audit_test");
        repository = new MongoAuditRecordRepository(mongoTemplate);
        indexBootstrap = new AuditMongoIndexBootstrap(mongoTemplate);
        mongoClient.getDatabase("admin")
                .runCommand(new Document("setParameter", 1).append("ttlMonitorSleepSecs", 1));
    }

    @AfterAll
    static void disconnect() {
        if (mongoClient != null) {
            mongoClient.close();
        }
    }

    @BeforeEach
    void resetCollectionAndIndexes() {
        if (mongoTemplate.collectionExists(AuditMongoIndexBootstrap.COLLECTION)) {
            mongoTemplate.dropCollection(AuditMongoIndexBootstrap.COLLECTION);
        }
        indexBootstrap.run(null);
    }

    @Test
    void bootstrapCreatesAllNamedIndexesAndCanRunAgain() {
        indexBootstrap.run(null);

        List<Document> indexes = mongoTemplate.getCollection(AuditMongoIndexBootstrap.COLLECTION)
                .listIndexes()
                .into(new ArrayList<>());

        assertThat(indexes).extracting(index -> index.getString("name"))
                .containsExactlyInAnyOrder(
                        "_id_",
                        "ux_source",
                        "ix_tenant_time",
                        "ix_tenant_action_time",
                        "ix_tenant_target_time",
                        "ttl_expires");
        assertThat(index(indexes, "ux_source").getBoolean("unique")).isTrue();
        assertThat(index(indexes, "ttl_expires").get("expireAfterSeconds", Number.class).longValue())
                .isZero();
    }

    @Test
    void duplicateSourceAndEventIdConvergeToOneDocument() {
        AuditRecord record = record("commerce", EVENT_ID, Instant.parse("2026-08-07T00:00:00Z"));

        assertThat(repository.save(record)).isEqualTo(SaveOutcome.SAVED);
        assertThat(repository.save(record)).isEqualTo(SaveOutcome.DUPLICATE);
        assertThat(countDocuments()).isEqualTo(1);
    }

    @Test
    void sameEventIdFromDifferentSourcesCreatesSeparateDocuments() {
        repository.save(record("commerce", EVENT_ID, Instant.parse("2026-08-07T00:00:00Z")));
        repository.save(record("payment", EVENT_ID, Instant.parse("2026-08-07T00:00:00Z")));

        assertThat(countDocuments()).isEqualTo(2);
    }

    @Test
    void reverseArrivalOrderPreservesBothAppendOnlyRecordsAndBsonDates() {
        repository.save(record("commerce", UUID.randomUUID(), Instant.parse("2026-08-07T00:00:10Z")));
        repository.save(record("commerce", UUID.randomUUID(), Instant.parse("2026-08-07T00:00:00Z")));

        List<Document> records = mongoTemplate.getCollection(AuditMongoIndexBootstrap.COLLECTION)
                .find()
                .into(new ArrayList<>());

        assertThat(records).hasSize(2);
        assertThat(records).allSatisfy(document -> {
            assertThat(document.get("occurredAt")).isInstanceOf(Date.class);
            assertThat(document.get("receivedAt")).isInstanceOf(Date.class);
            assertThat(document.get("expiresAt")).isInstanceOf(Date.class);
            assertThat(document.get("source", Document.class).getString("service")).isEqualTo("commerce");
            assertThat(document.get("actor", Document.class).getString("type")).isEqualTo("EMPLOYEE");
            assertThat(document.get("target", Document.class).getString("type")).isEqualTo("ORDER");
        });
    }

    @Test
    void expiredDocumentIsEventuallyRemovedByTheMongoTtlMonitor() {
        AuditRecord expired = record("commerce", UUID.randomUUID(), Instant.now().minus(Duration.ofDays(100)));

        repository.save(expired);

        await().atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(250))
                .untilAsserted(() -> assertThat(countDocuments()).isZero());
    }

    private static final UUID EVENT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    private AuditRecord record(String sourceService, UUID eventId, Instant occurredAt) {
        return new AuditRecord(
                new AuditSource(sourceService, eventId),
                1,
                UUID.fromString("20000000-0000-0000-0000-000000000002"),
                null,
                new AuditActor(
                        "EMPLOYEE",
                        UUID.fromString("40000000-0000-0000-0000-000000000004"),
                        "MANAGER"),
                "ORDER_ACCEPTED",
                new AuditTarget(
                        "ORDER",
                        UUID.fromString("50000000-0000-0000-0000-000000000005")),
                "SUCCESS",
                null,
                Map.of("orderNumber", "A-001"),
                "req-123",
                occurredAt,
                Instant.parse("2026-08-07T00:01:00Z"),
                occurredAt.plus(Duration.ofDays(90)));
    }

    private long countDocuments() {
        return mongoTemplate.getCollection(AuditMongoIndexBootstrap.COLLECTION).countDocuments();
    }

    private Document index(List<Document> indexes, String name) {
        return indexes.stream()
                .filter(index -> name.equals(index.getString("name")))
                .findFirst()
                .orElseThrow();
    }
}
