package com.dorosoft.erp.shared.security;

import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** STORE-02 임시 인증 스텁이며 Identity 모듈이 실제 세션 인증(Redis 기반)을 구현하면 교체·삭제 대상이다. */
@Component
public class ActorContextProvider {

    public ActorContext currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("인증된 사용자 정보가 없습니다");
        }

        Set<String> permissions = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .collect(Collectors.toUnmodifiableSet());
        Object details = authentication.getDetails();
        String actorRole = details instanceof String role ? role : null;
        return new ActorContext(authentication.getName(), actorRole, permissions);
    }
}
