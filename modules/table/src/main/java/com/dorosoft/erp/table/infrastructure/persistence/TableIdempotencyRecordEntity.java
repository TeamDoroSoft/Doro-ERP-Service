package com.dorosoft.erp.table.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "table_idempotency_record")
public class TableIdempotencyRecordEntity {

    @Id
    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @Column(name = "request_method", nullable = false, length = 10)
    private String requestMethod;

    @Column(name = "request_path", nullable = false, length = 255)
    private String requestPath;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "response_status", nullable = false)
    private short responseStatus;

    @Column(name = "response_body", nullable = false, columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TableIdempotencyRecordEntity() {}

    public TableIdempotencyRecordEntity(
            String idempotencyKey,
            String requestMethod,
            String requestPath,
            String requestHash,
            int responseStatus,
            String responseBody,
            Instant createdAt) {
        this.idempotencyKey = idempotencyKey;
        this.requestMethod = requestMethod;
        this.requestPath = requestPath;
        this.requestHash = requestHash;
        this.responseStatus = (short) responseStatus;
        this.responseBody = responseBody;
        this.createdAt = createdAt;
    }

    public String getRequestMethod() {
        return requestMethod;
    }

    public String getRequestPath() {
        return requestPath;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public int getResponseStatus() {
        return responseStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
