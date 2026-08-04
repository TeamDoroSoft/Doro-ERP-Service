package com.dorosoft.erp.store.application.schedule;

import com.dorosoft.erp.audit.application.api.AuditContext;
import com.dorosoft.erp.audit.application.api.AuditRecordCommand;
import com.dorosoft.erp.audit.application.api.AuditTargetType;
import com.dorosoft.erp.audit.application.api.AuditWriter;
import com.dorosoft.erp.shared.security.ActorContext;
import com.dorosoft.erp.store.application.audit.StoreAuditJsonWriter;
import com.dorosoft.erp.store.application.audit.StoreAuditTargets;
import com.dorosoft.erp.store.application.audit.StoreAuditValueMapper;
import com.dorosoft.erp.store.application.exception.ClosedDayHasBusinessHoursException;
import com.dorosoft.erp.store.application.exception.DuplicateTemporaryClosureException;
import com.dorosoft.erp.store.application.exception.OverlappingBusinessHoursException;
import com.dorosoft.erp.store.application.exception.ServiceWindowOutsideBusinessHoursException;
import com.dorosoft.erp.store.application.exception.StoreNotInitializedException;
import com.dorosoft.erp.store.application.exception.StoreSettingsVersionConflictException;
import com.dorosoft.erp.store.application.port.StoreSettingsRepository;
import com.dorosoft.erp.store.domain.schedule.OperatingSchedule;
import com.dorosoft.erp.store.domain.schedule.OperatingScheduleViolationException;
import com.dorosoft.erp.store.domain.settings.StoreSettings;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class UpdateOperatingScheduleService {

    private static final Logger log = LoggerFactory.getLogger(UpdateOperatingScheduleService.class);

    private final StoreSettingsRepository repository;
    private final AuditWriter auditWriter;
    private final StoreAuditJsonWriter jsonWriter;
    private final Clock clock;

    public UpdateOperatingScheduleService(
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
    public StoreSettings update(UpdateScheduleCommand command, ActorContext actor, String requestId) {
        StoreSettings current = repository.findCurrent().orElseThrow(StoreNotInitializedException::new);
        if (current.version() != command.ifMatchVersion()) {
            throw new StoreSettingsVersionConflictException(current.version(), command.ifMatchVersion());
        }

        OperatingSchedule before = current.schedule();
        OperatingSchedule after = OperatingSchedule.of(
                command.businessHours(),
                command.regularClosedDays(),
                command.temporaryClosures(),
                command.serviceWindows());
        long beforeVersion = current.version();
        try {
            current.replaceSchedule(after);
        } catch (OperatingScheduleViolationException exception) {
            throw toApiException(exception);
        }
        StoreSettings saved = repository.save(current);

        String operationId =
                requestId != null && !requestId.isBlank()
                        ? requestId
                        : "op-" + UUID.randomUUID();
        auditWriter.record(
                new AuditRecordCommand(
                        "STORE",
                        "STORE_SCHEDULE_UPDATED",
                        operationId,
                        0,
                        StoreAuditTargets.primary(AuditTargetType.STORE_SCHEDULE, saved.storeId()),
                        StoreAuditTargets.related(saved.storeId()),
                        jsonWriter.toJson(StoreAuditValueMapper.schedule(before, beforeVersion)),
                        jsonWriter.toJson(StoreAuditValueMapper.schedule(after, saved.version())),
                        null,
                        null,
                        "2"),
                new AuditContext(actor.actorId(), actor.actorRole(), clock.instant(), requestId));
        log.info(
                "store.settings.updated action=STORE_SCHEDULE_UPDATED version={}", saved.version());
        return saved;
    }

    private RuntimeException toApiException(OperatingScheduleViolationException exception) {
        return switch (exception.reason()) {
            case OVERLAPPING_BUSINESS_HOURS -> new OverlappingBusinessHoursException(exception);
            case SERVICE_WINDOW_OUTSIDE_BUSINESS_HOURS ->
                new ServiceWindowOutsideBusinessHoursException(exception);
            case CLOSED_DAY_HAS_BUSINESS_HOURS -> new ClosedDayHasBusinessHoursException(exception);
            case DUPLICATE_TEMPORARY_CLOSURE -> new DuplicateTemporaryClosureException(exception);
        };
    }
}
