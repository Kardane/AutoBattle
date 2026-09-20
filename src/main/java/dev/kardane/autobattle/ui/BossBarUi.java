package dev.kardane.autobattle.ui;

import dev.kardane.autobattle.match.MatchPhase;
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

    public void updateIntermission(
        MatchSession match,
        long currentTick,
        int totalRounds,
        int countdownTicks
    ) {
        MatchPhase phase = match.phase();
        String label;
        float progress = 1.0F;

        if (phase == MatchPhase.COUNTDOWN) {
            long elapsed = Math.max(
                0L,
                currentTick - match.phaseStartedTick()
            );
            long remaining = Math.max(
                0L,
                countdownTicks - elapsed
            );

            int seconds = (int) Math.ceil(
                remaining / 20.0D
            );

            int nextRound = Math.min(
                totalRounds,
                Math.max(1, match.currentRound() + 1)
            );

            label = "ROUND "
                + nextRound
                + "/"
                + totalRounds
                + " | STARTING IN "
                + seconds
                + "s";

            progress = countdownTicks <= 0
                ? 0.0F
                : (float) remaining / (float) countdownTicks;
        } else if (phase == MatchPhase.ROUND_REVIEW) {
            label = "ROUND "
                + match.currentRound()
                + "/"
                + totalRounds
                + " | REVIEW";
        } else if (phase == MatchPhase.DOCTRINE_EDIT) {
            label = "ROUND "
                + match.currentRound()
                + "/"
                + totalRounds
                + " | DOCTRINE EDIT";
        } else {
            label = "AUTO BATTLE | " + phase.name();
        }

        event.setName(Component.literal(label));
        event.setProgress(
            Math.max(0.0F, Math.min(1.0F, progress))
        );
    }

    public void clear() {
        event.removeAllPlayers();
    }
}
