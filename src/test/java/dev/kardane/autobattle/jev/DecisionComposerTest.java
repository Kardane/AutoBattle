package dev.kardane.autobattle.jev;

import dev.kardane.autobattle.command.PlayerCommandType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class DecisionComposerTest {
    private final DecisionComposer composer =
        new DecisionComposer();

    @Test
    void retreatComposesWheneverItIsLegal() {
        DecisionComposition result = composer.compose(
            response(
                choice("RETREAT", 0.9D),
                null,
                null
            ),
            List.of(
                "CAPTURE_CORE",
                "RETREAT"
            ),
            "CAPTURE_CORE",
            0.35D
        );

        assertEquals("RETREAT", result.planId());
        assertFalse(result.lowConfidence());
    }

    @Test
    void completedRetreatCannotBeRetainedWhenNoLongerLegal() {
        DecisionComposition result = composer.compose(
            response(
                choice("RETREAT", 0.9D),
                null,
                null
            ),
            List.of(
                "CAPTURE_CORE",
                "HOLD_POSITION"
            ),
            "RETREAT",
            0.35D
        );

        assertNull(result.planId());
        assertFalse(result.lowConfidence());
    }

    @Test
    void controlCoreRecomposesFromCaptureToDefend() {
        DecisionResponse response = response(
            choice("CONTROL_CORE", 0.9D),
            null,
            null
        );

        DecisionComposition requested = composer.compose(
            response,
            List.of(
                "CAPTURE_CORE",
                "RETREAT"
            ),
            null,
            0.35D
        );

        DecisionComposition applied = composer.compose(
            response,
            List.of(
                "DEFEND_CORE",
                "RETREAT"
            ),
            null,
            0.35D
        );

        assertEquals(
            "CAPTURE_CORE",
            requested.planId()
        );
        assertEquals(
            "DEFEND_CORE",
            applied.planId()
        );
    }


    @Test
    void lowConfidenceTargetFallsBackWithoutTargetLoss() {
        DecisionComposition result = composer.compose(
            response(
                choice("FIGHT", 0.9D),
                choice("B1", 0.2D),
                choice("CHASE", 0.9D)
            ),
            List.of(
                "ENGAGE_B1",
                "CHASE_B1",
                "CAPTURE_CORE",
                "RETREAT"
            ),
            "CAPTURE_CORE",
            0.35D
        );

        assertNull(result.planId());
        assertTrue(result.lowConfidence());
        assertFalse(result.targetUnavailable());
    }

    @Test
    void unavailableTargetIsNotSilentlyReplaced() {
        DecisionComposition result = composer.compose(
            response(
                choice("FIGHT", 0.9D),
                choice("B1", 0.9D),
                choice("CHASE", 0.9D)
            ),
            List.of(
                "ENGAGE_R1",
                "CHASE_R1",
                "RETREAT"
            ),
            null,
            0.35D
        );

        assertNull(result.planId());
        assertTrue(result.targetUnavailable());
    }


    @Test
    void engagePreferenceOutsideEngageRangeIsNotTargetLoss() {
        DecisionComposition result = composer.compose(
            response(
                choice("FIGHT", 0.9D),
                choice("B1", 0.9D),
                choice("ENGAGE", 0.9D)
            ),
            List.of(
                "CHASE_B1",
                "CAPTURE_CORE",
                "RETREAT"
            ),
            "CAPTURE_CORE",
            0.35D
        );

        assertNull(result.planId());
        assertFalse(result.targetUnavailable());
    }

    @Test
    void lowConfidencePursuitDegradesToEngage() {
        DecisionComposition result = composer.compose(
            response(
                choice("FIGHT", 0.9D),
                choice("B1", 0.9D),
                choice("CHASE", 0.2D)
            ),
            List.of(
                "ENGAGE_B1",
                "CHASE_B1",
                "RETREAT"
            ),
            null,
            0.35D
        );

        assertEquals("ENGAGE_B1", result.planId());
        assertTrue(result.lowConfidence());
    }

    @Test
    void attackCommandOverridesStrategicIntent() {
        DecisionComposition result = composer.compose(
            response(
                choice("RETREAT", 0.9D),
                choice("B1", 0.9D),
                choice("ENGAGE", 0.9D)
            ),
            List.of(
                "ENGAGE_B1",
                "CHASE_B1",
                "CAPTURE_CORE",
                "RETREAT"
            ),
            "CAPTURE_CORE",
            0.35D,
            PlayerCommandType.ATTACK
        );

        assertEquals("ENGAGE_B1", result.planId());
        assertFalse(result.lowConfidence());
    }

    @Test
    void captureCommandOverridesStrategicIntent() {
        DecisionComposition result = composer.compose(
            response(
                choice("FIGHT", 0.9D),
                choice("B1", 0.9D),
                choice("CHASE", 0.9D)
            ),
            List.of(
                "ENGAGE_B1",
                "CHASE_B1",
                "CAPTURE_CORE",
                "RETREAT"
            ),
            "ENGAGE_B1",
            0.35D,
            PlayerCommandType.CAPTURE
        );

        assertEquals("CAPTURE_CORE", result.planId());
        assertFalse(result.lowConfidence());
    }

    @Test
    void surviveCommandOverridesStrategicIntent() {
        DecisionComposition result = composer.compose(
            response(
                choice("FIGHT", 0.9D),
                choice("B1", 0.9D),
                choice("CHASE", 0.9D)
            ),
            List.of(
                "ENGAGE_B1",
                "CHASE_B1",
                "DEFEND_CORE",
                "RETREAT"
            ),
            "ENGAGE_B1",
            0.35D,
            PlayerCommandType.SURVIVE
        );

        assertEquals("RETREAT", result.planId());
        assertFalse(result.lowConfidence());
    }

    @Test
    void supportIntentComposesAssistPlan() {
        DecisionComposition result = composer.compose(
            response(
                choice("SUPPORT", 0.9D),
                null,
                null,
                choice("R2", 0.9D)
            ),
            List.of(
                "ASSIST_R2",
                "ASSIST_R3",
                "HOLD_POSITION",
                "RETREAT"
            ),
            "HOLD_POSITION",
            0.35D
        );

        assertEquals("ASSIST_R2", result.planId());
        assertFalse(result.lowConfidence());
        assertFalse(result.targetUnavailable());
    }

    @Test
    void supportWithOneLegalAllyCanBeResolvedWithoutAllyAnswer() {
        DecisionComposition result = composer.compose(
            response(
                choice("SUPPORT", 0.9D),
                null,
                null,
                null
            ),
            List.of(
                "ASSIST_R2",
                "HOLD_POSITION",
                "RETREAT"
            ),
            "HOLD_POSITION",
            0.35D
        );

        assertEquals("ASSIST_R2", result.planId());
        assertFalse(result.lowConfidence());
    }

    @Test
    void lowConfidenceSupportKeepsCurrentAssistPlan() {
        DecisionComposition result = composer.compose(
            response(
                choice("SUPPORT", 0.9D),
                null,
                null,
                choice("R3", 0.2D)
            ),
            List.of(
                "ASSIST_R2",
                "ASSIST_R3",
                "HOLD_POSITION"
            ),
            "ASSIST_R2",
            0.35D
        );

        assertEquals("ASSIST_R2", result.planId());
        assertTrue(result.lowConfidence());
        assertFalse(result.targetUnavailable());
    }

    @Test
    void holdIntentComposesHoldPosition() {
        DecisionComposition result = composer.compose(
            response(
                choice("HOLD", 0.9D),
                null,
                null,
                null
            ),
            List.of(
                "CAPTURE_CORE",
                "HOLD_POSITION",
                "RETREAT"
            ),
            "CAPTURE_CORE",
            0.35D
        );

        assertEquals("HOLD_POSITION", result.planId());
        assertFalse(result.lowConfidence());
    }

    @Test
    void lowConfidenceHoldIsRejectedEvenAboveGlobalMinimum() {
        DecisionComposition result = composer.compose(
            response(
                choice("HOLD", 0.50D),
                null,
                null
            ),
            List.of(
                "CAPTURE_CORE",
                "HOLD_POSITION",
                "RETREAT"
            ),
            "HOLD_POSITION",
            0.35D
        );

        assertNull(result.planId());
        assertTrue(result.lowConfidence());
    }

    @Test
    void lowConfidenceIntentDoesNotKeepCurrentHold() {
        DecisionComposition result = composer.compose(
            response(
                choice("RETREAT", 0.20D),
                null,
                null
            ),
            List.of(
                "CAPTURE_CORE",
                "HOLD_POSITION",
                "RETREAT"
            ),
            "HOLD_POSITION",
            0.35D
        );

        assertNull(result.planId());
        assertTrue(result.lowConfidence());
    }

    @Test
    void lowConfidenceIntentKeepsLegalCurrentPlan() {
        DecisionComposition result = composer.compose(
            response(
                choice("RETREAT", 0.2D),
                null,
                null
            ),
            List.of(
                "CAPTURE_CORE",
                "RETREAT"
            ),
            "CAPTURE_CORE",
            0.35D
        );

        assertEquals(
            "CAPTURE_CORE",
            result.planId()
        );
        assertTrue(result.lowConfidence());
    }

    @Test
    void lowConfidenceIntentWithoutCurrentPlanDoesNotThrow() {
        DecisionComposition result = composer.compose(
            response(
                choice("RETREAT", 0.2D),
                null,
                null
            ),
            List.of(
                "CAPTURE_CORE",
                "RETREAT"
            ),
            null,
            0.35D
        );

        assertNull(result.planId());
        assertTrue(result.lowConfidence());
    }

    private DecisionResponse response(
        ChoiceDecision intent,
        ChoiceDecision target,
        ChoiceDecision pursuit
    ) {
        return response(
            intent,
            target,
            pursuit,
            null
        );
    }

    private DecisionResponse response(
        ChoiceDecision intent,
        ChoiceDecision target,
        ChoiceDecision pursuit,
        ChoiceDecision allyTarget
    ) {
        return new DecisionResponse(
            intent,
            target,
            pursuit,
            allyTarget,
            10L
        );
    }

    private ChoiceDecision choice(
        String value,
        double confidence
    ) {
        return new ChoiceDecision(
            value,
            confidence,
            Map.of(value, 1.0D)
        );
    }
}
