package com.dorosoft.erp.identity.infrastructure.session;

public final class IdentitySessionAttributes {

    public static final String AUTHENTICATED_AT = "doro.identity.authenticatedAt";
    public static final String ABSOLUTE_EXPIRES_AT = "doro.identity.absoluteExpiresAt";

    public static final int IDLE_TIMEOUT_SECONDS = 3_600;
    public static final int ABSOLUTE_TIMEOUT_SECONDS = 28_800;

    private IdentitySessionAttributes() {
    }
}
