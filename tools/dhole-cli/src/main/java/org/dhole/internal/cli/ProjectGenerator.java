package org.dhole.internal.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.lang.model.SourceVersion;

import org.dhole.internal.build.BuildException;
import org.dhole.internal.build.Terminal;

/**
 * {@code dhole new <name>} (roadmap §12): a small, editable project with {@code dhole.toml},
 * {@code App.java}, {@code config/Settings.java}, one controller and its test, {@code .env},
 * {@code .env.example} and {@code .gitignore}. No Maven or Gradle files.
 */
final class ProjectGenerator {

    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9-]*");

    private ProjectGenerator() {
    }

    static int run(Main.Cli cli, List<String> arguments) {
        String name = null;
        String packageName = null;
        for (int index = 0; index < arguments.size(); index++) {
            if (arguments.get(index).equals("--package") && index + 1 < arguments.size()) {
                packageName = arguments.get(++index);
            } else if (name == null && !arguments.get(index).startsWith("-")) {
                name = arguments.get(index);
            } else {
                cli.err().println("Usage: dhole new <name> [--package <java.package>]");
                return 2;
            }
        }
        if (name == null) {
            cli.err().println("Usage: dhole new <name> [--package <java.package>]");
            return 2;
        }
        if (!NAME.matcher(name).matches()) {
            throw new BuildException("Project Error\n\nThe project name '" + name + "' must start with a lower-case letter "
                    + "and contain only lower-case letters, digits and '-', for example 'hello' or 'order-service'.");
        }
        if (packageName == null) {
            packageName = name.replace("-", "");
        }
        if (!SourceVersion.isName(packageName) || !packageName.equals(packageName.toLowerCase(Locale.ROOT))) {
            throw new BuildException("Project Error\n\n'" + packageName + "' is not a valid lower-case Java package name."
                    + "\n\nChoose one with --package, for example --package com.example." + packageName.replaceAll("\\W", ""));
        }
        Path root = cli.directory().resolve(name).toAbsolutePath().normalize();
        if (Files.exists(root) && !empty(root)) {
            throw new BuildException("Project Error\n\n" + root + " already exists and is not empty.");
        }
        String version = cli.distribution().version();
        for (Map.Entry<String, String> file : files(name, packageName, version).entrySet()) {
            write(root.resolve(file.getKey()), file.getValue());
        }
        cli.out().println(Terminal.ok() + " Created " + name + " (package " + packageName + ", Dhole " + version + ")");
        cli.out().println();
        cli.out().println("Next:");
        cli.out().println("  cd " + name);
        cli.out().println("  dhole dev");
        return 0;
    }

    static Map<String, String> files(String name, String packageName, String version) {
        String path = packageName.replace('.', '/');
        String title = title(name);
        Map<String, String> files = new LinkedHashMap<>();
        files.put("dhole.toml", """
                [project]
                name = "%1$s"
                version = "0.1.0"
                java = "21"

                [dhole]
                version = "%2$s"

                [dependencies]
                web = "%2$s"

                [build]
                main = "%3$s.App"
                """.formatted(name, version, packageName));
        files.put("src/main/java/" + path + "/App.java", """
                package %1$s;

                import org.dhole.Dhole;

                public final class App {

                    private App() {
                    }

                    public static void main(String[] args) {
                        Dhole.run(App.class);
                    }
                }
                """.formatted(packageName));
        files.put("src/main/java/" + path + "/config/Settings.java", """
                package %1$s.config;

                import static org.dhole.env.Env.env;
                import static org.dhole.env.Env.envInt;

                import org.dhole.config.SettingsBuilder;

                public final class Settings {

                    private Settings() {
                    }

                    public static void configure(SettingsBuilder settings) {
                        settings.app(app -> app
                            .name(env("APP_NAME", "%2$s"))
                            .port(envInt("APP_PORT", 8080))
                        );
                    }
                }
                """.formatted(packageName, title));
        files.put("src/main/java/" + path + "/controllers/HelloController.java", """
                package %1$s.controllers;

                import org.dhole.routing.Router;
                import org.dhole.web.Controller;

                public final class HelloController extends Controller {

                    @Override
                    public void routes(Router routes) {
                        routes.get("/hello").to(this::hello);
                    }

                    String hello() {
                        return "Hello from %2$s";
                    }
                }
                """.formatted(packageName, title));
        files.put("src/test/java/" + path + "/controllers/HelloControllerTest.java", """
                package %1$s.controllers;

                import static org.junit.jupiter.api.Assertions.assertEquals;

                import org.junit.jupiter.api.Test;

                class HelloControllerTest {

                    @Test
                    void greets() {
                        assertEquals("Hello from %2$s", new HelloController().hello());
                    }
                }
                """.formatted(packageName, title));
        files.put(".env", """
                # Local development values. Never commit this file.
                APP_ENV=development
                APP_PORT=8080
                """);
        files.put(".env.example", """
                # The variables this application reads. Copy to .env for local development.
                APP_ENV=development
                APP_NAME=
                APP_PORT=8080
                """);
        files.put(".gitignore", """
                # Build output and Dhole tool state
                /build/
                /.dhole/

                # Local configuration and secrets
                .env
                .env.*
                !.env.example
                """);
        return files;
    }

    private static String title(String name) {
        StringBuilder title = new StringBuilder();
        for (String word : name.split("-")) {
            if (!word.isEmpty()) {
                title.append(title.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return title.toString();
    }

    private static boolean empty(Path directory) {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.findAny().isEmpty();
        } catch (IOException e) {
            return false;
        }
    }

    private static void write(Path file, String text) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BuildException("Project Error\n\nUnable to write " + file + ": " + e.getMessage(), e);
        }
    }
}
