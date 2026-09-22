package dev.kardane.autobattle.tactics;

import dev.kardane.autobattle.command.PlayerCommandType;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.*;

final class ProvisionalPlanPolicyTest {
    private static final UUID ENEMY_A = UUID.fromString(
        "00000000-0000-0000-0000-000000000001"
    );
    private static final UUID ENEMY_B = UUID.fromString(
        "00000000-0000-0000-0000-000000000002"
    );

    @Test
    void activeCaptureCommandOverridesLowHealthRetreat() {
        List<TacticalPlan> candidates = List.of(
            capture(),
            retreat()
        );

        assertEquals(
            "CAPTURE_CORE",
            ProvisionalPlanPolicy.chooseCandidate(
                candidates,
                PlayerCommandType.CAPTURE,
                0.10D,
                0.25D,
                true,
                ignored -> Double.POSITIVE_INFINITY
            ).orElseThrow().externalId()
        );
    }

    @Test
    void surviveCommandSelectsRetreat() {
        List<TacticalPlan> candidates = List.of(
            capture(),
            retreat()
        );

        assertEquals(
            "RETREAT",
            ProvisionalPlanPolicy.chooseCandidate(
                candidates,
                PlayerCommandType.SURVIVE,
                1.0D,
                0.25D,
                false,
                ignored -> Double.POSITIVE_INFINITY
            ).orElseThrow().externalId()
        );
    }

    @Test
    void attackCommandUsesNearestAvailableCombatPlan() {
        List<TacticalPlan> candidates = List.of(
            engage(ENEMY_A, "ENGAGE_B1"),
            engage(ENEMY_B, "ENGAGE_B2"),
            capture()
        );

        ToDoubleFunction<UUID> distance =
            owner -> owner.equals(ENEMY_A)
                ? 6.0D
                : 3.0D;

        assertEquals(
            "ENGAGE_B2",
            ProvisionalPlanPolicy.chooseCandidate(
                candidates,
                PlayerCommandType.ATTACK,
                1.0D,
                0.25D,
                false,
                distance
            ).orElseThrow().externalId()
        );
    }

    @Test
    void lowHealthRetreatRequiresNearbyDanger() {
        List<TacticalPlan> candidates = List.of(
            capture(),
            retreat()
        );

        assertEquals(
            "CAPTURE_CORE",
            ProvisionalPlanPolicy.chooseCandidate(
                candidates,
                null,
                0.10D,
                0.25D,
                false,
                ignored -> Double.POSITIVE_INFINITY
            ).orElseThrow().externalId()
        );

        assertEquals(
            "RETREAT",
            ProvisionalPlanPolicy.chooseCandidate(
                candidates,
                null,
                0.10D,
                0.25D,
                true,
                ignored -> Double.POSITIVE_INFINITY
            ).orElseThrow().externalId()
        );
    }

    @Test
    void immediateNearbyEnemyBeatsObjective() {
        List<TacticalPlan> candidates = List.of(
            engage(ENEMY_A, "ENGAGE_B1"),
            capture()
        );

        assertEquals(
            "ENGAGE_B1",
            ProvisionalPlanPolicy.chooseCandidate(
                candidates,
                null,
                1.0D,
                0.25D,
                false,
                ignored -> 3.5D
            ).orElseThrow().externalId()
        );
    }

    @Test
    void distantEnemyDoesNotDistractFromObjective() {
        List<TacticalPlan> candidates = List.of(
            engage(ENEMY_A, "ENGAGE_B1"),
            capture()
        );

        assertEquals(
            "CAPTURE_CORE",
            ProvisionalPlanPolicy.chooseCandidate(
                candidates,
                null,
                1.0D,
                0.25D,
                false,
                ignored -> 6.0D
            ).orElseThrow().externalId()
        );
    }

    private TacticalPlan engage(
        UUID target,
        String id
    ) {
        return TacticalPlan.engage(
            target,
            id,
            10L,
            40L
        );
    }

    private TacticalPlan capture() {
        return TacticalPlan.capture(
            new Vec3(0.5D, 80.0D, 0.5D),
            10L,
            40L
        );
    }

    private TacticalPlan retreat() {
        return TacticalPlan.retreat(
            10L,
            40L
        );
    }
}
