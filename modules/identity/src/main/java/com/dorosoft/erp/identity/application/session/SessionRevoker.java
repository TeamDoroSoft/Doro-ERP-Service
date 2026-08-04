package com.dorosoft.erp.identity.application.session;

import java.util.UUID;

/**
 * Deletes every Redis session indexed for one account.
 *
 * <p>Callers perform this operation while holding the target account row lock. A failure is
 * deliberately propagated so an enclosing MySQL transaction can roll back.</p>
 */
public interface SessionRevoker {

    int revokeAll(UUID accountId);
}
