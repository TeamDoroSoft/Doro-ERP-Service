plugins {
    java
}

dependencies {
    testImplementation(project(":apps:store-access-api"))
    testImplementation(project(":apps:commerce-api"))
    testImplementation(project(":apps:payment-api"))
    testImplementation(project(":apps:queue-api"))
    testImplementation(project(":apps:audit-api"))
    testImplementation(project(":platform:web"))
    testImplementation(project(":platform:observability"))
    testImplementation(project(":platform:messaging-contract"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")
}

tasks.jar {
    enabled = false
}
