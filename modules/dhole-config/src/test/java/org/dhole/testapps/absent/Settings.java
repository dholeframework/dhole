package org.dhole.testapps.absent;

import org.dhole.config.SettingsBuilder;

/**
 * Must never be used: it is outside the conventional config package.
 */
public final class Settings {

    public static void configure(SettingsBuilder settings) {
        throw new AssertionError("unrelated Settings class was invoked");
    }
}
