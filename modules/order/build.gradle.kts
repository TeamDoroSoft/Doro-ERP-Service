plugins {
    `java-library`
}

dependencies {
    implementation(project(":modules:catalog"))
    implementation(project(":platform:web"))

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    testImplementation(project(":test-support"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway")
    testImplementation("org.flywaydb:flyway-mysql")
    testRuntimeOnly("com.mysql:mysql-connector-j")
}
