package com.dorosoft.erp.table.infrastructure.web;

import com.dorosoft.erp.table.application.TableErrorCode;
import com.dorosoft.erp.table.application.TableManagementException;
import com.dorosoft.erp.table.infrastructure.persistence.TableIdempotencyRecordEntity;
import com.dorosoft.erp.table.infrastructure.persistence.TableIdempotencyRecordJpaRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
class TableIdempotencyService {

    private final TableIdempotencyRecordJpaRepository repository;
    private final ObjectMapper objectMapper;

    TableIdempotencyService(
            TableIdempotencyRecordJpaRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    ResponseEntity<Object> execute(
            String idempotencyKey,
            HttpServletRequest request,
            Object requestBody,
            Supplier<ResponseEntity<?>> operation) {
        String normalizedKey = normalizeKey(idempotencyKey);
        String method = request.getMethod();
        String path = request.getRequestURI();
        String requestHash = requestHash(requestBody);

        Optional<TableIdempotencyRecordEntity> existing = repository.findById(normalizedKey);
        if (existing.isPresent()) {
            TableIdempotencyRecordEntity record = existing.orElseThrow();
            if (!record.getRequestMethod().equals(method)
                    || !record.getRequestPath().equals(path)
                    || !record.getRequestHash().equals(requestHash)) {
                throw new TableManagementException(
                        HttpStatus.CONFLICT,
                        TableErrorCode.IDEMPOTENCY_KEY_CONFLICT,
                        "Idempotency-Key was already used for a different request.");
            }
            return replay(record);
        }

        ResponseEntity<?> response = operation.get();
        repository.saveAndFlush(
                new TableIdempotencyRecordEntity(
                        normalizedKey,
                        method,
                        path,
                        requestHash,
                        response.getStatusCode().value(),
                        writeBody(response.getBody()),
                        Instant.now()));
        return ResponseEntity.status(response.getStatusCode())
                .headers(response.getHeaders())
                .body(response.getBody());
    }

    private ResponseEntity<Object> replay(TableIdempotencyRecordEntity record) {
        return ResponseEntity.status(record.getResponseStatus()).body(readBody(record.getResponseBody()));
    }

    private String normalizeKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new TableManagementException(
                    HttpStatus.BAD_REQUEST,
                    TableErrorCode.IDEMPOTENCY_KEY_REQUIRED,
                    "Idempotency-Key header is required.");
        }
        String normalized = idempotencyKey.trim();
        if (normalized.length() > 255) {
            throw new TableManagementException(
                    HttpStatus.BAD_REQUEST,
                    TableErrorCode.IDEMPOTENCY_KEY_REQUIRED,
                    "Idempotency-Key must be 255 characters or fewer.");
        }
        return normalized;
    }

    private String requestHash(Object requestBody) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = objectMapper.writeValueAsBytes(requestBody);
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (JacksonException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to hash idempotent request.", e);
        }
    }

    private String writeBody(Object body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize idempotent response.", e);
        }
    }

    private JsonNode readBody(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to deserialize idempotent response.", e);
        }
    }
}
