plugins {
    id("org.dhole.java-conventions")
}

dependencies {
    // End-to-end tests feed the generated index to the dependency container. dhole-di itself never
    // depends on the compiler: they share only the documented index format.
    testImplementation(project(":dhole-di"))
}
