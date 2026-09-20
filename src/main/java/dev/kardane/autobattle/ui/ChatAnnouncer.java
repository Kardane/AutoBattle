package dev.kardane.autobattle.ui;

import dev.kardane.autobattle.config.LanguageService;
import dev.kardane.autobattle.match.MatchSession;
import dev.kardane.autobattle.match.PlayerSlot;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

public final class ChatAnnouncer {
    private final LanguageService language;

    public ChatAnnouncer(LanguageService language) {
        this.language = Objects.requireNonNull(
            language,
            "language"
        );
    }

    public void roundStarted(
        MinecraftServer server,
        MatchSession match
    ) {
        broadcastParticipants(
            server,
            match,
            language.format(
                "chat.round-started",
                "round",
                match.currentRound()
            )
        );
    }

    public void roundEnded(
        MinecraftServer server,
        MatchSession match
    ) {
        broadcastParticipants(
            server,
            match,
            language.format(
                "chat.round-ended",
                "round",
                match.currentRound()
            )
        );
    }

    public void robotKilled(
        MinecraftServer server,
        MatchSession match,
        PlayerSlot killer,
        PlayerSlot victim
    ) {
        broadcastParticipants(
            server,
            match,
            language.format(
                "chat.robot-killed",
                "killer_color",
                killer.color().name(),
                "victim_color",
                victim.color().name()
            )
        );
    }

    public void coreCaptured(
        MinecraftServer server,
        MatchSession match,
        PlayerSlot owner
    ) {
        broadcastParticipants(
            server,
            match,
            language.format(
                "chat.core-captured",
                "color",
                owner.color().name()
            )
        );
    }

    private void broadcastParticipants(
        MinecraftServer server,
        MatchSession match,
        String message
    ) {
        Component component =
            Component.literal(message);

        for (PlayerSlot slot : match.players()) {
            ServerPlayer player = server.getPlayerList()
                .getPlayer(slot.playerUuid());

            if (player != null) {
                player.displayClientMessage(
                    component,
                    false
                );
            }
        }
    }
}
