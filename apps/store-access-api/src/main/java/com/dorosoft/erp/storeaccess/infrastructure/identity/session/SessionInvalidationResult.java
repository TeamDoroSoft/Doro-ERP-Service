package com.dorosoft.erp.storeaccess.infrastructure.identity.session;

public record SessionInvalidationResult(boolean succeeded, int attempts) {
}
