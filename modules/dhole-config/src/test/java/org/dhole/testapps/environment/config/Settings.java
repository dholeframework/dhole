package org.dhole.testapps.environment.config;

import org.dhole.config.SettingsBuilder;

public final class Settings {

    public static void configure(SettingsBuilder settings) {
        String name = settings.environment().isProduction() ? "Live" : "Local";
        settings.app(app -> app.name(name + " " + settings.environment().name()));
    }
}
