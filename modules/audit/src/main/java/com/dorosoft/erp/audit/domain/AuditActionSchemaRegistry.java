package com.dorosoft.erp.audit.domain;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

public final class AuditActionSchemaRegistry {
    private static final Map<AuditAction, AuditActionSchema> SCHEMAS = new EnumMap<>(AuditAction.class);

    static {
        register(AuditDomain.IDENTITY, AuditTargetType.ACCOUNT, fields("status", "role", "permissionCodes"),
                Set.of(), fields("status", "role", "permissionCodes"), false, false,
                AuditAction.EMPLOYEE_ACCOUNT_CREATED);
        register(AuditDomain.IDENTITY, AuditTargetType.ACCOUNT, fields("status"),
                fields("status"), fields("status"), false, false,
                AuditAction.EMPLOYEE_ACCOUNT_ACTIVATED, AuditAction.EMPLOYEE_ACCOUNT_DEACTIVATED);
        register(AuditDomain.IDENTITY, AuditTargetType.ACCOUNT,
                fields("lockStatus", "failedLoginCount", "temporaryLockCount", "lockedAt", "lockedUntil", "version"),
                fields("lockStatus", "failedLoginCount", "temporaryLockCount", "lockedAt", "lockedUntil", "version"),
                fields("lockStatus", "failedLoginCount", "temporaryLockCount", "lockedAt", "lockedUntil", "version"),
                true, false, AuditAction.ACCOUNT_LOGIN_UNLOCKED);
        register(AuditDomain.IDENTITY, AuditTargetType.ACCOUNT, fields("role", "permissionCodes"),
                fields("role", "permissionCodes"), fields("role", "permissionCodes"), false, false,
                AuditAction.EMPLOYEE_PERMISSIONS_CHANGED);
        register(AuditDomain.IDENTITY, AuditTargetType.ROLE,
                fields("roleCode", "permissionCodes", "version", "affectedAccountCount"),
                fields("roleCode", "permissionCodes", "version"),
                fields("roleCode", "permissionCodes", "version", "affectedAccountCount"), false, false,
                AuditAction.ROLE_PERMISSIONS_CHANGED);

        register(AuditDomain.STORE, AuditTargetType.STORE_PROFILE,
                fields("storeName", "timeZone", "changedFields", "version"), Set.of(), Set.of(), false, false,
                AuditAction.STORE_PROFILE_UPDATED);
        register(AuditDomain.STORE, AuditTargetType.STORE_SCHEDULE,
                fields("businessHours", "regularClosedDays", "temporaryClosures", "serviceWindows", "version"),
                Set.of(), Set.of(), false, false, AuditAction.STORE_SCHEDULE_UPDATED);
        register(AuditDomain.STORE, AuditTargetType.STORE_FEATURE_SETTINGS,
                fields("featureSettings", "notificationEventSettings", "version"), Set.of(), Set.of(), false, false,
                AuditAction.STORE_FEATURE_SETTINGS_UPDATED);
        register(AuditDomain.STORE, AuditTargetType.TABLE,
                fields("tableNumber", "displayName", "seatCapacity", "active", "version"), Set.of(), Set.of(), false, false,
                AuditAction.TABLE_CREATED, AuditAction.TABLE_UPDATED);
        register(AuditDomain.STORE, AuditTargetType.TABLE, fields("active", "version"), Set.of(), Set.of(), false, false,
                AuditAction.TABLE_ACTIVATION_CHANGED);
        register(AuditDomain.STORE, AuditTargetType.TABLE_QR_CREDENTIAL,
                fields("credentialId", "predecessorCredentialId", "status"), Set.of(), Set.of(), false, false,
                AuditAction.TABLE_QR_ISSUED, AuditAction.TABLE_QR_ROTATED);
        register(AuditDomain.ORDER, AuditTargetType.TABLE_SESSION,
                fields("tableSessionId", "tableId", "status", "openedAt", "closedAt", "version"),
                Set.of(), Set.of(), false, false, AuditAction.TABLE_SESSION_OPENED);
        register(AuditDomain.ORDER, AuditTargetType.TABLE_SESSION,
                fields("tableSessionId", "tableId", "status", "openedAt", "closedAt", "version"),
                Set.of(), Set.of(), false, true, AuditAction.TABLE_SESSION_CLOSED);
        register(AuditDomain.ORDER, AuditTargetType.TABLE_SESSION,
                fields("tableSessionId", "tableId", "status", "openedAt", "closedAt", "version"),
                Set.of(), Set.of(), true, true, AuditAction.TABLE_SESSION_CLOSE_BLOCKED);
        register(AuditDomain.STORE, AuditTargetType.TABLE_LAYOUT,
                fields("layoutRevision", "chunkIndex", "chunkCount", "canvas", "items"),
                Set.of(), Set.of(), false, false, AuditAction.TABLE_LAYOUT_UPDATED);
        register(AuditDomain.STORE, AuditTargetType.TABLE,
                fields("tableId", "status", "triggerReason", "version"), Set.of(), Set.of(), false, false,
                AuditAction.TABLE_TURNOVER_STATUS_CHANGED);

        register(AuditDomain.CATALOG, AuditTargetType.CATEGORY, fields("name", "version"), Set.of(), Set.of(), false, false,
                AuditAction.CATEGORY_CREATED, AuditAction.CATEGORY_UPDATED);
        register(AuditDomain.CATALOG, AuditTargetType.CATEGORY, fields("categoryIds", "catalogRevision"), Set.of(), Set.of(), false, false,
                AuditAction.CATEGORY_ORDER_CHANGED);
        register(AuditDomain.CATALOG, AuditTargetType.PRODUCT,
                fields("name", "description", "categoryId", "version"), Set.of(), Set.of(), false, false,
                AuditAction.PRODUCT_CREATED, AuditAction.PRODUCT_UPDATED);
        register(AuditDomain.CATALOG, AuditTargetType.PRODUCT, fields("basePrice", "currency", "version"), Set.of(), Set.of(), false, false,
                AuditAction.PRODUCT_PRICE_CHANGED);
        register(AuditDomain.CATALOG, AuditTargetType.PRODUCT, fields("mediaId", "imageAltText", "version"), Set.of(), Set.of(), false, false,
                AuditAction.PRODUCT_IMAGE_CHANGED);
        register(AuditDomain.CATALOG, AuditTargetType.PRODUCT, fields("options", "version"), Set.of(), Set.of(), false, false,
                AuditAction.PRODUCT_OPTIONS_CHANGED);
        register(AuditDomain.CATALOG, AuditTargetType.PRODUCT, fields("salesEnabled", "stockManaged", "version"), Set.of(), Set.of(), false, false,
                AuditAction.PRODUCT_SALES_POLICY_CHANGED);
        register(AuditDomain.CATALOG, AuditTargetType.PRODUCT, fields("soldOut", "version"), Set.of(), Set.of(), false, false,
                AuditAction.PRODUCT_SOLD_OUT_CHANGED);
        register(AuditDomain.CATALOG, AuditTargetType.PRODUCT, fields("categoryId", "productIds", "catalogRevision"), Set.of(), Set.of(), false, false,
                AuditAction.PRODUCT_ORDER_CHANGED);

        register(AuditDomain.ORDER, AuditTargetType.TABLE_SESSION,
                fields("tableSessionId", "sourceTableId", "targetTableId", "orderCount", "version"),
                Set.of(), Set.of(), true, false, AuditAction.ORDER_TABLE_MOVED);
        register(AuditDomain.ORDER, AuditTargetType.ORDER,
                fields("status", "cancellationStatus", "version", "totalAmount", "currency"),
                Set.of(), Set.of(), true, true, AuditAction.ORDER_CANCELLED);
        register(AuditDomain.PAYMENT, AuditTargetType.PAYMENT, fields("status", "amount", "currency", "methodClass"), Set.of(), Set.of(), false, false,
                AuditAction.PAYMENT_APPROVED);
        register(AuditDomain.PAYMENT, AuditTargetType.PAYMENT, fields("status", "amount", "currency", "failureClass"), Set.of(), Set.of(), false, false,
                AuditAction.PAYMENT_FAILED);
        register(AuditDomain.PAYMENT, AuditTargetType.PAYMENT, fields("status", "cancelledAmount", "currency"), Set.of(), Set.of(), true, true,
                AuditAction.PAYMENT_CANCELLED);
        register(AuditDomain.PAYMENT, AuditTargetType.PAYMENT,
                fields("originalMethodClass", "previousEffectiveMethodClass", "effectiveMethodClass", "version"),
                Set.of(), Set.of(), true, false, AuditAction.PAYMENT_METHOD_CORRECTED);
        register(AuditDomain.INVENTORY, AuditTargetType.INVENTORY,
                fields("productId", "quantity", "currentQuantity", "version"), Set.of(), Set.of(), false, false,
                AuditAction.INVENTORY_RECEIVED);
        register(AuditDomain.INVENTORY, AuditTargetType.INVENTORY,
                fields("productId", "currentQuantity", "deltaQuantity", "version"), Set.of(), Set.of(), true, false,
                AuditAction.INVENTORY_ADJUSTED);
        register(AuditDomain.WAITING, AuditTargetType.WAITING_ENTRY, fields("status", "queueNumber", "version"), Set.of(), Set.of(), false, false,
                AuditAction.WAITING_STATUS_CHANGED);
        register(AuditDomain.RESERVATION, AuditTargetType.RESERVATION, fields("status", "partySize", "scheduledAt", "version"), Set.of(), Set.of(), false, false,
                AuditAction.RESERVATION_APPROVED, AuditAction.RESERVATION_CHANGED);
        register(AuditDomain.RESERVATION, AuditTargetType.RESERVATION, fields("status", "version"), Set.of(), Set.of(), true, false,
                AuditAction.RESERVATION_REJECTED);
        register(AuditDomain.RESERVATION, AuditTargetType.RESERVATION, fields("status", "version"), Set.of(), Set.of(), false, false,
                AuditAction.RESERVATION_CANCELLED, AuditAction.RESERVATION_VISIT_STATUS_CHANGED);
        register(AuditDomain.SALES, AuditTargetType.DAILY_CLOSING,
                fields("businessDate", "revision", "grossSales", "cancelledSales", "netSales", "orderCount", "currency"),
                Set.of(), Set.of(), false, false, AuditAction.DAILY_CLOSING_COMPLETED);
        register(AuditDomain.SALES, AuditTargetType.DAILY_CLOSING,
                fields("businessDate", "revision", "netSales", "orderCount", "currency"),
                Set.of(), Set.of(), true, false, AuditAction.DAILY_CLOSING_CORRECTED);

        if (SCHEMAS.size() != AuditAction.values().length) {
            throw new IllegalStateException("Every audit action must have exactly one schema");
        }
    }

    private AuditActionSchemaRegistry() {
    }

    public static AuditActionSchema schema(AuditAction action) {
        AuditActionSchema schema = SCHEMAS.get(action);
        if (schema == null) {
            throw new IllegalArgumentException("Unsupported audit action");
        }
        return schema;
    }

    public static AuditDomain domainFor(AuditAction action) {
        return schema(action).domain();
    }

    public static AuditTargetType primaryTargetFor(AuditAction action) {
        return schema(action).primaryTargetType();
    }

    public static Set<String> allowedFields(AuditAction action) {
        return schema(action).allowedFields();
    }

    private static Set<String> fields(String... names) {
        return Set.of(names);
    }

    private static void register(AuditDomain domain, AuditTargetType target, Set<String> allowed,
                                 Set<String> requiredBefore, Set<String> requiredAfter,
                                 boolean reasonRequired, boolean reasonCodeRequired,
                                 AuditAction... actions) {
        AuditActionSchema schema = new AuditActionSchema(
                domain, target, allowed, requiredBefore, requiredAfter, reasonRequired, reasonCodeRequired);
        for (AuditAction action : actions) {
            if (SCHEMAS.put(action, schema) != null) {
                throw new IllegalStateException("Duplicate audit action schema");
            }
        }
    }
}
