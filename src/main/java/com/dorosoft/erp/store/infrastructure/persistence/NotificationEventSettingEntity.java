package com.dorosoft.erp.store.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "notification_event_setting")
class NotificationEventSettingEntity {

    @EmbeddedId private NotificationEventSettingId id;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    protected NotificationEventSettingEntity() {}

    NotificationEventSettingEntity(NotificationEventSettingId id, boolean enabled) {
        this.id = id;
        this.enabled = enabled;
    }

    NotificationEventSettingId getId() {
        return id;
    }

    boolean isEnabled() {
        return enabled;
    }
}
