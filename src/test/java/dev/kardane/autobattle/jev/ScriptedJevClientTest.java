package dev.kardane.autobattle.jev;

import dev.kardane.autobattle.doctrine.Doctrine;
import dev.kardane.autobattle.doctrine.DoctrineNormalizationStatus;
import dev.kardane.autobattle.match.BattleTeam;
import dev.kardane.autobattle.robot.RobotColor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ScriptedJevClientTest {
    @Test
    void contestedCoreWithEnemyChoosesFight() {
        ScriptedJevClient client = new ScriptedJevClient();

        DecisionResponse response = client.decide(
            request(true)
        ).join();

        assertEquals(
            "FIGHT",
            response.strategicIntent().choice()
        );
    }

    @Test
    void neutralUncontestedCoreStillChoosesControl() {
        ScriptedJevClient client = new ScriptedJevClient();

        DecisionResponse response = client.decide(
            request(false)
        ).join();

        assertEquals(
            "CONTROL_CORE",
            response.strategicIntent().choice()
        );
    }

    private DecisionRequest request(boolean contested) {
        UUID self = UUID.fromString(
            "00000000-0000-0000-0000-000000000001"
        );
        UUID enemy = UUID.fromString(
            "00000000-0000-0000-0000-000000000002"
        );

        Doctrine doctrine = new Doctrine(
            1,
            "a",
            "b",
            "c",
            "a",
            "b",
            "c",
            "hash",
            "none",
            DoctrineNormalizationStatus.FALLBACK_DISABLED,
            null,
            "prompt",
            0,
            0L,
            null
        );

        RobotDecisionSnapshot snapshot =
            new RobotDecisionSnapshot(
                UUID.fromString(
                    "00000000-0000-0000-0000-000000000010"
                ),
                1,
                100L,
                30,
                new RobotSnapshot(
                    self,
                    "R1",
                    BattleTeam.RED,
                    RobotColor.RED,
                    100.0F,
                    100.0F,
                    0,
                    0,
                    1,
                    "CAPTURE_CORE"
                ),
                new TeamContextSnapshot(
                    BattleTeam.RED,
                    0,
                    0,
                    0,
                    1,
                    contested ? 1 : 0,
                    contested ? 1 : 0
                ),
                new CoreSnapshot(
                    null,
                    contested,
                    1.0D
                ),
                List.of(
                    new EnemySnapshot(
                        enemy,
                        "B1",
                        BattleTeam.BLUE,
                        RobotColor.BLUE,
                        true,
                        100.0F,
                        100.0F,
                        1.0D,
                        2.0D,
                        DistanceTrend.STABLE,
                        true,
                        true,
                        1,
                        0,
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
            List.of(
                "ENGAGE_B1",
                "CHASE_B1",
                "CAPTURE_CORE",
                "RETREAT"
            )
        );
    }
}
