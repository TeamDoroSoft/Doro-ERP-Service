package com.dorosoft.erp.table.application;

import com.dorosoft.erp.table.application.port.TableUsageSessionQueryRepository.SessionHistoryCursor;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
class TableSessionHistoryCursorCodec {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    SessionHistoryCursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(DECODER.decode(cursor), StandardCharsets.US_ASCII);
            String[] parts = decoded.split("\\|", -1);
            if (parts.length != 2) {
                throw invalidCursor();
            }
            return new SessionHistoryCursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (IllegalArgumentException exception) {
            throw invalidCursor();
        }
    }

    String encode(Instant closedAt, UUID sessionId) {
        String payload = closedAt + "|" + sessionId;
        return ENCODER.encodeToString(payload.getBytes(StandardCharsets.US_ASCII));
    }

    private static TableManagementException invalidCursor() {
        return new TableManagementException(
                HttpStatus.BAD_REQUEST,
                TableErrorCode.INVALID_CURSOR,
                "Cursor is invalid.");
    }
}
