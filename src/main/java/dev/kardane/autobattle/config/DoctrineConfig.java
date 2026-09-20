package dev.kardane.autobattle.config;

public record DoctrineConfig(
    int maxLineLength
) {
    public DoctrineConfig {
        if (maxLineLength < 1 || maxLineLength > 1024) {
            throw new IllegalArgumentException(
                "maxLineLength must be between 1 and 1024"
            );
        }
    }
}
