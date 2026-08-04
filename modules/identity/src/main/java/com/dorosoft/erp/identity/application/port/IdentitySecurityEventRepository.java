package com.dorosoft.erp.identity.application.port;

import com.dorosoft.erp.identity.domain.securityevent.IdentitySecurityEvent;

public interface IdentitySecurityEventRepository {
    /** 결정적 Event ID 재시도는 이미 저장된 성공으로 처리한다. */
    void appendIfAbsent(IdentitySecurityEvent event);
}
