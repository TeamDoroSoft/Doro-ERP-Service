package com.dorosoft.erp.commerce.domain.catalog;

import java.util.UUID;

/**
 * Category와 Product가 공유하는 입력 불변 규칙.
 */
public final class CatalogRules {

    public static final int NAME_MAX_LENGTH = 100;
    public static final int DESCRIPTION_MAX_LENGTH = 500;
    public static final int DISPLAY_ORDER_MAX = 9999;
    /** KRW 정수 상한. 금액 합산 Overflow와 오입력을 함께 막는다. */
    public static final long PRICE_MAX = 100_000_000L;

    private CatalogRules() {
    }

    public static UUID requireIdentifier(UUID value) {
        if (value == null) {
            throw new CatalogRuleException(CatalogViolation.IDENTIFIER_REQUIRED);
        }
        return value;
    }

    public static UUID requireTenant(UUID value) {
        if (value == null) {
            throw new CatalogRuleException(CatalogViolation.TENANT_REQUIRED);
        }
        return value;
    }

    public static UUID requireCategory(UUID value) {
        if (value == null) {
            throw new CatalogRuleException(CatalogViolation.CATEGORY_REQUIRED);
        }
        return value;
    }

    public static CatalogStatus requireStatus(CatalogStatus value) {
        if (value == null) {
            throw new CatalogRuleException(CatalogViolation.STATUS_REQUIRED);
        }
        return value;
    }

    /** 앞뒤 공백을 제거한 이름을 돌려준다. 공백만 있는 이름은 누락으로 본다. */
    public static String normalizeName(String value) {
        if (value == null || value.isBlank()) {
            throw new CatalogRuleException(CatalogViolation.NAME_REQUIRED);
        }
        String normalized = value.trim();
        if (normalized.length() > NAME_MAX_LENGTH) {
            throw new CatalogRuleException(CatalogViolation.NAME_TOO_LONG);
        }
        return normalized;
    }

    /** 설명은 선택 값이다. 공백만 입력하면 값 없음으로 정규화한다. */
    public static String normalizeDescription(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > DESCRIPTION_MAX_LENGTH) {
            throw new CatalogRuleException(CatalogViolation.DESCRIPTION_TOO_LONG);
        }
        return normalized;
    }

    /** 가격은 0 이상의 KRW 정수다. */
    public static long requirePrice(long value) {
        if (value < 0L) {
            throw new CatalogRuleException(CatalogViolation.PRICE_NEGATIVE);
        }
        if (value > PRICE_MAX) {
            throw new CatalogRuleException(CatalogViolation.PRICE_TOO_LARGE);
        }
        return value;
    }

    public static int requireDisplayOrder(int value) {
        if (value < 0) {
            throw new CatalogRuleException(CatalogViolation.DISPLAY_ORDER_NEGATIVE);
        }
        if (value > DISPLAY_ORDER_MAX) {
            throw new CatalogRuleException(CatalogViolation.DISPLAY_ORDER_TOO_LARGE);
        }
        return value;
    }
}
