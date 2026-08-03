package com.dorosoft.erp.catalog.domain.category;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 카테고리명과 Catalog 안의 표시 순서(FR-MENU-003). 순서는 정렬 전체 교체 Use Case에서만 바뀐다. */
public record Category(
        UUID categoryId, UUID catalogId, String name, int displayOrder, long version, Instant createdAt, Instant updatedAt) {

    private static final int NAME_MAX_LENGTH = 60;

    public Category {
        Objects.requireNonNull(categoryId, "categoryId는 필수다");
        Objects.requireNonNull(catalogId, "catalogId는 필수다");
        name = requireValidName(name);
        if (displayOrder < 0) {
            throw new IllegalArgumentException("displayOrder는 0 이상이어야 한다");
        }
        Objects.requireNonNull(createdAt, "createdAt은 필수다");
        Objects.requireNonNull(updatedAt, "updatedAt은 필수다");
    }

    public static Category create(UUID categoryId, UUID catalogId, String name, int displayOrder, Instant now) {
        return new Category(categoryId, catalogId, name, displayOrder, 0L, now, now);
    }

    /** 이름만 바꾼다. version은 매개변수로 받은 값(호출자가 확인한 최신값)을 그대로 유지한다. */
    public Category rename(String newName) {
        return new Category(categoryId, catalogId, newName, displayOrder, version, createdAt, updatedAt);
    }

    private static String requireValidName(String name) {
        Objects.requireNonNull(name, "name은 필수다");
        String trimmed = name.trim();
        if (trimmed.isEmpty() || trimmed.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("name은 공백 제거 후 1~" + NAME_MAX_LENGTH + "자여야 한다");
        }
        return trimmed;
    }
}
