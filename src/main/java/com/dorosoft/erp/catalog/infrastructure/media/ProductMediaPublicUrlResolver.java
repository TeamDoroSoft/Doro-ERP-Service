package com.dorosoft.erp.catalog.infrastructure.media;

import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 불변 Public Object를 CloudFront 읽기 전용 HTTPS URL로 변환한다(ADR-007).
 * 공개 메뉴 Projection(MENU-06)이 이 Resolver를 사용해 Bucket명·내부 Key 형식을 노출하지 않는다.
 */
@Component
public class ProductMediaPublicUrlResolver {

    private final ProductMediaProperties properties;

    public ProductMediaPublicUrlResolver(ProductMediaProperties properties) {
        this.properties = properties;
    }

    public String toPublicUrl(String publishedObjectKey) {
        Objects.requireNonNull(publishedObjectKey, "publishedObjectKey는 필수다");
        if (publishedObjectKey.isBlank()) {
            throw new IllegalArgumentException("publishedObjectKey는 공백일 수 없다");
        }
        return "https://" + properties.cloudFrontDomain() + "/" + publishedObjectKey;
    }
}
