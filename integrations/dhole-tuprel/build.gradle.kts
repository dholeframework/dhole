plugins {
    id("org.dhole.java-conventions")
}

dependencies {
    // Adapter between the Dhole Database SPI and Tuprel (see docs/DATABASE.md).
    api(project(":dhole-database"))
}
