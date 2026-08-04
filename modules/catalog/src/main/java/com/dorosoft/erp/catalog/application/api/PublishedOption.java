package com.dorosoft.erp.catalog.application.api;

import java.util.UUID;

/** 공개 메뉴의 옵션 Projection. 비활성 옵션은 이 목록에 포함되지 않는다. */
public record PublishedOption(UUID optionId, String name, long additionalPrice) {}
