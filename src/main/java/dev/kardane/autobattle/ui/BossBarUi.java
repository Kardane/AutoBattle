package dev.kardane.autobattle.ui;

import dev.kardane.autobattle.match.MatchSession;
import dev.kardane.autobattle.match.PlayerSlot;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;

public final class BossBarUi {
    private final ServerBossEvent event = new ServerBossEvent(
        Component.literal("AutoBattle"),
        BossEvent.BossBarColor.WHITE,
        BossEvent.BossBarOverlay.PROGRESS
    );

    public void addPlayer(ServerPlayer player) {
        event.addPlayer(player);
    }

    public void removePlayer(ServerPlayer player) {
        event.removePlayer(player);
    }

    public void updateRound(
        MatchSession match,
        long currentTick,
        int totalRounds,
        int durationTicks
    ) {
        long remainingTicks = match.roundState()
            .remainingTicks(currentTick);

        int seconds = (int) Math.ceil(
            remainingTicks / 20.0D
        );

        String coreOwner = match.core()
            .state()
            .ownerUuid()
            .flatMap(match::player)
            .map(PlayerSlot::color)
            .map(Enum::name)
            .orElse("NEUTRAL");

        String contested = match.core()
            .state()
            .contested()
            ? " | CONTESTED"
            : "";

        event.setName(
            Component.literal(
                "ROUND "
                    + match.currentRound()
                    + "/"
                    + totalRounds
                    + " | "
                    + seconds
                    + "s | CORE "
                    + coreOwner
                    + contested
            )
        );

        float progress = durationTicks <= 0
            ? 0.0F
            : (float) remainingTicks / (float) durationTicks;

        event.setProgress(
            Math.max(0.0F, Math.min(1.0F, progress))
        );
    }

    public void clear() {
        event.removeAllPlayers();
    }
}
