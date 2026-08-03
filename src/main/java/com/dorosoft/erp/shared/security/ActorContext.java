package com.dorosoft.erp.shared.security;

import java.util.Set;

/** STORE-02 임시 인증 스텁이며 Identity 모듈이 실제 세션 인증(Redis 기반)을 구현하면 교체·삭제 대상이다. */
public record ActorContext(String actorId, String actorRole, Set<String> permissions) {
}
