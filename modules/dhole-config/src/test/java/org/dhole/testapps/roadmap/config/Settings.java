package org.dhole.testapps.roadmap.config;

import static org.dhole.env.Env.env;
import static org.dhole.env.Env.envInt;

import org.dhole.config.SettingsBuilder;

/**
 * The first Settings.java from IMPLEMENTATION_ROADMAP.md §6.
 */
public final class Settings {

    public static void configure(SettingsBuilder settings) {
        settings.app(app -> app
            .name(env("APP_NAME", "Hello"))
            .port(envInt("APP_PORT", 8080))
        );
    }
}
