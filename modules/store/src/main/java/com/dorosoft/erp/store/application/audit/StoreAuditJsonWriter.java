package com.dorosoft.erp.store.application.audit;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.util.Map;

@Component
public class StoreAuditJsonWriter {

    private final ObjectMapper objectMapper;

    public StoreAuditJsonWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> toMap(Object value) {
        return objectMapper.convertValue(value, Map.class);
    }
}
