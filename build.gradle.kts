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

subprojects {
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

    tasks.withType<JavaCompile> {
        // Spring MVC의 @PathVariable·@RequestParam 등이 명시적 name 없이도 리플렉션으로 매개변수
        // 이름을 읽을 수 있어야 한다(Spring Boot 권장 설정).
        options.compilerArgs.add("-parameters")
    }

    dependencies {
        add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
        add("testImplementation", "org.junit.jupiter:junit-jupiter")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}

tasks.named("check") {
    dependsOn(subprojects.map { it.tasks.named("check") })
}
