pluginManagement {
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

rootProject.name = "dhole"

// Each project keeps its directory name as the project name (for example ":dhole-core").
fun includeFrom(directory: String, vararg names: String) {
    for (name in names) {
        include(name)
        project(":$name").projectDir = file("$directory/$name")
    }
}

includeFrom(
    "modules",
    "dhole-core",
    "dhole-config",
    "dhole-di",
    "dhole-http",
    "dhole-routing",
    "dhole-web",
    "dhole-validation",
    "dhole-serialization",
    "dhole-json",
    "dhole-security",
    "dhole-database",
    "dhole-observability",
    "dhole-plugin-api",
    "dhole-plugin-runtime",
    "dhole-devtools",
    "dhole-testing",
)

includeFrom(
    "tools",
    "dhole-compiler",
    "dhole-build",
    "dhole-cli",
)

includeFrom(
    "integrations",
    "dhole-tuprel",
)

includeFrom(
    "examples",
    "hello-api",
    "bookstore-api",
)
