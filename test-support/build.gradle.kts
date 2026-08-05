plugins {
    `java-library`
}

dependencies {
    implementation(project(":modules:audit"))

    implementation("org.springframework:spring-jdbc")
    implementation("tools.jackson.core:jackson-databind")
    implementation("org.springframework.boot:spring-boot-test")
    implementation("org.springframework.boot:spring-boot-testcontainers")
    implementation("org.testcontainers:testcontainers-junit-jupiter")
    implementation("org.testcontainers:testcontainers-mysql")
}
