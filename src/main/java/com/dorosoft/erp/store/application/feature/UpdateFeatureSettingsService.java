package com.dorosoft.erp.store.application.feature;

import com.dorosoft.erp.audit.application.api.AuditContext;
import com.dorosoft.erp.audit.application.api.AuditRecordCommand;
import com.dorosoft.erp.audit.application.api.AuditTargetType;
import com.dorosoft.erp.audit.application.api.AuditWriter;
import com.dorosoft.erp.platform.web.error.FieldError;
import com.dorosoft.erp.shared.security.ActorContext;
import com.dorosoft.erp.store.application.audit.StoreAuditJsonWriter;
import com.dorosoft.erp.store.application.audit.StoreAuditTargets;
import com.dorosoft.erp.store.application.audit.StoreAuditValueMapper;
import com.dorosoft.erp.store.application.exception.InvalidSettingCodeException;
import com.dorosoft.erp.store.application.exception.StoreNotInitializedException;
import com.dorosoft.erp.store.application.exception.StoreSettingsVersionConflictException;
import com.dorosoft.erp.store.application.port.StoreSettingsRepository;
import com.dorosoft.erp.store.domain.feature.FeatureCode;
import com.dorosoft.erp.store.domain.feature.FeatureSettings;
import com.dorosoft.erp.store.domain.settings.StoreSettings;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class UpdateFeatureSettingsService {

    private static final Logger log = LoggerFactory.getLogger(UpdateFeatureSettingsService.class);

    private final StoreSettingsRepository repository;
    private final AuditWriter auditWriter;
    private final StoreAuditJsonWriter jsonWriter;
    private final Clock clock;

    public UpdateFeatureSettingsService(
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
        long beforeVersion = current.version();
        current.replaceFeatures(after);
        StoreSettings saved = repository.save(current);

        String operationId =
                requestId != null && !requestId.isBlank()
                        ? requestId
                        : "op-" + UUID.randomUUID();
        auditWriter.record(
                new AuditRecordCommand(
                        "STORE",
                        "STORE_FEATURE_SETTINGS_UPDATED",
                        operationId,
                        0,
                        StoreAuditTargets.primary(
                                AuditTargetType.STORE_FEATURE_SETTINGS, saved.storeId()),
                        StoreAuditTargets.related(saved.storeId()),
                        jsonWriter.toJson(StoreAuditValueMapper.features(before, beforeVersion)),
                        jsonWriter.toJson(StoreAuditValueMapper.features(after, saved.version())),
                        null,
                        null,
                        "2"),
                new AuditContext(actor.actorId(), actor.actorRole(), clock.instant(), requestId));
        log.info(
                "store.settings.updated action=STORE_FEATURE_SETTINGS_UPDATED version={}",
                saved.version());
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
}
