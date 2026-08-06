package com.dorosoft.erp.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ServiceBoundaryTest {

    private static final List<String> SERVICE_ROOTS = List.of(
            "com.dorosoft.erp.storeaccess",
            "com.dorosoft.erp.commerce",
            "com.dorosoft.erp.payment",
            "com.dorosoft.erp.queue",
            "com.dorosoft.erp.audit");

    @Test
    void servicesMustNotReferenceAnotherServicePackage() {
        JavaClasses classes = productionClasses();

        for (JavaClass source : classes) {
            String sourceService = serviceRoot(source.getPackageName());
            if (sourceService == null) {
                continue;
            }
            for (Dependency dependency : source.getDirectDependenciesFromSelf()) {
                String targetService = serviceRoot(dependency.getTargetClass().getPackageName());
                Assertions.assertTrue(
                        targetService == null || targetService.equals(sourceService),
                        () -> source.getName() + " must not depend on " + dependency.getTargetClass().getName());
            }
        }
    }

    @Test
    void platformMustNotContainServiceDependencies() {
        noClasses()
                .that().resideInAPackage("com.dorosoft.erp.platform..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.dorosoft.erp.storeaccess..",
                        "com.dorosoft.erp.commerce..",
                        "com.dorosoft.erp.payment..",
                        "com.dorosoft.erp.queue..",
                        "com.dorosoft.erp.audit..")
                .check(productionClasses());
    }

    @Test
    void domainMustRemainFrameworkIndependent() {
        noClasses()
                .that().resideInAnyPackage(
                        "com.dorosoft.erp.storeaccess.domain..",
                        "com.dorosoft.erp.commerce.domain..",
                        "com.dorosoft.erp.payment.domain..",
                        "com.dorosoft.erp.queue.domain..",
                        "com.dorosoft.erp.audit.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "com.mongodb..",
                        "software.amazon.awssdk..")
                .allowEmptyShould(true)
                .check(productionClasses());
    }

    @Test
    void serviceLayersMustFollowHexagonalDependencyDirection() {
        JavaClasses classes = productionClasses();

        for (String root : SERVICE_ROOTS) {
            noClasses()
                    .that().resideInAPackage(root + ".presentation..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            root + ".infrastructure..",
                            root + ".domain..")
                    .allowEmptyShould(true)
                    .check(classes);
            noClasses()
                    .that().resideInAPackage(root + ".application..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            root + ".presentation..",
                            root + ".infrastructure..")
                    .allowEmptyShould(true)
                    .check(classes);
            noClasses()
                    .that().resideInAPackage(root + ".domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            root + ".presentation..",
                            root + ".application..",
                            root + ".infrastructure..")
                    .allowEmptyShould(true)
                    .check(classes);
            noClasses()
                    .that().resideInAPackage(root + ".infrastructure..")
                    .should().dependOnClassesThat().resideInAPackage(root + ".presentation..")
                    .allowEmptyShould(true)
                    .check(classes);
        }
    }

    @Test
    void productionCodeMustNotDependOnTestSupport() {
        noClasses()
                .that().resideInAPackage("com.dorosoft.erp..")
                .should().dependOnClassesThat().resideInAPackage("com.dorosoft.erp.testsupport..")
                .allowEmptyShould(true)
                .check(productionClasses());
    }

    @Test
    void legacyMonolithPackagesMustNotRemain() {
        JavaClasses classes = productionClasses();

        Assertions.assertTrue(classes.stream().noneMatch(javaClass ->
                        isPackageOrChild(javaClass.getPackageName(), "com.dorosoft.erp.identity")
                                || isPackageOrChild(javaClass.getPackageName(), "com.dorosoft.erp.store")
                                || isPackageOrChild(javaClass.getPackageName(), "com.dorosoft.erp.table")),
                "identity, store and table legacy packages must be removed");
    }

    private boolean isPackageOrChild(String packageName, String expectedRoot) {
        return packageName.equals(expectedRoot) || packageName.startsWith(expectedRoot + ".");
    }

    private String serviceRoot(String packageName) {
        return SERVICE_ROOTS.stream()
                .filter(root -> packageName.equals(root) || packageName.startsWith(root + "."))
                .findFirst()
                .orElse(null);
    }

    private JavaClasses productionClasses() {
        return new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.dorosoft.erp");
    }
}
