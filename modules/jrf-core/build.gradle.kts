plugins {
    id("jrf.java-conventions")
}

// jrf-core must not depend on any other JRF project (see docs/CORE_ARCHITECTURE.md).
val verifyCoreIsolation = tasks.register("verifyCoreIsolation") {
    group = "verification"
    description = "Verifies that jrf-core does not depend on other JRF projects."
    val violations = provider {
        configurations.flatMap { configuration ->
            configuration.dependencies.withType<ProjectDependency>()
                .map { "${configuration.name} -> ${it.path}" }
        }
    }
    inputs.property("violations", violations)
    doLast {
        val found = violations.get()
        check(found.isEmpty()) { "jrf-core must not depend on other JRF projects: $found" }
    }
}

tasks.named("check") {
    dependsOn(verifyCoreIsolation)
}
