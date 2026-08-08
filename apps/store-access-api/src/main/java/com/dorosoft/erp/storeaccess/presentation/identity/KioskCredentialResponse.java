package com.dorosoft.erp.storeaccess.presentation.identity;

import java.util.UUID;

public record KioskCredentialResponse(UUID kioskDeviceId, String credential) {
}
