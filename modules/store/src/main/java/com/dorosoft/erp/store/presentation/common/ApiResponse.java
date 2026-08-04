package com.dorosoft.erp.store.presentation.common;

public record ApiResponse<T>(T data, String requestId) {}
