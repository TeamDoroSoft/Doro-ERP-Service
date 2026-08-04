package com.dorosoft.erp.identity.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public final class SecurityProblemWriter {

    private static final String REQUEST_ID_ATTRIBUTE = "doro.erp.requestId";
    private final ObjectMapper objectMapper;

    public SecurityProblemWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            int status,
            String code,
            String title,
            String detail
    ) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        String requestId = requestId(request);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "urn:doro-erp:problem:" + code.toLowerCase(Locale.ROOT).replace('_', '-'));
        body.put("title", title);
        body.put("status", status);
        body.put("detail", detail);
        body.put("instance", "urn:doro-erp:request:" + requestId);
        body.put("code", code);
        body.put("requestId", requestId);
        body.put("fieldErrors", List.of());

        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    private String requestId(HttpServletRequest request) {
        Object candidate = request.getAttribute(REQUEST_ID_ATTRIBUTE);
        if (candidate instanceof String value && !value.isBlank()) {
            return value;
        }
        String generated = "req-" + UUID.randomUUID();
        request.setAttribute(REQUEST_ID_ATTRIBUTE, generated);
        return generated;
    }
}
