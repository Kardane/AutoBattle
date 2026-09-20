package dev.kardane.autobattle.ui;

import dev.kardane.autobattle.match.MatchPhase;
import dev.kardane.autobattle.match.PlayerSlot;
import dev.kardane.autobattle.tactics.RobotController;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class ActionBarUi {
    public void updateIntermission(
        ServerPlayer player,
        PlayerSlot slot,
        MatchPhase phase,
        int currentRound,
        int totalRounds
    ) {
        String status = switch (phase) {
            case ROUND_REVIEW -> "ROUND REVIEW";
            case DOCTRINE_EDIT -> "DOCTRINE EDIT";
            case COUNTDOWN -> "NEXT ROUND";
            default -> phase.name();
        };

        player.displayClientMessage(
            Component.literal(
                status
                    + " | ROUND "
                    + currentRound
                    + "/"
                    + totalRounds
                    + " | TOTAL SCORE "
                    + slot.score().totalScore()
            ),
            true
        );
    }

    public void updatePlayer(
        ServerPlayer player,
        PlayerSlot slot,
        RobotController controller,
        long currentTick
    ) {
        String text;

        if (controller == null) {
            text = "ROBOT UNAVAILABLE";
        } else if (controller.alive()) {
            float hp = controller.entity()
                .map(entity -> entity.getHealth())
                .orElse(0.0F);

            float maxHp = controller.entity()
                .map(entity -> entity.getMaxHealth())
                .orElse(100.0F);

            String plan = controller.currentPlan()
                .map(tacticalPlan -> tacticalPlan.externalId())
                .orElse("WAITING");

            String commandState = slot.runtime()
                .activeCommand()
                .map(active -> active.type().name())
                .orElseGet(() ->
                    slot.runtime().commandUsed()
                        ? "USED"
                        : "READY"
                );

            text = "HP "
                + Math.round(hp)
                + "/"
                + Math.round(maxHp)
                + " | "
                + plan
                + " | COMMAND "
                + commandState
                + " | SCORE "
                + slot.score().roundScore();
        } else {
            long remainingTicks = Math.max(
                0L,
                controller.runtime().respawnAtTick()
                    - currentTick
            );

            double remainingSeconds =
                remainingTicks / 20.0D;

            text = String.format(
                java.util.Locale.ROOT,
                "ROBOT DESTROYED | RESPAWN %.1fs | SCORE %d",
                remainingSeconds,
                slot.score().roundScore()
            );
        }

        player.displayClientMessage(
            Component.literal(text),
            true
        );
    }
}
