package com.dorosoft.erp.table.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public interface TableSessionCloseGuardReader {

    CloseGuardResult inspect(CloseGuardQuery query);

    record CloseGuardQuery(UUID tableId, UUID sessionId, Instant closeRequestedAt) {
        public CloseGuardQuery {
            Objects.requireNonNull(tableId, "tableId");
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(closeRequestedAt, "closeRequestedAt");
        }
    }

    record CloseGuardResult(List<CloseGuardBlocker> blockers) {
        public CloseGuardResult {
            blockers = blockers == null ? List.of() : List.copyOf(blockers);
        }

        public static CloseGuardResult closable() {
            return new CloseGuardResult(List.of());
        }

        public static CloseGuardResult blocked(String code, String message) {
            return new CloseGuardResult(List.of(new CloseGuardBlocker(code, message)));
        }

        public boolean canClose() {
            return blockers.isEmpty();
        }
    }

    record CloseGuardBlocker(String code, String message) {
        public CloseGuardBlocker {
            if (code == null || code.isBlank()) {
                throw new IllegalArgumentException("code must not be blank");
            }
            if (message == null || message.isBlank()) {
                throw new IllegalArgumentException("message must not be blank");
            }
        }
    }
}
