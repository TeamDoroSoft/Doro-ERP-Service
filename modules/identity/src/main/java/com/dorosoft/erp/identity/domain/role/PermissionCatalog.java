package com.dorosoft.erp.identity.domain.role;

import java.util.Set;

/** 기능 01~17에서 승인된 전역 Permission 63개 Registry. */
public final class PermissionCatalog {
    private static final Set<String> ALL = Set.of(
            "identity.account.read", "identity.account.create", "identity.account.status.update",
            "identity.account.unlock", "identity.credential.reset", "identity.role.read",
            "identity.role.assign", "identity.role.update", "identity.audit.read", "audit.read",
            "privacy.access-log.read", "store.settings.read", "store.settings.update", "catalog.read",
            "catalog.manage", "catalog.soldout.update", "table.read", "table.manage",
            "table.session.manage", "table.session.move", "table.order.read", "table.layout.read",
            "table.layout.manage", "table.cleaning.manage", "order.create", "order.read",
            "order.status.update", "order.cancel", "order.history.read", "pickup.contact.read",
            "payment.read", "payment.event.read", "payment.recovery.read", "payment.recovery.manage",
            "payment.manual.complete", "payment.manual.correct", "inventory.read", "inventory.initialize",
            "inventory.receive", "inventory.adjust", "inventory.safety-stock.manage", "inventory.history.read",
            "inventory.alert.read", "sales.read", "sales.closing.read", "sales.close", "sales.correct",
            "waiting.read", "waiting.call", "waiting.admit", "waiting.cancel", "waiting.no_show",
            "waiting.notification.resend", "reservation.read", "reservation.decide", "reservation.update",
            "reservation.cancel", "reservation.visit.update", "reservation.no_show",
            "reservation.change.decide", "reservation.policy.read", "reservation.policy.manage",
            "notification.read"
    );

    private PermissionCatalog() {
    }

    public static Set<String> all() {
        return ALL;
    }

    public static boolean contains(String permissionCode) {
        return ALL.contains(permissionCode);
    }
}
