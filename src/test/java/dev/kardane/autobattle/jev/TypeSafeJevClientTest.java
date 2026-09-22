package dev.kardane.autobattle.jev;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.kardane.autobattle.doctrine.Doctrine;
import dev.kardane.autobattle.doctrine.DoctrineNormalizationStatus;
import dev.kardane.autobattle.match.BattleTeam;
import dev.kardane.autobattle.robot.RobotColor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

final class TypeSafeJevClientTest {
    private HttpServer server;

    private final TypeSafeJevClient client =
        new TypeSafeJevClient(
            "test-key",
            "https://example.invalid",
            "test-model",
            1500
        );

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void transientFailuresOpenCircuitAndShortCircuitLaterCalls()
        throws Exception {
        AtomicInteger calls = new AtomicInteger();

        startServer(exchange -> {
            calls.incrementAndGet();
            respond(
                exchange,
                503,
                "{\"error\":\"unavailable\"}"
            );
        });

        TypeSafeJevClient resilient =
            localClient(500);

        DecisionRequest request = request(
            List.of(
                "CAPTURE_CORE",
                "RETREAT"
            )
        );

        for (int index = 0; index < 8; index++) {
            assertThrows(
                CompletionException.class,
                () -> resilient.decide(request).join()
            );
        }

        assertEquals(8, calls.get());

        assertThrows(
            CompletionException.class,
            () -> resilient.decide(request).join()
        );

        assertEquals(
            8,
            calls.get(),
            "open circuit must not call upstream"
        );
    }

    @Test
    void ordinaryClientErrorsDoNotOpenCircuit()
        throws Exception {
        AtomicInteger calls = new AtomicInteger();

        startServer(exchange -> {
            calls.incrementAndGet();
            respond(
                exchange,
                400,
                "{\"error\":\"bad request\"}"
            );
        });

        TypeSafeJevClient resilient =
            localClient(500);

        DecisionRequest request = request(
            List.of(
                "CAPTURE_CORE",
                "RETREAT"
            )
        );

        for (int index = 0; index < 10; index++) {
            assertThrows(
                CompletionException.class,
                () -> resilient.decide(request).join()
            );
        }

        assertEquals(
            10,
            calls.get(),
            "HTTP 400 must remain a per-request failure"
        );
    }

    @Test
    void requestUsesTeamAwareDecomposedState() {
        DecisionRequest request = request(
            List.of(
                "ENGAGE_B1",
                "CHASE_B1",
                "CHASE_B2",
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
        assertEquals("R1", self.get("id").getAsString());
        assertEquals("RED", self.get("team").getAsString());
        assertFalse(self.has("owner_uuid"));
        assertEquals(
            0.75D,
            self.get("hp_ratio").getAsDouble(),
            1.0E-9D
        );

        JsonObject teamContext =
            state.getAsJsonObject("team_context");

        assertEquals(
            38,
            teamContext.get("team_score").getAsInt()
        );
        assertEquals(
            41,
            teamContext.get("enemy_team_score").getAsInt()
        );
        assertEquals(
            1,
            teamContext.get("alive_allies").getAsInt()
        );
        assertEquals(
            2,
            teamContext.get("alive_enemies").getAsInt()
        );

        JsonObject core = state.getAsJsonObject("core");
        assertEquals(
            "ENEMY_TEAM",
            core.get("ownership").getAsString()
        );
        assertFalse(core.has("owner_uuid"));

        assertEquals(1, state.getAsJsonArray("allies").size());
        assertEquals(2, state.getAsJsonArray("enemies").size());

        JsonObject ally = state
            .getAsJsonArray("allies")
            .get(0)
            .getAsJsonObject();

        assertEquals("R2", ally.get("id").getAsString());
        assertTrue(ally.has("hp_ratio"));
        assertTrue(ally.has("distance"));
        assertFalse(ally.has("within_engage_range"));
        assertFalse(ally.has("within_chase_range"));
        assertFalse(ally.has("attacking_self"));

        JsonObject blue = state
            .getAsJsonArray("enemies")
            .get(0)
            .getAsJsonObject();

        assertEquals("B1", blue.get("id").getAsString());
        assertEquals("BLUE", blue.get("team").getAsString());
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
    void singleLegalEnemyTargetIsNotAskedAsChoice() {
        DecisionRequest request = request(
            List.of(
                "ENGAGE_B1",
                "CHASE_B1",
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

    private TypeSafeJevClient localClient(int timeoutMs) {
        return new TypeSafeJevClient(
            "test-key",
            "http://127.0.0.1:"
                + server.getAddress().getPort(),
            "test-model",
            timeoutMs
        );
    }

    private void startServer(
        com.sun.net.httpserver.HttpHandler handler
    ) throws IOException {
        server = HttpServer.create(
            new InetSocketAddress(
                "127.0.0.1",
                0
            ),
            0
        );
        server.createContext(
            "/v1/systemone",
            handler
        );
        server.start();
    }

    private void respond(
        HttpExchange exchange,
        int status,
        String body
    ) throws IOException {
        byte[] bytes = body.getBytes(
            StandardCharsets.UTF_8
        );

        exchange.sendResponseHeaders(
            status,
            bytes.length
        );

        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private DecisionRequest request(
        List<String> validPlanIds
    ) {
        UUID self = UUID.fromString(
            "00000000-0000-0000-0000-000000000001"
        );
        UUID blueOne = UUID.fromString(
            "00000000-0000-0000-0000-000000000002"
        );
        UUID blueTwo = UUID.fromString(
            "00000000-0000-0000-0000-000000000003"
        );
        UUID ally = UUID.fromString(
            "00000000-0000-0000-0000-000000000004"
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
            "prompt-v3",
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
                    "R1",
                    BattleTeam.RED,
                    RobotColor.RED,
                    75.0F,
                    100.0F,
                    3,
                    9,
                    2,
                    "CAPTURE_CORE"
                ),
                new TeamContextSnapshot(
                    BattleTeam.RED,
                    38,
                    41,
                    1,
                    2,
                    1,
                    1
                ),
                new CoreSnapshot(
                    BattleTeam.BLUE,
                    true,
                    6.5D
                ),
                List.of(
                    new EnemySnapshot(
                        blueOne,
                        "B1",
                        BattleTeam.BLUE,
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
                        blueTwo,
                        "B2",
                        BattleTeam.BLUE,
                        RobotColor.BLUE,
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
                    ),
                    new EnemySnapshot(
                        ally,
                        "R2",
                        BattleTeam.RED,
                        RobotColor.RED,
                        true,
                        65.0F,
                        100.0F,
                        0.65D,
                        4.0D,
                        DistanceTrend.APPROACHING,
                        true,
                        true,
                        4,
                        2,
                        false
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
                DecisionTrigger.INTERVAL,
                1
            ),
            snapshot,
            validPlanIds
        );
    }
}
