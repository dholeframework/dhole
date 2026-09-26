plugins {
    id("org.dhole.java-conventions")
}

val moduleIndexGenerator: Configuration = configurations.create("moduleIndexGenerator") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    implementation(project(":dhole-core"))
    implementation(project(":dhole-web"))
    // config/Settings.java is written against the configuration API.
    implementation(project(":dhole-config"))

    // Internal repository build only: the Dhole build system (dhole build) will run the metadata
    // compiler with the application class from dhole.toml.
    annotationProcessor(project(":dhole-compiler"))

    // Internal repository build only: dhole build writes META-INF/dhole/modules.idx from the module
    // descriptors of the application's runtime artifacts; this harness runs the same generator.
    moduleIndexGenerator(project(":dhole-build"))

    // The black-box test starts the internal web runtime, whose startup signature uses DI types.
    testImplementation(project(":dhole-di"))
}

tasks.named<JavaCompile>("compileJava") {
    options.compilerArgs.add("-Adhole.application=example.hello.App")
}

val generatedResources = layout.buildDirectory.dir("generated/dhole-modules")

val generateModulesIndex = tasks.register<JavaExec>("generateModulesIndex") {
    description = "Writes META-INF/dhole/modules.idx for Dhole.run (internal harness of dhole build)."
    val runtime = configurations.runtimeClasspath
    classpath = moduleIndexGenerator
    mainClass = "org.dhole.internal.build.ModulesIndexGenerator"
    inputs.files(runtime).withPropertyName("runtime")
    outputs.dir(generatedResources)
    argumentProviders.add(CommandLineArgumentProvider {
        listOf(generatedResources.get().asFile.absolutePath) + runtime.get().files.map { it.absolutePath }
    })
}

sourceSets.named("main") {
    resources.srcDir(generateModulesIndex)
}
