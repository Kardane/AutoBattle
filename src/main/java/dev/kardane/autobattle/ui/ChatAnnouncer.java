package dev.kardane.autobattle.ui;

import dev.kardane.autobattle.match.MatchSession;
import dev.kardane.autobattle.match.PlayerSlot;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class ChatAnnouncer {
    public void roundStarted(
        MinecraftServer server,
        MatchSession match
    ) {
        broadcastParticipants(
            server,
            match,
            Component.literal(
                "[AutoBattle] Round "
                    + match.currentRound()
                    + " started."
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
            Component.literal(
                "[AutoBattle] Round "
                    + match.currentRound()
                    + " ended."
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
            Component.literal("[AutoBattle] ")
                .append(killer.color().displayName())
                .append(Component.literal(" destroyed "))
                .append(victim.color().displayName())
                .append(Component.literal("."))
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
            Component.literal("[AutoBattle] ")
                .append(owner.color().displayName())
                .append(Component.literal(" captured CORE."))
        );
    }

    private void broadcastParticipants(
        MinecraftServer server,
        MatchSession match,
        Component message
    ) {
        for (PlayerSlot slot : match.players()) {
            ServerPlayer player = server.getPlayerList()
                .getPlayer(slot.playerUuid());

            if (player != null) {
                player.displayClientMessage(message, false);
            }
        }
    }
}
