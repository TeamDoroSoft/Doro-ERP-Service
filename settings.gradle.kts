plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "doro-erp-service"

include(
    "apps:store-access-api",
    "apps:commerce-api",
    "apps:payment-api",
    "apps:queue-api",
    "apps:audit-api",
    "platform:web",
    "platform:observability",
    "platform:messaging-contract",
    "test-support",
    "architecture-tests"
)
