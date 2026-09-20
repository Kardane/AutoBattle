package dev.kardane.autobattle.config;

public record ScoringConfig(
    int killScore,
    int assistScore,
    int coreCaptureScore,
    int coreHoldScore,
    int assistWindowTicks
) {
    public ScoringConfig {
        if (killScore < 0
            || assistScore < 0
            || coreCaptureScore < 0
            || coreHoldScore < 0
            || assistWindowTicks < 1) {
            throw new IllegalArgumentException(
                "Invalid scoring configuration"
            );
        }
    }
}
