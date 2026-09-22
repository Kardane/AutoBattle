package dev.kardane.autobattle.tactics;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class PlanReachabilityTrackerTest {
    private static final Vec3 DESTINATION =
        new Vec3(0.0D, 80.0D, 0.0D);

    @Test
    void exclusionExpires() {
        PlanReachabilityTracker tracker =
            new PlanReachabilityTracker(
                40,
                3.0D
            );

        tracker.exclude(
            "CAPTURE_CORE",
            DESTINATION,
            10L
        );

        assertTrue(
            tracker.isExcluded(
                "CAPTURE_CORE",
                DESTINATION,
                49L
            )
        );
        assertFalse(
            tracker.isExcluded(
                "CAPTURE_CORE",
                DESTINATION,
                50L
            )
        );
    }

    @Test
    void movedDestinationReleasesExclusion() {
        PlanReachabilityTracker tracker =
            new PlanReachabilityTracker(
                40,
                3.0D
            );

        tracker.exclude(
            "DEFEND_CORE",
            DESTINATION,
            10L
        );

        assertFalse(
            tracker.isExcluded(
                "DEFEND_CORE",
                DESTINATION.add(
                    3.0D,
                    0.0D,
                    0.0D
                ),
                11L
            )
        );
    }

    @Test
    void destinationlessPlanUsesCooldownOnly() {
        PlanReachabilityTracker tracker =
            new PlanReachabilityTracker(
                40,
                3.0D
            );

        tracker.exclude(
            "RETREAT",
            null,
            10L
        );

        assertTrue(
            tracker.isExcluded(
                "RETREAT",
                null,
                49L
            )
        );
        assertFalse(
            tracker.isExcluded(
                "RETREAT",
                null,
                50L
            )
        );
    }
}
