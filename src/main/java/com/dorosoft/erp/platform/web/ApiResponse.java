package com.dorosoft.erp.platform.web;

public record ApiResponse<T>(T data, String requestId) {
}
