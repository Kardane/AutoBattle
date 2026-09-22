package dev.kardane.autobattle.config;

import java.util.Locale;
import java.util.Objects;

public record TeamSpawnConfig(
    String axis,
    double distanceFromCore,
    double memberSpacing,
    double y,
    boolean swapSidesEachRound,
    double randomRadius
) {
    public TeamSpawnConfig(
        String axis,
        double distanceFromCore,
        double memberSpacing,
        double y,
        boolean swapSidesEachRound
    ) {
        this(
            axis,
            distanceFromCore,
            memberSpacing,
            y,
            swapSidesEachRound,
            0.0D
        );
    }

    public TeamSpawnConfig {
        axis = Objects.requireNonNull(axis, "axis")
            .trim()
            .toLowerCase(Locale.ROOT);

        if (!axis.equals("x") && !axis.equals("z")) {
            throw new IllegalArgumentException(
                "team-spawns.axis must be x or z"
            );
        }

        if (distanceFromCore <= 0.0D
            || memberSpacing <= 0.0D
            || !Double.isFinite(y)
            || !Double.isFinite(randomRadius)
            || randomRadius < 0.0D) {
            throw new IllegalArgumentException(
                "Invalid team spawn configuration"
            );
        }
    }
}
