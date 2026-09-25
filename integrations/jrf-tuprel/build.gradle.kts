plugins {
    id("jrf.java-conventions")
}

dependencies {
    // Adapter between the JRF Database SPI and Tuprel (see docs/DATABASE.md).
    api(project(":jrf-database"))
}
