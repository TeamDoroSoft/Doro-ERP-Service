package com.dorosoft.erp.storeaccess.infrastructure.identity.security;

import com.dorosoft.erp.platform.web.ProblemCode;
import com.dorosoft.erp.platform.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes a {@link ProblemCode} as a {@code ProblemDetail} JSON body, for the Security Filter/entry-point/
 * access-denied layer that runs outside Spring MVC's exception handling (@code GlobalProblemAdvice} in
 * {@code platform:web} only ever sees requests that reach a Controller).
 */
@Component
public class ProblemResponseWriter {

    private final ObjectMapper objectMapper;

    public ProblemResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, ProblemCode code) throws IOException {
        Object requestIdAttribute = request.getAttribute(RequestIdFilter.ATTRIBUTE_REQUEST_ID);
        String requestId = requestIdAttribute instanceof String value ? value : "req-" + UUID.randomUUID();
        ProblemDetail problemDetail = code.toProblemDetail(requestId, List.of(), code.title());
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        if (code.retryAfterSeconds() != null) {
            response.setHeader("Retry-After", String.valueOf(code.retryAfterSeconds()));
        }
        objectMapper.writeValue(response.getWriter(), problemDetail);
    }
}
