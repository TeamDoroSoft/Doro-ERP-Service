package com.dorosoft.erp.store.infrastructure.persistence;

import com.dorosoft.erp.store.application.port.StoreSettingsRepository;
import com.dorosoft.erp.store.domain.feature.FeatureCode;
import com.dorosoft.erp.store.domain.feature.FeatureSettings;
import com.dorosoft.erp.store.domain.feature.NotificationEventCode;
import com.dorosoft.erp.store.domain.schedule.BusinessPeriod;
import com.dorosoft.erp.store.domain.schedule.OperatingSchedule;
import com.dorosoft.erp.store.domain.schedule.ServiceWindow;
import com.dorosoft.erp.store.domain.schedule.TemporaryClosure;
import com.dorosoft.erp.store.domain.settings.StoreProfile;
import com.dorosoft.erp.store.domain.settings.StoreSettings;
import jakarta.persistence.EntityManager;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

/** JPA 엔티티와 도메인 Aggregate 사이의 변환을 전담한다. 엔티티는 이 패키지 밖으로 나가지 않는다. */
@Repository
public class JpaStoreSettingsRepositoryAdapter implements StoreSettingsRepository {

    private final StoreProfileJpaRepository jpaRepository;
    private final EntityManager entityManager;

    public JpaStoreSettingsRepositoryAdapter(
            StoreProfileJpaRepository jpaRepository, EntityManager entityManager) {
        this.jpaRepository = jpaRepository;
        this.entityManager = entityManager;
    }

    @Override
    public Optional<StoreSettings> findCurrent() {
        List<StoreProfileEntity> entities = jpaRepository.findAll();
        if (entities.isEmpty()) {
            return Optional.empty();
        }
        if (entities.size() > 1) {
            throw new IllegalStateException(
                    "업체 Schema에는 store_profile이 하나만 존재해야 합니다. 실제 행 수: " + entities.size());
        }
        return Optional.of(toDomain(entities.getFirst()));
    }

    @Override
    public boolean exists() {
        return jpaRepository.count() > 0;
    }

    @Override
    public StoreSettings save(StoreSettings settings) {
        StoreProfile profile = settings.profile();
        StoreProfileEntity entity = jpaRepository.findById(settings.storeId()).orElse(null);

        if (entity == null) {
            entity =
                    new StoreProfileEntity(
                            settings.storeId(),
                            profile.name(),
                            profile.address(),
                            profile.contact(),
                            profile.timeZone().getId());
        } else {
            if (entity.getVersion() != settings.version()) {
                throw new OptimisticLockingFailureException(
                        "매장 설정이 다른 트랜잭션에서 변경되었습니다. storeId="
                                + settings.storeId()
                                + ", 요청 version="
                                + settings.version()
                                + ", 현재 version="
                                + entity.getVersion());
            }
            // 일정 자식은 매번 대리키를 새로 만들어 통째로 교체하므로 delete-then-insert가 된다.
            // 새 일정 INSERT보다 기존 일정 DELETE가 먼저 실행되도록 자식 행만 즉시 삭제한다.
            // 프로필을 변경하기 전에 실행하므로 이 과정에서는 store_profile UPDATE가 발생하지 않는다.
            deleteScheduleRows(settings.storeId());

            entity.applyProfile(
                    profile.name(), profile.address(), profile.contact(), profile.timeZone().getId());
        }

        applySchedule(entity, settings.storeId(), settings.schedule());
        applyFeatures(entity, settings.storeId(), settings.features());

        StoreProfileEntity saved = jpaRepository.saveAndFlush(entity);
        return StoreSettings.reconstitute(
                settings.storeId(),
                settings.profile(),
                settings.schedule(),
                settings.features(),
                saved.getVersion());
    }

    private void deleteScheduleRows(UUID storeId) {
        deleteScheduleRows("business_hour", storeId);
        deleteScheduleRows("temporary_closure", storeId);
        deleteScheduleRows("service_window", storeId);
        deleteScheduleRows("regular_closed_day", storeId);
    }

    private void deleteScheduleRows(String tableName, UUID storeId) {
        entityManager
                .createNativeQuery("DELETE FROM " + tableName + " WHERE store_id = :storeId")
                .setParameter("storeId", storeId)
                .executeUpdate();
    }

    // --- Domain -> Entity ----------------------------------------------------

