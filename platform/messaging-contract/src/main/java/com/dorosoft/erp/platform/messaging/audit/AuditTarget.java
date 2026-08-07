package com.dorosoft.erp.platform.messaging.audit;

import java.util.UUID;

public record AuditTarget(String type, UUID id) {}
