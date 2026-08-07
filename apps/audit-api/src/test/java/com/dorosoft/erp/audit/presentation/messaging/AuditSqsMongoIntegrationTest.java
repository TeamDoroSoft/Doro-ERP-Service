package com.dorosoft.erp.audit.presentation.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.dorosoft.erp.audit.application.port.AuditRecordRepositoryPort;
import com.dorosoft.erp.audit.application.port.SaveOutcome;
import com.dorosoft.erp.audit.domain.record.AuditRecord;
import com.dorosoft.erp.audit.infrastructure.mongodb.MongoAuditRecordRepository;
import com.dorosoft.erp.audit.infrastructure.mongodb.index.AuditMongoIndexBootstrap;
import com.dorosoft.erp.platform.messaging.audit.AuditActor;
import com.dorosoft.erp.platform.messaging.audit.AuditEventEnvelope;
import com.dorosoft.erp.platform.messaging.audit.AuditTarget;
import io.awspring.cloud.sqs.listener.MessageListenerContainer;
import io.awspring.cloud.sqs.listener.MessageListenerContainerRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(AuditSqsMongoIntegrationTest.FailureInjectionConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuditSqsMongoIntegrationTest {

    private static final String CONTRACT_DLQ = "audit-events-dlq.fifo";
    private static final String CONTRACT_QUEUE = "audit-events.fifo";
    private static final String TEST_DLQ = "audit-events-consumer-test-dlq.fifo";
    private static final String TEST_QUEUE = "audit-events-consumer-test.fifo";

    @Container
    private static final MongoDBContainer MONGODB = new MongoDBContainer("mongo:8.0.12");

    @Container
    private static final LocalStackContainer LOCALSTACK = new LocalStackContainer(
                    DockerImageName.parse("localstack/localstack:4.7.0"))
            .withServices("sqs");

    private static SqsAsyncClient sqsClient;
    private static String contractQueueUrl;
    private static String contractDlqUrl;
    private static String testQueueUrl;
    private static String testDlqUrl;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private ToggleAuditRecordRepository toggleRepository;

    @Autowired
    private MessageListenerContainerRegistry listenerContainerRegistry;

    @DynamicPropertySource
    static void configureApplication(DynamicPropertyRegistry registry) {
        initializeSqs();
        registry.add("spring.mongodb.uri", MONGODB::getReplicaSetUrl);
        registry.add("spring.cloud.aws.endpoint", () -> LOCALSTACK.getEndpoint().toString());
        registry.add("spring.cloud.aws.region.static", LOCALSTACK::getRegion);
        registry.add("spring.cloud.aws.credentials.access-key", LOCALSTACK::getAccessKey);
        registry.add("spring.cloud.aws.credentials.secret-key", LOCALSTACK::getSecretKey);
        registry.add("audit.sqs.queue-name", () -> TEST_QUEUE);
        registry.add("audit.sqs.poll-timeout", () -> "1s");
        registry.add("audit.sqs.max-messages-per-poll", () -> "10");
        registry.add("audit.sqs.max-concurrent-messages", () -> "10");
    }

    @AfterAll
    static void closeSqsClient() {
        if (sqsClient != null) {
            sqsClient.close();
        }
    }

    @BeforeEach
    void resetFailureInjection() {
        toggleRepository.reset();
    }

    @Test
    void localQueueContractsMatchRetentionVisibilityAndRedrivePolicy() {
        GetQueueAttributesResponse source = attributes(contractQueueUrl);
        GetQueueAttributesResponse dlq = attributes(contractDlqUrl);

        assertThat(source.attributes().get(QueueAttributeName.FIFO_QUEUE)).isEqualTo("true");
        assertThat(source.attributes().get(QueueAttributeName.MESSAGE_RETENTION_PERIOD)).isEqualTo("1209600");
        assertThat(source.attributes().get(QueueAttributeName.VISIBILITY_TIMEOUT)).isEqualTo("30");
        assertThat(source.attributes().get(QueueAttributeName.REDRIVE_POLICY))
                .contains("\"maxReceiveCount\":\"5\"")
                .contains(CONTRACT_DLQ);
        assertThat(dlq.attributes().get(QueueAttributeName.FIFO_QUEUE)).isEqualTo("true");
        assertThat(dlq.attributes().get(QueueAttributeName.MESSAGE_RETENTION_PERIOD)).isEqualTo("1209600");
    }

    @Test
    void validMessageWithUnknownFieldIsStoredThenRemovedFromTheSourceQueue() throws Exception {
        UUID eventId = UUID.randomUUID();
        String json = objectMapper.writeValueAsString(event(eventId, 1));
        String forwardCompatibleJson = json.substring(0, json.length() - 1) + ",\"futureField\":true}";

        send(testQueueUrl, forwardCompatibleJson, "tenant-valid");

        awaitDocument(eventId);
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(messages(testQueueUrl)).noneMatch(message ->
                        message.body().contains(eventId.toString())));
    }

    @Test
    void duplicateEventIdsWithDifferentSqsDeduplicationIdsConvergeToOneDocument() throws Exception {
        UUID eventId = UUID.randomUUID();
        String body = objectMapper.writeValueAsString(event(eventId, 1));

        send(testQueueUrl, body, "tenant-duplicate");
        send(testQueueUrl, body, "tenant-duplicate");

        awaitDocument(eventId);
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(documentCount(eventId)).isEqualTo(1));
    }

    @Test
    void invalidMessageDoesNotBlockAValidMessageFromAnotherFifoGroup() throws Exception {
        UUID validEventId = UUID.randomUUID();

        send(testQueueUrl, "{not-json", "tenant-bad");
        send(testQueueUrl, objectMapper.writeValueAsString(event(validEventId, 1)), "tenant-good");

        awaitDocument(validEventId);
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(messages(testDlqUrl)).anyMatch(message ->
                        message.body().contains("{not-json")));
    }

    @Test
    void repositoryFailureSurvivesConsumerRestartAndSucceedsAfterRecovery() throws Exception {
        UUID eventId = UUID.randomUUID();
        MessageListenerContainer<?> container = listenerContainerRegistry.getContainerById("auditEventSqsListener");
        toggleRepository.fail();

        send(testQueueUrl, objectMapper.writeValueAsString(event(eventId, 1)), "tenant-restart");
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(toggleRepository.attempts()).isPositive());

        CountDownLatch stopped = new CountDownLatch(1);
        container.stop(stopped::countDown);
        assertThat(stopped.await(30, TimeUnit.SECONDS)).isTrue();
        try {
            toggleRepository.recover();
            container.start();
            awaitDocument(eventId);
        } finally {
            if (!container.isRunning()) {
                container.start();
            }
        }

        assertThat(toggleRepository.attempts()).isGreaterThanOrEqualTo(2);
        assertThat(documentCount(eventId)).isEqualTo(1);
    }

    @Test
    void unsupportedVersionIsNotAcknowledgedAndEventuallyMovesToTheDlq() throws Exception {
        UUID eventId = UUID.randomUUID();

        send(testQueueUrl, objectMapper.writeValueAsString(event(eventId, 2)), "tenant-version");

        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(messages(testDlqUrl)).anyMatch(message ->
                        message.body().contains(eventId.toString())));
        assertThat(documentCount(eventId)).isZero();
    }

    private static synchronized void initializeSqs() {
        if (sqsClient != null) {
            return;
        }
        sqsClient = SqsAsyncClient.builder()
                .endpointOverride(LOCALSTACK.getEndpoint())
                .region(Region.of(LOCALSTACK.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey())))
                .build();

        contractDlqUrl = createFifoQueue(CONTRACT_DLQ, 30, null, null);
        String contractDlqArn = attributes(contractDlqUrl).attributes().get(QueueAttributeName.QUEUE_ARN);
        contractQueueUrl = createFifoQueue(CONTRACT_QUEUE, 30, contractDlqArn, 5);

        testDlqUrl = createFifoQueue(TEST_DLQ, 2, null, null);
        String testDlqArn = attributes(testDlqUrl).attributes().get(QueueAttributeName.QUEUE_ARN);
        testQueueUrl = createFifoQueue(TEST_QUEUE, 2, testDlqArn, 2);
    }

    private static String createFifoQueue(
            String name,
            int visibilityTimeout,
            String deadLetterQueueArn,
            Integer maxReceiveCount) {
        Map<QueueAttributeName, String> attributes = new java.util.EnumMap<>(QueueAttributeName.class);
        attributes.put(QueueAttributeName.FIFO_QUEUE, "true");
        attributes.put(QueueAttributeName.MESSAGE_RETENTION_PERIOD, "1209600");
        attributes.put(QueueAttributeName.VISIBILITY_TIMEOUT, Integer.toString(visibilityTimeout));
        if (deadLetterQueueArn != null) {
            attributes.put(
                    QueueAttributeName.REDRIVE_POLICY,
                    "{\"deadLetterTargetArn\":\"" + deadLetterQueueArn
                            + "\",\"maxReceiveCount\":\"" + maxReceiveCount + "\"}");
        }
        return sqsClient.createQueue(CreateQueueRequest.builder()
                        .queueName(name)
                        .attributes(attributes)
                        .build())
                .thenApply(response -> sqsClient.getQueueUrl(request -> request.queueName(name)).join().queueUrl())
                .join();
    }

    private static GetQueueAttributesResponse attributes(String queueUrl) {
        return sqsClient.getQueueAttributes(request -> request
                        .queueUrl(queueUrl)
                        .attributeNames(QueueAttributeName.ALL))
                .join();
    }

    private void send(String queueUrl, String body, String groupId) {
        sqsClient.sendMessage(request -> request
                        .queueUrl(queueUrl)
                        .messageBody(body)
                        .messageGroupId(groupId)
                        .messageDeduplicationId(UUID.randomUUID().toString()))
                .join();
    }

    private List<Message> messages(String queueUrl) {
        return sqsClient.receiveMessage(request -> request
                        .queueUrl(queueUrl)
                        .waitTimeSeconds(1)
                        .maxNumberOfMessages(10))
                .join()
                .messages();
    }

    private void awaitDocument(UUID eventId) {
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(documentCount(eventId)).isEqualTo(1));
    }

    private long documentCount(UUID eventId) {
        Query query = Query.query(Criteria.where("source.eventId").is(eventId.toString()));
        return mongoTemplate.count(query, AuditMongoIndexBootstrap.COLLECTION);
    }

    private AuditEventEnvelope event(UUID eventId, int version) {
        return new AuditEventEnvelope(
                eventId,
                "AuditRecorded",
                version,
                "commerce",
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
                "req-" + eventId,
                Instant.now());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FailureInjectionConfiguration {

        @Bean
        @Primary
        ToggleAuditRecordRepository toggleAuditRecordRepository(MongoAuditRecordRepository delegate) {
            return new ToggleAuditRecordRepository(delegate);
        }
    }

    static class ToggleAuditRecordRepository implements AuditRecordRepositoryPort {

        private final MongoAuditRecordRepository delegate;
        private final AtomicBoolean failing = new AtomicBoolean();
        private final AtomicInteger attempts = new AtomicInteger();

        ToggleAuditRecordRepository(MongoAuditRecordRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public SaveOutcome save(AuditRecord record) {
            attempts.incrementAndGet();
            if (failing.get()) {
                throw new IllegalStateException("simulated MongoDB outage");
            }
            return delegate.save(record);
        }

        void fail() {
            failing.set(true);
        }

        void recover() {
            failing.set(false);
        }

        void reset() {
            failing.set(false);
            attempts.set(0);
        }

        int attempts() {
            return attempts.get();
        }
    }
}
