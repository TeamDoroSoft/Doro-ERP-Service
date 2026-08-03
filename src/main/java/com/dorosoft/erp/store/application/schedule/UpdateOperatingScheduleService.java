package com.dorosoft.erp.store.application.schedule;

import com.dorosoft.erp.audit.application.api.AuditContext;
import com.dorosoft.erp.audit.application.api.AuditRecordCommand;
import com.dorosoft.erp.audit.application.api.AuditRelatedTarget;
import com.dorosoft.erp.audit.application.api.AuditRelationType;
import com.dorosoft.erp.audit.application.api.AuditTarget;
import com.dorosoft.erp.audit.application.api.AuditTargetType;
import com.dorosoft.erp.audit.application.api.AuditWriter;
import com.dorosoft.erp.shared.security.ActorContext;
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
import com.dorosoft.erp.store.application.dto.StoreSettingsWebMapper;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class UpdateOperatingScheduleService {

    private final StoreSettingsRepository repository;
    private final AuditWriter auditWriter;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public UpdateOperatingScheduleService(
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
        try {
            current.replaceSchedule(after);
        } catch (OperatingScheduleViolationException exception) {
            throw toApiException(exception);
        }
        StoreSettings saved = repository.save(current);

        String storeId = saved.storeId().toString();
        auditWriter.record(
                new AuditRecordCommand(
                        "STORE",
                        "STORE_SCHEDULE_UPDATED",
                        UUID.randomUUID().toString(),
                        0,
                        new AuditTarget(AuditTargetType.STORE_SCHEDULE, storeId),
                        List.of(new AuditRelatedTarget(
                                AuditRelationType.STORE, AuditTargetType.STORE_SCHEDULE, storeId)),
                        toJson(before),
                        toJson(after),
                        null,
                        null,
                        "1"),
                new AuditContext(actor.actorId(), actor.actorRole(), clock.instant(), requestId));
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

    private String toJson(OperatingSchedule schedule) {
        try {
            return objectMapper.writeValueAsString(StoreSettingsWebMapper.toScheduleResponse(schedule));
        } catch (JacksonException exception) {
            throw new IllegalStateException("매장 운영 일정 감사 값을 JSON으로 변환할 수 없습니다", exception);
        }
    }
}
