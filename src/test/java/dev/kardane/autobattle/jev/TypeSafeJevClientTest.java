package dev.kardane.autobattle.jev;

import com.google.gson.JsonObject;
import dev.kardane.autobattle.doctrine.Doctrine;
import dev.kardane.autobattle.doctrine.DoctrineNormalizationStatus;
import dev.kardane.autobattle.robot.RobotColor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class TypeSafeJevClientTest {
    private final TypeSafeJevClient client =
        new TypeSafeJevClient(
            "test-key",
            "https://example.invalid",
            "test-model",
            1500
        );

    @Test
    void requestUsesDecomposedQuestionsAndSemanticState() {
        DecisionRequest request = request(
            List.of(
                "ENGAGE_BLUE",
                "CHASE_BLUE",
                "CHASE_GREEN",
                "CAPTURE_CORE",
                "RETREAT"
            )
        );

        JsonObject body = client.buildRequestBody(request);
        JsonObject questions =
            body.getAsJsonObject("questions");

        assertTrue(questions.has("strategic_intent"));
        assertTrue(questions.has("combat_target"));
        assertTrue(questions.has("pursuit_style"));
        assertFalse(questions.has("tactical_plan"));

        JsonObject state = body.getAsJsonObject("state");

        assertFalse(state.has("match_id"));
        assertFalse(state.has("server_tick"));

        JsonObject self = state.getAsJsonObject("self");
        assertFalse(self.has("owner_uuid"));
        assertEquals(
            0.75D,
            self.get("hp_ratio").getAsDouble(),
            1.0E-9D
        );

        JsonObject core = state.getAsJsonObject("core");
        assertEquals(
            "ENEMY",
            core.get("ownership").getAsString()
        );
        assertFalse(core.has("owner_uuid"));

        JsonObject blue = state
            .getAsJsonArray("enemies")
            .get(0)
            .getAsJsonObject();

        assertFalse(blue.has("owner_uuid"));
        assertFalse(
            blue.has("kills_against_self_this_round")
        );
        assertEquals(
            "SEPARATING",
            blue.get("distance_trend").getAsString()
        );
        assertTrue(
            blue.get("within_engage_range")
                .getAsBoolean()
        );
        assertTrue(
            blue.get("within_chase_range")
                .getAsBoolean()
        );

        assertEquals(
            "Capture CORE first.",
            state.getAsJsonArray("doctrine")
                .get(0)
                .getAsString()
        );
    }

    @Test
    void singleCombatTargetIsNotAskedAsChoice() {
        DecisionRequest request = request(
            List.of(
                "ENGAGE_BLUE",
                "CHASE_BLUE",
                "CAPTURE_CORE",
                "RETREAT"
            )
        );

        JsonObject questions = client
            .buildRequestBody(request)
            .getAsJsonObject("questions");

        assertFalse(questions.has("combat_target"));
        assertTrue(questions.has("pursuit_style"));
    }

    private DecisionRequest request(
        List<String> validPlanIds
    ) {
        UUID self = UUID.fromString(
            "00000000-0000-0000-0000-000000000001"
        );
        UUID blue = UUID.fromString(
            "00000000-0000-0000-0000-000000000002"
        );
        UUID green = UUID.fromString(
            "00000000-0000-0000-0000-000000000003"
        );

        Doctrine doctrine = new Doctrine(
            2,
            "원본 1",
            "원본 2",
            "원본 3",
            "Capture CORE first.",
            "Fight weak enemies.",
            "Do not chase without a reason.",
            "hash",
            "normalizer-model",
            DoctrineNormalizationStatus.NORMALIZED,
            null,
            "prompt-v2",
            1,
            120L,
            200
        );

        RobotDecisionSnapshot snapshot =
            new RobotDecisionSnapshot(
                UUID.fromString(
                    "00000000-0000-0000-0000-000000000010"
                ),
                2,
                500L,
                42,
                new RobotSnapshot(
                    self,
                    RobotColor.RED,
                    75.0F,
                    100.0F,
                    3,
                    9,
                    2,
                    "CAPTURE_CORE"
                ),
                new CoreSnapshot(
                    blue,
                    true,
                    6.5D
                ),
                List.of(
                    new EnemySnapshot(
                        blue,
                        RobotColor.BLUE,
                        true,
                        40.0F,
                        100.0F,
                        0.4D,
                        5.0D,
                        DistanceTrend.SEPARATING,
                        true,
                        true,
                        1,
                        7,
                        false
                    ),
                    new EnemySnapshot(
                        green,
                        RobotColor.GREEN,
                        true,
                        90.0F,
                        100.0F,
                        0.9D,
                        12.0D,
                        DistanceTrend.STABLE,
                        false,
                        true,
                        3,
                        1,
                        true
                    )
                ),
                doctrine,
                null
            );

        return new DecisionRequest(
            new DecisionContext(
                snapshot.matchId(),
                snapshot.round(),
                self,
                UUID.fromString(
                    "00000000-0000-0000-0000-000000000020"
                ),
                doctrine.version(),
                1L,
                snapshot.serverTick(),
                DecisionTrigger.INTERVAL
            ),
            snapshot,
            validPlanIds
        );
    }
}
