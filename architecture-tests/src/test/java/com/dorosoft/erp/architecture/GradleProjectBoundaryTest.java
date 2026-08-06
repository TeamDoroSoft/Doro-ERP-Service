package com.dorosoft.erp.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class GradleProjectBoundaryTest {

    private static final List<String> APPS = List.of(
            "store-access-api",
            "commerce-api",
            "payment-api",
            "queue-api",
            "audit-api");

    @Test
    void settingsMustRegisterExactlyTheFiveDeployableApps() throws IOException {
        Path root = repositoryRoot();
        String settings = Files.readString(root.resolve("settings.gradle.kts"));

        for (String app : APPS) {
            assertThat(settings).contains("\"apps:" + app + "\"");
        }
        assertThat(settings)
                .doesNotContain("apps:erp-api")
                .doesNotContain("modules:");
    }

    @Test
    void appBuildsMustNotDependOnAnotherApp() throws IOException {
        Path root = repositoryRoot();

        for (String app : APPS) {
            String build = Files.readString(root.resolve("apps").resolve(app).resolve("build.gradle.kts"));
            assertThat(build)
                    .as(app + " must communicate through HTTP or versioned events")
                    .doesNotContain("project(\":apps:");
        }
    }

    @Test
    void eachAppMustDeclareOnlyItsOwnedDatabaseKind() throws IOException {
        Path root = repositoryRoot();

        assertPostgresOwnership(root, "store-access-api", "store_access_db");
        assertPostgresOwnership(root, "commerce-api", "commerce_db");
        assertPostgresOwnership(root, "payment-api", "payment_db");
        assertPostgresOwnership(root, "queue-api", "queue_db");

        String auditConfiguration = applicationYaml(root, "audit-api");
        assertThat(auditConfiguration)
                .contains("mongodb:")
                .doesNotContain("datasource:")
                .doesNotContain("store_access_db", "commerce_db", "payment_db", "queue_db");
    }

    private void assertPostgresOwnership(Path root, String app, String database) throws IOException {
        String configuration = applicationYaml(root, app);
        assertThat(configuration)
                .contains("datasource:", database)
                .doesNotContain("mongodb:");

        for (String otherDatabase : List.of("store_access_db", "commerce_db", "payment_db", "queue_db")) {
            if (!otherDatabase.equals(database)) {
                assertThat(configuration).doesNotContain(otherDatabase);
            }
        }
    }

    private String applicationYaml(Path root, String app) throws IOException {
        return Files.readString(root.resolve("apps")
                .resolve(app)
                .resolve("src/main/resources/application.yaml"));
    }

    private Path repositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null && !Files.exists(current.resolve("settings.gradle.kts"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("Could not locate the Gradle root project");
        }
        return current;
    }
}
