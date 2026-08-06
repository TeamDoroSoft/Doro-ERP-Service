import org.springframework.boot.gradle.tasks.bundling.BootBuildImage

plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":platform:web"))
    implementation(project(":platform:observability"))
    implementation(project(":platform:messaging-contract"))

    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation(project(":test-support"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.named<BootBuildImage>("bootBuildImage") {
    imageName.set("doro-erp-audit:${project.version}")
}
