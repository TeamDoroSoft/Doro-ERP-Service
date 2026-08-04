rootProject.name = "doro-erp-service"

include(
    "apps:erp-api",
    "modules:identity",
    "modules:audit",
    "modules:store",
    "platform:web",
    "test-support",
    "architecture-tests"
)
