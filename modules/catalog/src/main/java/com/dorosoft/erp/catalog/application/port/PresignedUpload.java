package com.dorosoft.erp.catalog.application.port;

import java.time.Instant;
import java.util.Map;

/**
 * Staging 업로드 준비 결과. stagingObjectKey는 Adapter가 업체 Prefix로 생성한 값이며
 * Application은 이를 그대로 Media Row에 저장할 뿐 형식을 알 필요가 없다.
 */
public record PresignedUpload(
        String stagingObjectKey, String uploadUrl, Map<String, String> requiredHeaders, Instant expiresAt) {

    public PresignedUpload {
        requiredHeaders = Map.copyOf(requiredHeaders);
    }
}
