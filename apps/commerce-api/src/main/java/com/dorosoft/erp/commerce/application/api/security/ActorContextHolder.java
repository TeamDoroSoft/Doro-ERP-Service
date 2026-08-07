package com.dorosoft.erp.commerce.application.api.security;

import java.util.Optional;

/**
 * 요청 Thread에 묶인 검증 완료 Actor Context 보관소.
 *
 * <p>Infrastructure 인증 Filter가 설정하고 Presentation·Application이 읽는다.
 * Filter는 요청 종료 시 반드시 {@link #clear()}를 호출한다.
 */
public final class ActorContextHolder {

    private static final ThreadLocal<ActorContext> CURRENT = new ThreadLocal<>();

    private ActorContextHolder() {
    }

    public static void set(ActorContext context) {
        CURRENT.set(context);
    }

    public static Optional<ActorContext> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static void clear() {
        CURRENT.remove();
    }
}
