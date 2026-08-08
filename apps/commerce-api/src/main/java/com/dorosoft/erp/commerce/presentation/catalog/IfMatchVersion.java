package com.dorosoft.erp.commerce.presentation.catalog;

/**
 * {@code If-Match} Header의 version 표현을 Use Case 입력으로 변환한다.
 *
 * <p>Header가 없거나 형식이 잘못되면 {@code null}을 반환하고 Application이 428로 거절한다.
 */
final class IfMatchVersion {

    private IfMatchVersion() {
    }

    static Long parse(String ifMatch) {
        if (ifMatch == null) {
            return null;
        }
        String normalized = ifMatch.trim();
        if (normalized.startsWith("W/")) {
            normalized = normalized.substring(2).trim();
        }
        if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(normalized);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
