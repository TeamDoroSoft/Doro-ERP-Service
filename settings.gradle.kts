rootProject.name = "doro-erp-service"

include(
    "apps:erp-api",
    "modules:identity",
    "modules:audit",
    "modules:catalog",
    "modules:order",
    "platform:web",
    "test-support",
    "architecture-tests"
)
