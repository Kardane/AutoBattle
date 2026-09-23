package dev.kardane.autobattle.robot;

import dev.kardane.autobattle.match.BattleTeam;
import dev.kardane.autobattle.tactics.TacticalPlanType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Objects;

public final class RobotNameplateText {
    public static final int HEALTH_BAR_LENGTH = 20;
    private static final int EMPTY_HEALTH_COLOR = 0x808080;
    private static final int RED_HEALTH_COLOR = 0xFF8C00;
    private static final int BLUE_HEALTH_COLOR = 0x87CEEB;

    private static final int CHASE_BEHAVIOR_COLOR = 0xFFD700;
    private static final int ENGAGE_BEHAVIOR_COLOR = 0xFF5555;
    private static final int CAPTURE_BEHAVIOR_COLOR = 0x55FFFF;
    private static final int DEFEND_BEHAVIOR_COLOR = 0x5555FF;
    private static final int ASSIST_BEHAVIOR_COLOR = 0x55FF55;
    private static final int HOLD_BEHAVIOR_COLOR = 0xAAAAAA;
    private static final int RETREAT_BEHAVIOR_COLOR = 0xAA00AA;

    private RobotNameplateText() {
    }

    public static Component build(
        String targetId,
        Component ownerName,
        BattleTeam team,
        float health,
        float maxHealth,
        String behavior
    ) {
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(ownerName, "ownerName");
        Objects.requireNonNull(team, "team");
        Objects.requireNonNull(behavior, "behavior");

        MutableComponent nameLine = Component.literal(
                "[" + targetId + "] "
            )
            .withStyle(team.robotColor().chatColor())
            .append(ownerName.copy());

        MutableComponent healthLine = Component.empty();
        int filled = filledSegments(health, maxHealth);
        int teamColor = teamHealthColor(team);

        for (int index = 0;
             index < HEALTH_BAR_LENGTH;
             index++) {
            healthLine.append(
                Component.literal("|")
                    .withColor(
                        index < filled
                            ? teamColor
                            : EMPTY_HEALTH_COLOR
                    )
            );
        }

        return Component.empty()
            .append(nameLine)
            .append("\n")
            .append(healthLine)
            .append("\n")
            .append(
                Component.literal(behavior)
                    .withColor(behaviorColor(behavior))
            );
    }

    static int teamHealthColor(BattleTeam team) {
        return team == BattleTeam.RED
            ? RED_HEALTH_COLOR
            : BLUE_HEALTH_COLOR;
    }

    static int behaviorColor(String behavior) {
        Objects.requireNonNull(behavior, "behavior");

        return switch (behavior) {
            case "추격", "CHASE" -> CHASE_BEHAVIOR_COLOR;
            case "공격", "ENGAGE" -> ENGAGE_BEHAVIOR_COLOR;
            case "점령", "CAPTURE" -> CAPTURE_BEHAVIOR_COLOR;
            case "방어", "DEFEND" -> DEFEND_BEHAVIOR_COLOR;
            case "지원", "ASSIST" -> ASSIST_BEHAVIOR_COLOR;
            case "후퇴", "RETREAT" -> RETREAT_BEHAVIOR_COLOR;
            case "대기", "HOLD" -> HOLD_BEHAVIOR_COLOR;
            default -> HOLD_BEHAVIOR_COLOR;
        };
    }

    public static String behaviorLabel(
        TacticalPlanType planType
    ) {
        if (planType == null) {
            return "대기";
        }

        return switch (planType) {
            case CHASE -> "추격";
            case ENGAGE -> "공격";
            case CAPTURE -> "점령";
            case DEFEND -> "방어";
            case ASSIST -> "지원";
            case HOLD -> "대기";
            case RETREAT -> "후퇴";
        };
    }

    static int filledSegments(
        float health,
        float maxHealth
    ) {
        if (!Float.isFinite(health)
            || !Float.isFinite(maxHealth)
            || maxHealth <= 0.0F
            || health <= 0.0F) {
            return 0;
        }

        double ratio = Math.min(
            1.0D,
            Math.max(0.0D, health / maxHealth)
        );

        return Math.min(
            HEALTH_BAR_LENGTH,
            Math.max(
                0,
                (int) Math.ceil(
                    ratio * HEALTH_BAR_LENGTH
                )
            )
        );
    }
}
