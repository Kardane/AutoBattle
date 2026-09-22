package dev.kardane.autobattle.tactics;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class TargetReachabilityTrackerTest {
    private static final UUID TARGET = UUID.fromString(
        "00000000-0000-0000-0000-000000000001"
    );
    private static final Vec3 ORIGIN =
        new Vec3(0.0D, 80.0D, 0.0D);

    @Test
    void excludesOnlyAfterRepeatedFailures() {
        TargetReachabilityTracker tracker =
            new TargetReachabilityTracker(
                3,
                40,
                3.0D
            );

        tracker.recordAttempt(
            TARGET,
            ORIGIN,
            false,
            10L
        );
        tracker.recordAttempt(
            TARGET,
            ORIGIN,
            false,
            11L
        );

        assertFalse(
            tracker.isTemporarilyUnreachable(
                TARGET,
                ORIGIN,
                11L
            )
        );

        tracker.recordAttempt(
            TARGET,
            ORIGIN,
            false,
            12L
        );

        assertTrue(
            tracker.isTemporarilyUnreachable(
                TARGET,
                ORIGIN,
                51L
            )
        );
        assertFalse(
            tracker.isTemporarilyUnreachable(
                TARGET,
                ORIGIN,
                52L
            )
        );
    }

    @Test
    void finalRecoveryCanExcludeImmediately() {
        TargetReachabilityTracker tracker =
            new TargetReachabilityTracker(
                3,
                40,
                3.0D
            );

        tracker.excludeNow(
            TARGET,
            ORIGIN,
            10L
        );

        assertTrue(
            tracker.isTemporarilyUnreachable(
                TARGET,
                ORIGIN,
                49L
            )
        );
        assertFalse(
            tracker.isTemporarilyUnreachable(
                TARGET,
                ORIGIN,
                50L
            )
        );
    }

    @Test
    void targetMovementReleasesExclusionImmediately() {
        TargetReachabilityTracker tracker =
            excludedTracker();

        assertFalse(
            tracker.isTemporarilyUnreachable(
                TARGET,
                ORIGIN.add(3.0D, 0.0D, 0.0D),
                13L
            )
        );
    }

    @Test
    void successfulPathAttemptClearsFailureHistory() {
        TargetReachabilityTracker tracker =
            new TargetReachabilityTracker(
                3,
                40,
                3.0D
            );

        tracker.recordAttempt(
            TARGET,
            ORIGIN,
            false,
            10L
        );
        tracker.recordAttempt(
            TARGET,
            ORIGIN,
            false,
            11L
        );
        tracker.recordAttempt(
            TARGET,
            ORIGIN,
            true,
            12L
        );
        tracker.recordAttempt(
            TARGET,
            ORIGIN,
            false,
            13L
        );

        assertFalse(
            tracker.isTemporarilyUnreachable(
                TARGET,
                ORIGIN,
                13L
            )
        );
    }

    @Test
    void clearReleasesExcludedTargets() {
        TargetReachabilityTracker tracker =
            excludedTracker();

        tracker.clear();

        assertFalse(
            tracker.isTemporarilyUnreachable(
                TARGET,
                ORIGIN,
                13L
            )
        );
    }

    private TargetReachabilityTracker excludedTracker() {
        TargetReachabilityTracker tracker =
            new TargetReachabilityTracker(
                3,
                40,
                3.0D
            );

        tracker.recordAttempt(
            TARGET,
            ORIGIN,
            false,
            10L
        );
        tracker.recordAttempt(
            TARGET,
            ORIGIN,
            false,
            11L
        );
        tracker.recordAttempt(
            TARGET,
            ORIGIN,
            false,
            12L
        );

        return tracker;
    }
}
