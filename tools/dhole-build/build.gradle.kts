plugins {
    id("org.dhole.java-conventions")
}

dependencies {
    // The module index format lives in dhole-core (MODULE_SYSTEM.md §29).
    implementation(project(":dhole-core"))
    // The metadata compiler runs inside the in-process javac invocation.
    implementation(project(":dhole-compiler"))
    // TestRunner runs in the application's test JVM, where the distribution's JUnit Platform is
    // on the class path; the build tool itself never loads it.
    compileOnly(platform(libs.junit.bom))
    compileOnly(libs.junit.platform.launcher)
    // A metadata diagnostic fixture compiles against the validation API.
    testImplementation(project(":dhole-validation"))
}
