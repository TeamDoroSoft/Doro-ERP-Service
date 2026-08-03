package com.dorosoft.erp.catalog.domain.product;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 상품 기본 정보, 가격, 대표 이미지 참조와 옵션을 포함하는 Aggregate(FR-MENU-001, 002, 004).
 * 옵션 변경은 이 Aggregate를 통해서만 이뤄지며 Product version을 증가시킨다.
 * 수동 품절(soldOut)은 이 클래스가 다루지 않고 전용 Use Case(MENU-05)가 소유한다.
 */
public record Product(
        UUID productId,
        UUID catalogId,
        UUID categoryId,
        UUID mediaId,
        String name,
        String description,
        long basePrice,
        String imageAltText,
        boolean salesEnabled,
        boolean soldOut,
        boolean stockManaged,
        int displayOrder,
        long version,
        Instant createdAt,
        Instant updatedAt,
        List<ProductOption> options,
        String idempotencyKey,
        String idempotencyRequestHash) {

    private static final int NAME_MAX_LENGTH = 100;
    private static final int DESCRIPTION_MAX_LENGTH = 2000;
    private static final int IMAGE_ALT_TEXT_MAX_LENGTH = 200;

    public Product {
        Objects.requireNonNull(productId, "productId는 필수다");
        Objects.requireNonNull(catalogId, "catalogId는 필수다");
        Objects.requireNonNull(categoryId, "categoryId는 필수다");
        name = requireValidName(name);
        description = requireValidDescription(description);
        imageAltText = requireValidImageAltText(imageAltText);
        if (basePrice < 0) {
            throw new InvalidPriceException("상품 가격", basePrice);
        }
        if (displayOrder < 0) {
            throw new IllegalArgumentException("displayOrder는 0 이상이어야 한다");
        }
        options = List.copyOf(options);
    }

    public static Product create(
            UUID productId,
            UUID catalogId,
            UUID categoryId,
            UUID mediaId,
            String name,
            String description,
            long basePrice,
            String imageAltText,
            boolean salesEnabled,
            boolean stockManaged,
            int displayOrder,
            Instant now,
            String idempotencyKey,
            String idempotencyRequestHash) {
        return new Product(
                productId, catalogId, categoryId, mediaId, name, description, basePrice, imageAltText,
                salesEnabled, false, stockManaged, displayOrder, 0L, now, now, List.of(),
                idempotencyKey, idempotencyRequestHash);
    }

    /** 생성과 같은 기본 정보 필드를 전체 교체한다. soldOut·options·version은 바뀌지 않는다. */
    public Product replaceBasicInfo(
            UUID categoryId,
            UUID mediaId,
            String name,
            String description,
            long basePrice,
            String imageAltText,
            boolean salesEnabled,
            boolean stockManaged,
            int displayOrder) {
        return new Product(
                productId, catalogId, categoryId, mediaId, name, description, basePrice, imageAltText,
                salesEnabled, soldOut, stockManaged, displayOrder, version, createdAt, updatedAt, options,
                idempotencyKey, idempotencyRequestHash);
    }

    /**
     * 옵션을 전체 교체한다. 새 Option은 optionId가 null이며 서버가 새로 부여한다.
     * 기존 Option ID는 모두 요청에 포함돼야 하고(누락 시 거부), 다른 Product Option ID나
     * 존재하지 않는 ID, 중복 ID는 거부한다. 배열 순서대로 displayOrder를 부여한다.
     */
    public Product replaceOptions(List<ProductOptionRequest> requested) {
        Set<UUID> existingIds = options.stream().map(ProductOption::optionId).collect(java.util.stream.Collectors.toSet());

        List<UUID> requestedExistingIds =
                requested.stream().map(ProductOptionRequest::optionId).filter(Objects::nonNull).toList();
        Set<UUID> requestedExistingIdSet = new HashSet<>(requestedExistingIds);
        if (requestedExistingIdSet.size() != requestedExistingIds.size()) {
            throw new InvalidProductOptionsException("옵션 ID가 중복되었습니다");
        }
        for (UUID id : requestedExistingIdSet) {
            if (!existingIds.contains(id)) {
                throw new InvalidProductOptionsException("다른 상품의 옵션이거나 존재하지 않는 옵션입니다: " + id);
            }
        }
        if (!requestedExistingIdSet.containsAll(existingIds)) {
            throw new OptionOmissionNotAllowedException("기존 옵션 ID가 누락되었습니다. 제거 대신 enabled=false를 사용해야 합니다");
        }

        List<ProductOption> newOptions = new ArrayList<>();
        for (int i = 0; i < requested.size(); i++) {
            ProductOptionRequest r = requested.get(i);
            UUID optionId = r.optionId() != null ? r.optionId() : UUID.randomUUID();
            newOptions.add(new ProductOption(optionId, r.name(), r.additionalPrice(), r.enabled(), i));
        }

        return new Product(
                productId, catalogId, categoryId, mediaId, name, description, basePrice, imageAltText,
                salesEnabled, soldOut, stockManaged, displayOrder, version, createdAt, updatedAt, newOptions,
                idempotencyKey, idempotencyRequestHash);
    }

    private static String requireValidName(String name) {
        Objects.requireNonNull(name, "name은 필수다");
        String trimmed = name.trim();
        if (trimmed.isEmpty() || trimmed.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("name은 공백 제거 후 1~" + NAME_MAX_LENGTH + "자여야 한다");
        }
        return trimmed;
    }

    private static String requireValidDescription(String description) {
        if (description == null) {
            return null;
        }
        if (description.length() > DESCRIPTION_MAX_LENGTH) {
            throw new IllegalArgumentException("description은 " + DESCRIPTION_MAX_LENGTH + "자 이하여야 한다");
        }
        return description;
    }

    private static String requireValidImageAltText(String imageAltText) {
        if (imageAltText == null) {
            return null;
        }
        if (imageAltText.length() > IMAGE_ALT_TEXT_MAX_LENGTH) {
            throw new IllegalArgumentException("imageAltText는 " + IMAGE_ALT_TEXT_MAX_LENGTH + "자 이하여야 한다");
        }
        return imageAltText;
    }
}
