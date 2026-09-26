package org.dhole.internal.build;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * A small Dhole installation for tests: core, config, http, routing and web (with a web activator
 * descriptor), one third-party runtime JAR and test tools.
 */
final class FakeDistribution {

    static final String CATALOG = """
            dhole-distribution 1
            dhole 0.1.0
            test-roots org.junit.jupiter:junit-jupiter:6.1.3

            artifact com.example:json-lib:2.0
            file json-lib-2.0.jar

            artifact org.dhole:dhole-config:0.1.0
            file dhole-config-0.1.0.jar

            artifact org.dhole:dhole-core:0.1.0
            file dhole-core-0.1.0.jar

            artifact org.dhole:dhole-http:0.1.0
            file dhole-http-0.1.0.jar

            artifact org.dhole:dhole-routing:0.1.0
            file dhole-routing-0.1.0.jar
            requires org.dhole:dhole-http:0.1.0

            artifact org.dhole:dhole-web:0.1.0
            file dhole-web-0.1.0.jar
            requires org.dhole:dhole-routing:0.1.0 org.dhole:dhole-config:0.1.0 org.dhole:dhole-core:0.1.0 com.example:json-lib:2.0

            artifact org.junit.jupiter:junit-jupiter:6.1.3
            file junit-jupiter-6.1.3.jar
            requires com.example:json-lib:2.0 org.opentest4j:opentest4j:1.3.0

            artifact org.opentest4j:opentest4j:1.3.0
            file opentest4j-1.3.0.jar
            """;

    private FakeDistribution() {
    }

    /**
     * Creates the installation under {@code home} and returns it.
     */
    static Distribution create(Path home) throws IOException {
        Path lib = Files.createDirectories(home.resolve("lib"));
        Files.writeString(lib.resolve(Distribution.CATALOG), CATALOG, StandardCharsets.UTF_8);
        jar(lib.resolve("json-lib-2.0.jar"), null);
        jar(lib.resolve("dhole-config-0.1.0.jar"), "module config\n");
        jar(lib.resolve("dhole-core-0.1.0.jar"), null);
        jar(lib.resolve("dhole-http-0.1.0.jar"), "module http\n");
        jar(lib.resolve("dhole-routing-0.1.0.jar"), "module routing\nrequires http\n");
        jar(lib.resolve("dhole-web-0.1.0.jar"), "module web\nactivator org.dhole.internal.web.WebActivator\n"
                + "requires config http routing\n");
        jar(lib.resolve("junit-jupiter-6.1.3.jar"), null);
        jar(lib.resolve("opentest4j-1.3.0.jar"), null);
        return Distribution.load(home);
    }

    static void jar(Path file, String module) throws IOException {
        try (OutputStream output = Files.newOutputStream(file); ZipOutputStream zip = new ZipOutputStream(output)) {
            ZipEntry marker = new ZipEntry("content/" + file.getFileName() + ".txt");
            marker.setTimeLocal(LocalDateTime.of(2000, 1, 1, 0, 0));
            zip.putNextEntry(marker);
            zip.write(file.getFileName().toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            if (module != null) {
                ZipEntry descriptor = new ZipEntry("META-INF/dhole/module.idx");
                descriptor.setTimeLocal(LocalDateTime.of(2000, 1, 1, 0, 0));
                zip.putNextEntry(descriptor);
                zip.write(("dhole-module 1\n\n" + module).getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
    }
}
