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
        int filledColor = team == BattleTeam.RED
            ? RED_HEALTH_COLOR
            : BLUE_HEALTH_COLOR;

        for (int index = 0;
             index < HEALTH_BAR_LENGTH;
             index++) {
            healthLine.append(
                Component.literal("|")
                    .withColor(
                        index < filled
                            ? filledColor
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
                    .withStyle(team.robotColor().chatColor())
            );
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
