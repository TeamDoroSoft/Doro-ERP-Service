package com.dorosoft.erp.shared.security;

import com.dorosoft.erp.platform.web.RequestIdFilter;
import com.dorosoft.erp.platform.web.error.ProblemDetailFactory;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** STORE-02 임시 인증 스텁이며 Identity 모듈이 실제 세션 인증(Redis 기반)을 구현하면 교체·삭제 대상이다. */
@Component
public class DoroAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public DoroAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(
            HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException, ServletException {
        String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        ProblemDetail problemDetail = ProblemDetailFactory.create(
                HttpStatus.FORBIDDEN, "FORBIDDEN", "접근 권한이 없습니다", requestId, List.of());
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problemDetail);
    }
}
