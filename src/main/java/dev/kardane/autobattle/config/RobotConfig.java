package dev.kardane.autobattle.config;

public record RobotConfig(
    double maxHealth,
    double attackDamage,
    double movementSpeed,
    double followRange,
    int regenDelayTicks,
    int regenIntervalTicks,
    float regenAmount,
    double engageLeashDistance,
    double chaseLeashDistance,
    double engageSpeed,
    double chaseSpeed,
    double captureSpeed,
    double defendSpeed,
    double repositionSpeed,
    double retreatSpeed,
    double positionReachedDistance,
    double defendRadius,
    double retreatDistance
) {
    public RobotConfig {
        if (maxHealth <= 0.0D
            || attackDamage < 0.0D
            || movementSpeed <= 0.0D
            || followRange <= 0.0D
            || regenDelayTicks < 0
            || regenIntervalTicks < 1
            || regenAmount < 0.0F
            || engageLeashDistance <= 0.0D
            || chaseLeashDistance <= 0.0D
            || engageSpeed <= 0.0D
            || chaseSpeed <= 0.0D
            || captureSpeed <= 0.0D
            || defendSpeed <= 0.0D
            || repositionSpeed <= 0.0D
            || retreatSpeed <= 0.0D
            || positionReachedDistance <= 0.0D
            || defendRadius <= 0.0D
            || retreatDistance <= 0.0D) {
            throw new IllegalArgumentException(
                "Invalid robot configuration"
            );
        }
    }
}
