package dev.kardane.autobattle.ui;

import dev.kardane.autobattle.match.MatchSession;
import dev.kardane.autobattle.match.PlayerSlot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public final class GameSoundService {
    public void roundStarted(
        MinecraftServer server,
        MatchSession match
    ) {
        play(
            server,
            match,
            SoundEvents.PLAYER_LEVELUP,
            0.8F,
            1.15F
        );
    }

    public void roundEnded(
        MinecraftServer server,
        MatchSession match
    ) {
        play(
            server,
            match,
            SoundEvents.UI_TOAST_IN,
            0.7F,
            1.0F
        );
    }

    public void coreCaptured(
        MinecraftServer server,
        MatchSession match
    ) {
        play(
            server,
            match,
            SoundEvents.BEACON_ACTIVATE,
            0.8F,
            1.15F
        );
    }

    public void robotKilled(
        MinecraftServer server,
        MatchSession match
    ) {
        play(
            server,
            match,
            SoundEvents.ANVIL_LAND,
            0.45F,
            1.35F
        );
    }

    public void matchFinished(
        MinecraftServer server,
        MatchSession match
    ) {
        play(
            server,
            match,
            SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
            0.9F,
            1.0F
        );
    }

    private void play(
        MinecraftServer server,
        MatchSession match,
        SoundEvent sound,
        float volume,
        float pitch
    ) {
        for (PlayerSlot slot : match.players()) {
            if (slot.forfeited()) {
                continue;
            }

            ServerPlayer player = server.getPlayerList()
                .getPlayer(slot.playerUuid());

            if (player == null) {
                continue;
            }

            player.playNotifySound(
                sound,
                SoundSource.PLAYERS,
                volume,
                pitch
            );
        }
    }
}
