package com.dorosoft.erp.catalog.infrastructure.media;

import com.dorosoft.erp.catalog.application.port.ProductMediaPublicUrlResolver;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** ProductMediaPublicUrlResolver의 CloudFront 구현(ADR-007). */
@Component
class CloudFrontProductMediaPublicUrlResolver implements ProductMediaPublicUrlResolver {

    private final ProductMediaProperties properties;

    CloudFrontProductMediaPublicUrlResolver(ProductMediaProperties properties) {
        this.properties = properties;
    }

    @Override
    public String toPublicUrl(String publishedObjectKey) {
        Objects.requireNonNull(publishedObjectKey, "publishedObjectKey는 필수다");
        if (publishedObjectKey.isBlank()) {
            throw new IllegalArgumentException("publishedObjectKey는 공백일 수 없다");
        }
        return "https://" + properties.cloudFrontDomain() + "/" + publishedObjectKey;
    }
}
