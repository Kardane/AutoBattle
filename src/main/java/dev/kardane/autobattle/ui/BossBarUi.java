package dev.kardane.autobattle.ui;

import dev.kardane.autobattle.config.LanguageService;
import dev.kardane.autobattle.match.MatchPhase;
import dev.kardane.autobattle.match.MatchSession;
import dev.kardane.autobattle.match.PlayerSlot;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

import java.util.Objects;

public final class BossBarUi {
    private final LanguageService language;
    private final ServerBossEvent event;

    public BossBarUi(LanguageService language) {
        this.language = Objects.requireNonNull(
            language,
            "language"
        );

        this.event = new ServerBossEvent(
            Component.literal(
                language.text("bossbar.phase")
            ),
            BossEvent.BossBarColor.WHITE,
            BossEvent.BossBarOverlay.PROGRESS
        );
    }

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
            .orElse(
                language.text("bossbar.neutral-core")
            );

        String contestedSuffix = match.core()
            .state()
            .contested()
            ? language.text(
                "bossbar.contested-suffix"
            )
            : "";

        event.setName(
            Component.literal(
                language.format(
                    "bossbar.round",
                    "round",
                    match.currentRound(),
                    "total_rounds",
                    totalRounds,
                    "seconds",
                    seconds,
                    "core_owner",
                    coreOwner,
                    "contested_suffix",
                    contestedSuffix
                )
            )
        );

        float progress = durationTicks <= 0
            ? 0.0F
            : (float) remainingTicks
                / (float) durationTicks;

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
                Math.max(
                    1,
                    match.currentRound() + 1
                )
            );

            label = language.format(
                "bossbar.countdown",
                "round",
                nextRound,
                "total_rounds",
                totalRounds,
                "seconds",
                seconds
            );

            progress = countdownTicks <= 0
                ? 0.0F
                : (float) remaining
                    / (float) countdownTicks;
        } else if (phase == MatchPhase.ROUND_REVIEW) {
            label = language.format(
                "bossbar.review",
                "round",
                match.currentRound(),
                "total_rounds",
                totalRounds
            );
        } else if (phase == MatchPhase.DOCTRINE_EDIT) {
            label = language.format(
                "bossbar.doctrine-edit",
                "round",
                match.currentRound(),
                "total_rounds",
                totalRounds
            );
        } else {
            label = language.format(
                "bossbar.phase",
                "phase",
                phase.name()
            );
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
