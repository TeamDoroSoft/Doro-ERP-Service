package com.dorosoft.erp.store.domain.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("OperatingSchedule.validate(): 영업 구간·서비스 구간·정기 휴무의 불변식")
class OperatingScheduleValidateTest {

    private static BusinessPeriod period(int order, int startHour, int endHour) {
        return new BusinessPeriod(order, LocalTime.of(startHour, 0), LocalTime.of(endHour, 0));
    }

    private static ServiceWindow window(
            ServiceType type, DayOfWeek day, int order, int startHour, int endHour) {
        return new ServiceWindow(type, day, order, LocalTime.of(startHour, 0), LocalTime.of(endHour, 0));
    }

    @Test
    @DisplayName("같은 요일 안에서 영업 구간이 겹치면 거부한다")
    void rejectsOverlappingPeriodsWithinSameDay() {
        OperatingSchedule schedule =
                OperatingSchedule.of(
                        Map.of(DayOfWeek.MONDAY, List.of(period(0, 9, 12), period(1, 11, 15))),
                        Set.of(),
                        Set.of(),
                        Set.of());

        assertThatThrownBy(schedule::validate)
                .isInstanceOfSatisfying(
                        OperatingScheduleViolationException.class,
                        exception -> {
                            assertThat(exception.reason())
                                    .isEqualTo(
                                            OperatingScheduleViolationException.Reason
                                                    .OVERLAPPING_BUSINESS_HOURS);
                            assertThat(exception.field()).isEqualTo("businessHours.MONDAY");
                        })
                .hasMessageContaining("영업 구간이 서로 겹칩니다");
    }

    @Test
    @DisplayName("자정을 넘긴 구간이 다음 요일 구간과 겹치면 거부한다")
    void rejectsOverlapAcrossMidnightWithNextDay() {
        OperatingSchedule schedule =
                OperatingSchedule.of(
                        Map.of(
                                DayOfWeek.MONDAY, List.of(period(0, 22, 2)),
                                DayOfWeek.TUESDAY, List.of(period(0, 1, 5))),
                        Set.of(),
                        Set.of(),
                        Set.of());

        assertThatThrownBy(schedule::validate)
                .isInstanceOfSatisfying(
                        OperatingScheduleViolationException.class,
                        exception -> {
                            assertThat(exception.reason())
                                    .isEqualTo(
                                            OperatingScheduleViolationException.Reason
                                                    .OVERLAPPING_BUSINESS_HOURS);
                            assertThat(exception.field()).isEqualTo("businessHours.MONDAY");
                        })
                .hasMessageContaining("영업 구간이 서로 겹칩니다");
    }

    @Test
    @DisplayName("서비스 구간이 영업시간 합집합을 벗어나면 거부한다")
    void rejectsServiceWindowOutsideBusinessHours() {
        OperatingSchedule schedule =
                OperatingSchedule.of(
                        Map.of(DayOfWeek.MONDAY, List.of(period(0, 9, 18))),
                        Set.of(),
                        Set.of(),
                        Set.of(window(ServiceType.ORDER, DayOfWeek.MONDAY, 0, 8, 10)));

        assertThatThrownBy(schedule::validate)
                .isInstanceOfSatisfying(
                        OperatingScheduleViolationException.class,
                        exception -> {
                            assertThat(exception.reason())
                                    .isEqualTo(
                                            OperatingScheduleViolationException.Reason
                                                    .SERVICE_WINDOW_OUTSIDE_BUSINESS_HOURS);
                            assertThat(exception.field())
                                    .isEqualTo("serviceWindows.ORDER.MONDAY");
                        })
                .hasMessageContaining("서비스 구간이 영업시간을 벗어납니다");
    }

