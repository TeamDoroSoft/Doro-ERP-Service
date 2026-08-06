import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension
import org.gradle.api.plugins.JavaPluginExtension

plugins {
    base
    id("org.springframework.boot") version "4.1.0" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

group = "com.dorosoft"
version = "0.0.1-SNAPSHOT"
description = "Doro-ERP-Service"

allprojects {
    repositories {
        mavenCentral()
    }
}

val buildableProjects = subprojects.filter { it.buildFile.isFile }

configure(buildableProjects) {
    apply(plugin = "java")
    apply(plugin = "io.spring.dependency-management")

    extensions.configure<DependencyManagementExtension>("dependencyManagement") {
        imports {
            mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
        }
    }

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
        }
    }

    dependencies {
        add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
        add("testImplementation", "org.junit.jupiter:junit-jupiter")
        add("testImplementation", "org.assertj:assertj-core")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}

tasks.named("check") {
    dependsOn(buildableProjects.map { it.tasks.named("check") })
}

tasks.register("bootJars") {
    description = "Builds every deployable service JAR."
    group = "build"
    dependsOn(
        ":apps:store-access-api:bootJar",
        ":apps:commerce-api:bootJar",
        ":apps:payment-api:bootJar",
        ":apps:queue-api:bootJar",
        ":apps:audit-api:bootJar"
    )
}
