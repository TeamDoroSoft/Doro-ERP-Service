package com.dorosoft.erp.commerce.application.api.security;

import java.util.Objects;
import java.util.UUID;

/**
 * 인증 Filter가 검증한 뒤 Application에 전달하는 최소 Actor Context.
 *
 * <p>Commerce는 이 값을 Controller 입력이 아니라 검증된 Context로만 받는다.
 */
public record ActorContext(UUID tenantId, UUID storeId, ActorType actorType, UUID actorId, ActorRole role) {

    public ActorContext {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(actorType, "actorType");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(role, "role");
        if (role.requiredActorType() != actorType) {
            throw new IllegalArgumentException("actor type does not match role");
        }
    }

    public boolean canManageCatalog() {
        return role.canManageCatalog();
    }

    public boolean canChangeSoldOut() {
        return role.canChangeSoldOut();
    }
}
