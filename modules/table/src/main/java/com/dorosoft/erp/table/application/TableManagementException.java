package com.dorosoft.erp.table.application;

import org.springframework.http.HttpStatus;

public class TableManagementException extends RuntimeException {

    private final HttpStatus status;
    private final TableErrorCode code;

    public TableManagementException(HttpStatus status, TableErrorCode code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public TableErrorCode code() {
        return code;
    }
}