    private static void applySchedule(
            StoreProfileEntity entity, UUID storeId, OperatingSchedule schedule) {
        List<BusinessHourEntity> businessHours = new ArrayList<>();
        for (Map.Entry<DayOfWeek, List<BusinessPeriod>> entry : schedule.businessHours().entrySet()) {
            short dayOfWeek = (short) entry.getKey().getValue();
            for (BusinessPeriod period : entry.getValue()) {
                businessHours.add(
                        new BusinessHourEntity(
                                UUID.randomUUID(),
                                dayOfWeek,
                                (short) period.order(),
                                period.start(),
                                period.end()));
            }
        }
        entity.replaceBusinessHours(businessHours);

        List<TemporaryClosureEntity> closures = new ArrayList<>();
        for (TemporaryClosure closure : schedule.temporaryClosures()) {
            closures.add(new TemporaryClosureEntity(UUID.randomUUID(), closure.date(), closure.reason()));
        }
        entity.replaceTemporaryClosures(closures);

        List<ServiceWindowEntity> windows = new ArrayList<>();
        for (ServiceWindow window : schedule.serviceWindows()) {
            windows.add(
                    new ServiceWindowEntity(
                            UUID.randomUUID(),
                            window.serviceType(),
                            (short) window.dayOfWeek().getValue(),
                            (short) window.order(),
                            window.start(),
                            window.end()));
        }
        entity.replaceServiceWindows(windows);

        List<RegularClosedDayEntity> closedDays = new ArrayList<>();
        for (DayOfWeek day : schedule.regularClosedDays()) {
            closedDays.add(
                    new RegularClosedDayEntity(new RegularClosedDayId(storeId, (short) day.getValue())));
        }
        entity.replaceRegularClosedDays(closedDays);
    }

    private static void applyFeatures(
            StoreProfileEntity entity, UUID storeId, FeatureSettings features) {
        List<FeatureSettingEntity> featureRows = new ArrayList<>();
        for (Map.Entry<FeatureCode, Boolean> entry : features.customerFeatures().entrySet()) {
            featureRows.add(
                    new FeatureSettingEntity(
                            new FeatureSettingId(storeId, entry.getKey()), entry.getValue()));
        }
        entity.replaceFeatureSettings(featureRows);

        List<NotificationEventSettingEntity> eventRows = new ArrayList<>();
        for (Map.Entry<NotificationEventCode, Boolean> entry :
                features.notificationEvents().entrySet()) {
            eventRows.add(
                    new NotificationEventSettingEntity(
                            new NotificationEventSettingId(storeId, entry.getKey()), entry.getValue()));
        }
        entity.replaceNotificationEventSettings(eventRows);
    }

    // --- Entity -> Domain ----------------------------------------------------

    private static StoreSettings toDomain(StoreProfileEntity entity) {
        StoreProfile profile =
                new StoreProfile(
                        entity.getName(),
                        entity.getAddress(),
                        entity.getContact(),
                        ZoneId.of(entity.getTimeZone()));
        return StoreSettings.reconstitute(
                entity.getStoreId(),
                profile,
                toSchedule(entity),
                toFeatureSettings(entity),
                entity.getVersion());
    }

    private static OperatingSchedule toSchedule(StoreProfileEntity entity) {
        Map<DayOfWeek, List<BusinessPeriod>> businessHours = new EnumMap<>(DayOfWeek.class);
        for (BusinessHourEntity row : entity.getBusinessHours()) {
            businessHours
                    .computeIfAbsent(DayOfWeek.of(row.getDayOfWeek()), day -> new ArrayList<>())
                    .add(new BusinessPeriod(row.getPeriodOrder(), row.getStartLocalTime(), row.getEndLocalTime()));
        }
        businessHours.values().forEach(periods -> periods.sort(Comparator.comparingInt(BusinessPeriod::order)));

        Set<DayOfWeek> regularClosedDays = new LinkedHashSet<>();
        for (RegularClosedDayEntity row : entity.getRegularClosedDays()) {
            regularClosedDays.add(DayOfWeek.of(row.getId().getDayOfWeek()));
        }

        Set<TemporaryClosure> temporaryClosures = new LinkedHashSet<>();
        for (TemporaryClosureEntity row : entity.getTemporaryClosures()) {
            temporaryClosures.add(new TemporaryClosure(row.getClosureDate(), row.getReason()));
        }

        Set<ServiceWindow> serviceWindows = new LinkedHashSet<>();
        for (ServiceWindowEntity row : entity.getServiceWindows()) {
            serviceWindows.add(
                    new ServiceWindow(
                            row.getServiceType(),
                            DayOfWeek.of(row.getDayOfWeek()),
                            row.getPeriodOrder(),
                            row.getStartLocalTime(),
                            row.getEndLocalTime()));
        }

        return OperatingSchedule.of(businessHours, regularClosedDays, temporaryClosures, serviceWindows);
    }

    // 코드가 누락된 채 저장돼 있으면 FeatureSettings.of가 예외를 던진다. 여기서 임의로 보정하지 않는다.
    private static FeatureSettings toFeatureSettings(StoreProfileEntity entity) {
        Map<FeatureCode, Boolean> customerFeatures = new EnumMap<>(FeatureCode.class);
        for (FeatureSettingEntity row : entity.getFeatureSettings()) {
            customerFeatures.put(row.getId().getFeatureCode(), row.isEnabled());
        }

        Map<NotificationEventCode, Boolean> notificationEvents =
                new EnumMap<>(NotificationEventCode.class);
        for (NotificationEventSettingEntity row : entity.getNotificationEventSettings()) {
            notificationEvents.put(row.getId().getEventCode(), row.isEnabled());
        }

        return FeatureSettings.of(customerFeatures, notificationEvents);
    }
}
