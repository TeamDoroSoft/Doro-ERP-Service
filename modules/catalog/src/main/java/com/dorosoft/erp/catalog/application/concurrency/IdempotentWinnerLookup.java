package com.dorosoft.erp.catalog.application.concurrency;

import java.util.function.Supplier;

/**
 * 동시 생성 경합에서 진 쪽이 같은 Idempotency-Key의 승자를 재조회할 때 쓴다. 승자의 REQUIRES_NEW
 * Transaction은 진 쪽이 충돌을 감지한 시점(락 경합·Deadlock 강제 종료)에 아직 Commit을 완전히 반영하지
 * 않았을 수 있어(락 경합 감지와 Commit 가시성 반영 사이에는 시간차가 있다), 승자가 보일 때까지 아주 짧게
 * 재시도한다. 이미 벌어진 충돌의 승자를 읽어오는 것일 뿐 실패한 쓰기 자체를 다시 시도하는 것이 아니므로
 * 서버 자동 재시도 금지 정책과 무관하다.
 */
public final class IdempotentWinnerLookup {

    private static final int MAX_ATTEMPTS = 20;
    private static final long RETRY_DELAY_MILLIS = 25L;

    private IdempotentWinnerLookup() {}

    public static <T> T await(Supplier<T> lookup) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            T found = lookup.get();
            if (found != null) {
                return found;
            }
            if (attempt < MAX_ATTEMPTS) {
                sleep();
            }
        }
        return null;
    }

    private static void sleep() {
        try {
            Thread.sleep(RETRY_DELAY_MILLIS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("승자 재조회 대기 중 Interrupt 되었습니다", ex);
        }
    }
}
