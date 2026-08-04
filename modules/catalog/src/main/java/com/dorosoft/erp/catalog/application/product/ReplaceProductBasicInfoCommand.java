package com.dorosoft.erp.catalog.application.product;

import java.util.UUID;

public record ReplaceProductBasicInfoCommand(
        UUID categoryId,
        String name,
        String description,
        long basePrice,
        UUID mediaId,
        String imageAltText,
        boolean salesEnabled,
        boolean stockManaged) {}
