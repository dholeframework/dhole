package org.dhole.testapps.config;

import org.dhole.config.SettingsBuilder;

/**
 * Must never be used: it is not the conventional Settings of any test application.
 */
public final class Settings {

    public static void configure(SettingsBuilder settings) {
        throw new AssertionError("unrelated Settings class was invoked");
    }
}
