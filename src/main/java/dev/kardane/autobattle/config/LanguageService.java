package dev.kardane.autobattle.config;

import java.util.Objects;

public final class LanguageService {
    private LanguageConfig config;

    public LanguageService(LanguageConfig config) {
        this.config = Objects.requireNonNull(
            config,
            "config"
        );
    }

    public void reload(LanguageConfig config) {
        this.config = Objects.requireNonNull(
            config,
            "config"
        );
    }

    public String text(String key) {
        return config.text(key);
    }

    public String format(
        String key,
        Object... placeholders
    ) {
        return config.format(key, placeholders);
    }
}
