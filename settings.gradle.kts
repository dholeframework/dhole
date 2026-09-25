pluginManagement {
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

rootProject.name = "java-rest-framework"

// Each project keeps its directory name as the project name (for example ":jrf-core").
fun includeFrom(directory: String, vararg names: String) {
    for (name in names) {
        include(name)
        project(":$name").projectDir = file("$directory/$name")
    }
}

includeFrom(
    "modules",
    "jrf-core",
    "jrf-config",
    "jrf-di",
    "jrf-http",
    "jrf-routing",
    "jrf-web",
    "jrf-validation",
    "jrf-serialization",
    "jrf-json",
    "jrf-security",
    "jrf-database",
    "jrf-observability",
    "jrf-plugin-api",
    "jrf-plugin-runtime",
    "jrf-devtools",
    "jrf-testing",
)

includeFrom(
    "tools",
    "jrf-compiler",
    "jrf-build",
    "jrf-cli",
)

includeFrom(
    "integrations",
    "jrf-tuprel",
)

includeFrom(
    "examples",
    "hello-api",
    "bookstore-api",
)
