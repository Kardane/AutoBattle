package dev.kardane.autobattle.tactics;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class RetreatPlannerTest {
    private static final Vec3 SELF =
        new Vec3(0.0D, 80.0D, 0.0D);
    private static final Vec3 CENTER =
        new Vec3(0.0D, 80.0D, 0.0D);

    @Test
    void prefersCandidateFartherFromEnemy() {
        List<Vec3> enemies = List.of(
            new Vec3(-4.0D, 80.0D, 0.0D)
        );

        double towardEnemy =
            RetreatPlanner.geometryScore(
                SELF,
                new Vec3(-6.0D, 80.0D, 0.0D),
                enemies,
                List.of(),
                CENTER,
                20.0D
            );

        double awayFromEnemy =
            RetreatPlanner.geometryScore(
                SELF,
                new Vec3(6.0D, 80.0D, 0.0D),
                enemies,
                List.of(),
                CENTER,
                20.0D
            );

        assertTrue(awayFromEnemy > towardEnemy);
    }

    @Test
    void multipleThreatsPenalizeCrowdedCandidate() {
        List<Vec3> enemies = List.of(
            new Vec3(5.0D, 80.0D, 0.0D),
            new Vec3(5.0D, 80.0D, 2.0D),
            new Vec3(5.0D, 80.0D, -2.0D)
        );

        double crowded =
            RetreatPlanner.geometryScore(
                SELF,
                new Vec3(7.0D, 80.0D, 0.0D),
                enemies,
                List.of(),
                CENTER,
                20.0D
            );

        double open =
            RetreatPlanner.geometryScore(
                SELF,
                new Vec3(-7.0D, 80.0D, 0.0D),
                enemies,
                List.of(),
                CENTER,
                20.0D
            );

        assertTrue(open > crowded);
    }

    @Test
    void arenaEdgeReceivesTrapPenalty() {
        List<Vec3> enemies = List.of(
            new Vec3(0.0D, 80.0D, -4.0D)
        );

        double edge =
            RetreatPlanner.geometryScore(
                SELF,
                new Vec3(19.2D, 80.0D, 0.0D),
                enemies,
                List.of(),
                CENTER,
                20.0D
            );

        double interior =
            RetreatPlanner.geometryScore(
                SELF,
                new Vec3(10.0D, 80.0D, 0.0D),
                enemies,
                List.of(),
                CENTER,
                20.0D
            );

        assertTrue(interior > edge);
    }

    @Test
    void allySupportDoesNotOverruleEnemySafety() {
        List<Vec3> enemies = List.of(
            new Vec3(4.0D, 80.0D, 0.0D)
        );
        List<Vec3> allies = List.of(
            new Vec3(6.0D, 80.0D, 0.0D)
        );

        double nearAllyButThreatened =
            RetreatPlanner.geometryScore(
                SELF,
                new Vec3(6.0D, 80.0D, 0.0D),
                enemies,
                allies,
                CENTER,
                20.0D
            );

        double saferWithoutAlly =
            RetreatPlanner.geometryScore(
                SELF,
                new Vec3(-6.0D, 80.0D, 0.0D),
                enemies,
                allies,
                CENTER,
                20.0D
            );

        assertTrue(
            saferWithoutAlly > nearAllyButThreatened
        );
    }

    @Test
    void smallScoreImprovementDoesNotCauseSwitch() {
        RetreatPlanner.RetreatChoice current =
            new RetreatPlanner.RetreatChoice(
                new Vec3(5.0D, 80.0D, 0.0D),
                20.0D,
                8.0D,
                8.0D,
                5.0D
            );
        RetreatPlanner.RetreatChoice slightlyBetter =
            new RetreatPlanner.RetreatChoice(
                new Vec3(0.0D, 80.0D, 5.0D),
                22.0D,
                9.0D,
                9.0D,
                5.0D
            );

        assertFalse(
            RetreatPlanner.shouldSwitch(
                current,
                slightlyBetter
            )
        );
    }

    @Test
    void dangerousCurrentDestinationSwitchesImmediately() {
        RetreatPlanner.RetreatChoice current =
            new RetreatPlanner.RetreatChoice(
                new Vec3(5.0D, 80.0D, 0.0D),
                30.0D,
                3.0D,
                3.0D,
                5.0D
            );
        RetreatPlanner.RetreatChoice safer =
            new RetreatPlanner.RetreatChoice(
                new Vec3(-5.0D, 80.0D, 0.0D),
                20.0D,
                8.0D,
                8.0D,
                5.0D
            );

        assertTrue(
            RetreatPlanner.shouldSwitch(
                current,
                safer
            )
        );
    }
}
