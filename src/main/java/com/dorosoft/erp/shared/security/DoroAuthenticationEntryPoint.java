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
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** STORE-02 임시 인증 스텁이며 Identity 모듈이 실제 세션 인증(Redis 기반)을 구현하면 교체·삭제 대상이다. */
@Component
public class DoroAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public DoroAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException, ServletException {
        String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        ProblemDetail problemDetail = ProblemDetailFactory.create(
                HttpStatus.UNAUTHORIZED,
                "AUTHENTICATION_REQUIRED",
                "인증이 필요합니다",
                requestId,
                List.of());
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problemDetail);
    }
}
