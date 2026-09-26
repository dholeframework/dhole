plugins {
    id("org.dhole.java-conventions")
}

dependencies {
    // dhole dev compiles, resolves and generates metadata through the Dhole build pipeline.
    implementation(project(":dhole-build"))
    // Fast restart is tested against the real core bootstrap.
    testImplementation(project(":dhole-core"))
}
