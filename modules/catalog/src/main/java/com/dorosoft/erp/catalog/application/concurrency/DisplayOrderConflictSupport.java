package com.dorosoft.erp.catalog.application.concurrency;

import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 표시 순서(UNIQUE) 충돌 판별을 돕는다. 동시 생성·유입 시 실제 MySQL/InnoDB 동작은 두 갈래로 나타난다: (1) 한쪽이
 * 먼저 커밋해 다른 쪽이 {@code uk_category_display_order}/{@code uk_product_display_order} 위반으로
 * 실패하는 경우({@link DataIntegrityViolationException}), (2) 같은 표시 순서 범위(대상 UNIQUE 인덱스)와
 * 공유 catalog_revision 행을 동시에 건드리며 InnoDB가 순환 대기를 감지해 한쪽을 강제 종료하는 경우
 * ({@link org.springframework.dao.CannotAcquireLockException}, "Deadlock found when trying to get
 * lock"). 이 클래스가 다루는 것은 (1)의 제약 이름 판별뿐이며, (2)는 호출부에서 발생 범위(REQUIRES_NEW로 격리된
 * 단일 생성 시도)만으로 이미 표시 순서 충돌로 간주해도 안전하다.
 */
public final class DisplayOrderConflictSupport {

    private DisplayOrderConflictSupport() {}

    public static boolean matchesConstraint(DataIntegrityViolationException exception, String constraintName) {
        String needle = constraintName.toLowerCase(Locale.ROOT);
        Throwable cause = exception;
        while (cause != null) {
            String message = cause.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains(needle)) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
