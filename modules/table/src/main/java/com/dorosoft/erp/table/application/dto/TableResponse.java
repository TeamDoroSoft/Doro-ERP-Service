package com.dorosoft.erp.table.application.dto;

import com.dorosoft.erp.table.domain.TableUsageStatus;
import java.util.UUID;

public record TableResponse(
        UUID tableId,
        String tableNumber,
        String displayName,
        int seatCapacity,
        boolean active,
        TableUsageStatus usageStatus,
        long version) {}
