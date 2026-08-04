rootProject.name = "doro-erp-service"

include(
    "apps:erp-api",
    "modules:identity",
    "modules:audit",
    "modules:store",
    "modules:table",
    "platform:web",
    "test-support",
    "architecture-tests"
)
