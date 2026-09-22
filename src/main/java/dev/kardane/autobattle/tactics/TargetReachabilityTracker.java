package dev.kardane.autobattle.tactics;

import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

final class TargetReachabilityTracker {
    private final int failureThreshold;
    private final int cooldownTicks;
    private final double resetDistanceSqr;
    private final Map<UUID, FailureState> failures =
        new HashMap<>();

    TargetReachabilityTracker(
        int failureThreshold,
        int cooldownTicks,
        double resetDistance
    ) {
        if (failureThreshold < 1) {
            throw new IllegalArgumentException(
                "failureThreshold must be positive"
            );
        }

        if (cooldownTicks < 1) {
            throw new IllegalArgumentException(
                "cooldownTicks must be positive"
            );
        }

        if (resetDistance <= 0.0D) {
            throw new IllegalArgumentException(
                "resetDistance must be positive"
            );
        }

        this.failureThreshold = failureThreshold;
        this.cooldownTicks = cooldownTicks;
        this.resetDistanceSqr =
            resetDistance * resetDistance;
    }

    void recordAttempt(
        UUID targetOwnerUuid,
        Vec3 targetPosition,
        boolean success,
        long currentTick
    ) {
        Objects.requireNonNull(
            targetOwnerUuid,
            "targetOwnerUuid"
        );
        Objects.requireNonNull(
            targetPosition,
            "targetPosition"
        );

        if (success) {
            failures.remove(targetOwnerUuid);
            return;
        }

        FailureState previous =
            failures.get(targetOwnerUuid);

        if (previous != null
            && movedEnough(
                previous.targetPosition(),
                targetPosition
            )) {
            previous = null;
        }

        int count = previous == null
            ? 1
            : previous.consecutiveFailures() + 1;

        long excludedUntilTick =
            count >= failureThreshold
                ? currentTick + cooldownTicks
                : -1L;

        failures.put(
            targetOwnerUuid,
            new FailureState(
                count,
                excludedUntilTick,
                targetPosition
            )
        );
    }

    void excludeNow(
        UUID targetOwnerUuid,
        Vec3 targetPosition,
        long currentTick
    ) {
        Objects.requireNonNull(
            targetOwnerUuid,
            "targetOwnerUuid"
        );
        Objects.requireNonNull(
            targetPosition,
            "targetPosition"
        );

        failures.put(
            targetOwnerUuid,
            new FailureState(
                failureThreshold,
                currentTick + cooldownTicks,
                targetPosition
            )
        );
    }

    boolean isTemporarilyUnreachable(
        UUID targetOwnerUuid,
        Vec3 targetPosition,
        long currentTick
    ) {
        Objects.requireNonNull(
            targetOwnerUuid,
            "targetOwnerUuid"
        );
        Objects.requireNonNull(
            targetPosition,
            "targetPosition"
        );

        FailureState state =
            failures.get(targetOwnerUuid);

        if (state == null) {
            return false;
        }

        if (movedEnough(
            state.targetPosition(),
            targetPosition
        )) {
            failures.remove(targetOwnerUuid);
            return false;
        }

        if (state.excludedUntilTick() < 0L) {
            return false;
        }

        if (currentTick >= state.excludedUntilTick()) {
            failures.remove(targetOwnerUuid);
            return false;
        }

        return true;
    }

    void clear() {
        failures.clear();
    }

    private boolean movedEnough(
        Vec3 previous,
        Vec3 current
    ) {
        return previous.distanceToSqr(current)
            >= resetDistanceSqr;
    }

    private record FailureState(
        int consecutiveFailures,
        long excludedUntilTick,
        Vec3 targetPosition
    ) {
    }
}
