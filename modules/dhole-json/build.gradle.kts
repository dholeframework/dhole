plugins {
    id("org.dhole.java-conventions")
}

dependencies {
    api(project(":dhole-serialization"))

    // Official JSON implementation; internal to dhole-json, never part of a public Dhole API.
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
}
