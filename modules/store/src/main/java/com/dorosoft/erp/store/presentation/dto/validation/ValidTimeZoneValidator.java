package com.dorosoft.erp.store.presentation.dto.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.ZoneId;

public class ValidTimeZoneValidator implements ConstraintValidator<ValidTimeZone, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value != null && ZoneId.getAvailableZoneIds().contains(value);
    }
}
