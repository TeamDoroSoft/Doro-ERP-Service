package com.dorosoft.erp.store.application.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.dorosoft.erp.store.application.audit.StoreScheduleAuditValue.TemporaryClosureValue;
import com.dorosoft.erp.store.application.audit.StoreScheduleAuditValue.TimePeriod;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class StoreAuditForbiddenKeyTest {

    private static final List<String> FORBIDDEN = List.of(
            "password", "hash", "salt", "session", "cookie", "token", "secret", "apikey",
            "paymentkey", "cardnumber", "approvalnumber", "phone", "email", "address",
            "recipient", "residentnumber");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void canonicalStoreAuditValuesContainNoForbiddenKeys() throws Exception {
        List<Object> values = List.of(
                new StoreProfileAuditValue("매장", "Asia/Seoul", List.of("name"), 2),
                new StoreScheduleAuditValue(
                        Map.of("MONDAY", List.of(new TimePeriod("09:00", "18:00"))),
                        List.of("SUNDAY"),
                        List.of(new TemporaryClosureValue("2026-08-15", "OTHER")),
                        Map.of("ORDER", Map.of(
                                "MONDAY", List.of(new TimePeriod("10:00", "17:00")))),
                        2),
                new StoreFeatureSettingsAuditValue(
                        Map.of("WAITING", true),
                        Map.of("WAITING_REGISTERED", true),
                        2));

        for (Object value : values) {
            Object jsonTree = objectMapper.readValue(
                    objectMapper.writeValueAsString(value), Object.class);
            assertKeysAreAllowed(jsonTree);
        }
    }

    private static void assertKeysAreAllowed(Object node) {
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String normalized = String.valueOf(entry.getKey())
                        .toLowerCase()
                        .replaceAll("[^a-z0-9]", "");
                assertThat(FORBIDDEN)
                        .noneMatch(normalized::contains);
                assertKeysAreAllowed(entry.getValue());
            }
        } else if (node instanceof List<?> list) {
            list.forEach(StoreAuditForbiddenKeyTest::assertKeysAreAllowed);
        }
    }
}
