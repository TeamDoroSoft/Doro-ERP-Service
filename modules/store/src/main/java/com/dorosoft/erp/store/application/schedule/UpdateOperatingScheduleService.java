package com.dorosoft.erp.store.application.schedule;

import com.dorosoft.erp.audit.application.api.AuditContext;
import com.dorosoft.erp.audit.application.api.AuditWriter;
import com.dorosoft.erp.identity.application.authentication.AuthenticatedActor;
import com.dorosoft.erp.store.application.audit.StoreAuditJsonWriter;
import com.dorosoft.erp.store.application.audit.StoreAuditTargets;
import com.dorosoft.erp.store.application.audit.StoreAuditValueMapper;
import com.dorosoft.erp.store.application.exception.ClosedDayHasBusinessHoursException;
import com.dorosoft.erp.store.application.exception.DuplicatePeriodOrderException;
import com.dorosoft.erp.store.application.exception.DuplicateTemporaryClosureException;
import com.dorosoft.erp.store.application.exception.OverlappingBusinessHoursException;
import com.dorosoft.erp.store.application.exception.OverlappingServiceWindowException;
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
    public StoreSettings update(
            UpdateScheduleCommand command, AuthenticatedActor actor, String requestId) {
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

        auditWriter.record(
                StoreAuditTargets.storeScheduleUpdated(
                        UUID.randomUUID(),
                        saved.storeId(),
                        jsonWriter.toMap(StoreAuditValueMapper.schedule(before, beforeVersion)),
                        jsonWriter.toMap(StoreAuditValueMapper.schedule(after, saved.version()))),
                AuditContext.storeUser(
                        actor.tenantId(),
                        actor.accountId(),
                        actor.roleCode(),
                        actor.accountId().toString(),
                        requestId,
                        clock.instant()));
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
            case OVERLAPPING_SERVICE_WINDOW -> new OverlappingServiceWindowException(exception);
            case DUPLICATE_PERIOD_ORDER -> new DuplicatePeriodOrderException(exception);
        };
    }
}
