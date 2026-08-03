package com.dorosoft.erp.store.presentation.dto;

import com.dorosoft.erp.store.domain.feature.FeatureSettings;
import com.dorosoft.erp.store.domain.schedule.BusinessPeriod;
import com.dorosoft.erp.store.domain.schedule.OperatingSchedule;
import com.dorosoft.erp.store.domain.schedule.ServiceType;
import com.dorosoft.erp.store.domain.schedule.ServiceWindow;
import com.dorosoft.erp.store.domain.schedule.TemporaryClosure;
import com.dorosoft.erp.store.domain.settings.StoreProfile;
import com.dorosoft.erp.store.domain.settings.StoreSettings;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StoreSettingsWebMapper {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private StoreSettingsWebMapper() {}

    public static StoreSettingsResponse toResponse(StoreSettings settings) {
        return new StoreSettingsResponse(
                toProfileResponse(settings.profile()),
                toScheduleResponse(settings.schedule()),
                toFeatureResponse(settings.features()),
                settings.version());
    }

    public static StoreProfileResponse toProfileResponse(StoreProfile profile) {
        return new StoreProfileResponse(
                profile.name(), profile.address(), profile.contact(), profile.timeZone().getId());
    }

    public static StoreScheduleResponse toScheduleResponse(OperatingSchedule schedule) {
        Map<String, List<StoreScheduleResponse.TimePeriodResponse>> businessHours =
                new LinkedHashMap<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            List<BusinessPeriod> periods = schedule.businessHours().get(day);
            if (periods != null) {
                businessHours.put(
                        day.name(),
                        periods.stream()
                                .sorted(Comparator.comparingInt(BusinessPeriod::order))
                                .map(period -> period(period.start(), period.end()))
                                .toList());
            }
        }

        List<String> regularClosedDays = schedule.regularClosedDays().stream()
                .sorted(Comparator.comparingInt(DayOfWeek::getValue))
                .map(Enum::name)
                .toList();
        List<StoreScheduleResponse.TemporaryClosureResponse> temporaryClosures =
                schedule.temporaryClosures().stream()
                        .sorted(Comparator.comparing(TemporaryClosure::date))
                        .map(closure -> new StoreScheduleResponse.TemporaryClosureResponse(
                                closure.date().toString(), closure.reason()))
                        .toList();

        Map<String, Map<String, List<StoreScheduleResponse.TimePeriodResponse>>> serviceWindows =
                new LinkedHashMap<>();
        for (ServiceType serviceType : ServiceType.values()) {
            Map<String, List<StoreScheduleResponse.TimePeriodResponse>> byDay = new LinkedHashMap<>();
            for (DayOfWeek day : DayOfWeek.values()) {
                List<ServiceWindow> windows = schedule.serviceWindows().stream()
                        .filter(window -> window.serviceType() == serviceType && window.dayOfWeek() == day)
                        .sorted(Comparator.comparingInt(ServiceWindow::order))
                        .toList();
                if (!windows.isEmpty()) {
                    byDay.put(
                            day.name(),
                            windows.stream()
                                    .map(window -> period(window.start(), window.end()))
                                    .toList());
                }
            }
            if (!byDay.isEmpty()) {
                serviceWindows.put(serviceType.name(), byDay);
            }
        }
        return new StoreScheduleResponse(
                Map.copyOf(businessHours),
                regularClosedDays,
                temporaryClosures,
                immutableNestedMap(serviceWindows));
    }

    public static StoreFeatureSettingsResponse toFeatureResponse(FeatureSettings features) {
        Map<String, Boolean> customerFeatures = new LinkedHashMap<>();
        features.customerFeatures().forEach((code, enabled) -> customerFeatures.put(code.name(), enabled));
        Map<String, Boolean> notificationEvents = new LinkedHashMap<>();
        features.notificationEvents().forEach((code, enabled) -> notificationEvents.put(code.name(), enabled));
        return new StoreFeatureSettingsResponse(Map.copyOf(customerFeatures), Map.copyOf(notificationEvents));
    }

    private static StoreScheduleResponse.TimePeriodResponse period(LocalTime start, LocalTime end) {
        return new StoreScheduleResponse.TimePeriodResponse(
                TIME_FORMAT.format(start), TIME_FORMAT.format(end));
    }

    private static Map<String, Map<String, List<StoreScheduleResponse.TimePeriodResponse>>>
            immutableNestedMap(
                    Map<String, Map<String, List<StoreScheduleResponse.TimePeriodResponse>>> source) {
        Map<String, Map<String, List<StoreScheduleResponse.TimePeriodResponse>>> result =
                new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, Map.copyOf(value)));
        return Map.copyOf(result);
    }
}
