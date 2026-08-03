package com.dorosoft.erp.catalog.application.api;

import java.util.UUID;

public record SelectedOptionSnapshot(UUID optionId, String optionName, long additionalPrice) {}
