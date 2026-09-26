package org.dhole.internal.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Black-box tests of the installed distribution: every command runs through bin/dhole (or
 * bin/dhole.cmd on Windows) in a separate process, exactly as a developer would run it.
 */
class CliTest {

    private static final Path HOME = Path.of(System.getProperty("dhole.distribution"));
    private static final boolean WINDOWS = System.getProperty("os.name").startsWith("Windows");
    private static final Duration TIMEOUT = Duration.ofMinutes(3);

    @TempDir(cleanup = org.junit.jupiter.api.io.CleanupMode.ON_SUCCESS)
    Path temporary;

    private final HttpClient client = HttpClient.newHttpClient();
    private final List<Running> running = new ArrayList<>();

    @AfterEach
    void stopEverything() {
        running.forEach(Running::kill);
        client.close();
    }

    @Test
    void newBuildAndRunTheDistribution() throws Exception {
        Path work = Files.createDirectories(temporary.resolve("work dir"));
        Result created = dhole(work, Map.of(), "new", "hello");
        assertEquals(0, created.exitCode(), created.output());
        Path project = work.resolve("hello");

        assertEquals(Set.of(".env", ".env.example", ".gitignore", "dhole.toml", "src/main/java/hello/App.java",
                "src/main/java/hello/config/Settings.java", "src/main/java/hello/controllers/HelloController.java",
                "src/test/java/hello/controllers/HelloControllerTest.java"), files(project));
        String sources = read(project, files(project));
        assertFalse(sources.toLowerCase().contains("jrf") || sources.contains("pom.xml") || sources.contains("gradle"), sources);
        assertTrue(Files.readString(project.resolve("dhole.toml")).contains("main = \"hello.App\""));
        assertTrue(Files.readString(project.resolve(".gitignore")).contains("\n.env\n"));

        Result build = dhole(project, Map.of(), "build");
        assertEquals(0, build.exitCode(), build.output());
        assertTrue(build.output().contains("Dependencies resolved (wrote dhole.lock)"), build.output());
        Path distribution = project.resolve("build/distributions/hello");
        assertTrue(Files.exists(distribution.resolve("bin/hello")) && Files.exists(distribution.resolve("bin/hello.cmd")));

        String lock = Files.readString(project.resolve("dhole.lock"));
        Set<String> locked = runtimeChecksums(lock);
        Set<String> packaged = new TreeSet<>();
        try (Stream<Path> jars = Files.list(distribution.resolve("lib"))) {
            for (Path jar : jars.toList()) {
                if (!jar.getFileName().toString().equals("hello.jar")) {
                    packaged.add(sha256(jar));
                }
            }
        }
        assertEquals(locked, packaged, "exactly the locked runtime artifacts are packaged");
        try (JarFile jar = new JarFile(distribution.resolve("lib/hello.jar").toFile())) {
            Attributes manifest = jar.getManifest().getMainAttributes();
            assertEquals("hello.App", manifest.getValue(Attributes.Name.MAIN_CLASS));
            assertTrue(manifest.getValue(Attributes.Name.CLASS_PATH).contains("dhole-web-0.1.0.jar"));
            assertTrue(jar.getEntry("META-INF/dhole/modules.idx") != null && jar.getEntry("META-INF/dhole/routes.idx") != null);
        }

        int port = freePort();
        Running launched = start(project, Map.of("APP_PORT", Integer.toString(port)), script(distribution.resolve("bin/hello")));
        launched.await("Application ready.");
        assertEquals("Hello from Hello", get(port, "/hello"));
        launched.stop();
        assertRefused(port);
        if (!WINDOWS) {
            assertTrue(launched.output().contains("Application stopped."), launched.output());
        }

        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Running jar = start(project, Map.of("APP_PORT", Integer.toString(port)),
                List.of(java, "-jar", distribution.resolve("lib/hello.jar").toString()));
        jar.await("Application ready.");
        assertEquals("Hello from Hello", get(port, "/hello"));
        jar.stop();
    }

    @Test
    void devRestartsAfterAControllerEditAndKeepsServingOnBuildErrors() throws Exception {
        Path project = newProject("hello");
        Path controller = project.resolve("src/main/java/hello/controllers/HelloController.java");
        int port = freePort();

        Running dev = start(project, Map.of("APP_PORT", Integer.toString(port)), dholeCommand("dev"));
        dev.await("Application started in");
        dev.await("Watching source files");
        assertEquals("Hello from Hello", get(port, "/hello"));

        replace(controller, "\"Hello from Hello\"", "\"Hello again\"");
        dev.await("Application restarted in");
        assertEquals("Hello again", get(port, "/hello"));

        replace(controller, "return \"Hello again\";", "return 42;");
        dev.await("Reload rejected. The previous version is still running.");
        assertTrue(dev.output().contains("Build Error"), dev.output());
        assertTrue(dev.output().contains("src/main/java/hello/controllers/HelloController.java:"), dev.output());
        assertEquals("Hello again", get(port, "/hello"));

        replace(controller, "return 42;", "return \"Hello, fixed\";");
        dev.awaitCount("Application restarted in", 2);
        assertEquals("Hello, fixed", get(port, "/hello"));

        dev.send("q");
        assertEquals(0, dev.waitFor(), dev.output());
        assertTrue(dev.output().contains("Application stopped."), dev.output());
        assertRefused(port);
    }

