package dev.kardane.autobattle.ui;

import dev.kardane.autobattle.match.PlayerSlot;
import dev.kardane.autobattle.tactics.RobotController;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class ActionBarUi {
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

            String command = slot.runtime()
                .activeCommand()
                .filter(active -> active.active(currentTick))
                .map(active -> String.format(
                    java.util.Locale.ROOT,
                    "%s %.1fs",
                    active.type().name(),
                    active.remainingTicks(currentTick) / 20.0D
                ))
                .orElseGet(() ->
                    slot.runtime().commandUsed()
                        ? "COMMAND USED"
                        : "COMMAND READY"
                );

            text = "HP "
                + Math.round(hp)
                + "/"
                + Math.round(maxHp)
                + " | "
                + plan
                + " | "
                + command
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
