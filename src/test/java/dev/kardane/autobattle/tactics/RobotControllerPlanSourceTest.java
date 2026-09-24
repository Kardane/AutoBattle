package dev.kardane.autobattle.tactics;

import dev.kardane.autobattle.config.AutoBattleConfig;
import dev.kardane.autobattle.match.BattleTeam;
import dev.kardane.autobattle.jev.DecisionTrigger;
import dev.kardane.autobattle.robot.RobotRegistry;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class RobotControllerPlanSourceTest {
    @Test
    void provisionalPlanDoesNotAdvanceDecisionClockOrGeneration() {
        RobotController controller = controller();
        TacticalPlan provisional =
            TacticalPlan.capture(
                new Vec3(0.5D, 80.0D, 0.5D),
                10L,
                40L
            );

        long generation =
            controller.decisionGeneration();

        assertTrue(
            controller.applyProvisionalPlan(
                provisional,
                10L
            )
        );

        assertEquals(
            PlanSource.PROVISIONAL,
            controller.currentPlanSource()
                .orElseThrow()
        );
        assertEquals(-1L, controller.lastDecisionTick());
        assertEquals(
            generation,
            controller.decisionGeneration()
        );
    }

    @Test
    void provisionalMovementFailureDoesNotBlacklistOrRetry() {
        RobotController controller = controller();
        TacticalPlan provisional =
            TacticalPlan.capture(
                new Vec3(0.5D, 80.0D, 0.5D),
                10L,
                40L
            );

        assertTrue(
            controller.applyProvisionalPlan(
                provisional,
                10L
            )
        );

        assertTrue(
            controller.suppressFailedProvisionalMovement(
                provisional.destination(),
                DecisionTrigger.MOVEMENT_FAILED
            )
        );

        assertTrue(controller.currentPlan().isEmpty());
        assertTrue(controller.provisionalBehaviorSuppressed());
        assertFalse(
            controller.isPlanTemporarilyUnreachable(
                provisional,
                11L
            )
        );
        assertFalse(
            controller.applyProvisionalPlan(
                provisional,
                11L
            )
        );
    }

    @Test
    void formalPlanCanImmediatelyPromoteSameProvisionalPlan() {
        RobotController controller = controller();
        TacticalPlan provisional =
            TacticalPlan.capture(
                new Vec3(0.5D, 80.0D, 0.5D),
                10L,
                40L
            );

        assertTrue(
            controller.applyProvisionalPlan(
                provisional,
                10L
            )
        );

        TacticalPlan formal =
            TacticalPlan.capture(
                new Vec3(0.5D, 80.0D, 0.5D),
                12L,
                40L
            );

        assertTrue(
            controller.applyPlan(
                formal,
                12L,
                false,
                PlanSource.AI
            )
        );

        assertEquals(
            PlanSource.AI,
            controller.currentPlanSource()
                .orElseThrow()
        );
        assertEquals(12L, controller.lastDecisionTick());
    }

    @Test
    void urgentRetreatSafeRedecisionInvalidatesPendingDecision() {
        RobotController controller = controller();

        long generation =
            controller.nextDecisionGeneration();

        controller.markDecisionRequested(
            generation,
            10L
        );

        assertTrue(controller.hasPendingDecision());

        controller.requestUrgentRedecision(
            dev.kardane.autobattle.jev.DecisionTrigger.RETREAT_SAFE
        );

        assertFalse(controller.hasPendingDecision());
        assertEquals(
            generation + 1L,
            controller.decisionGeneration()
        );
        assertEquals(
            dev.kardane.autobattle.jev.DecisionTrigger.RETREAT_SAFE,
            controller.decisionTrigger()
        );
    }

    @Test
    void holdExpiryIsResetWhenHoldIsAppliedAgain() {
        RobotController controller = controller();

        assertTrue(
            controller.applyPlan(
                TacticalPlan.hold(10L, 40L),
                10L
            )
        );
        assertFalse(controller.isHoldExpired(89L));
        assertTrue(controller.isHoldExpired(90L));

        assertTrue(
            controller.applyPlan(
                TacticalPlan.hold(90L, 40L),
                90L
            )
        );
        assertFalse(controller.isHoldExpired(169L));
        assertTrue(controller.isHoldExpired(170L));
    }

    private RobotController controller() {
        AutoBattleConfig config =
            AutoBattleConfig.defaults();
        RobotRegistry registry =
            new RobotRegistry();

        return new RobotController(
            UUID.fromString(
                "00000000-0000-0000-0000-000000000010"
            ),
            BattleTeam.RED,
            "R1",
            registry,
            config.robot(),
            config.arena(),
            new PlanValidityPolicy(config)
        );
    }
}