    @Test
    void routesConfigDoctorAndTest() throws Exception {
        Path project = newProject("hello");
        replace(project.resolve("src/main/java/hello/config/Settings.java"), "settings.app(app -> app",
                "env(\"JWT_SECRET\");\n        settings.app(app -> app");
        Files.writeString(project.resolve(".env"), "APP_ENV=development\nAPP_PORT=8080\nJWT_SECRET=super-secret-value\n");

        Result routes = dhole(project, Map.of(), "routes");
        assertEquals(0, routes.exitCode(), routes.output());
        assertTrue(routes.output().contains("METHOD  PATH    HANDLER"), routes.output());
        assertTrue(routes.output().contains("GET     /hello  HelloController.hello"), routes.output());

        Result config = dhole(project, Map.of(), "config");
        assertEquals(0, config.exitCode(), config.output());
        assertTrue(config.output().contains("Environment   development"), config.output());
        assertTrue(config.output().contains("JWT_SECRET") && config.output().contains("********"), config.output());
        assertFalse(config.output().contains("super-secret-value"), config.output());
        Result check = dhole(project, Map.of(), "config", "check");
        assertTrue(check.exitCode() == 0 && check.output().contains("Configuration valid (development)"), check.output());

        boolean git = git(project, "init", "-q");
        Result doctor = dhole(project, Map.of(), "doctor");
        assertEquals(0, doctor.exitCode(), doctor.output());
        assertTrue(doctor.output().contains("dhole.lock valid"), doctor.output());
        assertTrue(doctor.output().contains("dependency graph valid (1 controller)"), doctor.output());
        assertTrue(doctor.output().contains("configuration valid (development)"), doctor.output());
        assertTrue(doctor.output().contains("No critical problems found."), doctor.output());
        if (git) {
            assertTrue(doctor.output().contains(".env ignored by Git"), doctor.output());
        }
        assertFalse(doctor.output().contains("super-secret-value"), doctor.output());

        Result test = dhole(project, Map.of(), "test");
        assertEquals(0, test.exitCode(), test.output());
        assertTrue(test.output().contains("1 passed, 0 failed"), test.output());
        replace(project.resolve("src/test/java/hello/controllers/HelloControllerTest.java"), "\"Hello from Hello\"",
                "\"Goodbye\"");
        Result failing = dhole(project, Map.of(), "test", "HelloControllerTest");
        assertEquals(1, failing.exitCode(), failing.output());
        assertTrue(failing.output().contains("0 passed, 1 failed"), failing.output());
    }

    @Test
    void failuresAreActionable() throws Exception {
        Path empty = Files.createDirectories(temporary.resolve("empty"));
        Result noProject = dhole(empty, Map.of(), "build");
        assertEquals(1, noProject.exitCode(), noProject.output());
        assertTrue(noProject.output().contains("No dhole.toml") && noProject.output().contains("dhole new <name>"),
                noProject.output());
        assertEquals(2, dhole(empty, Map.of(), "frobnicate").exitCode());
        assertTrue(dhole(empty, Map.of(), "version").output().contains("Dhole 0.1.0"));

        Path project = newProject("hello");
        assertEquals(0, dhole(project, Map.of(), "build").exitCode());
        Path lock = project.resolve("dhole.lock");
        String original = Files.readString(lock);
        Files.writeString(lock, original.replaceFirst("sha256 [0-9a-f]{64}", "sha256 " + "0".repeat(64)));
        Result tampered = dhole(project, Map.of(), "build");
        assertEquals(1, tampered.exitCode(), tampered.output());
        assertTrue(tampered.output().contains("Checksum Error"), tampered.output());
        assertFalse(tampered.output().contains("\tat "), "no stack trace by default");

        Files.writeString(lock, original);
        replace(project.resolve("src/main/java/hello/App.java"), "Dhole.run(App.class);", "Dhole.run(App.class)");
        Result broken = dhole(project, Map.of(), "build");
        assertEquals(1, broken.exitCode(), broken.output());
        assertTrue(broken.output().contains("Build Error") && broken.output().contains("src/main/java/hello/App.java:"),
                broken.output());
    }

    // Helpers

    private Path newProject(String name) throws IOException, InterruptedException {
        Result created = dhole(temporary, Map.of(), "new", name);
        assertEquals(0, created.exitCode(), created.output());
        return temporary.resolve(name);
    }

    record Result(int exitCode, String output) {
    }

    private static Result dhole(Path directory, Map<String, String> environment, String... arguments)
            throws IOException, InterruptedException {
        Process process = builder(directory, environment, dholeCommand(arguments)).start();
        process.getOutputStream().close();
        byte[] output = process.getInputStream().readAllBytes();
        if (!process.waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
            process.destroyForcibly();
            fail("dhole " + String.join(" ", arguments) + " did not finish");
        }
        return new Result(process.exitValue(), new String(output, StandardCharsets.UTF_8).replace("\r\n", "\n"));
    }

