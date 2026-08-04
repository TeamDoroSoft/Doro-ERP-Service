package com.dorosoft.erp.identity.infrastructure.session;

import com.dorosoft.erp.identity.infrastructure.security.IdentityAuthentication;
import com.dorosoft.erp.identity.infrastructure.security.IdentityPrincipal;
import com.dorosoft.erp.identity.infrastructure.security.SessionCsrfTokenRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Explicit allowlist serializer for Redis session attributes. It never enables Java
 * serialization or unrestricted JSON polymorphism.
 */
public final class IdentitySessionJsonSerializer implements RedisSerializer<Object> {

    private static final String TYPE = "type";
    private final ObjectMapper objectMapper;

    public IdentitySessionJsonSerializer() {
        this(new ObjectMapper());
    }

    IdentitySessionJsonSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public byte[] serialize(Object value) throws SerializationException {
        if (value == null) {
            return new byte[0];
        }
        try {
            return objectMapper.writeValueAsBytes(toEnvelope(value));
        } catch (Exception exception) {
            throw new SerializationException("Session attribute is not in the allowlist", exception);
        }
    }

    @Override
    public Object deserialize(byte[] bytes) throws SerializationException {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            return fromEnvelope(objectMapper.readTree(bytes));
        } catch (Exception exception) {
            throw new SerializationException("Session attribute payload is invalid", exception);
        }
    }

    private Map<String, Object> toEnvelope(Object value) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        switch (value) {
            case SecurityContext context -> writeSecurityContext(envelope, context);
            case CsrfToken token -> {
                envelope.put(TYPE, "csrf");
                envelope.put("token", token.getToken());
            }
            case String string -> {
                envelope.put(TYPE, "string");
                envelope.put("value", string);
            }
            case Long number -> {
                envelope.put(TYPE, "long");
                envelope.put("value", number);
            }
            case Integer number -> {
                envelope.put(TYPE, "integer");
                envelope.put("value", number);
            }
            case Boolean bool -> {
                envelope.put(TYPE, "boolean");
                envelope.put("value", bool);
            }
            default -> throw new IllegalArgumentException("Unsupported session attribute type");
        }
        return envelope;
    }

    private void writeSecurityContext(Map<String, Object> envelope, SecurityContext context) {
        Authentication authentication = context.getAuthentication();
        if (!(authentication instanceof IdentityAuthentication identityAuthentication)) {
            throw new IllegalArgumentException("Only Identity authentication may be stored");
        }
        IdentityPrincipal principal = identityAuthentication.getPrincipal();
        List<String> permissions = new ArrayList<>(principal.permissions());
        permissions.sort(Comparator.naturalOrder());

        envelope.put(TYPE, "security-context");
        envelope.put("accountId", principal.accountId().toString());
        envelope.put("tenantKey", principal.tenantKey());
        envelope.put("roleCode", principal.roleCode());
        envelope.put("permissions", permissions);
        envelope.put("mustChangePassword", principal.mustChangePassword());
    }

    private Object fromEnvelope(JsonNode root) {
        String type = requiredText(root, TYPE);
        return switch (type) {
            case "security-context" -> readSecurityContext(root);
            case "csrf" -> SessionCsrfTokenRepository.token(requiredText(root, "token"));
            case "string" -> requiredText(root, "value");
            case "long" -> root.path("value").asLong();
            case "integer" -> root.path("value").asInt();
            case "boolean" -> root.path("value").asBoolean();
            default -> throw new IllegalArgumentException("Unsupported session attribute discriminator");
        };
    }

    private SecurityContext readSecurityContext(JsonNode root) {
        List<String> permissions = root.path("permissions").values().stream()
                .map(JsonNode::asString)
                .toList();
        IdentityPrincipal principal = new IdentityPrincipal(
                UUID.fromString(requiredText(root, "accountId")),
                requiredText(root, "tenantKey"),
                requiredText(root, "roleCode"),
                Set.copyOf(permissions),
                root.path("mustChangePassword").asBoolean());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new IdentityAuthentication(principal));
        return context;
    }

    private String requiredText(JsonNode root, String property) {
        String value = root.path(property).asString();
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Required session field is missing");
        }
        return value;
    }
}
