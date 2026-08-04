package com.dorosoft.erp.architecture;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.UUID;


class Step02Feature17ContractTest {

    @Test
    void auditPublicContractsMustMatchFeature17Signatures() throws Exception {
        Class<?> writer = Class.forName("com.dorosoft.erp.audit.application.api.AuditWriter");
        Method record = writer.getDeclaredMethod(
                "record",
                Class.forName("com.dorosoft.erp.audit.application.api.AuditRecordCommand"),
                Class.forName("com.dorosoft.erp.audit.application.api.AuditContext")
        );
        Assertions.assertEquals(
                Class.forName("com.dorosoft.erp.audit.domain.AuditWriteResult"),
                record.getReturnType(),
                "AuditWriter.record signature must return AuditWriteResult"
        );

        Class<?> query = Class.forName("com.dorosoft.erp.audit.application.api.AuditQuery");
        Method queryMethod = query.getDeclaredMethod(
                "query",
                String.class,
                Class.forName("com.dorosoft.erp.audit.application.api.AuditQueryFilter")
        );
        Assertions.assertEquals(
                Class.forName("com.dorosoft.erp.audit.application.api.AuditQueryResult"),
                queryMethod.getReturnType()
        );

        Method findById = query.getDeclaredMethod("findById", String.class, UUID.class);
        Assertions.assertEquals(
                java.util.Optional.class,
                findById.getReturnType()
        );

        Class<?> logger = Class.forName("com.dorosoft.erp.audit.application.api.PrivacyAccessLogger");
        Method append = logger.getDeclaredMethod(
                "append",
                Class.forName("com.dorosoft.erp.audit.application.api.PrivacyAccessCommand"),
                Class.forName("com.dorosoft.erp.audit.application.api.PrivacyAccessContext")
        );
        Assertions.assertEquals(
                Class.forName("com.dorosoft.erp.audit.application.api.PrivacyAccessResult"),
                append.getReturnType()
        );
    }

    @Test
    void concreteAppendImplementationsMustNotBePlaceholderServices() throws Exception {
        Class<?> writerImplementation = Class.forName(
                "com.dorosoft.erp.audit.application.usecase.DefaultAuditWriter");
        Assertions.assertFalse(Modifier.isAbstract(writerImplementation.getModifiers()));
        Assertions.assertTrue(Class.forName("com.dorosoft.erp.audit.application.api.AuditWriter")
                .isAssignableFrom(writerImplementation));
        Method record = writerImplementation.getDeclaredMethod(
                "record",
                Class.forName("com.dorosoft.erp.audit.application.api.AuditRecordCommand"),
                Class.forName("com.dorosoft.erp.audit.application.api.AuditContext"));
        Assertions.assertEquals("MANDATORY", transactionPropagation(record));

        Class<?> privacyImplementation = Class.forName(
                "com.dorosoft.erp.audit.application.usecase.DefaultPrivacyAccessLogger");
        Assertions.assertTrue(Class.forName("com.dorosoft.erp.audit.application.api.PrivacyAccessLogger")
                .isAssignableFrom(privacyImplementation));
        Method append = privacyImplementation.getDeclaredMethod(
                "append",
                Class.forName("com.dorosoft.erp.audit.application.api.PrivacyAccessCommand"),
                Class.forName("com.dorosoft.erp.audit.application.api.PrivacyAccessContext"));
        Assertions.assertEquals("REQUIRED", transactionPropagation(append));

        Class<?> repository = Class.forName("com.dorosoft.erp.audit.infrastructure.JdbcAuditRepository");
        for (String port : new String[]{
                "com.dorosoft.erp.audit.application.port.AuditAppendPort",
                "com.dorosoft.erp.audit.application.port.AuditReadPort",
                "com.dorosoft.erp.audit.application.port.PrivacyAccessAppendPort"
        }) {
            Assertions.assertTrue(Class.forName(port).isAssignableFrom(repository));
        }

        Assertions.assertTrue(Arrays.stream(new Class<?>[]{writerImplementation, privacyImplementation, repository})
                .noneMatch(type -> type.getSimpleName().matches("(?i).*(noop|fake|placeholder).*")));
    }

    private String transactionPropagation(Method method) throws Exception {
        var annotation = Arrays.stream(method.getDeclaredAnnotations())
                .filter(candidate -> candidate.annotationType().getName()
                        .equals("org.springframework.transaction.annotation.Transactional"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Transactional annotation is required"));
        Object propagation = annotation.annotationType().getMethod("propagation").invoke(annotation);
        return propagation.toString();
    }
}
