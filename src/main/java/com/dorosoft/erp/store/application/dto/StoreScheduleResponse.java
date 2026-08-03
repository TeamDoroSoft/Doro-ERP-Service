package com.dorosoft.erp.store.application.dto;

import java.util.List;
import java.util.Map;

public record StoreScheduleResponse(
        Map<String, List<TimePeriodResponse>> businessHours,
        List<String> regularClosedDays,
        List<TemporaryClosureResponse> temporaryClosures,
        Map<String, Map<String, List<TimePeriodResponse>>> serviceWindows) {

    public record TimePeriodResponse(String start, String end) {}

    public record TemporaryClosureResponse(String date, String reason) {}
}
