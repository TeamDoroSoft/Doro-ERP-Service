package com.dorosoft.erp.store.domain.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("BusinessPeriod: 영업 구간 값 객체의 불변식과 Instant 변환")
class BusinessPeriodTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    /** 2026-08-03은 월요일이다. */
    private static final LocalDate MONDAY_DATE = LocalDate.of(2026, 8, 3);

    @Test
    @DisplayName("start와 end가 같으면 길이 0인지 24시간인지 구분할 수 없으므로 거부한다")
    void rejectsSameStartAndEnd() {
        assertThatThrownBy(() -> new BusinessPeriod(0, LocalTime.of(9, 0), LocalTime.of(9, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("order가 음수면 거부한다")
    void rejectsNegativeOrder() {
        assertThatThrownBy(() -> new BusinessPeriod(-1, LocalTime.of(9, 0), LocalTime.of(18, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("order 0은 허용한다")
    void allowsZeroOrder() {
        assertThatCode(() -> new BusinessPeriod(0, LocalTime.of(9, 0), LocalTime.of(18, 0)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("start가 null이면 NullPointerException")
    void rejectsNullStart() {
        assertThatThrownBy(() -> new BusinessPeriod(0, null, LocalTime.of(18, 0)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("end가 null이면 NullPointerException")
    void rejectsNullEnd() {
        assertThatThrownBy(() -> new BusinessPeriod(0, LocalTime.of(9, 0), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("종료 시각이 시작 시각보다 이르면 자정을 넘긴 구간이다")
    void crossesMidnightIsTrueWhenEndBeforeStart() {
        BusinessPeriod period = new BusinessPeriod(0, LocalTime.of(22, 0), LocalTime.of(2, 0));

        assertThat(period.crossesMidnight()).isTrue();
    }

    @Test
    @DisplayName("같은 날 안에서 끝나는 구간은 자정을 넘기지 않는다")
    void crossesMidnightIsFalseWithinSameDay() {
        BusinessPeriod period = new BusinessPeriod(0, LocalTime.of(9, 0), LocalTime.of(18, 0));

        assertThat(period.crossesMidnight()).isFalse();
    }

    @Test
    @DisplayName("자정을 넘기지 않는 구간의 toInterval은 같은 날의 Instant 쌍을 만든다")
    void toIntervalKeepsSameDayWhenNotCrossingMidnight() {
        assertThat(MONDAY_DATE.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        BusinessPeriod period = new BusinessPeriod(0, LocalTime.of(9, 0), LocalTime.of(18, 0));

        TimeInterval interval = period.toInterval(MONDAY_DATE, SEOUL);

        // KST(+09:00) 기준: 08-03 09:00 -> 08-03T00:00Z, 08-03 18:00 -> 08-03T09:00Z
        assertThat(interval.start()).isEqualTo(Instant.parse("2026-08-03T00:00:00Z"));
        assertThat(interval.end()).isEqualTo(Instant.parse("2026-08-03T09:00:00Z"));
        assertThat(interval.end().atZone(SEOUL).toLocalDate()).isEqualTo(MONDAY_DATE);
    }

    @Test
    @DisplayName("자정을 넘긴 구간의 toInterval은 종료 Instant가 다음 날로 넘어간다")
    void toIntervalRollsEndToNextDayWhenCrossingMidnight() {
        BusinessPeriod period = new BusinessPeriod(0, LocalTime.of(22, 0), LocalTime.of(2, 0));

        TimeInterval interval = period.toInterval(MONDAY_DATE, SEOUL);

        // KST(+09:00) 기준: 08-03 22:00 -> 08-03T13:00Z, 08-04 02:00 -> 08-03T17:00Z
        assertThat(interval.start()).isEqualTo(Instant.parse("2026-08-03T13:00:00Z"));
        assertThat(interval.end()).isEqualTo(Instant.parse("2026-08-03T17:00:00Z"));
        assertThat(interval.end().atZone(SEOUL).toLocalDate()).isEqualTo(MONDAY_DATE.plusDays(1));
    }

    @Test
    @DisplayName("toInterval의 startDate·zone이 null이면 NullPointerException")
    void toIntervalRejectsNullArguments() {
        BusinessPeriod period = new BusinessPeriod(0, LocalTime.of(9, 0), LocalTime.of(18, 0));

        assertThatThrownBy(() -> period.toInterval(null, SEOUL)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> period.toInterval(MONDAY_DATE, null))
                .isInstanceOf(NullPointerException.class);
    }
}
