import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult

plugins {
    id("org.dhole.java-conventions")
}

// JavaCheck is compiled for Java 8 so an old Java can still print why Dhole cannot run.
val launcher: SourceSet = sourceSets.create("launcher")

tasks.named<JavaCompile>(launcher.compileJavaTaskName) {
    options.release = 8
    options.compilerArgs.add("-Xlint:-options")
}

tasks.named<Jar>("jar") {
    from(launcher.output)
}

dependencies {
    implementation(project(":dhole-build"))
    implementation(project(":dhole-devtools"))
    // dhole routes reads routes.idx with the web module's reader.
    implementation(project(":dhole-web"))
}

// Internal repository build only (CLI.md §17): assembles the installable distribution
//
//   bin/dhole, bin/dhole.cmd
//   lib/  CLI and build tools, plus every artifact an application may select
//         (BUILD_SYSTEM.md §21), described by lib/dhole-distribution.idx
//
// Gradle builds the distribution; applications never use Gradle.

val bundled: Configuration = configurations.create("bundled") {
    isCanBeConsumed = false
    isCanBeResolved = true
    description = "Runtime artifacts and test tools bundled for applications."
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, objects.named(TargetJvmEnvironment.STANDARD_JVM))
        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 21)
    }
}

val runtimeModules = listOf(
    "dhole-core", "dhole-config", "dhole-di", "dhole-http", "dhole-routing",
    "dhole-serialization", "dhole-json", "dhole-validation", "dhole-web",
)
val testTools = listOf(
    "org.junit.jupiter:junit-jupiter", "org.junit.platform:junit-platform-launcher",
    "org.apiguardian:apiguardian-api", "org.jspecify:jspecify",
)

dependencies {
    for (module in runtimeModules) {
        bundled(project(":$module"))
    }
    bundled(platform(libs.junit.bom))
    bundled(libs.junit.jupiter)
    bundled(libs.junit.platform.launcher)
    // Annotation-only dependencies of the JUnit API, needed to compile tests without warnings.
    bundled(libs.apiguardian)
    bundled(libs.jspecify)
}

val distributionCatalog = tasks.register("distributionCatalog") {
    description = "Writes lib/dhole-distribution.idx, the catalog of bundled artifacts."
    val output = layout.buildDirectory.file("distribution-catalog/dhole-distribution.idx")
    val root = bundled.incoming.resolutionResult.rootComponent
    val artifacts = bundled.incoming.artifacts.resolvedArtifacts
    val dholeVersion = project.version.toString()
    inputs.files(bundled)
    inputs.property("version", dholeVersion)
    outputs.file(output)
    doLast {
        val files = artifacts.get().associate { it.id.componentIdentifier to it.file.name }
        val components = LinkedHashMap<ComponentIdentifier, ResolvedComponentResult>()
        fun visit(component: ResolvedComponentResult) {
            if (components.put(component.id, component) == null) {
                component.dependencies.filterIsInstance<ResolvedDependencyResult>().forEach { visit(it.selected) }
            }
        }
        root.get().dependencies.filterIsInstance<ResolvedDependencyResult>().forEach { visit(it.selected) }
        fun coordinates(component: ResolvedComponentResult): String {
            val module = component.moduleVersion ?: error("No module version for ${component.id}")
            return "${module.group}:${module.name}:${module.version}"
        }
        val packaged = components.values.filter { files.containsKey(it.id) }
        val roots = packaged.filter { component -> testTools.any { coordinates(component).startsWith("$it:") } }
            .map(::coordinates).sorted()
        check(roots.size == testTools.size) { "Missing test tools: $roots" }
        val text = StringBuilder("dhole-distribution 1\ndhole $dholeVersion\ntest-roots ${roots.joinToString(" ")}\n")
        for (component in packaged.sortedBy(::coordinates)) {
            text.append("\nartifact ${coordinates(component)}\nfile ${files.getValue(component.id)}\n")
            val requires = component.dependencies.filterIsInstance<ResolvedDependencyResult>()
                .map { it.selected }.filter { files.containsKey(it.id) }.map(::coordinates).distinct().sorted()
            if (requires.isNotEmpty()) {
                text.append("requires ${requires.joinToString(" ")}\n")
            }
        }
        output.get().asFile.writeText(text.toString())
    }
}

val dholeDistribution = tasks.register<Sync>("dholeDistribution") {
    group = "distribution"
    description = "Assembles the installable Dhole distribution into build/dhole."
    into(layout.buildDirectory.dir("dhole"))
    from("src/dist") {
        filesMatching("bin/dhole") {
            permissions { unix("rwxr-xr-x") }
        }
    }
    into("lib") {
        from(tasks.named("jar"))
        from(configurations.runtimeClasspath)
        from(bundled)
        from(distributionCatalog)
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.register<Zip>("dholeDistributionZip") {
    group = "distribution"
    description = "Packages the Dhole distribution as a zip."
    archiveFileName = "dhole-${project.version}.zip"
    destinationDirectory = layout.buildDirectory.dir("distributions")
    from(dholeDistribution) {
        into("dhole-${project.version}")
    }
}

// The black-box tests drive the real distribution through its launcher scripts.
tasks.named<Test>("test") {
    dependsOn(dholeDistribution)
    val home = layout.buildDirectory.dir("dhole")
    inputs.files(dholeDistribution).withPropertyName("distribution")
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf("-Ddhole.distribution=${home.get().asFile.absolutePath}")
    })
}
