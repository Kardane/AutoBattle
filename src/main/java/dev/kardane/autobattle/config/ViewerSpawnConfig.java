package dev.kardane.autobattle.config;

public record ViewerSpawnConfig(
    double radius,
    double y
) {
    public ViewerSpawnConfig {
        if (radius <= 0.0D || !Double.isFinite(y)) {
            throw new IllegalArgumentException(
                "Invalid viewer spawn configuration"
            );
        }
    }
}
