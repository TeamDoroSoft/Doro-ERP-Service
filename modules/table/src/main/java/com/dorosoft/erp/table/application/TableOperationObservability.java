package com.dorosoft.erp.table.application;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class TableOperationObservability {

    public static final String METRIC_NAME = "doro.table.operation";

    private static final Logger log = LoggerFactory.getLogger(TableOperationObservability.class);
    private static final String NONE = "none";

    private final MeterRegistry meterRegistry;

    public TableOperationObservability(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.meterRegistry = meterRegistryProvider.getIfAvailable();
    }

    public void success(
            String operation,
            String tenantId,
            UUID actorId,
            String requestId,
            String targetType,
            UUID targetId) {
        record(operation, "success", NONE, tenantId, actorId, requestId, targetType, targetId);
    }

    public void failure(
            String operation,
            String reason,
            String tenantId,
            UUID actorId,
            String requestId,
            String targetType,
            UUID targetId) {
        record(operation, "failure", reason, tenantId, actorId, requestId, targetType, targetId);
    }

    public void blocked(
            String operation,
            String reason,
            String tenantId,
            UUID actorId,
            String requestId,
            String targetType,
            UUID targetId) {
        record(operation, "blocked", reason, tenantId, actorId, requestId, targetType, targetId);
    }

    private void record(
            String operation,
            String result,
            String reason,
            String tenantId,
            UUID actorId,
            String requestId,
            String targetType,
            UUID targetId) {
        String safeOperation = lowCardinality(operation, "unknown");
        String safeResult = lowCardinality(result, "unknown");
        String safeReason = lowCardinality(reason, NONE);
        increment(safeOperation, safeResult, safeReason);
        log(safeOperation, safeResult, safeReason, tenantId, actorId, requestId, targetType, targetId);
    }

    private void increment(String operation, String result, String reason) {
        if (meterRegistry == null) {
            return;
        }
        meterRegistry
                .counter(
                        METRIC_NAME,
                        Tags.of(
                                "operation", operation,
                                "result", result,
                                "reason", reason))
                .increment();
    }

    private void log(
            String operation,
            String result,
            String reason,
            String tenantId,
            UUID actorId,
            String requestId,
            String targetType,
            UUID targetId) {
        var event =
                ("success".equals(result) ? log.atInfo() : log.atWarn())
                        .addKeyValue("requestId", safeText(requestId))
                        .addKeyValue("storeId", safeText(tenantId))
                        .addKeyValue("actorId", actorId == null ? NONE : actorId.toString())
                        .addKeyValue("operation", operation)
                        .addKeyValue("targetType", safeText(targetType))
                        .addKeyValue("targetId", targetId == null ? NONE : targetId.toString())
                        .addKeyValue("result", result)
                        .addKeyValue("failureReason", reason);
        event.log(
                "table.operation operation={} targetType={} result={} failureReason={}",
                operation,
                safeText(targetType),
                result,
                reason);
    }

    public static String reason(Throwable exception) {
        if (exception instanceof TableManagementException tableException) {
            return tableException.code().name();
        }
        String simpleName = exception == null ? null : exception.getClass().getSimpleName();
        return lowCardinality(simpleName, "EXCEPTION");
    }

    public static String lowCardinality(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim().replaceAll("[^A-Za-z0-9_.-]", "_");
        if (normalized.isBlank()) {
            return fallback;
        }
        return normalized.length() > 80 ? normalized.substring(0, 80) : normalized;
    }

    private static String safeText(String value) {
        return value == null || value.isBlank() ? NONE : value;
    }
}
