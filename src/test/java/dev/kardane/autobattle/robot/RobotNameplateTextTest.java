package dev.kardane.autobattle.robot;

import dev.kardane.autobattle.match.BattleTeam;
import dev.kardane.autobattle.tactics.TacticalPlanType;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RobotNameplateTextTest {
    @Test
    void buildsThreeLinesWithTeamHealthBar() {
        Component text = RobotNameplateText.build(
            "R1",
            Component.literal("Alice"),
            BattleTeam.RED,
            50.0F,
            100.0F,
            "공격"
        );

        assertEquals(
            "[R1] Alice\n||||||||||||||||||||\n공격",
            text.getString()
        );
        assertEquals(
            10,
            RobotNameplateText.filledSegments(
                50.0F,
                100.0F
            )
        );
    }

    @Test
    void mapsTacticalPlansToKoreanLabels() {
        assertEquals(
            "추격",
            RobotNameplateText.behaviorLabel(
                TacticalPlanType.CHASE
            )
        );
        assertEquals(
            "공격",
            RobotNameplateText.behaviorLabel(
                TacticalPlanType.ENGAGE
            )
        );
        assertEquals(
            "대기",
            RobotNameplateText.behaviorLabel(null)
        );
    }

    @Test
    void healthBarClampsToTwentySegments() {
        assertEquals(
            RobotNameplateText.HEALTH_BAR_LENGTH,
            RobotNameplateText.filledSegments(
                150.0F,
                100.0F
            )
        );
        assertEquals(
            0,
            RobotNameplateText.filledSegments(
                0.0F,
                100.0F
            )
        );
        assertTrue(
            RobotNameplateText.filledSegments(
                1.0F,
                100.0F
            ) > 0
        );
    }
}
