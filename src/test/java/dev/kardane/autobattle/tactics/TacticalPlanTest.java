package dev.kardane.autobattle.tactics;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class TacticalPlanTest {
    @Test
    void assistTargetsAnAllyWithAnAssistPlanId() {
        UUID ally = UUID.randomUUID();

        TacticalPlan plan = TacticalPlan.assist(
            ally,
            "ASSIST_R2",
            10L,
            40L
        );

        assertEquals(TacticalPlanType.ASSIST, plan.type());
        assertEquals(ally, plan.targetOwnerUuid());
        assertNull(plan.destination());
        assertEquals("ASSIST_R2", plan.externalId());
    }

    @Test
    void holdHasNoTargetOrDestinationAndUsesStableExternalId() {
        TacticalPlan plan = TacticalPlan.hold(10L, 40L);

        assertEquals(TacticalPlanType.HOLD, plan.type());
        assertNull(plan.targetOwnerUuid());
        assertNull(plan.destination());
        assertEquals("HOLD_POSITION", plan.externalId());
    }

    @Test
    void existingPlanFactoriesRemainUnchanged() {
        TacticalPlan plan = TacticalPlan.capture(
            new Vec3(0.5D, 80.0D, 0.5D),
            10L,
            40L
        );

        assertEquals(TacticalPlanType.CAPTURE, plan.type());
        assertEquals("CAPTURE_CORE", plan.externalId());
    }
}
