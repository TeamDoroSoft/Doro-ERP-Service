package com.dorosoft.erp.store.application.audit;

import com.dorosoft.erp.store.application.audit.StoreScheduleAuditValue.TemporaryClosureValue;
import com.dorosoft.erp.store.application.audit.StoreScheduleAuditValue.TimePeriod;
import com.dorosoft.erp.store.domain.feature.FeatureSettings;
import com.dorosoft.erp.store.domain.schedule.BusinessPeriod;
import com.dorosoft.erp.store.domain.schedule.OperatingSchedule;
import com.dorosoft.erp.store.domain.schedule.ServiceType;
import com.dorosoft.erp.store.domain.schedule.ServiceWindow;
import com.dorosoft.erp.store.domain.schedule.TemporaryClosure;
import com.dorosoft.erp.store.domain.settings.StoreProfile;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class StoreAuditValueMapper {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private StoreAuditValueMapper() {}

    public static List<String> changedFields(StoreProfile before, StoreProfile after) {
        List<String> changed = new ArrayList<>();
        addIfChanged(changed, "name", before.name(), after.name());
        addIfChanged(changed, "address", before.address(), after.address());
        addIfChanged(changed, "contact", before.contact(), after.contact());
        addIfChanged(changed, "timeZone", before.timeZone(), after.timeZone());
        return List.copyOf(changed);
    }

    public static StoreProfileAuditValue profileBefore(StoreProfile before, long version) {
        return new StoreProfileAuditValue(
                before.name(), before.timeZone().getId(), List.of(), version);
    }

    public static StoreProfileAuditValue profileAfter(
            StoreProfile after, List<String> changedFields, long version) {
        return new StoreProfileAuditValue(
                after.name(), after.timeZone().getId(), List.copyOf(changedFields), version);
    }

    public static StoreScheduleAuditValue schedule(OperatingSchedule schedule, long version) {
        Map<String, List<TimePeriod>> businessHours = new LinkedHashMap<>();
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
        List<TemporaryClosureValue> temporaryClosures = schedule.temporaryClosures().stream()
                .sorted(Comparator.comparing(TemporaryClosure::date))
                .map(closure -> new TemporaryClosureValue(
                        closure.date().toString(), reasonCodeOf(closure)))
                .toList();

        Map<String, Map<String, List<TimePeriod>>> serviceWindows = new LinkedHashMap<>();
        for (ServiceType serviceType : ServiceType.values()) {
            Map<String, List<TimePeriod>> byDay = new LinkedHashMap<>();
            for (DayOfWeek day : DayOfWeek.values()) {
                List<ServiceWindow> windows = schedule.serviceWindows().stream()
                        .filter(window -> window.serviceType() == serviceType
                                && window.dayOfWeek() == day)
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
                serviceWindows.put(
                        serviceType.name(),
                        Collections.unmodifiableMap(new LinkedHashMap<>(byDay)));
            }
        }

        return new StoreScheduleAuditValue(
                Collections.unmodifiableMap(new LinkedHashMap<>(businessHours)),
                regularClosedDays,
                temporaryClosures,
                Collections.unmodifiableMap(new LinkedHashMap<>(serviceWindows)),
                version);
    }

    public static StoreFeatureSettingsAuditValue features(FeatureSettings features, long version) {
        Map<String, Boolean> featureSettings = new LinkedHashMap<>();
        features.customerFeatures().forEach((code, enabled) -> featureSettings.put(code.name(), enabled));
        Map<String, Boolean> notificationEventSettings = new LinkedHashMap<>();
        features.notificationEvents()
                .forEach((code, enabled) -> notificationEventSettings.put(code.name(), enabled));
        return new StoreFeatureSettingsAuditValue(
                Collections.unmodifiableMap(new LinkedHashMap<>(featureSettings)),
                Collections.unmodifiableMap(new LinkedHashMap<>(notificationEventSettings)),
                version);
    }

    public static String reasonCodeOf(TemporaryClosure closure) {
        return closure.reason() == null || closure.reason().isBlank() ? "UNSPECIFIED" : "OTHER";
    }

    private static void addIfChanged(List<String> changed, String field, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            changed.add(field);
        }
    }

    private static TimePeriod period(LocalTime start, LocalTime end) {
        return new TimePeriod(TIME_FORMAT.format(start), TIME_FORMAT.format(end));
    }
}
