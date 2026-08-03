package com.dorosoft.erp.store.application.profile;

import com.dorosoft.erp.audit.application.api.AuditContext;
import com.dorosoft.erp.audit.application.api.AuditRecordCommand;
import com.dorosoft.erp.audit.application.api.AuditRelatedTarget;
import com.dorosoft.erp.audit.application.api.AuditRelationType;
import com.dorosoft.erp.audit.application.api.AuditTarget;
import com.dorosoft.erp.audit.application.api.AuditTargetType;
import com.dorosoft.erp.audit.application.api.AuditWriter;
import com.dorosoft.erp.store.application.exception.StoreNotInitializedException;
import com.dorosoft.erp.store.application.exception.StoreSettingsVersionConflictException;
import com.dorosoft.erp.store.application.port.StoreSettingsRepository;
import com.dorosoft.erp.store.domain.settings.StoreProfile;
import com.dorosoft.erp.store.domain.settings.StoreSettings;
import com.dorosoft.erp.shared.security.ActorContext;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class UpdateStoreProfileService {

    private final StoreSettingsRepository repository;
    private final AuditWriter auditWriter;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public UpdateStoreProfileService(
            StoreSettingsRepository repository,
            AuditWriter auditWriter,
            ObjectMapper objectMapper,
            Clock clock) {
        this.repository = repository;
        this.auditWriter = auditWriter;
        this.objectMapper = objectMapper;
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
        current.updateProfile(after);
        StoreSettings saved = repository.save(current);

        String storeId = saved.storeId().toString();
        auditWriter.record(
                new AuditRecordCommand(
                        "STORE",
                        "STORE_PROFILE_UPDATED",
                        UUID.randomUUID().toString(),
                        0,
                        new AuditTarget(AuditTargetType.STORE_PROFILE, storeId),
                        List.of(new AuditRelatedTarget(
                                AuditRelationType.STORE, AuditTargetType.STORE_PROFILE, storeId)),
                        toJson(before),
                        toJson(after),
                        null,
                        null,
                        "1"),
                new AuditContext(actor.actorId(), actor.actorRole(), clock.instant(), requestId));
        return saved;
    }

    private String toJson(StoreProfile profile) {
        try {
            Map<String, String> value = new LinkedHashMap<>();
            value.put("name", profile.name());
            value.put("address", profile.address());
            value.put("contact", profile.contact());
            value.put("timeZone", profile.timeZone().getId());
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("매장 프로필 감사 값을 JSON으로 변환할 수 없습니다", exception);
        }
    }
}
