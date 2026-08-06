plugins {
    `java-library`
}

dependencies {
    api("org.springframework.boot:spring-boot-test")
    api("org.springframework.boot:spring-boot-testcontainers")
    api("org.testcontainers:testcontainers-junit-jupiter")
    api("org.testcontainers:testcontainers-postgresql")
    api("org.testcontainers:testcontainers-mongodb")
}
