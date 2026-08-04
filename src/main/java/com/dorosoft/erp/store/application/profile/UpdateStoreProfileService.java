package com.dorosoft.erp.store.application.profile;

import com.dorosoft.erp.audit.application.api.AuditContext;
import com.dorosoft.erp.audit.application.api.AuditRecordCommand;
import com.dorosoft.erp.audit.application.api.AuditTargetType;
import com.dorosoft.erp.audit.application.api.AuditWriter;
import com.dorosoft.erp.store.application.audit.StoreAuditJsonWriter;
import com.dorosoft.erp.store.application.audit.StoreAuditTargets;
import com.dorosoft.erp.store.application.audit.StoreAuditValueMapper;
import com.dorosoft.erp.store.application.exception.StoreNotInitializedException;
import com.dorosoft.erp.store.application.exception.StoreSettingsVersionConflictException;
import com.dorosoft.erp.store.application.port.StoreSettingsRepository;
import com.dorosoft.erp.store.domain.settings.StoreProfile;
import com.dorosoft.erp.store.domain.settings.StoreSettings;
import com.dorosoft.erp.shared.security.ActorContext;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateStoreProfileService {

    private final StoreSettingsRepository repository;
    private final AuditWriter auditWriter;
    private final StoreAuditJsonWriter jsonWriter;
    private final Clock clock;

    public UpdateStoreProfileService(
            StoreSettingsRepository repository,
            AuditWriter auditWriter,
            StoreAuditJsonWriter jsonWriter,
            Clock clock) {
        this.repository = repository;
        this.auditWriter = auditWriter;
        this.jsonWriter = jsonWriter;
        this.clock = clock;
    }

    @Transactional
    public StoreSettings update(UpdateStoreProfileCommand command, ActorContext actor, String requestId) {
        StoreSettings current = repository.findCurrent().orElseThrow(StoreNotInitializedException::new);
        if (current.version() != command.ifMatchVersion()) {
            throw new StoreSettingsVersionConflictException(current.version(), command.ifMatchVersion());
        }

        StoreProfile before = current.profile();
        StoreProfile after =
                new StoreProfile(command.name(), command.address(), command.contact(), command.timeZone());
        List<String> changedFields = StoreAuditValueMapper.changedFields(before, after);
        long beforeVersion = current.version();
        current.updateProfile(after);
        StoreSettings saved = repository.save(current);

        String operationId =
                requestId != null && !requestId.isBlank()
                        ? requestId
                        : "op-" + UUID.randomUUID();
        auditWriter.record(
                new AuditRecordCommand(
                        "STORE",
                        "STORE_PROFILE_UPDATED",
                        operationId,
                        0,
                        StoreAuditTargets.primary(AuditTargetType.STORE_PROFILE, saved.storeId()),
                        StoreAuditTargets.related(saved.storeId()),
                        jsonWriter.toJson(StoreAuditValueMapper.profileBefore(before, beforeVersion)),
                        jsonWriter.toJson(StoreAuditValueMapper.profileAfter(
                                after, changedFields, saved.version())),
                        null,
                        null,
                        "2"),
                new AuditContext(actor.actorId(), actor.actorRole(), clock.instant(), requestId));
        return saved;
    }
}
