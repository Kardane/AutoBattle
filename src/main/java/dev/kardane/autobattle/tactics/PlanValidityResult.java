package dev.kardane.autobattle.tactics;

import dev.kardane.autobattle.robot.RobotZombie;

import java.util.Objects;
import java.util.Optional;

public record PlanValidityResult(
    PlanValidityStatus status,
    RobotZombie target,
    double distance
) {
    public PlanValidityResult {
        Objects.requireNonNull(status, "status");

        if (status == PlanValidityStatus.VALID
            && target == null
            && Double.isFinite(distance)) {
            throw new IllegalArgumentException(
                "Finite distance requires a target"
            );
        }
    }

    public static PlanValidityResult valid(
        RobotZombie target,
        double distance
    ) {
        return new PlanValidityResult(
            PlanValidityStatus.VALID,
            target,
            distance
        );
    }

    public static PlanValidityResult validNonTarget() {
        return valid(null, Double.NaN);
    }

    public static PlanValidityResult invalid(
        PlanValidityStatus status
    ) {
        if (status == PlanValidityStatus.VALID) {
            throw new IllegalArgumentException(
                "Use valid() for VALID status"
            );
        }

        return new PlanValidityResult(
            status,
            null,
            Double.NaN
        );
    }

    public boolean valid() {
        return status == PlanValidityStatus.VALID;
    }

    public Optional<RobotZombie> targetEntity() {
        return Optional.ofNullable(target);
    }
}
