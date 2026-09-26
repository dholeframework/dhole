package org.dhole.internal.build;

import static org.junit.platform.engine.discovery.ClassNameFilter.includeClassNamePatterns;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClasspathRoots;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

/**
 * Runs the application's tests with the JUnit Platform bundled in the distribution; started by
 * {@code dhole test} in a separate JVM with the test class path. Not part of any application.
 *
 * <p>Arguments: the test class directory, then optional class name filters ({@code UserApiTest}
 * matches classes with that simple name).
 */
public final class TestRunner {

    private TestRunner() {
    }

    public static void main(String[] args) {
        Path classes = Path.of(args[0]);
        LauncherDiscoveryRequestBuilder request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClasspathRoots(Set.of(classes)));
        List<String> patterns = new ArrayList<>();
        for (int index = 1; index < args.length; index++) {
            patterns.add("(.*\\.)?" + Pattern.quote(args[index]).replace("\\*", ".*") + "(\\$.*)?");
        }
        if (!patterns.isEmpty()) {
            request.filters(includeClassNamePatterns(patterns.toArray(String[]::new)));
        }
        LauncherDiscoveryRequest discovery = request.build();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        LauncherFactory.create().execute(discovery, listener);
        TestExecutionSummary summary = listener.getSummary();
        PrintWriter out = new PrintWriter(System.out, true);
        if (summary.getTestsFoundCount() == 0) {
            out.println("No tests found.");
            System.exit(patterns.isEmpty() ? 0 : 1);
        }
        summary.getFailures().forEach(failure -> {
            out.println();
            out.println("FAILED " + failure.getTestIdentifier().getDisplayName()
                    + failure.getTestIdentifier().getSource().map(source -> " (" + source + ")").orElse(""));
            failure.getException().printStackTrace(out);
        });
        out.println();
        out.println(summary.getTestsSucceededCount() + " passed, " + summary.getTotalFailureCount() + " failed, "
                + summary.getTestsSkippedCount() + " skipped (" + summary.getTestsFoundCount() + " tests)");
        out.flush();
        System.exit(summary.getTotalFailureCount() == 0 ? 0 : 1);
    }
}
