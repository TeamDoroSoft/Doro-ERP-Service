package com.dorosoft.erp.store.infrastructure.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** 매장 설정 Aggregate Root의 JPA 매핑. 자식 컬렉션을 모두 소유한다. */
@Entity
@Table(name = "store_profile")
class StoreProfileEntity {

    @Id
    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "address", nullable = false, length = 255)
    private String address;

    @Column(name = "contact", nullable = false, length = 50)
    private String contact;

    @Column(name = "time_zone", nullable = false, length = 50)
    private String timeZone;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "store_id", nullable = false)
    private List<BusinessHourEntity> businessHours = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "store_id", nullable = false)
    private List<TemporaryClosureEntity> temporaryClosures = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "store_id", nullable = false)
    private List<ServiceWindowEntity> serviceWindows = new ArrayList<>();

    // 아래 세 자식은 store_id가 @EmbeddedId에도 포함되어 컬럼이 중복 매핑된다.
    // 부모 쪽 조인 컬럼을 읽기 전용으로 두고 storeId는 자식 @EmbeddedId가 직접 채운다.
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "store_id", nullable = false, insertable = false, updatable = false)
    private List<RegularClosedDayEntity> regularClosedDays = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "store_id", nullable = false, insertable = false, updatable = false)
    private List<FeatureSettingEntity> featureSettings = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "store_id", nullable = false, insertable = false, updatable = false)
    private List<NotificationEventSettingEntity> notificationEventSettings = new ArrayList<>();

    protected StoreProfileEntity() {}

    StoreProfileEntity(UUID storeId, String name, String address, String contact, String timeZone) {
        this.storeId = storeId;
        this.name = name;
        this.address = address;
        this.contact = contact;
        this.timeZone = timeZone;
    }

    UUID getStoreId() {
        return storeId;
    }

    String getName() {
        return name;
    }

    String getAddress() {
        return address;
    }

    String getContact() {
        return contact;
    }

    String getTimeZone() {
        return timeZone;
    }

    long getVersion() {
        return version;
    }

    List<BusinessHourEntity> getBusinessHours() {
        return businessHours;
    }

    List<TemporaryClosureEntity> getTemporaryClosures() {
        return temporaryClosures;
    }

    List<ServiceWindowEntity> getServiceWindows() {
        return serviceWindows;
    }

    List<RegularClosedDayEntity> getRegularClosedDays() {
        return regularClosedDays;
    }

    List<FeatureSettingEntity> getFeatureSettings() {
        return featureSettings;
    }

    List<NotificationEventSettingEntity> getNotificationEventSettings() {
        return notificationEventSettings;
    }

    void applyProfile(String name, String address, String contact, String timeZone) {
        this.name = name;
        this.address = address;
        this.contact = contact;
        this.timeZone = timeZone;
    }

    // 전체 교체. orphanRemoval이 동작하도록 컬렉션 참조는 유지하고 내용만 갈아끼운다.
    void replaceBusinessHours(List<BusinessHourEntity> replacements) {
        this.businessHours.clear();
        this.businessHours.addAll(replacements);
    }

    void replaceTemporaryClosures(List<TemporaryClosureEntity> replacements) {
        this.temporaryClosures.clear();
        this.temporaryClosures.addAll(replacements);
    }

    void replaceServiceWindows(List<ServiceWindowEntity> replacements) {
        this.serviceWindows.clear();
        this.serviceWindows.addAll(replacements);
    }

    void replaceRegularClosedDays(List<RegularClosedDayEntity> replacements) {
        this.regularClosedDays.clear();
        this.regularClosedDays.addAll(replacements);
    }

    void replaceFeatureSettings(List<FeatureSettingEntity> replacements) {
        this.featureSettings.clear();
        this.featureSettings.addAll(replacements);
    }

    void replaceNotificationEventSettings(List<NotificationEventSettingEntity> replacements) {
        this.notificationEventSettings.clear();
        this.notificationEventSettings.addAll(replacements);
    }
}
