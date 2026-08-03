package com.dorosoft.erp.store.application.feature;

import com.dorosoft.erp.audit.application.api.AuditContext;
import com.dorosoft.erp.audit.application.api.AuditRecordCommand;
import com.dorosoft.erp.audit.application.api.AuditRelatedTarget;
import com.dorosoft.erp.audit.application.api.AuditRelationType;
import com.dorosoft.erp.audit.application.api.AuditTarget;
import com.dorosoft.erp.audit.application.api.AuditTargetType;
import com.dorosoft.erp.audit.application.api.AuditWriter;
import com.dorosoft.erp.platform.web.error.FieldError;
import com.dorosoft.erp.shared.security.ActorContext;
import com.dorosoft.erp.store.application.exception.InvalidSettingCodeException;
import com.dorosoft.erp.store.application.exception.StoreNotInitializedException;
import com.dorosoft.erp.store.application.exception.StoreSettingsVersionConflictException;
import com.dorosoft.erp.store.application.port.StoreSettingsRepository;
import com.dorosoft.erp.store.domain.feature.FeatureCode;
import com.dorosoft.erp.store.domain.feature.FeatureSettings;
import com.dorosoft.erp.store.domain.settings.StoreSettings;
import com.dorosoft.erp.store.presentation.dto.StoreSettingsWebMapper;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class UpdateFeatureSettingsService {

    private final StoreSettingsRepository repository;
    private final AuditWriter auditWriter;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public UpdateFeatureSettingsService(
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
    public StoreSettings update(UpdateFeatureSettingsCommand command, ActorContext actor, String requestId) {
        StoreSettings current = repository.findCurrent().orElseThrow(StoreNotInitializedException::new);
        if (current.version() != command.ifMatchVersion()) {
            throw new StoreSettingsVersionConflictException(current.version(), command.ifMatchVersion());
        }

        FeatureSettings before = current.features();
        FeatureSettings after;
        try {
            after = FeatureSettings.of(command.customerFeatures(), command.notificationEvents());
        } catch (IllegalArgumentException exception) {
            throw new InvalidSettingCodeException(
                    exception.getMessage(),
                    List.of(new FieldError(missingField(command), "MISSING_SETTING_CODE")));
        }
        current.replaceFeatures(after);
        StoreSettings saved = repository.save(current);

        String storeId = saved.storeId().toString();
        auditWriter.record(
                new AuditRecordCommand(
                        "STORE",
                        "STORE_FEATURE_SETTINGS_UPDATED",
                        UUID.randomUUID().toString(),
                        0,
                        new AuditTarget(AuditTargetType.STORE_FEATURE_SETTINGS, storeId),
                        List.of(new AuditRelatedTarget(
                                AuditRelationType.STORE,
                                AuditTargetType.STORE_FEATURE_SETTINGS,
                                storeId)),
                        toJson(before),
                        toJson(after),
                        null,
                        null,
                        "1"),
                new AuditContext(actor.actorId(), actor.actorRole(), clock.instant(), requestId));
        return saved;
    }

    /** FeatureSettings.of()가 customerFeatures를 notificationEvents보다 먼저 검증하므로 같은 순서로 확인한다. */
    private static String missingField(UpdateFeatureSettingsCommand command) {
        for (FeatureCode code : FeatureCode.values()) {
            if (command.customerFeatures().get(code) == null) {
                return "customerFeatures";
            }
        }
        return "notificationEvents";
    }

    private String toJson(FeatureSettings features) {
        try {
            return objectMapper.writeValueAsString(StoreSettingsWebMapper.toFeatureResponse(features));
        } catch (JacksonException exception) {
            throw new IllegalStateException("매장 기능 설정 감사 값을 JSON으로 변환할 수 없습니다", exception);
        }
    }
}
