package org.dhole.internal.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.dhole.internal.bootstrap.Launcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The web module activated from build-generated metadata through the core bootstrap, in an
 * isolated class loader: core finds WebActivator only through modules.idx.
 */
class WebActivatorTest {

    private static final String APP = "org.dhole.internal.web.activation.App";
    private static final Pattern SERVER = Pattern.compile("Server http://localhost:(\\d+) \\(development\\)");

    @TempDir
    Path metadata;

    @Test
    void bootstrapActivatesWebFromModulesIndexServesAndStops() throws Exception {
        write("META-INF/dhole/modules.idx", """
                dhole-modules 1

                module config

                module di

                module http

                module json
                requires serialization

                module routing
                requires http

                module serialization

                module validation

                module web
                activator org.dhole.internal.web.WebActivator
                requires config di http json routing serialization validation
                """);
        write("META-INF/dhole/components.idx", "dhole-metadata 1\n\ncomponent org.dhole.internal.web.activation.PingController\n"
                + "constructor\nsupertype org.dhole.web.Controller\n");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        HttpClient client = HttpClient.newHttpClient();
        int port;

        try (URLClassLoader loader = isolatedLoader()) {
            AutoCloseable running = (AutoCloseable) loader.loadClass(Launcher.class.getName())
                    .getMethod("start", Class.class, PrintStream.class)
                    .invoke(null, loader.loadClass(APP), new PrintStream(output, true, StandardCharsets.UTF_8));
            Matcher server = SERVER.matcher(output.toString(StandardCharsets.UTF_8));
            assertTrue(server.find(), output.toString(StandardCharsets.UTF_8));
            port = Integer.parseInt(server.group(1));

            assertEquals("pong", client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/ping")).build(),
                    BodyHandlers.ofString()).body());
            running.close();
        }

        assertThrows(ConnectException.class, () -> client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/ping")).build(), BodyHandlers.ofString()));
        client.close();
        List<String> lines = output.toString(StandardCharsets.UTF_8).lines().toList();
        assertEquals(List.of("Dhole", "", "Application starting..."), lines.subList(0, 3));
        assertEquals(List.of("Application ready.", "Application stopped."), lines.subList(lines.size() - 2, lines.size()));
    }

    private void write(String location, String text) throws IOException {
        Path file = metadata.resolve(location);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    private URLClassLoader isolatedLoader() throws IOException {
        List<URL> urls = new ArrayList<>();
        urls.add(metadata.toUri().toURL());
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            urls.add(Path.of(entry).toUri().toURL());
        }
        return new URLClassLoader(urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader());
    }
}
