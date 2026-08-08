package com.dorosoft.erp.commerce.application.api.security;

/**
 * Commerce API가 인가에 사용하는 Actor Role.
 *
 * <p>직원 Role은 02 계정·역할·기기 인증이 소유한 {@code OWNER}, {@code MANAGER}, {@code STAFF}이며
 * {@code KIOSK_DEVICE}는 등록된 Kiosk 기기 자격증명을 나타낸다.
 */
public enum ActorRole {
    OWNER(ActorType.EMPLOYEE),
    MANAGER(ActorType.EMPLOYEE),
    STAFF(ActorType.EMPLOYEE),
    KIOSK_DEVICE(ActorType.DEVICE);

    private final ActorType requiredActorType;

    ActorRole(ActorType requiredActorType) {
        this.requiredActorType = requiredActorType;
    }

    public ActorType requiredActorType() {
        return requiredActorType;
    }

    /** Category·상품·가격·판매 상태 관리 권한. */
    public boolean canManageCatalog() {
        return this == OWNER || this == MANAGER;
    }

    /** 품절 상태 변경 권한. Kiosk 기기는 변경할 수 없다. */
    public boolean canChangeSoldOut() {
        return this == OWNER || this == MANAGER || this == STAFF;
    }

    /** 판매 메뉴 조회 권한. */
    public boolean canReadSalesMenu() {
        return true;
    }
}
