plugins {
    `java-library`
}

dependencies {
    implementation("org.springframework.boot:spring-boot-test")
    implementation("org.springframework.boot:spring-boot-testcontainers")
    implementation("org.testcontainers:testcontainers-junit-jupiter")
    implementation("org.testcontainers:testcontainers-mysql")
}
