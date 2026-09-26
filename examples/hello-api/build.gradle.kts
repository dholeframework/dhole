plugins {
    id("org.dhole.java-conventions")
}

dependencies {
    implementation(project(":dhole-core"))
    implementation(project(":dhole-web"))

    // Internal repository build only: the Dhole build system (dhole build) will run the metadata
    // compiler with the application class from dhole.toml.
    annotationProcessor(project(":dhole-compiler"))

    // The black-box test starts the internal web runtime, whose startup signature uses DI types.
    testImplementation(project(":dhole-di"))
}

tasks.named<JavaCompile>("compileJava") {
    options.compilerArgs.add("-Adhole.application=example.hello.App")
}
