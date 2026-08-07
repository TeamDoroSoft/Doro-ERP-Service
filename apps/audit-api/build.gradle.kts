import org.springframework.boot.gradle.tasks.bundling.BootBuildImage

plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":platform:web"))
    implementation(project(":platform:observability"))
    implementation(project(":platform:messaging-contract"))

    implementation(platform("io.awspring.cloud:spring-cloud-aws-dependencies:4.0.2"))
    implementation("io.awspring.cloud:spring-cloud-aws-starter-sqs")

    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation(project(":test-support"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:testcontainers-localstack")
}

tasks.named<BootBuildImage>("bootBuildImage") {
    imageName.set("doro-erp-audit:${project.version}")
}
