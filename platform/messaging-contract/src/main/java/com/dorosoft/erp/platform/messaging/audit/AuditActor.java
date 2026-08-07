package com.dorosoft.erp.platform.messaging.audit;

import java.util.UUID;

public record AuditActor(String type, UUID id, String role) {}
