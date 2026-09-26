plugins {
    id("org.dhole.java-conventions")
}

// dhole-core must not depend on any other Dhole project (see docs/CORE_ARCHITECTURE.md).
val verifyCoreIsolation = tasks.register("verifyCoreIsolation") {
    group = "verification"
    description = "Verifies that dhole-core does not depend on other Dhole projects."
    val violations = provider {
        configurations.flatMap { configuration ->
            configuration.dependencies.withType<ProjectDependency>()
                .map { "${configuration.name} -> ${it.path}" }
        }
    }
    inputs.property("violations", violations)
    doLast {
        val found = violations.get()
        check(found.isEmpty()) { "dhole-core must not depend on other Dhole projects: $found" }
    }
}

tasks.named("check") {
    dependsOn(verifyCoreIsolation)
}
