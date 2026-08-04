package com.dorosoft.erp.identity.infrastructure.security;

import com.dorosoft.erp.identity.application.authentication.IdentityDeniedPrivacyAccessHandler;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;

public final class IdentityAccessDeniedHandler implements AccessDeniedHandler {

    private final SecurityProblemWriter problemWriter;
    private final IdentityDeniedPrivacyAccessHandler deniedPrivacyAccess;

    public IdentityAccessDeniedHandler(
            SecurityProblemWriter problemWriter,
            IdentityDeniedPrivacyAccessHandler deniedPrivacyAccess
    ) {
        this.problemWriter = problemWriter;
        this.deniedPrivacyAccess = deniedPrivacyAccess;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException, ServletException {
        if (accessDeniedException instanceof CsrfException) {
            problemWriter.write(request, response, 403, "CSRF_TOKEN_INVALID", "요청 검증 실패",
                    "요청 검증 정보가 유효하지 않습니다.");
            return;
        }
        try {
            deniedPrivacyAccess.recordIfRequired(request);
        } catch (RuntimeException exception) {
            problemWriter.write(request, response, 503, "PRIVACY_ACCESS_LOG_UNAVAILABLE",
                    "개인정보 접근기록 일시 중단", "개인정보 접근기록을 저장할 수 없습니다.");
            return;
        }
        problemWriter.write(request, response, 403, "FORBIDDEN", "접근 거부",
                "이 작업을 수행할 권한이 없습니다.");
    }
}
