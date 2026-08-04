package com.dorosoft.erp.identity.application.authentication;

import org.springframework.security.core.Authentication;

public interface AuthenticatedActorResolver {
    AuthenticatedActor resolve(Authentication authentication);
}
