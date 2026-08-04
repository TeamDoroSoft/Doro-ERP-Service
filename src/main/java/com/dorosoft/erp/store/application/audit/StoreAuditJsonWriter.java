package com.dorosoft.erp.store.application.audit;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class StoreAuditJsonWriter {

    private final ObjectMapper objectMapper;

    public StoreAuditJsonWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("매장 감사 값을 JSON으로 변환할 수 없습니다", exception);
        }
    }
}
