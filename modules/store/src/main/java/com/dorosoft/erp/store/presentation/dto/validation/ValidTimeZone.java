package com.dorosoft.erp.store.presentation.dto.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = ValidTimeZoneValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidTimeZone {
    String message() default "유효한 IANA Time Zone ID여야 합니다";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
