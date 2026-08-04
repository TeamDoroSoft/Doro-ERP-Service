package com.dorosoft.erp.table.infrastructure.web;

import com.dorosoft.erp.table.application.TableErrorCode;
import com.dorosoft.erp.table.application.TableManagementException;
import org.springframework.http.HttpStatus;

final class TablePreconditions {

    private TablePreconditions() {}

    static long requiredVersion(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new TableManagementException(
                    HttpStatus.PRECONDITION_REQUIRED,
                    TableErrorCode.PRECONDITION_REQUIRED,
                    "If-Match header is required.");
        }

        String candidate = ifMatch.trim();
        if (candidate.startsWith("\"") && candidate.endsWith("\"") && candidate.length() >= 2) {
            candidate = candidate.substring(1, candidate.length() - 1);
        }

        try {
            return Long.parseLong(candidate);
        } catch (NumberFormatException e) {
            throw new TableManagementException(
                    HttpStatus.PRECONDITION_FAILED,
                    TableErrorCode.PRECONDITION_FAILED,
                    "If-Match must be a table version.");
        }
    }
}
