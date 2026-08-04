rootProject.name = "doro-erp-service"

include(
    "apps:erp-api",
    "modules:identity",
    "modules:audit",
    "platform:web",
    "test-support",
    "architecture-tests"
)
