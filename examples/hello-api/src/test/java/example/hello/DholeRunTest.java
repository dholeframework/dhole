package example.hello;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/**
 * hello-api started by its real main method, {@code Dhole.run(App.class)}, in a separate JVM: the
 * core bootstrap activates the web module from the generated modules.idx.
 */
class DholeRunTest {

    @Test
    void mainServesHttpThroughTheActivatedWebModule() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilder builder = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"), App.class.getName())
                .redirectErrorStream(true);
        builder.environment().put("APP_PORT", Integer.toString(port));
        builder.environment().remove("APP_ENV");
        Process process = builder.start();
        StringBuffer output = new StringBuffer();
        Thread reader = new Thread(() -> {
            try (InputStream input = process.getInputStream()) {
                byte[] buffer = new byte[1024];
                for (int read; (read = input.read(buffer)) > 0; ) {
                    output.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                }
            } catch (IOException ignored) {
                // The process ended.
            }
        });
        reader.setDaemon(true);
        reader.start();
        try (HttpClient client = HttpClient.newHttpClient()) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
            while (!output.toString().contains("Application ready.")) {
                if (!process.isAlive() || System.nanoTime() > deadline) {
                    fail("hello-api did not start:\n" + output);
                }
                Thread.sleep(100);
            }
            assertTrue(output.toString().contains("Server http://localhost:" + port), output.toString());

            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/users/7")).build(), BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertEquals("{\"id\":7,\"name\":\"User 7\",\"email\":\"user7@example.com\"}", response.body());
        } finally {
            process.destroy();
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        }
    }
}
