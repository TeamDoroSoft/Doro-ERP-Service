package com.dorosoft.erp.store.application.profile;

import java.time.ZoneId;

public record UpdateStoreProfileCommand(
        String name, String address, String contact, ZoneId timeZone, long ifMatchVersion) {}
