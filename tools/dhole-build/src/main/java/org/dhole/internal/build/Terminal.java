package org.dhole.internal.build;

import java.nio.charset.Charset;
import java.util.Locale;

/**
 * Status symbols for tooling output: check marks when standard output is UTF-8, plain ASCII
 * otherwise (for example a Windows console code page), so output never turns into '?'.
 */
public final class Terminal {

    private static final boolean UNICODE = unicode();

    private Terminal() {
    }

    public static String ok() {
        return UNICODE ? "✓" : "+";
    }

    public static String failed() {
        return UNICODE ? "✗" : "x";
    }

    public static String warning() {
        return "!";
    }

    private static boolean unicode() {
        String encoding = System.getProperty("stdout.encoding", Charset.defaultCharset().name());
        return encoding.toUpperCase(Locale.ROOT).replace("-", "").equals("UTF8");
    }
}
