package com.dorosoft.erp.store.presentation.dto;

import com.dorosoft.erp.platform.web.error.FieldError;
import com.dorosoft.erp.store.application.schedule.UpdateScheduleCommand;
import com.dorosoft.erp.store.domain.schedule.BusinessPeriod;
import com.dorosoft.erp.store.domain.schedule.ServiceType;
import com.dorosoft.erp.store.domain.schedule.ServiceWindow;
import com.dorosoft.erp.store.domain.schedule.TemporaryClosure;
import com.dorosoft.erp.store.presentation.exception.ScheduleValidationFailedException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record UpdateScheduleRequest(
        Map<String, List<TimePeriodRequest>> businessHours,
        List<String> regularClosedDays,
        List<TemporaryClosureRequest> temporaryClosures,
        Map<String, Map<String, List<TimePeriodRequest>>> serviceWindows) {

    public UpdateScheduleCommand toCommand(long ifMatchVersion) {
        List<FieldError> errors = new ArrayList<>();
        Map<DayOfWeek, List<BusinessPeriod>> parsedBusinessHours = parseBusinessHours(errors);
        Set<DayOfWeek> parsedClosedDays = parseClosedDays(errors);
        Set<TemporaryClosure> parsedTemporaryClosures = parseTemporaryClosures(errors);
        Set<ServiceWindow> parsedServiceWindows = parseServiceWindows(errors);
        if (!errors.isEmpty()) {
            throw new ScheduleValidationFailedException(errors);
        }
        return new UpdateScheduleCommand(
                parsedBusinessHours,
                parsedClosedDays,
                parsedTemporaryClosures,
                parsedServiceWindows,
                ifMatchVersion);
    }

    private Map<DayOfWeek, List<BusinessPeriod>> parseBusinessHours(List<FieldError> errors) {
        Map<DayOfWeek, List<BusinessPeriod>> result = new EnumMap<>(DayOfWeek.class);
        if (businessHours == null) {
            errors.add(new FieldError("businessHours", "REQUIRED"));
            return result;
        }
        businessHours.forEach((dayValue, periods) -> {
            DayOfWeek day = parseDay(dayValue, "businessHours." + dayValue, errors);
            if (day == null || periods == null) {
                if (periods == null) {
                    errors.add(new FieldError("businessHours." + dayValue, "INVALID"));
                }
                return;
            }
            List<BusinessPeriod> parsed = new ArrayList<>();
            for (int order = 0; order < periods.size(); order++) {
                String field = "businessHours." + dayValue + "[" + order + "]";
                TimePeriodRequest period = periods.get(order);
                try {
                    parsed.add(new BusinessPeriod(
                            order, LocalTime.parse(period.start()), LocalTime.parse(period.end())));
                } catch (IllegalArgumentException | NullPointerException exception) {
                    errors.add(new FieldError(field, "INVALID_TIME_PERIOD"));
                }
            }
            result.put(day, List.copyOf(parsed));
        });
        return result;
    }

    private Set<DayOfWeek> parseClosedDays(List<FieldError> errors) {
        Set<DayOfWeek> result = new LinkedHashSet<>();
        if (regularClosedDays == null) {
            errors.add(new FieldError("regularClosedDays", "REQUIRED"));
            return result;
        }
        for (int index = 0; index < regularClosedDays.size(); index++) {
            String value = regularClosedDays.get(index);
            DayOfWeek day = parseDay(value, "regularClosedDays[" + index + "]", errors);
            if (day != null) {
                result.add(day);
            }
        }
        return result;
    }

    private Set<TemporaryClosure> parseTemporaryClosures(List<FieldError> errors) {
        Set<TemporaryClosure> result = new LinkedHashSet<>();
        if (temporaryClosures == null) {
            errors.add(new FieldError("temporaryClosures", "REQUIRED"));
            return result;
        }
        for (int index = 0; index < temporaryClosures.size(); index++) {
            String field = "temporaryClosures[" + index + "].date";
            TemporaryClosureRequest closure = temporaryClosures.get(index);
            try {
                result.add(new TemporaryClosure(LocalDate.parse(closure.date()), closure.reason()));
            } catch (IllegalArgumentException | NullPointerException exception) {
                errors.add(new FieldError(field, "INVALID_DATE"));
            }
        }
        return result;
    }

    private Set<ServiceWindow> parseServiceWindows(List<FieldError> errors) {
        Set<ServiceWindow> result = new LinkedHashSet<>();
        if (serviceWindows == null) {
            errors.add(new FieldError("serviceWindows", "REQUIRED"));
            return result;
        }
        serviceWindows.forEach((serviceValue, byDay) -> {
            ServiceType serviceType = parseServiceType(serviceValue, errors);
            if (serviceType == null || byDay == null) {
                if (byDay == null) {
                    errors.add(new FieldError("serviceWindows." + serviceValue, "INVALID"));
                }
                return;
            }
            byDay.forEach((dayValue, periods) -> {
                String prefix = "serviceWindows." + serviceValue + "." + dayValue;
                DayOfWeek day = parseDay(dayValue, prefix, errors);
                if (day == null || periods == null) {
                    if (periods == null) {
                        errors.add(new FieldError(prefix, "INVALID"));
                    }
                    return;
                }
                for (int order = 0; order < periods.size(); order++) {
                    TimePeriodRequest period = periods.get(order);
                    try {
                        result.add(new ServiceWindow(
                                serviceType,
                                day,
                                order,
                                LocalTime.parse(period.start()),
                                LocalTime.parse(period.end())));
                    } catch (IllegalArgumentException | NullPointerException exception) {
                        errors.add(new FieldError(prefix + "[" + order + "]", "INVALID_TIME_PERIOD"));
                    }
                }
            });
        });
        return result;
    }

    private static DayOfWeek parseDay(String value, String field, List<FieldError> errors) {
        try {
            return DayOfWeek.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            errors.add(new FieldError(field, "INVALID_DAY_OF_WEEK"));
            return null;
        }
    }

    private static ServiceType parseServiceType(String value, List<FieldError> errors) {
        try {
            return ServiceType.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            errors.add(new FieldError("serviceWindows." + value, "INVALID_SERVICE_TYPE"));
            return null;
        }
    }

    public record TimePeriodRequest(String start, String end) {}

    public record TemporaryClosureRequest(String date, String reason) {}
}