    @Test
    @DisplayName("맞닿은 영업 구간은 하나로 병합되므로 두 구간에 걸친 서비스 구간도 통과한다")
    void mergesAdjacentBusinessPeriodsForServiceWindowCheck() {
        // 09:00~12:00 + 12:00~18:00 은 09:00~18:00 로 병합된다
        OperatingSchedule schedule =
                OperatingSchedule.of(
                        Map.of(DayOfWeek.MONDAY, List.of(period(0, 9, 12), period(1, 12, 18))),
                        Set.of(),
                        Set.of(),
                        Set.of(window(ServiceType.ORDER, DayOfWeek.MONDAY, 0, 10, 17)));

        assertThatCode(schedule::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("정기 휴무 요일에 영업 구간이 있으면 거부한다")
    void rejectsBusinessPeriodOnRegularClosedDay() {
        OperatingSchedule schedule =
                OperatingSchedule.of(
                        Map.of(DayOfWeek.SUNDAY, List.of(period(0, 9, 18))),
                        Set.of(DayOfWeek.SUNDAY),
                        Set.of(),
                        Set.of());

        assertThatThrownBy(schedule::validate)
                .isInstanceOfSatisfying(
                        OperatingScheduleViolationException.class,
                        exception -> {
                            assertThat(exception.reason())
                                    .isEqualTo(
                                            OperatingScheduleViolationException.Reason
                                                    .CLOSED_DAY_HAS_BUSINESS_HOURS);
                            assertThat(exception.field()).isEqualTo("businessHours.SUNDAY");
                        })
                .hasMessageContaining("정기 휴무 요일에 영업 구간이 존재합니다");
    }

    @Test
    @DisplayName("정기 휴무 요일에 서비스 구간이 있으면 거부한다")
    void rejectsServiceWindowOnRegularClosedDay() {
        // 토요일 20:00~06:00 이 일요일 새벽까지 이어지므로 서비스 구간은 영업시간 검사를 통과하고,
        // 정기 휴무 요일(일요일) 검사에서만 걸린다.
        OperatingSchedule schedule =
                OperatingSchedule.of(
                        Map.of(DayOfWeek.SATURDAY, List.of(period(0, 20, 6))),
                        Set.of(DayOfWeek.SUNDAY),
                        Set.of(),
                        Set.of(window(ServiceType.ORDER, DayOfWeek.SUNDAY, 0, 1, 5)));

        assertThatThrownBy(schedule::validate)
                .isInstanceOfSatisfying(
                        OperatingScheduleViolationException.class,
                        exception -> {
                            assertThat(exception.reason())
                                    .isEqualTo(
                                            OperatingScheduleViolationException.Reason
                                                    .CLOSED_DAY_HAS_BUSINESS_HOURS);
                            assertThat(exception.field())
                                    .isEqualTo("serviceWindows.ORDER.SUNDAY");
                        })
                .hasMessageContaining("정기 휴무 요일에 서비스 구간이 존재합니다");
    }

    @Test
    @DisplayName("같은 날짜의 임시 휴무가 다른 사유로 중복되면 거부한다")
    void rejectsDuplicateTemporaryClosureDateWithDifferentReasons() {
        LocalDate date = LocalDate.of(2026, 8, 3);
        OperatingSchedule schedule =
                OperatingSchedule.of(
                        Map.of(),
                        Set.of(),
                        Set.of(
                                new TemporaryClosure(date, "설비 점검"),
                                new TemporaryClosure(date, "직원 교육")),
                        Set.of());

        assertThatThrownBy(schedule::validate)
                .isInstanceOfSatisfying(
                        OperatingScheduleViolationException.class,
                        exception -> {
                            assertThat(exception.reason())
                                    .isEqualTo(
                                            OperatingScheduleViolationException.Reason
                                                    .DUPLICATE_TEMPORARY_CLOSURE);
                            assertThat(exception.field()).isEqualTo("temporaryClosures");
                        })
                .hasMessageContaining("임시 휴무 날짜가 중복됩니다");
    }

    @Test
    @DisplayName("영업시간 안의 서비스 구간과 겹치지 않는 휴무 요일 조합은 통과한다")
    void acceptsValidSchedule() {
        OperatingSchedule schedule =
                OperatingSchedule.of(
                        Map.of(
                                DayOfWeek.MONDAY, List.of(period(0, 9, 18)),
                                DayOfWeek.TUESDAY, List.of(period(0, 9, 18))),
                        Set.of(DayOfWeek.SUNDAY),
                        Set.of(new TemporaryClosure(LocalDate.of(2026, 8, 3), "설비 점검")),
                        Set.of(
                                window(ServiceType.ORDER, DayOfWeek.MONDAY, 0, 11, 14),
                                window(ServiceType.RESERVATION, DayOfWeek.TUESDAY, 0, 9, 18)));

        assertThatCode(schedule::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("빈 스케줄도 통과한다")
    void acceptsEmptySchedule() {
        assertThatCode(() -> OperatingSchedule.empty().validate()).doesNotThrowAnyException();
    }
}
