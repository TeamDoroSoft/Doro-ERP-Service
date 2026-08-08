package com.dorosoft.erp.storeaccess.application.port.identity;

import com.dorosoft.erp.storeaccess.application.api.identity.ActorContext;

/**
 * Builds an {@link ActorContext} from the currently authenticated employee Session (ADR-02-002). A Port so
 * Controllers — which may not depend on {@code infrastructure} — never touch the Security Context directly.
 */
public interface CurrentActorResolver {

    ActorContext currentActor();
}
