package org.dhole.testapps.secrets.config;

import static org.dhole.env.Env.env;

import org.dhole.config.SettingsBuilder;

public final class Settings {

    public static void configure(SettingsBuilder settings) {
        env("JWT_SECRET", "local-jwt-secret");
        env("DB_PASSWORD", "local-db-password");
        env("API_KEY", "local-api-key");
        settings.app(app -> app.name(env("APP_NAME", "Secrets")));
    }
}
