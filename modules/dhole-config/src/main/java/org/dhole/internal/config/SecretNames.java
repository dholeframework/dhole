package org.dhole.internal.config;

import java.util.Locale;

/**
 * Classifies configuration variables as secret by name.
 *
 * <p>Minimal M2 convention, kept in one place so it can be replaced by explicit secret metadata:
 * a name is secret when it is {@code SECRET}, {@code PASSWORD} or {@code KEY}, or ends with
 * {@code _SECRET}, {@code _PASSWORD} or {@code _KEY} (for example {@code JWT_SECRET},
 * {@code DB_PASSWORD}, {@code API_KEY}).
 */
final class SecretNames {

    static final String MASK = "********";

    private static final String[] SUFFIXES = {"SECRET", "PASSWORD", "KEY"};

    private SecretNames() {
    }

    static boolean isSecret(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        for (String suffix : SUFFIXES) {
            if (upper.equals(suffix) || upper.endsWith("_" + suffix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the value for display: the mask for secrets, the value itself otherwise.
     */
    static String display(String name, String value) {
        return isSecret(name) ? MASK : value;
    }
}
