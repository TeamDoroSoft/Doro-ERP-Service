package com.dorosoft.erp.store.domain.availability;

import com.dorosoft.erp.store.domain.feature.FeatureCode;
import com.dorosoft.erp.store.domain.schedule.BusinessPeriod;
import com.dorosoft.erp.store.domain.schedule.OperatingSchedule;
import com.dorosoft.erp.store.domain.schedule.ServiceType;
import com.dorosoft.erp.store.domain.schedule.ServiceWindow;
import com.dorosoft.erp.store.domain.settings.StoreSettings;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Store 설정 Snapshot만으로 고객 기능의 가용성을 판정하는 도메인 서비스. */
public final class AvailabilityPolicy {

    public FeatureAvailability evaluate(
            StoreSettings settings, FeatureCode featureCode, Instant instant) {
        Objects.requireNonNull(settings, "settings는 null일 수 없습니다");
        Objects.requireNonNull(featureCode, "featureCode는 null일 수 없습니다");
        Objects.requireNonNull(instant, "instant는 null일 수 없습니다");

        AvailabilityReason reason = evaluateReason(settings, featureCode, instant);
        if (reason == AvailabilityReason.AVAILABLE) {
            return new FeatureAvailability(featureCode, true, reason, instant, null);
        }
        if (reason == AvailabilityReason.FEATURE_DISABLED) {
            return new FeatureAvailability(featureCode, false, reason, instant, null);
        }
        return new FeatureAvailability(
                featureCode, false, reason, instant, findNextAvailable(settings, featureCode, instant));
    }

    private AvailabilityReason evaluateReason(
            StoreSettings settings, FeatureCode featureCode, Instant instant) {
        if (!settings.features().isEnabled(featureCode)) {
            return AvailabilityReason.FEATURE_DISABLED;
        }

        ZoneId zone = settings.profile().timeZone();
        LocalDate localDate = instant.atZone(zone).toLocalDate();
        OperatingSchedule schedule = settings.schedule();
        if (schedule.isTemporarilyClosed(localDate)) {
            return AvailabilityReason.TEMPORARILY_CLOSED;
        }
        if (schedule.isRegularlyClosed(localDate.getDayOfWeek())) {
            return AvailabilityReason.REGULARLY_CLOSED;
        }
        if (!schedule.isBusinessOpen(instant, zone)) {
            return AvailabilityReason.OUTSIDE_BUSINESS_HOURS;
        }

        ServiceType serviceType = serviceTypeOf(featureCode);
        if (serviceType != null && !schedule.isServiceOpen(serviceType, instant, zone)) {
            return AvailabilityReason.OUTSIDE_SERVICE_WINDOW;
        }
        return AvailabilityReason.AVAILABLE;
    }

    private Instant findNextAvailable(
            StoreSettings settings, FeatureCode featureCode, Instant instant) {
        ZoneId zone = settings.profile().timeZone();
        LocalDate localDate = instant.atZone(zone).toLocalDate();
        OperatingSchedule schedule = settings.schedule();
        ServiceType serviceType = serviceTypeOf(featureCode);
        List<Instant> candidates = new ArrayList<>();

        for (LocalDate date = localDate.minusDays(1);
                !date.isAfter(localDate.plusDays(30));
                date = date.plusDays(1)) {
            for (BusinessPeriod period :
                    schedule.businessHours().getOrDefault(date.getDayOfWeek(), List.of())) {
                addStartCandidates(candidates, date, period.start(), zone);
            }
            if (serviceType != null) {
                for (ServiceWindow window : schedule.serviceWindows()) {
                    if (window.serviceType() == serviceType
                            && window.dayOfWeek() == date.getDayOfWeek()) {
                        addStartCandidates(candidates, date, window.start(), zone);
                    }
                }
            }
        }

        return candidates.stream()
                .filter(candidate -> !candidate.isBefore(instant))
                .distinct()
                .sorted(Comparator.naturalOrder())
                .filter(
                        candidate ->
                                evaluateReason(settings, featureCode, candidate)
                                        == AvailabilityReason.AVAILABLE)
                .findFirst()
                .orElse(null);
    }

    private static void addStartCandidates(
            List<Instant> candidates, LocalDate date, LocalTime time, ZoneId zone) {
        ZonedDateTime earlier = ZonedDateTime.of(date, time, zone);
        candidates.add(earlier.toInstant());
        ZonedDateTime later = earlier.withLaterOffsetAtOverlap();
        if (!later.toInstant().equals(earlier.toInstant())) {
            candidates.add(later.toInstant());
        }
    }

    private static ServiceType serviceTypeOf(FeatureCode featureCode) {
        return switch (featureCode) {
            case WAITING -> null;
            case QR_ORDER, PICKUP_ORDER -> ServiceType.ORDER;
            case RESERVATION -> ServiceType.RESERVATION;
        };
    }
}
