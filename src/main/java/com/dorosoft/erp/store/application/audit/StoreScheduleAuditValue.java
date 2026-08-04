package com.dorosoft.erp.store.application.audit;

import java.util.List;
import java.util.Map;

public record StoreScheduleAuditValue(
        Map<String, List<TimePeriod>> businessHours,
        List<String> regularClosedDays,
        List<TemporaryClosureValue> temporaryClosures,
        Map<String, Map<String, List<TimePeriod>>> serviceWindows,
        long version) {

    public record TimePeriod(String start, String end) {}

    public record TemporaryClosureValue(String date, String reasonCode) {}
}
