package com.dorosoft.erp.catalog.application.product;

import java.util.UUID;

public record CreateProductCommand(
        UUID categoryId,
        String name,
        String description,
        long basePrice,
        UUID mediaId,
        String imageAltText,
        boolean salesEnabled,
        boolean stockManaged,
        String idempotencyKey) {}
