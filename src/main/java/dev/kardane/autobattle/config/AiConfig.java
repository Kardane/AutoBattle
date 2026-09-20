package dev.kardane.autobattle.config;

public record AiConfig(
    int decisionIntervalTicks,
    int decisionLockTicks,
    int decisionDebounceTicks,
    int requestTimeoutMs,
    double minimumConfidence
) {
    public AiConfig {
        if (decisionIntervalTicks < 1
            || decisionLockTicks < 0
            || decisionDebounceTicks < 0
            || requestTimeoutMs < 1) {
            throw new IllegalArgumentException(
                "Invalid AI timing configuration"
            );
        }

        if (minimumConfidence < 0.0D
            || minimumConfidence > 1.0D) {
            throw new IllegalArgumentException(
                "minimumConfidence must be between 0 and 1"
            );
        }
    }
}
