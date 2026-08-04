package com.dorosoft.erp.table.application.port;

import com.dorosoft.erp.table.domain.TableUsageStatus;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface TableUsageStatusReader {

    TableUsageStatus getUsageStatus(UUID tableId);

    Map<UUID, TableUsageStatus> getUsageStatuses(Collection<UUID> tableIds);
}
