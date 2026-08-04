plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-restclient")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-session-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.flywaydb:flyway-mysql")
    runtimeOnly("com.mysql:mysql-connector-j")
    implementation(project(":platform:web"))
    implementation(project(":modules:identity"))
    implementation(project(":modules:audit"))

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation(project(":test-support"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.register("verifyNoTestSupportInRuntimeClasspath") {
    description = "Verifies that test-support is not included in the production runtimeClasspath."
    group = "verification"

    doLast {
        val hasTestSupport = configurations.getByName("runtimeClasspath")
            .incoming
            .resolutionResult
            .allComponents
            .any { component ->
                val identifier = component.id
                identifier is org.gradle.api.artifacts.component.ProjectComponentIdentifier
                        && identifier.projectPath == ":test-support"
            }
        if (hasTestSupport) {
            throw GradleException("test-support must not be included in runtimeClasspath.")
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.named("verifyNoTestSupportInRuntimeClasspath"))
}
