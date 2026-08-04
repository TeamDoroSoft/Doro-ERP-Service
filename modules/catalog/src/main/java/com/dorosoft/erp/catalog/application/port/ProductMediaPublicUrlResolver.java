package com.dorosoft.erp.catalog.application.port;

/**
 * 불변 Public Object를 CloudFront 읽기 전용 HTTPS URL로 변환하는 Port(ADR-007).
 * 공개 메뉴 Projection이 이 계약으로만 URL을 만들어 Bucket명·내부 Key 형식을 노출하지 않는다.
 */
public interface ProductMediaPublicUrlResolver {

    String toPublicUrl(String publishedObjectKey);
}
