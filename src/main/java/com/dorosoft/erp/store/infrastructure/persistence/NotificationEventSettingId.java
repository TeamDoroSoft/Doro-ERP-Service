package com.dorosoft.erp.store.infrastructure.persistence;

import com.dorosoft.erp.store.domain.feature.NotificationEventCode;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
class NotificationEventSettingId implements Serializable {

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_code", nullable = false, length = 60)
    private NotificationEventCode eventCode;

    protected NotificationEventSettingId() {}

    NotificationEventSettingId(UUID storeId, NotificationEventCode eventCode) {
        this.storeId = storeId;
        this.eventCode = eventCode;
    }

    NotificationEventCode getEventCode() {
        return eventCode;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof NotificationEventSettingId that)) {
            return false;
        }
        return eventCode == that.eventCode && Objects.equals(storeId, that.storeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(storeId, eventCode);
    }
}
