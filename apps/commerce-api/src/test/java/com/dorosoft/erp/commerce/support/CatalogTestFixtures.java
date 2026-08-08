package com.dorosoft.erp.commerce.support;

import com.dorosoft.erp.commerce.application.api.security.ActorContext;
import com.dorosoft.erp.commerce.application.api.security.ActorContextHolder;
import com.dorosoft.erp.commerce.application.api.security.ActorRole;
import java.util.UUID;

public final class CatalogTestFixtures {

    public static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID TENANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");
    public static final UUID STORE_A = UUID.fromString("aaaaaaaa-1111-1111-1111-111111111111");
    public static final UUID STORE_B = UUID.fromString("bbbbbbbb-2222-2222-2222-222222222222");

    private CatalogTestFixtures() {
    }

    public static ActorContext actor(UUID tenantId, UUID storeId, ActorRole role) {
        return new ActorContext(tenantId, storeId, role.requiredActorType(), UUID.randomUUID(), role);
    }

    public static void authenticate(UUID tenantId, UUID storeId, ActorRole role) {
        ActorContextHolder.set(actor(tenantId, storeId, role));
    }

    public static void authenticate(UUID tenantId, ActorRole role) {
        authenticate(tenantId, STORE_A, role);
    }

    public static void clear() {
        ActorContextHolder.clear();
    }
}
