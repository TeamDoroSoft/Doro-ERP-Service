package com.dorosoft.erp.catalog.domain.category;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 정렬 전체 교체 요청의 ID 집합 규칙(중복·누락·다른 소속 금지)을 검증한다. */
public final class DisplayOrderValidation {

    private DisplayOrderValidation() {}

    /** requestedIds가 existingIds와 정확히 같은 집합(중복 없이)이 아니면 InvalidDisplayOrderException을 던진다. */
    public static void requireExactIdSet(List<UUID> existingIds, List<UUID> requestedIds) {
        Set<UUID> requestedSet = new HashSet<>(requestedIds);
        if (requestedSet.size() != requestedIds.size()) {
            throw new InvalidDisplayOrderException("정렬 대상 ID가 중복되었습니다");
        }

        Set<UUID> existingSet = new HashSet<>(existingIds);
        if (!existingSet.equals(requestedSet)) {
            throw new InvalidDisplayOrderException("정렬 대상 ID 집합이 현재 대상과 일치하지 않습니다(누락 또는 다른 소속 포함)");
        }
    }
}
