package com.dorosoft.erp.store.presentation.dto;

import com.dorosoft.erp.store.application.profile.UpdateStoreProfileCommand;
import com.dorosoft.erp.store.presentation.dto.validation.ValidTimeZone;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.ZoneId;

public record UpdateStoreProfileRequest(
        @NotBlank @Size(max = 100) @Pattern(regexp = "^[^<>\\p{Cntrl}]*$") String name,
        @NotBlank @Size(max = 255) @Pattern(regexp = "^[^<>\\p{Cntrl}]*$") String address,
        @NotBlank @Size(max = 50) @Pattern(regexp = "^[0-9+\\-() .]+$") String contact,
        @ValidTimeZone String timeZone) {

    public UpdateStoreProfileRequest {
        name = trim(name);
        address = trim(address);
        contact = trim(contact);
        timeZone = trim(timeZone);
    }

    public UpdateStoreProfileCommand toCommand(long ifMatchVersion) {
        return new UpdateStoreProfileCommand(
                name, address, contact, ZoneId.of(timeZone), ifMatchVersion);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
