plugins {
    id("org.dhole.java-conventions")
}

dependencies {
    api(project(":dhole-routing"))
    api(project(":dhole-serialization"))
    api(project(":dhole-validation"))
    implementation(project(":dhole-json"))
    implementation(project(":dhole-di"))
    implementation(project(":dhole-config"))
    // Internal module activation contract (MODULE_SYSTEM.md §29); dhole-core stays independent of web.
    implementation(project(":dhole-core"))
}
