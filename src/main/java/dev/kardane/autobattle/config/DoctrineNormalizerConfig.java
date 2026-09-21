package dev.kardane.autobattle.config;

import java.util.Objects;

public record DoctrineNormalizerConfig(
    boolean enabled,
    String apiKey,
    String baseUrl,
    String model,
    int requestTimeoutMs,
    int totalTimeoutMs,
    int maxAttempts,
    int retryBackoffMs
) {
    public DoctrineNormalizerConfig {
        apiKey = Objects.requireNonNull(apiKey, "apiKey").trim();
        baseUrl = requireNonBlank(baseUrl, "baseUrl");
        model = requireNonBlank(model, "model");

        if (requestTimeoutMs < 1
            || totalTimeoutMs < requestTimeoutMs
            || maxAttempts < 1
            || maxAttempts > 3
            || retryBackoffMs < 0) {
            throw new IllegalArgumentException(
                "Invalid Doctrine normalizer retry/timeout configuration"
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
