package com.dorosoft.erp.architecture;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@AnalyzeClasses(packages = "com.dorosoft.erp")
class Step01BoundaryTest {

    @Test
    void modulePackagesShouldHaveAtLeastOneClassForBoundaryCoverage() {
        JavaClasses importedClasses = importedClasses();

        assertHasClasses(importedClasses, "com.dorosoft.erp.identity.domain");
        assertHasClasses(importedClasses, "com.dorosoft.erp.identity.application");
        assertHasClasses(importedClasses, "com.dorosoft.erp.identity.infrastructure");
        assertHasClasses(importedClasses, "com.dorosoft.erp.identity.presentation");
        assertHasClasses(importedClasses, "com.dorosoft.erp.identity.application.api");
        assertHasClasses(importedClasses, "com.dorosoft.erp.audit.domain");
        assertHasClasses(importedClasses, "com.dorosoft.erp.audit.application");
        assertHasClasses(importedClasses, "com.dorosoft.erp.audit.infrastructure");
        assertHasClasses(importedClasses, "com.dorosoft.erp.audit.presentation");
        assertHasClasses(importedClasses, "com.dorosoft.erp.audit.application.api");
        assertHasClasses(importedClasses, "com.dorosoft.erp.platform.web");
    }

    @Test
    void modulesShouldRespectIdentityAuditBoundary() {
        JavaClasses importedClasses = importedClasses();

        assertIdentityMayReferenceOnlyAuditApplicationApi(importedClasses);
        noClasses()
                .that().resideInAPackage("com.dorosoft.erp.audit..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.dorosoft.erp.identity.presentation..",
                        "com.dorosoft.erp.identity.application..",
                        "com.dorosoft.erp.identity.infrastructure..",
                        "com.dorosoft.erp.identity.domain..").check(importedClasses);
    }

    @Test
    void identityAndAuditMustNotHaveMutualInternalDependencyDirection() {
        JavaClasses importedClasses = importedClasses();

        noClasses()
                .that().resideInAPackage("com.dorosoft.erp.identity..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.dorosoft.erp.audit.domain..",
                        "com.dorosoft.erp.audit.infrastructure..",
                        "com.dorosoft.erp.audit.presentation..")
                .check(importedClasses);

        noClasses()
                .that().resideInAPackage("com.dorosoft.erp.audit..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.dorosoft.erp.identity.domain..",
                        "com.dorosoft.erp.identity.infrastructure..",
                        "com.dorosoft.erp.identity.presentation..",
                        "com.dorosoft.erp.identity.application..")
                .check(importedClasses);
    }

    @Test
    void identityLayerDirectionRules() {
        JavaClasses importedClasses = importedClasses();

        noClasses().that().resideInAPackage("com.dorosoft.erp.identity.presentation..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.dorosoft.erp.identity.infrastructure..",
                        "com.dorosoft.erp.identity.domain..").check(importedClasses);

        noClasses().that().resideInAPackage("com.dorosoft.erp.identity.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.dorosoft.erp.identity.infrastructure..",
                        "com.dorosoft.erp.identity.presentation..").check(importedClasses);

        noClasses().that().resideInAPackage("com.dorosoft.erp.identity.infrastructure..")
                .should().dependOnClassesThat().resideInAnyPackage("com.dorosoft.erp.identity.presentation..").check(importedClasses);

        noClasses().that().resideInAPackage("com.dorosoft.erp.identity.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.dorosoft.erp.identity.application..",
                        "com.dorosoft.erp.identity.infrastructure..",
                        "com.dorosoft.erp.identity.presentation..").check(importedClasses);
    }

    @Test
    void auditLayerDirectionRules() {
        JavaClasses importedClasses = importedClasses();

        noClasses().that().resideInAPackage("com.dorosoft.erp.audit.presentation..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.dorosoft.erp.audit.infrastructure..",
                        "com.dorosoft.erp.audit.domain..").check(importedClasses);

        noClasses().that().resideInAPackage("com.dorosoft.erp.audit.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.dorosoft.erp.audit.infrastructure..",
                        "com.dorosoft.erp.audit.presentation..").check(importedClasses);

        noClasses().that().resideInAPackage("com.dorosoft.erp.audit.infrastructure..")
                .should().dependOnClassesThat().resideInAnyPackage("com.dorosoft.erp.audit.presentation..").check(importedClasses);

        noClasses().that().resideInAPackage("com.dorosoft.erp.audit.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.dorosoft.erp.audit.application..",
                        "com.dorosoft.erp.audit.infrastructure..",
                        "com.dorosoft.erp.audit.presentation..").check(importedClasses);
    }

    @Test
    void testSupportMustNotBeUsedByProductionCode() {
        JavaClasses importedClasses = importedClasses();

        noClasses().that()
                .resideInAnyPackage(
                        "com.dorosoft.erp",
                        "com.dorosoft.erp.identity..",
                        "com.dorosoft.erp.audit..",
                        "com.dorosoft.erp.platform..")
                .should().dependOnClassesThat().resideInAPackage("com.dorosoft.erp.testsupport..").check(importedClasses);
    }

    @Test
    void librariesMustNotDependOnApplicationAssemblyOrBusinessModulesFromPlatform() {
        JavaClasses importedClasses = importedClasses();

        noClasses().that().resideInAnyPackage(
                        "com.dorosoft.erp.identity..",
                        "com.dorosoft.erp.audit..",
                        "com.dorosoft.erp.platform..")
                .should().dependOnClassesThat().resideInAPackage("com.dorosoft.erp.config..")
                .check(importedClasses);

        noClasses().that().resideInAPackage("com.dorosoft.erp.platform..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.dorosoft.erp.identity..",
                        "com.dorosoft.erp.audit..")
                .check(importedClasses);
    }

    @Test
    void modulesShouldNotHaveCycleDependencies() {
        JavaClasses importedClasses = importedClasses();

        slices().matching("com.dorosoft.erp.(*)..")
                .should().beFreeOfCycles().check(importedClasses);
    }

    private void assertHasClasses(JavaClasses importedClasses, String packageName) {
        boolean hasClass = importedClasses.stream()
                .anyMatch(clazz ->
                        clazz.getPackageName().startsWith(packageName)
                                && !clazz.getSimpleName().equals("package-info"));
        Assertions.assertTrue(hasClass, packageName + " must have at least one non-test class for boundary verification.");
    }

    private void assertIdentityMayReferenceOnlyAuditApplicationApi(JavaClasses importedClasses) {
        for (JavaClass source : importedClasses) {
            if (!source.getPackageName().startsWith("com.dorosoft.erp.identity")) {
                continue;
            }
            for (Dependency dependency : source.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass();
                if (target == null) {
                    continue;
                }
                if (!target.getPackageName().startsWith("com.dorosoft.erp.audit")) {
                    continue;
                }
                Assertions.assertTrue(
                        target.getPackageName().startsWith("com.dorosoft.erp.audit.application.api"),
                        () -> String.format("Identity class %s must not depend on %s", source.getName(), target.getName())
                );
            }
        }
    }

    private JavaClasses importedClasses() {
        return new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.dorosoft.erp");
    }
}
