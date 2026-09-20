package dev.kardane.autobattle.match;

import dev.kardane.autobattle.config.AutoBattleConfig;
import dev.kardane.autobattle.config.SpawnPoint;
import dev.kardane.autobattle.core.CoreController;
import dev.kardane.autobattle.robot.RobotColor;
import dev.kardane.autobattle.robot.RobotFactory;
import dev.kardane.autobattle.robot.RobotRegistry;
import dev.kardane.autobattle.robot.RobotZombie;
import dev.kardane.autobattle.tactics.PlanExecutor;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class MatchManager {
    private final AutoBattleConfig config;
    private final RobotFactory robotFactory;
    private final PlanExecutor planExecutor;
    private final MatchSession session;

    private long serverTick;

    public MatchManager(
        AutoBattleConfig config,
        RobotRegistry robotRegistry,
        RobotFactory robotFactory,
        PlanExecutor planExecutor
    ) {
        this.config = config;
        this.robotFactory = robotFactory;
        this.planExecutor = planExecutor;
        this.session = new MatchSession(
            UUID.randomUUID(),
            robotRegistry,
            new CoreController(config.arena())
        );
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

        if (session.phase() != MatchPhase.ROUND_ACTIVE) {
            return;
        }

        session.core().tick(session, serverTick);

        if (session.roundState().expired(serverTick)) {
            stopPrototypeRound();
        }
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

    public boolean startPrototypeRound(MinecraftServer server) {
        if (session.phase() == MatchPhase.ROUND_ACTIVE) {
            return false;
        }

        long eligiblePlayers = session.players().stream()
            .filter(slot -> !slot.forfeited())
            .count();

        if (eligiblePlayers < 2L) {
            return false;
        }

        ServerLevel level = server.getLevel(
            config.arena().dimension()
        );

        if (level == null) {
            return false;
        }

        planExecutor.clear();
        session.core().reset();

        int nextRound = session.currentRound() <= 0
            ? 1
            : Math.min(
                session.currentRound() + 1,
                config.roundCount()
            );

        session.setCurrentRound(nextRound);

        for (PlayerSlot slot : session.players()) {
            if (slot.forfeited()) {
                continue;
            }

            SpawnPoint spawn = config.arena()
                .robotSpawns()
                .get(slot.slotIndex());

            ServerPlayer owner = server.getPlayerList()
                .getPlayer(slot.playerUuid());

            Component ownerName = owner != null
                ? owner.getName()
                : slot.color().displayName();

            slot.score().resetRound();
            slot.runtime().resetForRound();

            RobotZombie robot = robotFactory.spawnRobot(
                level,
                session.matchId(),
                slot.playerUuid(),
                ownerName,
                slot.color(),
                spawn.position(),
                spawn.yaw()
            );

            planExecutor.register(robot, serverTick);
        }

        session.roundState().start(
            nextRound,
            serverTick,
            config.roundDurationTicks()
        );

        session.setPhase(
            MatchPhase.ROUND_ACTIVE,
            serverTick
        );

        return true;
    }

    public boolean stopPrototypeRound() {
        if (session.phase() != MatchPhase.ROUND_ACTIVE) {
            return false;
        }

        session.roundState().stop();
        planExecutor.clear();

        session.setPhase(
            MatchPhase.ROUND_REVIEW,
            serverTick
        );

        return true;
    }

    public String statusLine() {
        return "phase=" + session.phase()
            + ", round=" + session.currentRound()
            + ", players=" + playerCount()
            + ", ready=" + readyCount()
            + ", minimum=" + config.minimumPlayers()
            + ", coreOwner="
            + session.core().state().ownerUuid()
                .flatMap(session::player)
                .map(slot -> slot.color().name())
                .orElse("none");
    }

    public void handleDisconnect(ServerPlayer player) {
        leave(player);
    }

    private int nextFreeSlot() {
        Set<Integer> used = new HashSet<>();

        for (PlayerSlot slot : session.players()) {
            used.add(slot.slotIndex());
        }

        for (int index = 0;
             index < RobotColor.values().length;
             index++) {
            if (!used.contains(index)) {
                return index;
            }
        }

        return -1;
    }
}
