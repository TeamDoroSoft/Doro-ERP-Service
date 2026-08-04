package com.dorosoft.erp.identity.application.idempotency;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

/** Length-prefixed request encoding; avoids delimiter ambiguity and never persists the bytes. */
public final class CanonicalIdentityRequest {
    private CanonicalIdentityRequest() {
    }

    public static byte[] encode(Object... fields) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                for (Object field : fields) {
                    byte[] encoded = encodeField(field);
                    output.writeInt(encoded.length);
                    output.write(encoded);
                    Arrays.fill(encoded, (byte) 0);
                }
            }
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException("In-memory canonical request encoding failed", impossible);
        }
    }

    private static byte[] encodeField(Object field) {
        String value = switch (field) {
            case null -> "<null>";
            case UUID uuid -> uuid.toString();
            case Enum<?> enumeration -> enumeration.name();
            default -> field.toString();
        };
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
