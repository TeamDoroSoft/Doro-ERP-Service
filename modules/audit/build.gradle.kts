plugins {
    `java-library`
}

dependencies {
    implementation("tools.jackson.core:jackson-databind")
    implementation("org.springframework:spring-context")
    implementation("org.springframework:spring-jdbc")
    implementation("org.springframework:spring-tx")
}
