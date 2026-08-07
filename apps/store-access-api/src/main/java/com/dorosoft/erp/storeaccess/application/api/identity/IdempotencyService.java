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
import org.springframework.transaction.annotation.Transactional;

/**
 * Claims, replays and completes idempotent operations scoped to
 * {@code tenantId + actorEmployeeId + operation + keyDigest} (ADR-02-014). The claim, the wrapped business
 * mutation and the completed result are committed in a single transaction, so a failed operation rolls the
 * claim back with it and can be retried safely with the same key.
 */
@Service
public class IdempotencyService {

    private static final Duration RECORD_TTL = Duration.ofHours(24);

    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final IdempotencyDigestSigner digestSigner;
    private final Clock clock;

    public IdempotencyService(
            IdempotencyRecordRepository idempotencyRecordRepository, IdempotencyDigestSigner digestSigner, Clock clock) {
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.digestSigner = digestSigner;
        this.clock = clock;
    }

    @Transactional
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

        Optional<IdempotencyRecord> existing =
                idempotencyRecordRepository.findByScope(tenantId, actorEmployeeId, operation, keyDigest);
        if (existing.isPresent()) {
            return replayOrReject(existing.get(), requestHmac, deserializer);
        }

        Instant now = clock.instant();
        IdempotencyRecord claim = IdempotencyRecord.claim(
                UUID.randomUUID(), tenantId, actorEmployeeId, operation, keyDigest, requestHmac, now,
                now.plus(RECORD_TTL));
        try {
            idempotencyRecordRepository.save(claim);
        } catch (DataIntegrityViolationException e) {
            throw new ProblemAwareException(
                    IdempotencyProblemCode.IDEMPOTENCY_REQUEST_IN_PROGRESS,
                    "동일한 요청이 이미 처리 중입니다.", List.of(), e);
        }

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
