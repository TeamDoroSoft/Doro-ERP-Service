package com.dorosoft.erp.catalog.application.port;

/** Staging·Public Object의 실제 S3 Metadata. HEAD 응답에서만 얻는다. */
public record S3ObjectMetadata(String etag, String contentType, long byteSize, String checksumSha256Base64) {}
