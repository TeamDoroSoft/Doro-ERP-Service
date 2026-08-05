package com.dorosoft.erp.audit.application.usecase;

import com.dorosoft.erp.audit.application.api.AuditContractException;
import com.dorosoft.erp.audit.application.api.AuditErrorCode;
import com.dorosoft.erp.audit.application.api.PrivacyAccessCommand;
import com.dorosoft.erp.audit.application.api.PrivacyAccessContext;
import com.dorosoft.erp.audit.application.api.PrivacyAccessLogger;
import com.dorosoft.erp.audit.application.api.PrivacyAccessResult;
import com.dorosoft.erp.audit.application.api.PrivacyAccessSubject;
import com.dorosoft.erp.audit.application.port.PrivacyAccessAppendPort;
import com.dorosoft.erp.audit.domain.ActorType;
import com.dorosoft.erp.audit.domain.Feature17PrivacyContracts;
import com.dorosoft.erp.audit.application.model.PrivacyAccessRecord;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class DefaultPrivacyAccessLogger implements PrivacyAccessLogger {
    private static final Set<String> SUBJECT_TYPES = Set.of(
            "EMPLOYEE", "WAITING_CUSTOMER", "RESERVATION_CUSTOMER", "PICKUP_CUSTOMER");

    private final PrivacyAccessAppendPort appendPort;
    private final Clock clock;

    public DefaultPrivacyAccessLogger(PrivacyAccessAppendPort appendPort) {
        this(appendPort, Clock.systemUTC());
    }

    DefaultPrivacyAccessLogger(PrivacyAccessAppendPort appendPort, Clock clock) {
        this.appendPort = Objects.requireNonNull(appendPort);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public PrivacyAccessResult append(PrivacyAccessCommand command, PrivacyAccessContext context) {
        validate(command, context);
        var recordedAt = clock.instant();
        var retentionUntil = context.accessedAt().atZone(ZoneOffset.UTC).plusYears(2).toInstant();
        var id = UUID.randomUUID();
        try {
            appendPort.append(new PrivacyAccessRecord(id, command, context, retentionUntil, recordedAt));
            return PrivacyAccessResult.success(id, recordedAt);
        } catch (DataAccessException exception) {
            throw new AuditContractException(
                    AuditErrorCode.PRIVACY_ACCESS_LOG_UNAVAILABLE, "Privacy access log append failed", exception);
        }
    }

    private void validate(PrivacyAccessCommand command, PrivacyAccessContext context) {
        if (command == null || context == null
                || !Feature17PrivacyContracts.isAllowedResourceType(command.resourceType())
                || !Feature17PrivacyContracts.isAllowedResultCode(command.resultCode())
                || command.resultCount() < 0 || blank(context.tenantId()) || blank(context.accessorType())
                || blank(context.accessorRoleSnapshot()) || context.accessorRoleSnapshot().length() > 40
                || blank(context.requestId()) || context.requestId().length() > 100 || context.accessedAt() == null) {
            throw unavailable("Invalid privacy access contract");
        }
        try {
            ActorType.valueOf(context.accessorType());
        } catch (IllegalArgumentException exception) {
            throw unavailable("Privacy accessor type is invalid");
        }
        if (!ActorType.SYSTEM.name().equals(context.accessorType()) && context.accessorId() == null) {
            throw unavailable("Non-system privacy accessor id is required");
        }
        if (context.clientAddressCiphertext() != null && blank(context.clientAddressKeyVersion())) {
            throw unavailable("Encrypted client address key version is required");
        }
        if (!ActorType.SYSTEM.name().equals(context.accessorType()) && context.clientAddressCiphertext() == null) {
            throw unavailable("Encrypted client address is required");
        }
        if (context.clientAddressCiphertext() != null) {
            try {
                byte[] ciphertext = Base64.getDecoder().decode(context.clientAddressCiphertext());
                if (ciphertext.length == 0 || ciphertext.length > 512) {
                    throw unavailable("Encrypted client address size is invalid");
                }
            } catch (IllegalArgumentException exception) {
                throw unavailable("Encrypted client address is invalid");
            }
        }
        Set<String> subjects = new HashSet<>();
        for (PrivacyAccessSubject subject : command.subjects()) {
            if (subject == null || !SUBJECT_TYPES.contains(subject.subjectType()) || subject.subjectId() == null
                    || !subjects.add(subject.subjectType() + ":" + subject.subjectId())) {
                throw unavailable("Invalid privacy access subject");
            }
        }
        if (command.resultCount() != command.subjects().size()) {
            throw unavailable("Privacy result count must match recorded subjects");
        }
    }

    private AuditContractException unavailable(String message) {
        return new AuditContractException(AuditErrorCode.PRIVACY_ACCESS_LOG_UNAVAILABLE, message);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
