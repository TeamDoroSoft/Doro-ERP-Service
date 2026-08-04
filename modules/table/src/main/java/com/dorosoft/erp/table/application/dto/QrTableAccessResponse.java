package com.dorosoft.erp.table.application.dto;

import java.util.UUID;

public record QrTableAccessResponse(
        boolean accessible,
        Store store,
        Table table,
        Session session) {

    public record Store(String tenantId) {}

    public record Table(String tableNumber, String displayName) {}

    public record Session(UUID sessionId) {}
}