    private static List<String> dholeCommand(String... arguments) {
        List<String> command = new ArrayList<>(script(HOME.resolve("bin/dhole")));
        command.addAll(List.of(arguments));
        return command;
    }

    private static List<String> script(Path unixScript) {
        return WINDOWS
                ? List.of("cmd.exe", "/c", unixScript.resolveSibling(unixScript.getFileName() + ".cmd").toString())
                : List.of("sh", unixScript.toString());
    }

    private static ProcessBuilder builder(Path directory, Map<String, String> environment, List<String> command) {
        ProcessBuilder builder = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true);
        builder.environment().put("JAVA_HOME", System.getProperty("java.home"));
        builder.environment().remove("DHOLE_OPTS");
        builder.environment().remove("APP_ENV");
        builder.environment().putAll(environment);
        return builder;
    }

    private Running start(Path directory, Map<String, String> environment, List<String> command) throws IOException {
        Running process = new Running(builder(directory, environment, command).start());
        running.add(process);
        return process;
    }

    private String get(int port, String path) throws IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).build(),
                BodyHandlers.ofString()).body();
    }

    private void assertRefused(int port) {
        assertThrows(ConnectException.class, () -> get(port, "/hello"));
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void replace(Path file, String from, String to) throws IOException {
        String text = Files.readString(file);
        assertTrue(text.contains(from), file + " does not contain " + from);
        Files.writeString(file, text.replace(from, to));
    }

    private static Set<String> files(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile).map(path -> root.relativize(path).toString().replace('\\', '/'))
                    .collect(Collectors.toCollection(TreeSet::new));
        }
    }

    private static String read(Path root, Set<String> files) throws IOException {
        StringBuilder text = new StringBuilder();
        for (String file : files) {
            text.append(Files.readString(root.resolve(file)));
        }
        return text.toString();
    }

    private static Set<String> runtimeChecksums(String lock) {
        Set<String> checksums = new TreeSet<>();
        List<String> lines = lock.lines().toList();
        for (int index = 0; index < lines.size(); index++) {
            if (lines.get(index).equals("scope runtime")) {
                checksums.add(lines.get(index + 1).substring("sha256 ".length()));
            }
        }
        return checksums;
    }

    private static String sha256(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }

    private static boolean git(Path directory, String... arguments) {
        List<String> command = new ArrayList<>(List.of("git"));
        command.addAll(List.of(arguments));
        try {
            Process process = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            return process.waitFor(60, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * A background process whose combined output is collected as it arrives.
     */
    static final class Running {

        private final Process process;
        private final StringBuffer output = new StringBuffer();

        Running(Process process) {
            this.process = process;
            Thread reader = new Thread(() -> {
                try (InputStream input = process.getInputStream()) {
                    byte[] buffer = new byte[4096];
                    for (int read; (read = input.read(buffer)) > 0; ) {
                        output.append(new String(buffer, 0, read, StandardCharsets.UTF_8).replace("\r\n", "\n"));
                    }
                } catch (IOException ignored) {
                    // The process ended.
                }
            }, "process-output");
            reader.setDaemon(true);
            reader.start();
        }

        String output() {
            return output.toString();
        }

        void await(String text) throws InterruptedException {
            awaitCount(text, 1);
        }

        void awaitCount(String text, int count) throws InterruptedException {
            long deadline = System.nanoTime() + TIMEOUT.toNanos();
            while (System.nanoTime() < deadline) {
                if (occurrences(output(), text) >= count) {
                    return;
                }
                if (!process.isAlive() && occurrences(output(), text) < count) {
                    Thread.sleep(200);
                    if (occurrences(output(), text) >= count) {
                        return;
                    }
                    fail("Process exited (" + process.exitValue() + ") before printing '" + text + "':\n" + output());
                }
                Thread.sleep(100);
            }
            fail("Timed out waiting for '" + text + "':\n" + output());
        }

        void send(String line) throws IOException {
            OutputStream input = process.getOutputStream();
            input.write((line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
            input.flush();
        }

        int waitFor() throws InterruptedException {
            if (!process.waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                fail("Process did not exit:\n" + output());
            }
            Thread.sleep(200);
            return process.exitValue();
        }

        /**
         * Asks the process tree to stop (SIGTERM on Unix-like systems) and waits for it.
         */
        void stop() throws InterruptedException {
            List<ProcessHandle> tree = process.descendants().toList();
            tree.forEach(ProcessHandle::destroy);
            process.destroy();
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                kill();
            }
            for (ProcessHandle child : tree) {
                child.onExit().completeOnTimeout(child, 60, TimeUnit.SECONDS).join();
            }
            Thread.sleep(300);
        }

        void kill() {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }

        private static int occurrences(String text, String part) {
            int count = 0;
            for (int index = text.indexOf(part); index >= 0; index = text.indexOf(part, index + part.length())) {
                count++;
            }
            return count;
        }
    }
}
