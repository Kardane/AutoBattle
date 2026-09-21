package dev.kardane.autobattle.ui;

import dev.kardane.autobattle.config.LanguageService;
import dev.kardane.autobattle.match.BattleTeam;
import dev.kardane.autobattle.match.MatchSession;
import dev.kardane.autobattle.match.PlayerSlot;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Comparator;
import java.util.List;
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
        broadcastAll(
            server,
            language.component(
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
        broadcastAll(
            server,
            language.component(
                "chat.round-ended",
                "round",
                match.currentRound()
            )
        );
    }

    public void reviewCompleted(
        MinecraftServer server,
        MatchSession match,
        ServerPlayer player
    ) {
        broadcastAll(
            server,
            language.component(
                "chat.review-completed",
                "player",
                player.getName().getString(),
                "round",
                match.currentRound()
            )
        );
    }

    public void doctrineEditCompleted(
        MinecraftServer server,
        MatchSession match,
        ServerPlayer player
    ) {
        broadcastAll(
            server,
            language.component(
                "chat.doctrine-edit-completed",
                "player",
                player.getName().getString(),
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
        broadcastAll(
            server,
            language.component(
                "chat.robot-killed",
                "killer_color",
                killer.team().name(),
                "killer_id",
                killer.targetId(),
                "victim_color",
                victim.team().name(),
                "victim_id",
                victim.targetId()
            )
        );
    }

    public void coreCaptured(
        MinecraftServer server,
        MatchSession match,
        BattleTeam owner
    ) {
        broadcastAll(
            server,
            language.component(
                "chat.core-captured",
                "color",
                owner.name(),
                "team",
                owner.name()
            )
        );
    }

    public void finalStandings(
        MinecraftServer server,
        MatchSession match
    ) {
        broadcastAll(
            server,
            language.component(
                "chat.final-standings-title"
            )
        );

        List<PlayerSlot> standings = match.players()
            .stream()
            .sorted(
                Comparator
                    .comparingInt(
                        (PlayerSlot slot) ->
                            slot.score().totalScore()
                    )
                    .reversed()
                    .thenComparingInt(
                        PlayerSlot::slotIndex
                    )
            )
            .toList();

        for (int index = 0;
             index < standings.size();
             index++) {
            PlayerSlot slot = standings.get(index);
            Component entry = language.component(
                "chat.final-standing",
                "rank",
                index + 1,
                "color",
                slot.color().name(),
                "score",
                slot.score().totalScore()
            ).copy().withStyle(slot.color().chatColor());

            broadcastAll(server, entry);
        }
    }

    private void broadcastAll(
        MinecraftServer server,
        Component component
    ) {
        for (ServerPlayer player :
            server.getPlayerList().getPlayers()) {
            player.displayClientMessage(
                component,
                false
            );
        }
    }
}
