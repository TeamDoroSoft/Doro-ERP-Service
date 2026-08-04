package com.dorosoft.erp.store.application.schedule;

import com.dorosoft.erp.store.domain.schedule.BusinessPeriod;
import com.dorosoft.erp.store.domain.schedule.ServiceWindow;
import com.dorosoft.erp.store.domain.schedule.TemporaryClosure;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record UpdateScheduleCommand(
        Map<DayOfWeek, List<BusinessPeriod>> businessHours,
        Set<DayOfWeek> regularClosedDays,
        Set<TemporaryClosure> temporaryClosures,
        Set<ServiceWindow> serviceWindows,
        long ifMatchVersion) {}
