package org.dhole.testapps.required.config;

import static org.dhole.env.Env.env;
import static org.dhole.env.Env.envBool;

import org.dhole.config.SettingsBuilder;

public final class Settings {

    public static void configure(SettingsBuilder settings) {
        boolean debug = envBool("APP_DEBUG", false);
        settings.app(app -> app.name(env("APP_NAME") + (debug ? " (debug)" : "")));
    }
}
