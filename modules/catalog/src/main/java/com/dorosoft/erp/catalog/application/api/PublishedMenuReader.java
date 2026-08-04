package com.dorosoft.erp.catalog.application.api;

/** 고객 주문 Web이 쓰는 공개 메뉴 읽기 전용 공개 계약(기능 연계 명세). Catalog 상태를 바꾸지 않는다. */
public interface PublishedMenuReader {

    PublishedMenu getPublishedMenu();
}
