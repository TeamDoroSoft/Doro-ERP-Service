package com.dorosoft.erp.store.domain.availability;

import static org.assertj.core.api.Assertions.assertThat;

import com.dorosoft.erp.store.domain.feature.FeatureCode;
import com.dorosoft.erp.store.domain.feature.FeatureSettings;
import com.dorosoft.erp.store.domain.feature.NotificationEventCode;
import com.dorosoft.erp.store.domain.schedule.BusinessPeriod;
import com.dorosoft.erp.store.domain.schedule.OperatingSchedule;
import com.dorosoft.erp.store.domain.schedule.ServiceType;
import com.dorosoft.erp.store.domain.schedule.ServiceWindow;
import com.dorosoft.erp.store.domain.schedule.TemporaryClosure;
import com.dorosoft.erp.store.domain.settings.StoreProfile;
import com.dorosoft.erp.store.domain.settings.StoreSettings;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AvailabilityPolicyTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate MONDAY = LocalDate.of(2026, 8, 3);
    private final AvailabilityPolicy policy = new AvailabilityPolicy();

    @Test
    void followsReasonPriorityAndReturnsAvailable() {
        OperatingSchedule ordinary = mondaySchedule(Set.of(), Set.of());
        assertReason(settings(ordinary, Set.of()), FeatureCode.QR_ORDER, at(SEOUL, MONDAY, 12, 0),
                AvailabilityReason.FEATURE_DISABLED);

        OperatingSchedule temporary =
                mondaySchedule(Set.of(), Set.of(new TemporaryClosure(MONDAY, "점검")));
        assertReason(settings(temporary, Set.of(FeatureCode.QR_ORDER)), FeatureCode.QR_ORDER,
                at(SEOUL, MONDAY, 8, 0), AvailabilityReason.TEMPORARILY_CLOSED);

        OperatingSchedule regular =
                OperatingSchedule.of(Map.of(), Set.of(DayOfWeek.MONDAY), Set.of(), Set.of());
        assertReason(settings(regular, Set.of(FeatureCode.QR_ORDER)), FeatureCode.QR_ORDER,
                at(SEOUL, MONDAY, 12, 0), AvailabilityReason.REGULARLY_CLOSED);

        assertReason(settings(ordinary, Set.of(FeatureCode.QR_ORDER)), FeatureCode.QR_ORDER,
                at(SEOUL, MONDAY, 8, 0), AvailabilityReason.OUTSIDE_BUSINESS_HOURS);
        assertReason(settings(ordinary, Set.of(FeatureCode.QR_ORDER)), FeatureCode.QR_ORDER,
                at(SEOUL, MONDAY, 9, 30), AvailabilityReason.OUTSIDE_SERVICE_WINDOW);
        assertReason(settings(ordinary, Set.of(FeatureCode.QR_ORDER)), FeatureCode.QR_ORDER,
                at(SEOUL, MONDAY, 10, 0), AvailabilityReason.AVAILABLE);
    }

    @Test
    void waitingSkipsServiceWindows() {
        OperatingSchedule schedule =
                OperatingSchedule.of(
                        Map.of(DayOfWeek.MONDAY, List.of(period(9, 18))),
                        Set.of(), Set.of(), Set.of());

        FeatureAvailability result = policy.evaluate(
                settings(schedule, Set.of(FeatureCode.WAITING)), FeatureCode.WAITING,
                at(SEOUL, MONDAY, 12, 0));

        assertThat(result.available()).isTrue();
        assertThat(result.reason()).isEqualTo(AvailabilityReason.AVAILABLE);
    }

    @Test
    void mapsOrderAndReservationFeaturesToTheirOwnWindows() {
        StoreSettings settings = settings(mondaySchedule(Set.of(), Set.of()), Set.of(
                FeatureCode.QR_ORDER, FeatureCode.PICKUP_ORDER, FeatureCode.RESERVATION));

        assertThat(policy.evaluate(settings, FeatureCode.QR_ORDER, at(SEOUL, MONDAY, 10, 30)).available())
                .isTrue();
        assertThat(policy.evaluate(settings, FeatureCode.PICKUP_ORDER, at(SEOUL, MONDAY, 10, 30)).available())
                .isTrue();
        assertThat(policy.evaluate(settings, FeatureCode.RESERVATION, at(SEOUL, MONDAY, 10, 30)).reason())
                .isEqualTo(AvailabilityReason.OUTSIDE_SERVICE_WINDOW);
        assertThat(policy.evaluate(settings, FeatureCode.RESERVATION, at(SEOUL, MONDAY, 15, 0)).available())
                .isTrue();
    }

    @Test
    void disabledFeatureNeverSearchesForNextAvailability() {
        FeatureAvailability result = policy.evaluate(
                settings(mondaySchedule(Set.of(), Set.of()), Set.of()), FeatureCode.QR_ORDER,
                at(SEOUL, MONDAY, 8, 0));

        assertThat(result.nextAvailableAt()).isNull();
    }

    @Test
    void findsExactNextAvailabilityWithinThirtyOneLocalDates() {
        StoreSettings settings =
                settings(mondaySchedule(Set.of(), Set.of()), Set.of(FeatureCode.QR_ORDER));

        FeatureAvailability result =
                policy.evaluate(settings, FeatureCode.QR_ORDER, at(SEOUL, MONDAY, 8, 0));

        assertThat(result.nextAvailableAt()).isEqualTo(at(SEOUL, MONDAY, 10, 0));
    }

    @Test
    void returnsNullWhenNoServiceWindowCandidateCanBecomeAvailable() {
        OperatingSchedule schedule =
                OperatingSchedule.of(
                        Map.of(DayOfWeek.MONDAY, List.of(period(9, 18))),
                        Set.of(), Set.of(), Set.of());

        FeatureAvailability result = policy.evaluate(
                settings(schedule, Set.of(FeatureCode.QR_ORDER)), FeatureCode.QR_ORDER,
                at(SEOUL, MONDAY, 12, 0));

        assertThat(result.nextAvailableAt()).isNull();
    }

    @Test
    void springGapMovesCandidateToFirstResolvedLocalTime() {
        ZoneId zone = ZoneId.of("America/New_York");
        LocalDate transitionDay = LocalDate.of(2026, 3, 8);
        OperatingSchedule schedule =
                OperatingSchedule.of(
                        Map.of(DayOfWeek.SUNDAY, List.of(period(2, 30, 4, 0))),
                        Set.of(), Set.of(), Set.of());

        FeatureAvailability result = policy.evaluate(
                settings(schedule, Set.of(FeatureCode.WAITING), zone), FeatureCode.WAITING,
                at(zone, transitionDay, 1, 59));

        // 02:30 is shifted forward by the one-hour gap to 03:30 EDT (07:30Z), not to 03:00.
        // BusinessPeriod.toInterval() uses the same Java default gap resolution, so the actual
        // business-opening boundary is also 03:30 EDT.
        assertThat(result.nextAvailableAt())
                .isEqualTo(Instant.parse("2026-03-08T07:30:00Z"));
    }

    @Test
    void bothOverlapInstantsUseTheSameBusinessRule() {
        ZoneId zone = ZoneId.of("America/New_York");
        LocalDate transitionDay = LocalDate.of(2026, 11, 1);
        OperatingSchedule schedule =
                OperatingSchedule.of(
                        Map.of(DayOfWeek.SUNDAY, List.of(period(1, 0, 2, 0))),
                        Set.of(), Set.of(), Set.of());
        StoreSettings settings = settings(schedule, Set.of(FeatureCode.WAITING), zone);
        ZonedDateTime earlier = ZonedDateTime.of(transitionDay, LocalTime.of(1, 30), zone);
        ZonedDateTime later = earlier.withLaterOffsetAtOverlap();

        FeatureAvailability first = policy.evaluate(settings, FeatureCode.WAITING, earlier.toInstant());
        FeatureAvailability second = policy.evaluate(settings, FeatureCode.WAITING, later.toInstant());

        assertThat(first.available()).isTrue();
        assertThat(second.available()).isTrue();
        assertThat(second.reason()).isEqualTo(first.reason());
    }

    @Test
    void overlapSearchChoosesEarliestCandidateAfterReferenceInstant() {
        ZoneId zone = ZoneId.of("America/New_York");
        OperatingSchedule schedule =
                OperatingSchedule.of(
                        Map.of(
                                DayOfWeek.SUNDAY,
                                List.of(period(1, 0, 1, 15), period(1, 45, 2, 0))),
                        Set.of(), Set.of(), Set.of());
        StoreSettings settings = settings(schedule, Set.of(FeatureCode.WAITING), zone);
        Instant reference = Instant.parse("2026-11-01T05:20:00Z");

        FeatureAvailability result =
                policy.evaluate(settings, FeatureCode.WAITING, reference);

        assertThat(result.reason()).isEqualTo(AvailabilityReason.OUTSIDE_BUSINESS_HOURS);
        assertThat(result.nextAvailableAt()).isEqualTo(Instant.parse("2026-11-01T05:45:00Z"));
    }

    private void assertReason(
            StoreSettings settings, FeatureCode code, Instant instant, AvailabilityReason expected) {
        assertThat(policy.evaluate(settings, code, instant).reason()).isEqualTo(expected);
    }

    private static OperatingSchedule mondaySchedule(
            Set<DayOfWeek> closedDays, Set<TemporaryClosure> closures) {
        return OperatingSchedule.of(
                Map.of(DayOfWeek.MONDAY, List.of(period(9, 18))),
                closedDays,
                closures,
                Set.of(
                        window(ServiceType.ORDER, DayOfWeek.MONDAY, 10, 12),
                        window(ServiceType.RESERVATION, DayOfWeek.MONDAY, 14, 16)));
    }

    private static StoreSettings settings(
            OperatingSchedule schedule, Set<FeatureCode> enabled) {
        return settings(schedule, enabled, SEOUL);
    }

    private static StoreSettings settings(
            OperatingSchedule schedule, Set<FeatureCode> enabled, ZoneId zone) {
        EnumMap<FeatureCode, Boolean> features = new EnumMap<>(FeatureCode.class);
        for (FeatureCode code : FeatureCode.values()) {
            features.put(code, enabled.contains(code));
        }
        EnumMap<NotificationEventCode, Boolean> notifications =
                new EnumMap<>(NotificationEventCode.class);
        for (NotificationEventCode code : NotificationEventCode.values()) {
            notifications.put(code, false);
        }
        return StoreSettings.create(
                UUID.randomUUID(),
                new StoreProfile("매장", "주소", "010-0000-0000", zone),
                schedule,
                FeatureSettings.of(features, notifications));
    }

    private static BusinessPeriod period(int start, int end) {
        return period(start, 0, end, 0);
    }

    private static BusinessPeriod period(int startHour, int startMinute, int endHour, int endMinute) {
        return new BusinessPeriod(
                0, LocalTime.of(startHour, startMinute), LocalTime.of(endHour, endMinute));
    }

    private static ServiceWindow window(
            ServiceType type, DayOfWeek day, int start, int end) {
        return new ServiceWindow(
                type, day, 0, LocalTime.of(start, 0), LocalTime.of(end, 0));
    }

    private static Instant at(
            ZoneId zone, LocalDate date, int hour, int minute) {
        return ZonedDateTime.of(date, LocalTime.of(hour, minute), zone).toInstant();
    }
}
