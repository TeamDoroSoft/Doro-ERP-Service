package com.dorosoft.erp.store.application.profile;

import com.dorosoft.erp.audit.application.api.AuditContext;
import com.dorosoft.erp.audit.application.api.AuditWriter;
import com.dorosoft.erp.identity.application.authentication.AuthenticatedActor;
import com.dorosoft.erp.store.application.audit.StoreAuditJsonWriter;
import com.dorosoft.erp.store.application.audit.StoreAuditTargets;
import com.dorosoft.erp.store.application.audit.StoreAuditValueMapper;
import com.dorosoft.erp.store.application.exception.StoreNotInitializedException;
import com.dorosoft.erp.store.application.exception.StoreSettingsVersionConflictException;
import com.dorosoft.erp.store.application.port.StoreSettingsRepository;
import com.dorosoft.erp.store.domain.settings.StoreProfile;
import com.dorosoft.erp.store.domain.settings.StoreSettings;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class UpdateStoreProfileService {

    private static final Logger log = LoggerFactory.getLogger(UpdateStoreProfileService.class);

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
    public StoreSettings update(
            UpdateStoreProfileCommand command, AuthenticatedActor actor, String requestId) {
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

        auditWriter.record(
                StoreAuditTargets.storeProfileUpdated(
                        UUID.randomUUID(),
                        saved.storeId(),
                        jsonWriter.toMap(StoreAuditValueMapper.profileBefore(before, beforeVersion)),
                        jsonWriter.toMap(StoreAuditValueMapper.profileAfter(
                                after, changedFields, saved.version()))),
                AuditContext.storeUser(
                        actor.tenantId(),
                        actor.accountId(),
                        actor.roleCode(),
                        actor.accountId().toString(),
                        requestId,
                        clock.instant()));
        log.info(
                "store.settings.updated action=STORE_PROFILE_UPDATED version={} changedFields={}",
                saved.version(),
                changedFields);
        return saved;
    }
}
