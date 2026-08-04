package com.dorosoft.erp.catalog.application.audit;

import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Catalog Before·After 감사 값을 5.17이 정한 Action별 허용 필드만 담은 JSON으로 직렬화한다
 * (민감정보·값 스키마 명세). Entity나 요청 DTO를 그대로 직렬화하지 않고 항상 이 Map을 거친다.
 */
public final class CatalogAuditValues {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private CatalogAuditValues() {}

    /** description·mediaId처럼 null이 가능한 필드가 있어 Map.of를 쓸 수 없는 경우의 순서 보존 Map 빌더다. */
    public static Map<String, Object> map(Object... keyValuePairs) {
        if (keyValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException("키-값 쌍이 맞지 않습니다");
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            fields.put((String) keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return fields;
    }

    public static String write(Map<String, Object> fields) {
        try {
            return OBJECT_MAPPER.writeValueAsString(fields);
        } catch (JacksonException e) {
            throw new IllegalStateException("감사 값 JSON 직렬화에 실패했습니다", e);
        }
    }
}
