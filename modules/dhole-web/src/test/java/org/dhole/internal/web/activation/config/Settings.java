package org.dhole.internal.web.activation.config;

import org.dhole.config.SettingsBuilder;

/**
 * Binds an ephemeral port so the test never collides with other servers.
 */
public final class Settings {

    private Settings() {
    }

    public static void configure(SettingsBuilder settings) {
        settings.app(app -> app.name("Activation").port(0));
    }
}
