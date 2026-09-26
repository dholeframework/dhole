plugins {
    id("org.dhole.java-conventions")
}

dependencies {
    api(project(":dhole-routing"))
    api(project(":dhole-serialization"))
    api(project(":dhole-validation"))
    implementation(project(":dhole-json"))
    implementation(project(":dhole-di"))
}
