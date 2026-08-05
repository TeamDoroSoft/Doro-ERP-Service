package com.dorosoft.erp.catalog.presentation.common;

import com.dorosoft.erp.catalog.application.port.audit.AuditContext;
import com.dorosoft.erp.identity.infrastructure.security.IdentityPrincipal;
import com.dorosoft.erp.platform.web.ApiErrorCode;
import com.dorosoft.erp.platform.web.ProblemAwareException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** 인증된 세션 Principal과 요청 정보로 Catalog 감사 기록용 AuditContext를 조립한다. */
@Component
public class CatalogWebContextFactory {

    public AuditContext create(Authentication authentication, HttpServletRequest request) {
        if (authentication == null || !(authentication.getPrincipal() instanceof IdentityPrincipal principal)) {
            throw new ProblemAwareException(ApiErrorCode.VALIDATION_FAILED, "인증 정보가 없습니다");
        }
        return new AuditContext(
                principal.accountId().toString(),
                principal.roleCode(),
                Instant.now(),
                CatalogRequestId.from(request));
    }
}
