package dev.kardane.autobattle.tactics;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class MovementRecoveryTrackerTest {
    private static final Vec3 START =
        new Vec3(0.0D, 80.0D, 0.0D);
    private static final Vec3 DESTINATION =
        new Vec3(10.0D, 80.0D, 0.0D);

    @Test
    void stalledPathEscalatesThroughLocalRecovery() {
        MovementRecoveryTracker tracker = tracker();

        assertEquals(
            MovementRecoveryTracker.Action.REFRESH_DIRECT,
            tracker.evaluate(
                START,
                DESTINATION,
                false,
                true,
                0L
            )
        );

        assertEquals(
            MovementRecoveryTracker.Action.NONE,
            tracker.recordPathAttempt(
                MovementRecoveryTracker.Action.REFRESH_DIRECT,
                true,
                null,
                0L
            )
        );

        assertEquals(
            MovementRecoveryTracker.Action.REFRESH_DIRECT,
            tracker.evaluate(
                START,
                DESTINATION,
                false,
                false,
                25L
            )
        );

        assertEquals(
            MovementRecoveryTracker.Action.NONE,
            tracker.recordPathAttempt(
                MovementRecoveryTracker.Action.REFRESH_DIRECT,
                true,
                null,
                25L
            )
        );

        assertEquals(
            MovementRecoveryTracker.Action.TRY_LEFT_DETOUR,
            tracker.evaluate(
                START,
                DESTINATION,
                false,
                false,
                50L
            )
        );

        Vec3 left = new Vec3(
            2.0D,
            80.0D,
            3.0D
        );

        assertEquals(
            MovementRecoveryTracker.Action.NONE,
            tracker.recordPathAttempt(
                MovementRecoveryTracker.Action.TRY_LEFT_DETOUR,
                true,
                left,
                50L
            )
        );

        assertEquals(
            MovementRecoveryTracker.Action.TRY_RIGHT_DETOUR,
            tracker.evaluate(
                START,
                DESTINATION,
                false,
                false,
                75L
            )
        );

        Vec3 right = new Vec3(
            2.0D,
            80.0D,
            -3.0D
        );

        assertEquals(
            MovementRecoveryTracker.Action.NONE,
            tracker.recordPathAttempt(
                MovementRecoveryTracker.Action.TRY_RIGHT_DETOUR,
                true,
                right,
                75L
            )
        );

        assertEquals(
            MovementRecoveryTracker.Action.ABANDON,
            tracker.evaluate(
                START,
                DESTINATION,
                false,
                false,
                100L
            )
        );
    }

    @Test
    void actualMovementPreventsFalseStallEvenIfRouteDetours() {
        MovementRecoveryTracker tracker = tracker();

        tracker.evaluate(
            START,
            DESTINATION,
            false,
            true,
            0L
        );
        tracker.recordPathAttempt(
            MovementRecoveryTracker.Action.REFRESH_DIRECT,
            true,
            null,
            0L
        );

        Vec3 movedSideways = new Vec3(
            0.0D,
            80.0D,
            1.0D
        );

        assertEquals(
            MovementRecoveryTracker.Action.REFRESH_DIRECT,
            tracker.evaluate(
                movedSideways,
                DESTINATION,
                false,
                false,
                20L
            )
        );

        tracker.recordPathAttempt(
            MovementRecoveryTracker.Action.REFRESH_DIRECT,
            true,
            null,
            20L
        );

        assertEquals(
            MovementRecoveryTracker.Action.NONE,
            tracker.evaluate(
                movedSideways,
                DESTINATION,
                false,
                false,
                30L
            )
        );
    }

    @Test
    void allowedStopIsNeverTreatedAsStall() {
        MovementRecoveryTracker tracker = tracker();

        tracker.evaluate(
            START,
            DESTINATION,
            false,
            true,
            0L
        );
        tracker.recordPathAttempt(
            MovementRecoveryTracker.Action.REFRESH_DIRECT,
            true,
            null,
            0L
        );

        assertEquals(
            MovementRecoveryTracker.Action.NONE,
            tracker.evaluate(
                START,
                DESTINATION,
                true,
                true,
                100L
            )
        );
    }

    @Test
    void movingDestinationTriggersImmediateRefresh() {
        MovementRecoveryTracker tracker = tracker();

        tracker.evaluate(
            START,
            DESTINATION,
            false,
            true,
            0L
        );
        tracker.recordPathAttempt(
            MovementRecoveryTracker.Action.REFRESH_DIRECT,
            true,
            null,
            0L
        );

        Vec3 movedDestination =
            DESTINATION.add(1.1D, 0.0D, 0.0D);

        assertEquals(
            MovementRecoveryTracker.Action.REFRESH_DIRECT,
            tracker.evaluate(
                START,
                movedDestination,
                false,
                false,
                2L
            )
        );
    }

    @Test
    void repeatedPathCreationFailureUsesDetoursBeforeAbandon() {
        MovementRecoveryTracker tracker = tracker();

        MovementRecoveryTracker.Action direct =
            tracker.evaluate(
                START,
                DESTINATION,
                false,
                true,
                0L
            );

        assertEquals(
            MovementRecoveryTracker.Action.NONE,
            tracker.recordPathAttempt(
                direct,
                false,
                null,
                0L
            )
        );

        direct = tracker.evaluate(
            START,
            DESTINATION,
            false,
            true,
            1L
        );

        assertEquals(
            MovementRecoveryTracker.Action.TRY_LEFT_DETOUR,
            tracker.recordPathAttempt(
                direct,
                false,
                null,
                1L
            )
        );

        assertEquals(
            MovementRecoveryTracker.Action.TRY_RIGHT_DETOUR,
            tracker.recordPathAttempt(
                MovementRecoveryTracker.Action.TRY_LEFT_DETOUR,
                false,
                null,
                1L
            )
        );

        assertEquals(
            MovementRecoveryTracker.Action.ABANDON,
            tracker.recordPathAttempt(
                MovementRecoveryTracker.Action.TRY_RIGHT_DETOUR,
                false,
                null,
                1L
            )
        );
    }

    private MovementRecoveryTracker tracker() {
        return new MovementRecoveryTracker(
            10,
            25,
            0.25D,
            1.0D,
            1.0D
        );
    }
}
