plugins {
    java
}

dependencies {
    testImplementation(project(":apps:erp-api"))
    testImplementation(project(":platform:web"))
    testImplementation(project(":modules:identity"))
    testImplementation(project(":modules:audit"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")
}

tasks.jar {
    enabled = false
}
