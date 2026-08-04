package com.dorosoft.erp.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.dorosoft.erp.testsupport.TestcontainersConfiguration;
import com.dorosoft.erp.store.application.bootstrap.StoreBootstrapService;
import com.dorosoft.erp.store.application.port.StoreSettingsRepository;
import com.dorosoft.erp.store.domain.schedule.BusinessPeriod;
import com.dorosoft.erp.store.domain.schedule.OperatingSchedule;
import com.dorosoft.erp.store.domain.schedule.ServiceType;
import com.dorosoft.erp.store.domain.schedule.ServiceWindow;
import com.dorosoft.erp.store.domain.schedule.TemporaryClosure;
import com.dorosoft.erp.store.domain.settings.StoreSettings;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * OperatingSchedule의 JPA 저장·조회 경로를 검증한다.
 *
 * <p>Bootstrap은 일정을 비운 채로 만들기 때문에 business_hour / regular_closed_day / temporary_closure /
 * service_window 네 테이블의 JPA 매핑은 다른 테스트에서 한 번도 실행되지 않는다. 여기서 왕복(round-trip)과 전체 교체(delete-then-insert)
 * 의미론을 함께 확인한다.
 */
@SpringBootTest(properties = "doro.store.bootstrap.enabled=false")
@Import(TestcontainersConfiguration.class)
@DisplayName("Store 일정 영속화 통합 테스트 - 영업시간·정기휴무·임시휴무·서비스구간의 저장/조회 왕복과 전체 교체 의미론을 검증한다")
class StoreSchedulePersistenceIntegrationTest {

    // 날짜는 "오늘"에 의존하지 않도록 고정 상수로 박는다.
    private static final LocalDate CLOSURE_DATE_SHARED = LocalDate.of(2026, 9, 15);
    private static final LocalDate CLOSURE_DATE_ONLY_IN_B = LocalDate.of(2026, 12, 25);
    private static final String REASON_A = "정기 소독 및 설비 점검";
    private static final String REASON_B = "연말 대청소";
    private static final String REASON_CHRISTMAS = "성탄절 휴무";

    @Autowired private StoreSettingsRepository repository;
    @Autowired private StoreBootstrapService bootstrapService;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcClient jdbcClient;

    @BeforeEach
    void 테이블을_비운다() {
        StoreIntegrationSupport.cleanStoreTables(jdbcClient);
    }

    // --- 케이스 1: 왕복 -------------------------------------------------------

    @Test
    @DisplayName(
            "요일별 다중 구간·자정 초과 구간·정기 휴무·한글 사유의 임시 휴무·ORDER/RESERVATION 서비스 구간을 저장하면"
                    + " 다시 읽었을 때 요일·시각·period order·사유 문자열까지 그대로 복원된다")
    void 일정_전체가_값_그대로_왕복된다() {
        bootstrap();

        StoreSettings settings = findCurrentInTransaction().orElseThrow();
        settings.replaceSchedule(scheduleA());
        transactionTemplate.execute(status -> repository.save(settings));

        OperatingSchedule reloaded = findCurrentInTransaction().orElseThrow().schedule();

        // 영업시간: 요일 집합, 요일별 구간 목록(순서 = period order), 값 전체
        assertThat(reloaded.businessHours())
                .containsOnlyKeys(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)
                .isEqualTo(businessHoursA());

        // 자정 초과 구간이 왜곡 없이 살아남았는지 시·분 단위로 확인한다.
        BusinessPeriod fridayNight = reloaded.businessHours().get(DayOfWeek.FRIDAY).getFirst();
        assertThat(fridayNight.order()).isZero();
        assertThat(fridayNight.start().getHour()).isEqualTo(22);
        assertThat(fridayNight.start().getMinute()).isZero();
        assertThat(fridayNight.end().getHour()).isEqualTo(2);
        assertThat(fridayNight.end().getMinute()).isEqualTo(30);
        assertThat(fridayNight.crossesMidnight()).isTrue();

        // 같은 요일 2구간은 period order 순서대로 복원된다.
        List<BusinessPeriod> tuesday = reloaded.businessHours().get(DayOfWeek.TUESDAY);
        assertThat(tuesday).extracting(BusinessPeriod::order).containsExactly(0, 1);
        assertThat(tuesday).extracting(BusinessPeriod::crossesMidnight).containsExactly(false, false);
        assertThat(tuesday.getFirst().start()).isEqualTo(LocalTime.of(11, 0));
        assertThat(tuesday.getFirst().end()).isEqualTo(LocalTime.of(15, 0));
        assertThat(tuesday.getLast().start()).isEqualTo(LocalTime.of(17, 0));
        assertThat(tuesday.getLast().end()).isEqualTo(LocalTime.of(22, 0));

        assertThat(reloaded.regularClosedDays()).containsExactly(DayOfWeek.MONDAY);

        // 한글 사유 문자열의 인코딩 왕복
        assertThat(reloaded.temporaryClosures())
                .containsExactlyInAnyOrderElementsOf(temporaryClosuresA());
        assertThat(reloaded.temporaryClosures())
                .extracting(TemporaryClosure::reason)
                .containsExactly(REASON_A);

        assertThat(reloaded.serviceWindows()).containsExactlyInAnyOrderElementsOf(serviceWindowsA());
        assertThat(reloaded.serviceWindows())
                .extracting(ServiceWindow::serviceType)
                .containsExactlyInAnyOrder(ServiceType.ORDER, ServiceType.RESERVATION);
    }

