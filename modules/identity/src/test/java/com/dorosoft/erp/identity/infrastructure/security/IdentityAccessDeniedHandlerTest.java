package com.dorosoft.erp.identity.infrastructure.security;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;

class IdentityAccessDeniedHandlerTest {
    @Test
    void privacyFailureOverridesTheOriginalForbiddenResponse() throws Exception {
        SecurityProblemWriter writer = mock(SecurityProblemWriter.class);
        IdentityDeniedPrivacyAccess privacy = mock(IdentityDeniedPrivacyAccess.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/employees");
        MockHttpServletResponse response = new MockHttpServletResponse();
        doThrow(new IllegalStateException("append failed")).when(privacy).recordIfRequired(request);

        new IdentityAccessDeniedHandler(writer, privacy)
                .handle(request, response, new AccessDeniedException("denied"));

        verify(writer).write(request, response, 503, "PRIVACY_ACCESS_LOG_UNAVAILABLE",
                "개인정보 접근기록 일시 중단", "개인정보 접근기록을 저장할 수 없습니다.");
    }

    @Test
    void csrfDenialDoesNotCreateAPrivacyRecord() throws Exception {
        SecurityProblemWriter writer = mock(SecurityProblemWriter.class);
        IdentityDeniedPrivacyAccess privacy = mock(IdentityDeniedPrivacyAccess.class);
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/api/v1/employees/1/status");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new IdentityAccessDeniedHandler(writer, privacy).handle(
                request, response, new InvalidCsrfTokenException(
                        SessionCsrfTokenRepository.token("expected-token"), "invalid-token"));

        verifyNoInteractions(privacy);
        verify(writer).write(request, response, 403, "CSRF_TOKEN_INVALID", "요청 검증 실패",
                "요청 검증 정보가 유효하지 않습니다.");
    }
}
