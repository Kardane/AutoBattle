package dev.kardane.autobattle.config;

import java.util.Objects;

public record DoctrineNormalizerConfig(
    boolean enabled,
    String apiKey,
    String baseUrl,
    String model,
    int requestTimeoutMs
) {
    public DoctrineNormalizerConfig {
        apiKey = Objects.requireNonNull(apiKey, "apiKey").trim();
        baseUrl = requireNonBlank(baseUrl, "baseUrl");
        model = requireNonBlank(model, "model");

        if (requestTimeoutMs < 1) {
            throw new IllegalArgumentException(
                "Doctrine normalizer timeout must be positive"
            );
        }
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
