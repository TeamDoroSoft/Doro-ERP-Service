package com.dorosoft.erp.catalog.application.query;

import com.dorosoft.erp.catalog.domain.query.InvalidCursorException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/** GET /catalog/products의 불투명 페이지 Cursor. 마지막으로 본 productId를 Base64로 감싼다. */
public final class ProductCursor {

    private ProductCursor() {}

    public static String encode(UUID lastProductId) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(lastProductId.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static UUID decode(String cursor) {
        try {
            return UUID.fromString(new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            throw new InvalidCursorException(cursor);
        }
    }
}
