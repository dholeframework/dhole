package org.dhole.internal.cli;

import java.io.InputStream;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.dhole.internal.build.BuildException;

/**
 * The {@code dhole} command line (CLI.md). Exit codes: {@code 0} success, {@code 1} failure,
 * {@code 2} usage error; {@code run} and {@code test} return the exit code of the application or
 * test JVM. Messages are short and actionable; internal stack traces only with {@code --verbose}.
 */
public final class Main {

    static final String USAGE = """
            Usage: dhole <command> [options]

            Commands:
              new <name>      Create a project (--package <name> to choose the Java package)
              dev             Run the application and restart it when files change
              run             Build and run the application (arguments after --)
              build           Build the application distribution
              test [Class]    Run the tests
              routes          List the typed routes
              config [check]  Show the configuration with secrets masked, or check it
              doctor          Check the project and the environment (read-only)
              version         Show the Dhole version

            Options:
              --update-lock   Select the dependencies again and rewrite dhole.lock
              --verbose       Show internal stack traces""";

    private Main() {
    }

    public static void main(String[] args) {
        System.exit(run(args, new Cli(System.out, System.err, System.in, home(), Path.of(""), false, false)));
    }

    static int run(String[] args, Cli defaults) {
        List<String> arguments = new ArrayList<>();
        List<String> passthrough = new ArrayList<>();
        boolean verbose = false;
        boolean updateLock = false;
        for (int index = 0; index < args.length; index++) {
            String argument = args[index];
            if (argument.equals("--")) {
                passthrough.addAll(List.of(args).subList(index + 1, args.length));
                break;
            } else if (argument.equals("--verbose")) {
                verbose = true;
            } else if (argument.equals("--update-lock")) {
                updateLock = true;
            } else {
                arguments.add(argument);
            }
        }
        Cli cli = new Cli(defaults.out(), defaults.err(), defaults.in(), defaults.home(), defaults.directory(), verbose, updateLock);
        if (arguments.isEmpty() || arguments.get(0).equals("help") || arguments.get(0).equals("--help")) {
            cli.out().println(USAGE);
            return arguments.isEmpty() ? 2 : 0;
        }
        String command = arguments.get(0);
        List<String> rest = arguments.subList(1, arguments.size());
        try {
            return switch (command) {
                case "new" -> ProjectGenerator.run(cli, rest);
                case "build" -> noArguments(cli, command, rest) ? 2 : Commands.build(cli);
                case "run" -> noArguments(cli, command, rest) ? 2 : Commands.run(cli, passthrough);
                case "test" -> Commands.test(cli, rest);
                case "dev" -> noArguments(cli, command, rest) ? 2 : Commands.dev(cli);
                case "routes" -> noArguments(cli, command, rest) ? 2 : Inspection.routes(cli);
                case "config" -> Inspection.config(cli, rest);
                case "doctor" -> noArguments(cli, command, rest) ? 2 : Inspection.doctor(cli);
                case "version", "--version" -> {
                    cli.out().println("Dhole " + cli.distribution().version());
                    cli.out().println("Java  " + System.getProperty("java.version") + " (" + System.getProperty("java.home") + ")");
                    yield 0;
                }
                default -> {
                    cli.err().println("Unknown command '" + command + "'.");
                    cli.err().println();
                    cli.err().println(USAGE);
                    yield 2;
                }
            };
        } catch (BuildException e) {
            cli.err().println();
            cli.err().println(e.getMessage());
            if (verbose) {
                e.printStackTrace(cli.err());
            }
            return 1;
        } catch (RuntimeException e) {
            cli.err().println();
            cli.err().println("Internal Error: " + e);
            if (verbose) {
                e.printStackTrace(cli.err());
            } else {
                cli.err().println("Run the command again with --verbose for details.");
            }
            return 1;
        }
    }

    private static boolean noArguments(Cli cli, String command, List<String> rest) {
        if (rest.isEmpty()) {
            return false;
        }
        cli.err().println("'dhole " + command + "' takes no arguments (got " + String.join(" ", rest) + ").");
        return true;
    }

    /**
     * The installation directory: from the launcher script, otherwise two levels above this JAR.
     */
    private static Path home() {
        String configured = System.getProperty("dhole.home");
        if (configured != null) {
            return Path.of(configured).toAbsolutePath().normalize();
        }
        try {
            Path location = Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return location.getParent().getParent();
        } catch (URISyntaxException | NullPointerException e) {
            return Path.of("").toAbsolutePath();
        }
    }

    /**
     * What every command receives.
     */
    record Cli(PrintStream out, PrintStream err, InputStream in, Path home, Path directory, boolean verbose,
            boolean updateLock) {

        org.dhole.internal.build.Distribution distribution() {
            return org.dhole.internal.build.Distribution.load(home);
        }

        org.dhole.internal.build.ProjectLayout layout() {
            return new org.dhole.internal.build.ProjectLayout(directory);
        }
    }
}
