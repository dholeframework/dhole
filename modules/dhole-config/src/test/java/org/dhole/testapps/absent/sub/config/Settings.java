package org.dhole.testapps.absent.sub.config;

import org.dhole.config.SettingsBuilder;

/**
 * Must never be used: it sits in a sub-package of an application without Settings.
 */
public final class Settings {

    public static void configure(SettingsBuilder settings) {
        throw new AssertionError("unrelated Settings class was invoked");
    }
}