    // --- 케이스 2: 교체 의미론 --------------------------------------------------

    @Test
    @DisplayName(
            "일정 A를 저장한 뒤 겹치는 키를 가진 일정 B로 교체 저장하면 A의 요소가 하나도 남지 않고"
                    + " 자식 테이블 4개의 실제 행 수도 B와 정확히 일치한다")
    void 일정을_교체_저장하면_이전_일정의_행이_남지_않는다() {
        bootstrap();

        StoreSettings first = findCurrentInTransaction().orElseThrow();
        first.replaceSchedule(scheduleA());
        transactionTemplate.execute(status -> repository.save(first));

        // A가 실제로 적재됐는지 먼저 확인한다. (B 저장이 조용히 아무 일도 안 하는 상황과 구분하기 위함)
        assertScheduleRowCounts(4, 1, 1, 2);

        // 저장 후에는 반드시 다시 읽은 인스턴스를 써야 version이 최신이다.
        StoreSettings second = findCurrentInTransaction().orElseThrow();
        second.replaceSchedule(scheduleB());
        transactionTemplate.execute(status -> repository.save(second));

        OperatingSchedule reloaded = findCurrentInTransaction().orElseThrow().schedule();

        // 도메인 왕복: B와 정확히 일치하고 A의 요소는 섞여 있지 않다.
        assertThat(reloaded.businessHours())
                .containsOnlyKeys(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY)
                .isEqualTo(businessHoursB());
        assertThat(reloaded.businessHours()).doesNotContainKey(DayOfWeek.SATURDAY);
        assertThat(reloaded.regularClosedDays())
                .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY);
        assertThat(reloaded.temporaryClosures())
                .containsExactlyInAnyOrderElementsOf(temporaryClosuresB());
        assertThat(reloaded.temporaryClosures())
                .extracting(TemporaryClosure::reason)
                .doesNotContain(REASON_A);
        assertThat(reloaded.serviceWindows()).containsExactlyInAnyOrderElementsOf(serviceWindowsB());
        assertThat(reloaded.serviceWindows()).doesNotContainAnyElementsOf(serviceWindowsA());

