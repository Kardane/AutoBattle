package dev.kardane.autobattle.tactics;

import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

final class PlanReachabilityTracker {
    private final int cooldownTicks;
    private final double destinationResetDistanceSqr;
    private final Map<String, Exclusion> exclusions =
        new HashMap<>();

    PlanReachabilityTracker(
        int cooldownTicks,
        double destinationResetDistance
    ) {
        if (cooldownTicks < 1
            || destinationResetDistance <= 0.0D) {
            throw new IllegalArgumentException(
                "Invalid plan reachability settings"
            );
        }

        this.cooldownTicks = cooldownTicks;
        this.destinationResetDistanceSqr =
            destinationResetDistance
                * destinationResetDistance;
    }

    void exclude(
        String planId,
        Vec3 destination,
        long currentTick
    ) {
        Objects.requireNonNull(planId, "planId");

        exclusions.put(
            planId,
            new Exclusion(
                currentTick + cooldownTicks,
                destination
            )
        );
    }

    boolean isExcluded(
        String planId,
        Vec3 destination,
        long currentTick
    ) {
        Objects.requireNonNull(planId, "planId");

        Exclusion exclusion = exclusions.get(planId);

        if (exclusion == null) {
            return false;
        }

        if (currentTick >= exclusion.untilTick()) {
            exclusions.remove(planId);
            return false;
        }

        if (destination != null
            && exclusion.destination() != null
            && destination.distanceToSqr(
                exclusion.destination()
            ) >= destinationResetDistanceSqr) {
            exclusions.remove(planId);
            return false;
        }

        return true;
    }

    void clear() {
        exclusions.clear();
    }

    private record Exclusion(
        long untilTick,
        Vec3 destination
    ) {
    }
}
