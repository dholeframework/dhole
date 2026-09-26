plugins {
    id("org.dhole.java-conventions")
}

dependencies {
    // End-to-end tests feed the generated index to the dependency container. dhole-di itself never
    // depends on the compiler: they share only the documented index format.
    testImplementation(project(":dhole-di"))
    // Route analysis fixtures are controllers written against the public web API.
    testImplementation(project(":dhole-web"))
    // Validation analysis fixtures are Validatable types written against the public validation API.
    testImplementation(project(":dhole-validation"))
}

// Internal repository harness (not the Dhole application build contract): a small fixture
// application compiled by the normal JavaCompile pipeline with this module's jar on the annotation
// processor path, exactly as the Dhole build system will invoke javac.
val fixture = sourceSets.create("fixture")

dependencies {
    "fixtureAnnotationProcessor"(files(tasks.named("jar")))
}

tasks.named<JavaCompile>(fixture.compileJavaTaskName) {
    options.compilerArgs.add("-Adhole.application=com.acme.fixture.App")
}

tasks.named<Test>("test") {
    val fixtureClasses = fixture.java.destinationDirectory
    inputs.files(fixture.output).withPropertyName("fixtureOutput")
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf("-Ddhole.fixture.classes=${fixtureClasses.get().asFile.absolutePath}")
    })
}
