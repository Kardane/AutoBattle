package dev.kardane.autobattle.jev;

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
    void unavailableTargetIsNotSilentlyReplaced() {
        DecisionComposition result = composer.compose(
            response(
                choice("FIGHT", 0.9D),
                choice("BLUE", 0.9D),
                choice("CHASE", 0.9D)
            ),
            List.of(
                "ENGAGE_RED",
                "CHASE_RED",
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
                choice("BLUE", 0.9D),
                choice("ENGAGE", 0.9D)
            ),
            List.of(
                "CHASE_BLUE",
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
                choice("BLUE", 0.9D),
                choice("CHASE", 0.2D)
            ),
            List.of(
                "ENGAGE_BLUE",
                "CHASE_BLUE",
                "RETREAT"
            ),
            null,
            0.35D
        );

        assertEquals("ENGAGE_BLUE", result.planId());
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

    private DecisionResponse response(
        ChoiceDecision intent,
        ChoiceDecision target,
        ChoiceDecision pursuit
    ) {
        return new DecisionResponse(
            intent,
            target,
            pursuit,
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
