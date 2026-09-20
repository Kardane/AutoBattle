package dev.kardane.autobattle.robot;

import dev.kardane.autobattle.config.AutoBattleConfig;
import dev.kardane.autobattle.config.SpawnPoint;
import dev.kardane.autobattle.match.MatchPhase;
import dev.kardane.autobattle.match.MatchSession;
import dev.kardane.autobattle.match.PlayerSlot;
import dev.kardane.autobattle.tactics.PlanExecutor;
import dev.kardane.autobattle.tactics.RobotController;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

public final class RobotRespawnManager {
    private final AutoBattleConfig config;
    private final RobotFactory robotFactory;
    private final PlanExecutor planExecutor;

    public RobotRespawnManager(
        AutoBattleConfig config,
        RobotFactory robotFactory,
        PlanExecutor planExecutor
    ) {
        this.config = Objects.requireNonNull(config, "config");
        this.robotFactory = Objects.requireNonNull(
            robotFactory,
            "robotFactory"
        );
        this.planExecutor = Objects.requireNonNull(
            planExecutor,
            "planExecutor"
        );
    }

    public void schedule(
        RobotController controller,
        long currentTick
    ) {
        controller.runtime().markDead(
            currentTick + config.respawnTicks()
        );
        controller.detachEntity();
    }

    public void tick(
        MinecraftServer server,
        MatchSession match,
        long currentTick
    ) {
        if (match.phase() != MatchPhase.ROUND_ACTIVE) {
            return;
        }

        ServerLevel level = server.getLevel(
            config.arena().dimension()
        );

        if (level == null) {
            return;
        }

        for (RobotController controller :
            match.robots().all()) {
            if (!controller.runtime()
                .readyToRespawn(currentTick)) {
                continue;
            }

            PlayerSlot slot = match.player(
                controller.ownerUuid()
            ).orElse(null);

            if (slot == null || slot.forfeited()) {
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

            RobotZombie robot = robotFactory.spawnRobot(
                level,
                match.matchId(),
                slot.playerUuid(),
                ownerName,
                slot.color(),
                spawn.position(),
                spawn.yaw()
            );

            RobotController attached = planExecutor.register(
                robot,
                currentTick
            );

            attached.requestRedecision();
        }
    }

    public void cancelAll(MatchSession match) {
        for (RobotController controller :
            match.robots().all()) {
            controller.clearPlan();
        }
    }
}
