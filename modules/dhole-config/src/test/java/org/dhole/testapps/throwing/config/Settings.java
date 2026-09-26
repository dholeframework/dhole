package org.dhole.testapps.throwing.config;

import org.dhole.config.SettingsBuilder;

public final class Settings {

    public static void configure(SettingsBuilder settings) {
        throw new IllegalStateException("password=hunter2");
    }
}
