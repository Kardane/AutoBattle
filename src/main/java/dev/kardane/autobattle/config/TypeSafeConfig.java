package dev.kardane.autobattle.config;

import java.util.Objects;

public record TypeSafeConfig(
    String apiKey,
    String baseUrl,
    String model
) {
    public TypeSafeConfig {
        apiKey = apiKey == null ? "" : apiKey.trim();
        baseUrl = requireNonBlank(baseUrl, "baseUrl");
        model = requireNonBlank(model, "model");
    }

    private static String requireNonBlank(
        String value,
        String name
    ) {
        Objects.requireNonNull(value, name);

        String trimmed = value.trim();

        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(
                name + " must not be blank"
            );
        }

        return trimmed;
    }
}
