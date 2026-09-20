package dev.kardane.autobattle.match;

import dev.kardane.autobattle.config.AutoBattleConfig;
import dev.kardane.autobattle.robot.RobotColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class MatchManager {
    private final AutoBattleConfig config;
    private final MatchSession session = new MatchSession(UUID.randomUUID());

    private long serverTick;

    public MatchManager(AutoBattleConfig config) {
        this.config = config;
    }

    public AutoBattleConfig config() {
        return config;
    }

    public MatchSession session() {
        return session;
    }

    public long serverTick() {
        return serverTick;
    }

    public void tick(MinecraftServer server) {
        serverTick++;
    }

    public boolean join(ServerPlayer player) {
        UUID uuid = player.getUUID();

        if (session.phase() != MatchPhase.LOBBY) {
            return false;
        }

        if (session.player(uuid).isPresent()) {
            return true;
        }

        int slotIndex = nextFreeSlot();
        RobotColor[] colors = RobotColor.values();

        if (slotIndex < 0 || slotIndex >= colors.length) {
            return false;
        }

        session.addPlayer(
            new PlayerSlot(uuid, colors[slotIndex], slotIndex)
        );

        return true;
    }

    public boolean leave(ServerPlayer player) {
        UUID uuid = player.getUUID();

        if (session.phase() != MatchPhase.LOBBY) {
            session.player(uuid).ifPresent(PlayerSlot::forfeit);
            return false;
        }

        if (session.player(uuid).isEmpty()) {
            return false;
        }

        session.removePlayer(uuid);
        return true;
    }

    public Optional<Boolean> toggleReady(ServerPlayer player) {
        return session.player(player.getUUID()).map(slot -> {
            if (session.phase() != MatchPhase.LOBBY) {
                return slot.ready();
            }

            boolean next = !slot.ready();
            slot.setReady(next);
            return next;
        });
    }

    public Optional<PlayerSlot> playerSlot(UUID playerUuid) {
        return session.player(playerUuid);
    }

    public int playerCount() {
        return session.players().size();
    }

    public int readyCount() {
        return (int) session.players().stream()
            .filter(PlayerSlot::ready)
            .count();
    }

    public boolean canStart() {
        return playerCount() >= config.minimumPlayers()
            && readyCount() == playerCount();
    }

    public String statusLine() {
        return "phase=" + session.phase()
            + ", players=" + playerCount()
            + ", ready=" + readyCount()
            + ", minimum=" + config.minimumPlayers();
    }

    public void handleDisconnect(ServerPlayer player) {
        leave(player);
    }

    private int nextFreeSlot() {
        Set<Integer> used = new HashSet<>();

        for (PlayerSlot slot : session.players()) {
            used.add(slot.slotIndex());
        }

        for (int index = 0; index < RobotColor.values().length; index++) {
            if (!used.contains(index)) {
                return index;
            }
        }

        return -1;
    }
}
