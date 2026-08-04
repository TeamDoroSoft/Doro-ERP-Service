package com.dorosoft.erp.store.domain.settings;

import java.time.ZoneId;
import java.util.Objects;

public record StoreProfile(String name, String address, String contact, ZoneId timeZone) {

    public StoreProfile {
        Objects.requireNonNull(name, "name은 null일 수 없습니다");
        Objects.requireNonNull(address, "address는 null일 수 없습니다");
        Objects.requireNonNull(contact, "contact는 null일 수 없습니다");
        Objects.requireNonNull(timeZone, "timeZone은 null일 수 없습니다");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name은 공백일 수 없습니다");
        }
        if (address.isBlank()) {
            throw new IllegalArgumentException("address는 공백일 수 없습니다");
        }
        if (contact.isBlank()) {
            throw new IllegalArgumentException("contact는 공백일 수 없습니다");
        }
    }
}
