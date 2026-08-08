package com.dorosoft.erp.storeaccess.application.api.identity;

import com.dorosoft.erp.platform.web.ProblemAwareException;
import com.dorosoft.erp.storeaccess.application.port.identity.IdempotencyDigestSigner;
import com.dorosoft.erp.storeaccess.application.port.identity.IdempotencyRecordRepository;
import com.dorosoft.erp.storeaccess.domain.identity.IdempotencyRecord;
import com.dorosoft.erp.storeaccess.domain.identity.IdempotencyRecordStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Claims, replays and completes idempotent operations scoped to
 * {@code tenantId + actorEmployeeId + operation + keyDigest} (ADR-02-014). The claim, the wrapped business
 * mutation and the completed result are committed in a single transaction, so a failed operation rolls the
 * claim back with it and can be retried safely with the same key.
 *
 * <p>Every phase runs through an explicit {@code REQUIRES_NEW} {@link TransactionTemplate} rather than a
 * method-level {@code @Transactional}: PostgreSQL aborts the entire current transaction the moment the claim
 * INSERT collides with a concurrent claim ("current transaction is aborted, commands ignored until end of
 * transaction block"), so no further statement — including a plain replay read — can run on that same
 * transaction afterward. Recovering from that race (ADR-02-014: "동시 요청은 선행 Transaction 완료까지 기다릴 수
 * 있다") needs a genuinely independent transaction for the replay read regardless of whether this method was
 * itself called from inside an already-open caller Transaction (e.g. {@code EmployeeManagementService}'s own
 * {@code @Transactional} boundary), which plain {@code REQUIRED} propagation would otherwise just join.
 */
@Service
public class IdempotencyService {

    private static final Duration RECORD_TTL = Duration.ofHours(24);

    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final IdempotencyDigestSigner digestSigner;
    private final Clock clock;
    private final TransactionTemplate requiresNewTransaction;

    public IdempotencyService(
            IdempotencyRecordRepository idempotencyRecordRepository, IdempotencyDigestSigner digestSigner, Clock clock,
            PlatformTransactionManager transactionManager) {
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.digestSigner = digestSigner;
        this.clock = clock;
        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public <T> T execute(
            UUID tenantId,
            UUID actorEmployeeId,
            String operation,
            String rawIdempotencyKey,
            String canonicalRequest,
            Function<T, String> serializer,
            Function<String, T> deserializer,
            Supplier<T> operationLogic) {

        String keyDigest = digestSigner.digestKey(rawIdempotencyKey);
        String requestHmac = digestSigner.digestRequest(canonicalRequest);

        Optional<IdempotencyRecord> existing = requiresNewTransaction.execute(status ->
                idempotencyRecordRepository.findByScope(tenantId, actorEmployeeId, operation, keyDigest));
        if (existing.isPresent()) {
            return replayOrReject(existing.get(), requestHmac, deserializer);
        }

        try {
            return requiresNewTransaction.execute(status -> claimAndRun(
                    tenantId, actorEmployeeId, operation, keyDigest, requestHmac, serializer, operationLogic));
        } catch (DataIntegrityViolationException e) {
            IdempotencyRecord raced = requiresNewTransaction
                    .execute(status -> idempotencyRecordRepository.findByScope(tenantId, actorEmployeeId, operation, keyDigest))
                    .orElseThrow(() -> new ProblemAwareException(
                            IdempotencyProblemCode.IDEMPOTENCY_REQUEST_IN_PROGRESS,
                            "동일한 요청이 이미 처리 중입니다.", List.of(), e));
            return replayOrReject(raced, requestHmac, deserializer);
        }
    }

    private <T> T claimAndRun(
            UUID tenantId, UUID actorEmployeeId, String operation, String keyDigest, String requestHmac,
            Function<T, String> serializer, Supplier<T> operationLogic) {
        Instant now = clock.instant();
        IdempotencyRecord claim = IdempotencyRecord.claim(
                UUID.randomUUID(), tenantId, actorEmployeeId, operation, keyDigest, requestHmac, now,
                now.plus(RECORD_TTL));
        idempotencyRecordRepository.save(claim);

        T result = operationLogic.get();
        String payload = serializer.apply(result);
        idempotencyRecordRepository.save(claim.complete(payload, clock.instant()));
        return result;
    }

    private <T> T replayOrReject(IdempotencyRecord record, String requestHmac, Function<String, T> deserializer) {
        if (!record.requestHmac().equals(requestHmac)) {
            throw new ProblemAwareException(
                    IdempotencyProblemCode.IDEMPOTENCY_KEY_REUSED,
                    "이미 사용된 Idempotency-Key이며 요청 내용이 다릅니다.");
        }
        if (record.status() == IdempotencyRecordStatus.IN_PROGRESS) {
            throw new ProblemAwareException(
                    IdempotencyProblemCode.IDEMPOTENCY_REQUEST_IN_PROGRESS, "동일한 요청이 이미 처리 중입니다.");
        }
        return deserializer.apply(record.resultPayload());
    }
}
