package example.hello.config;

import static org.dhole.env.Env.env;
import static org.dhole.env.Env.envInt;

import org.dhole.config.SettingsBuilder;

public final class Settings {

    private Settings() {
    }

    public static void configure(SettingsBuilder settings) {
        settings.app(app -> app
            .name(env("APP_NAME", "Hello API"))
            .port(envInt("APP_PORT", 8080))
        );
    }
}
