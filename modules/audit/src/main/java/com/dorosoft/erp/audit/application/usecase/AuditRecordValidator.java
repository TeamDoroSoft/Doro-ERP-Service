package com.dorosoft.erp.audit.application.usecase;

import com.dorosoft.erp.audit.application.api.AuditContext;
import com.dorosoft.erp.audit.application.api.AuditContractException;
import com.dorosoft.erp.audit.application.api.AuditErrorCode;
import com.dorosoft.erp.audit.application.api.AuditRecordCommand;
import com.dorosoft.erp.audit.domain.ActorType;
import com.dorosoft.erp.audit.domain.AuditAction;
import com.dorosoft.erp.audit.domain.AuditActionSchema;
import com.dorosoft.erp.audit.domain.AuditActionSchemaRegistry;
import com.dorosoft.erp.audit.domain.AuditRelatedTarget;
import com.dorosoft.erp.audit.domain.AuditRelationType;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public final class AuditRecordValidator {
    private static final int MAX_VALUE_BYTES = 32 * 1024;
    private static final Set<String> FORBIDDEN_KEY_TOKENS = Set.of(
            "password", "hash", "salt", "session", "cookie", "token", "secret", "apikey",
            "paymentkey", "cardnumber", "approvalnumber", "phone", "email", "address",
            "recipient", "residentnumber"
    );
    private static final Map<String, Set<String>> NESTED_FIELDS = Map.of(
            "temporaryClosures", Set.of("date", "reasonCode"),
            "options", Set.of("optionId", "name", "additionalPrice", "enabled", "displayOrder"),
            "items", Set.of("tableId", "changeType", "present", "positionX", "positionY", "width", "height", "shape", "rotation")
    );
    private static final Pattern EMAIL = Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\+82[- .]?|0)(?:1[016789]|2|[3-6][1-5])[- .]?\\d{3,4}[- .]?\\d{4}(?!\\d)");
    private static final Pattern RESIDENT = Pattern.compile("(?<!\\d)\\d{6}[- ]?[1-8]\\d{6}(?!\\d)");
    private static final Pattern JWT = Pattern.compile("(?i)(?:Bearer\\s+)?[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}");
    private static final Pattern URL_SECRET = Pattern.compile("(?i)[?&](?:token|key|secret)=[^&\\s]+");
    private static final Pattern IPV4 = Pattern.compile("(?<![\\d.])(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)(?:\\.(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)){3}(?![\\d.])");
    private static final Pattern IPV6 = Pattern.compile("(?i)(?<![0-9a-f:])(?:[0-9a-f]{1,4}:){2,7}[0-9a-f]{0,4}(?![0-9a-f:])");

    private final ObjectMapper objectMapper;

    public AuditRecordValidator(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    public void validate(AuditRecordCommand command, AuditContext context) {
        if (command == null || context == null || command.action() == null || command.domain() == null
                || command.operationId() == null || command.primaryTarget() == null
                || command.primaryTarget().targetType() == null || command.primaryTarget().targetId() == null) {
            schemaError("Required audit contract field is missing");
        }
        if (command.eventSequence() < 0 || command.valueSchemaVersion() != 1) {
            schemaError("Unsupported audit schema version or event sequence");
        }
        AuditActionSchema schema = AuditActionSchemaRegistry.schema(command.action());
        if (schema.domain() != command.domain() || schema.primaryTargetType() != command.primaryTarget().targetType()) {
            schemaError("Audit domain or primary target does not match the action registry");
        }
        validateContext(context);
        validateTargets(command);
        validateValue(command.beforeValue(), schema.allowedFields(), schema.requiredBeforeFields());
        validateValue(command.afterValue(), schema.allowedFields(), schema.requiredAfterFields());
        if (command.action() == AuditAction.EMPLOYEE_ACCOUNT_CREATED && !command.beforeValue().isEmpty()) {
            schemaError("Create action before value must be empty");
        }
        validateIdentityValues(command);
        validateRequiredRelations(command);
        validateReason(command.reason(), command.reasonCode(), schema);
    }

    private void validateContext(AuditContext context) {
        if (blank(context.tenantId()) || context.actorType() == null || blank(context.actorRoleSnapshot())
                || context.actorRoleSnapshot().length() > 40 || blank(context.actorDisplayNameSnapshot())
                || context.actorDisplayNameSnapshot().length() > 100 || blank(context.requestId())
                || context.requestId().length() > 100 || context.occurredAt() == null) {
            schemaError("Verified audit context is incomplete");
        }
        if (context.actorType() != ActorType.SYSTEM && context.actorId() == null) {
            schemaError("Non-system actor id is required");
        }
    }

    private void validateTargets(AuditRecordCommand command) {
        if (command.relatedTargets().size() > 20) {
            tooLarge("Audit related target limit exceeded");
        }
        Set<String> unique = new HashSet<>();
        for (AuditRelatedTarget target : command.relatedTargets()) {
            if (target == null || target.relationType() == null || target.targetType() == null || target.targetId() == null
                    || target.relationType() == AuditRelationType.PRIMARY) {
                schemaError("Invalid audit related target");
            }
            String key = target.relationType() + ":" + target.targetType() + ":" + target.targetId();
            if (!unique.add(key)) {
                schemaError("Duplicate audit related target");
            }
        }
    }

    private void validateValue(Map<String, Object> value, Set<String> allowed, Set<String> required) {
        inspectMap(value, null);
        if (!allowed.containsAll(value.keySet()) || !value.keySet().containsAll(required)) {
            schemaError("Audit value fields do not match the action schema");
        }
        try {
            int bytes = objectMapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8).length;
            if (bytes > MAX_VALUE_BYTES) {
                tooLarge("Audit value size limit exceeded");
            }
        } catch (JacksonException exception) {
            schemaError("Audit value is not valid JSON");
        }
    }

    private void validateIdentityValues(AuditRecordCommand command) {
        if (command.domain() != com.dorosoft.erp.audit.domain.AuditDomain.IDENTITY) {
            return;
        }
        validateIdentityMap(command.beforeValue());
        validateIdentityMap(command.afterValue());
        if (command.action() == AuditAction.ROLE_PERMISSIONS_CHANGED
                && command.beforeValue().containsKey("affectedAccountCount")) {
            schemaError("Affected account count is only allowed in the after value");
        }
    }

    private void validateIdentityMap(Map<String, Object> value) {
        for (Map.Entry<String, Object> entry : value.entrySet()) {
            Object fieldValue = entry.getValue();
            switch (entry.getKey()) {
                case "status", "role", "roleCode", "lockStatus" -> {
                    if (!(fieldValue instanceof String text) || text.isBlank() || text.length() > 100) {
                        schemaError("Identity audit string field is invalid");
                    }
                }
                case "permissionCodes" -> validatePermissionCodes(fieldValue);
                case "failedLoginCount", "temporaryLockCount", "version", "affectedAccountCount" -> {
                    if (!(fieldValue instanceof Number number) || number.longValue() < 0) {
                        schemaError("Identity audit numeric field is invalid");
                    }
                }
                case "lockedAt", "lockedUntil" -> {
                    if (fieldValue != null && !(fieldValue instanceof String)) {
                        schemaError("Identity audit timestamp field is invalid");
                    }
                }
                default -> {
                    // The action schema already handles unregistered top-level fields.
                }
            }
        }
    }

    private void validatePermissionCodes(Object value) {
        if (!(value instanceof List<?>)) {
            schemaError("Permission code collection is invalid");
        }
        List<?> codes = (List<?>) value;
        String previous = null;
        for (Object item : codes) {
            if (!(item instanceof String)) {
                schemaError("Permission codes must be sorted and unique");
            }
            String code = (String) item;
            if (code.isBlank() || code.length() > 100 || (previous != null && previous.compareTo(code) >= 0)) {
                schemaError("Permission codes must be sorted and unique");
            }
            previous = code;
        }
    }

    private void validateRequiredRelations(AuditRecordCommand command) {
        if (command.action() == AuditAction.EMPLOYEE_PERMISSIONS_CHANGED) {
            boolean hasRole = command.relatedTargets().stream().anyMatch(target ->
                    target.relationType() == AuditRelationType.SUBJECT_ROLE
                            && target.targetType() == com.dorosoft.erp.audit.domain.AuditTargetType.ROLE);
            if (!hasRole) {
                schemaError("Employee role change must include subject role targets");
            }
        }
    }

    private void inspectMap(Map<?, ?> value, String parentKey) {
        for (Map.Entry<?, ?> entry : value.entrySet()) {
            if (!(entry.getKey() instanceof String)) {
                schemaError("Audit JSON object keys must be strings");
            }
            String key = (String) entry.getKey();
            String normalized = normalizeKey(key);
            if (FORBIDDEN_KEY_TOKENS.stream().anyMatch(normalized::contains)) {
                sensitive("Sensitive audit value key rejected");
            }
            if (parentKey != null && NESTED_FIELDS.containsKey(parentKey)
                    && !NESTED_FIELDS.get(parentKey).contains(key)) {
                schemaError("Nested audit value field is not registered");
            }
            inspectNested(entry.getValue(), key);
        }
    }

    private void inspectNested(Object value, String parentKey) {
        if (value instanceof Map<?, ?> map) {
            inspectMap(map, parentKey);
        } else if (value instanceof List<?> list) {
            for (Object item : list) {
                inspectNested(item, parentKey);
            }
        }
    }

    private void validateReason(String reason, String reasonCode, AuditActionSchema schema) {
        if (schema.reasonRequired() && blank(reason)) {
            reasonError("Audit reason is required");
        }
        if (schema.reasonCodeRequired() && blank(reasonCode)) {
            reasonError("Audit reason code is required");
        }
        if (reasonCode != null && (reasonCode.isBlank() || reasonCode.length() > 50
                || !reasonCode.matches("[A-Z0-9_]+"))) {
            reasonError("Audit reason code is invalid");
        }
        if (reason == null) {
            return;
        }
        if (reason.isBlank() || reason.length() > 500 || reason.chars().anyMatch(Character::isISOControl)
                || reason.indexOf('<') >= 0 || reason.indexOf('>') >= 0) {
            reasonError("Audit reason is invalid");
        }
        if (EMAIL.matcher(reason).find() || PHONE.matcher(reason).find() || RESIDENT.matcher(reason).find()
                || JWT.matcher(reason).find() || URL_SECRET.matcher(reason).find()
                || IPV4.matcher(reason).find() || IPV6.matcher(reason).find() || containsLuhnCard(reason)) {
            sensitive("Sensitive audit reason pattern rejected");
        }
    }

    private boolean containsLuhnCard(String value) {
        String[] candidates = value.split("[^0-9 -]+");
        for (String candidate : candidates) {
            String digits = candidate.replaceAll("[^0-9]", "");
            if (digits.length() >= 13 && digits.length() <= 19 && luhn(digits)) {
                return true;
            }
        }
        return false;
    }

    private boolean luhn(String digits) {
        int sum = 0;
        boolean doubleDigit = false;
        for (int index = digits.length() - 1; index >= 0; index--) {
            int digit = digits.charAt(index) - '0';
            if (doubleDigit && (digit *= 2) > 9) {
                digit -= 9;
            }
            sum += digit;
            doubleDigit = !doubleDigit;
        }
        return sum % 10 == 0;
    }

    private String normalizeKey(String key) {
        return Normalizer.normalize(key, Normalizer.Form.NFC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private void schemaError(String message) {
        throw new AuditContractException(AuditErrorCode.AUDIT_SCHEMA_UNSUPPORTED, message);
    }

    private void reasonError(String message) {
        throw new AuditContractException(AuditErrorCode.AUDIT_REASON_REQUIRED, message);
    }

    private void sensitive(String message) {
        throw new AuditContractException(AuditErrorCode.AUDIT_SENSITIVE_VALUE_REJECTED, message);
    }

    private void tooLarge(String message) {
        throw new AuditContractException(AuditErrorCode.AUDIT_VALUE_TOO_LARGE, message);
    }
}