        // 실제 행 수: 도메인 왕복만으로는 고아 행을 잡을 수 없다.
        assertScheduleRowCounts(3, 2, 2, 3);
    }

    private void assertScheduleRowCounts(
            long businessHour, long regularClosedDay, long temporaryClosure, long serviceWindow) {
        assertThat(StoreIntegrationSupport.countOf(jdbcClient, "business_hour"))
                .as("business_hour 행 수")
                .isEqualTo(businessHour);
        assertThat(StoreIntegrationSupport.countOf(jdbcClient, "regular_closed_day"))
                .as("regular_closed_day 행 수")
                .isEqualTo(regularClosedDay);
        assertThat(StoreIntegrationSupport.countOf(jdbcClient, "temporary_closure"))
                .as("temporary_closure 행 수")
                .isEqualTo(temporaryClosure);
        assertThat(StoreIntegrationSupport.countOf(jdbcClient, "service_window"))
                .as("service_window 행 수")
                .isEqualTo(serviceWindow);
    }

    // --- 일정 A --------------------------------------------------------------
    // 정기휴무 월. 화 2구간, 금 자정 초과 구간, 토 주간 영업. 서비스 구간은 ORDER/RESERVATION 각 1개.

    private static Map<DayOfWeek, List<BusinessPeriod>> businessHoursA() {
        Map<DayOfWeek, List<BusinessPeriod>> hours = new EnumMap<>(DayOfWeek.class);
        hours.put(
                DayOfWeek.TUESDAY,
                List.of(
                        new BusinessPeriod(0, LocalTime.of(11, 0), LocalTime.of(15, 0)),
                        new BusinessPeriod(1, LocalTime.of(17, 0), LocalTime.of(22, 0))));
        hours.put(
                DayOfWeek.FRIDAY,
                List.of(new BusinessPeriod(0, LocalTime.of(22, 0), LocalTime.of(2, 30))));
        hours.put(
                DayOfWeek.SATURDAY,
                List.of(new BusinessPeriod(0, LocalTime.of(10, 0), LocalTime.of(20, 0))));
        return hours;
    }

    private static Set<TemporaryClosure> temporaryClosuresA() {
        return Set.of(new TemporaryClosure(CLOSURE_DATE_SHARED, REASON_A));
    }

    private static Set<ServiceWindow> serviceWindowsA() {
        return Set.of(
                new ServiceWindow(
                        ServiceType.ORDER, DayOfWeek.TUESDAY, 0, LocalTime.of(11, 30), LocalTime.of(14, 30)),
                new ServiceWindow(
                        ServiceType.RESERVATION,
                        DayOfWeek.SATURDAY,
                        0,
                        LocalTime.of(12, 0),
                        LocalTime.of(18, 0)));
    }

    private static OperatingSchedule scheduleA() {
        return OperatingSchedule.of(
                businessHoursA(), Set.of(DayOfWeek.MONDAY), temporaryClosuresA(), serviceWindowsA());
    }

    // --- 일정 B --------------------------------------------------------------
    // A와 UNIQUE 키를 일부러 겹치게 만든다: (화,0) (화,1) (금,0) 영업시간, (ORDER,화,0) 서비스 구간,
    // 같은 closure_date, 같은 정기휴무 요일(월). delete-then-insert 순서가 잘못되면 여기서 UNIQUE 위반으로 터진다.

    private static Map<DayOfWeek, List<BusinessPeriod>> businessHoursB() {
        Map<DayOfWeek, List<BusinessPeriod>> hours = new EnumMap<>(DayOfWeek.class);
        hours.put(
                DayOfWeek.TUESDAY,
                List.of(
                        new BusinessPeriod(0, LocalTime.of(9, 0), LocalTime.of(14, 0)),
                        new BusinessPeriod(1, LocalTime.of(16, 0), LocalTime.of(21, 0))));
        hours.put(
                DayOfWeek.FRIDAY,
                List.of(new BusinessPeriod(0, LocalTime.of(21, 0), LocalTime.of(1, 0))));
        return hours;
    }

    private static Set<TemporaryClosure> temporaryClosuresB() {
        return Set.of(
                new TemporaryClosure(CLOSURE_DATE_SHARED, REASON_B),
                new TemporaryClosure(CLOSURE_DATE_ONLY_IN_B, REASON_CHRISTMAS));
    }

    private static Set<ServiceWindow> serviceWindowsB() {
        return Set.of(
                new ServiceWindow(
                        ServiceType.ORDER, DayOfWeek.TUESDAY, 0, LocalTime.of(10, 0), LocalTime.of(13, 0)),
                new ServiceWindow(
                        ServiceType.ORDER, DayOfWeek.TUESDAY, 1, LocalTime.of(17, 0), LocalTime.of(20, 0)),
                new ServiceWindow(
                        ServiceType.RESERVATION,
                        DayOfWeek.FRIDAY,
                        0,
                        LocalTime.of(22, 0),
                        LocalTime.of(23, 30)));
    }

    private static OperatingSchedule scheduleB() {
        return OperatingSchedule.of(
                businessHoursB(),
                Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                temporaryClosuresB(),
                serviceWindowsB());
    }

    // --- 헬퍼 ----------------------------------------------------------------

    /** 자식 컬렉션이 LAZY이고 open-in-view가 꺼져 있어 조회도 트랜잭션 안에서 해야 한다. */
    private Optional<StoreSettings> findCurrentInTransaction() {
        return transactionTemplate.execute(status -> repository.findCurrent());
    }

    private StoreSettings bootstrap() {
        return transactionTemplate.execute(status -> bootstrapService.bootstrap());
    }
}
