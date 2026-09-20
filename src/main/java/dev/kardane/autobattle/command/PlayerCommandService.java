package dev.kardane.autobattle.command;

import dev.kardane.autobattle.config.AutoBattleConfig;
import dev.kardane.autobattle.log.MatchLogService;
import dev.kardane.autobattle.match.MatchPhase;
import dev.kardane.autobattle.match.MatchSession;
import dev.kardane.autobattle.match.PlayerSlot;
import dev.kardane.autobattle.tactics.PlanExecutor;
import dev.kardane.autobattle.tactics.RobotController;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

public final class PlayerCommandService {
    private AutoBattleConfig config;
    private final PlanExecutor planExecutor;
    private final MatchLogService matchLogs;

    public PlayerCommandService(
        AutoBattleConfig config,
        PlanExecutor planExecutor,
        MatchLogService matchLogs
    ) {
        this.config = Objects.requireNonNull(config, "config");
        this.planExecutor = Objects.requireNonNull(
            planExecutor,
            "planExecutor"
        );
        this.matchLogs = Objects.requireNonNull(
            matchLogs,
            "matchLogs"
        );
    }

    public void reloadConfig(AutoBattleConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public CommandUseResult use(
        MatchSession match,
        ServerPlayer player,
        PlayerCommandType type,
        long currentTick
    ) {
        PlayerSlot slot = match.player(
            player.getUUID()
        ).orElse(null);

        if (slot == null) {
            return CommandUseResult.NOT_PARTICIPANT;
        }

        if (match.phase() != MatchPhase.ROUND_ACTIVE) {
            return CommandUseResult.INVALID_PHASE;
        }

        if (slot.forfeited()) {
            return CommandUseResult.FORFEITED;
        }

        if (slot.runtime().commandUsed()) {
            return CommandUseResult.ALREADY_USED;
        }

        RobotController controller = planExecutor
            .byOwner(slot.playerUuid())
            .orElse(null);

        if (controller == null || !controller.alive()) {
            return CommandUseResult.ROBOT_DEAD;
        }

        ActiveCommand command = new ActiveCommand(
            type,
            currentTick,
            currentTick + config.commandDurationTicks()
        );

        slot.runtime().activateCommand(command);
        controller.requestRedecision();

        matchLogs.playerCommand(
            match,
            slot,
            type,
            currentTick
        );

        return CommandUseResult.SUCCESS;
    }

    public void tick(
        MatchSession match,
        long currentTick
    ) {
        for (PlayerSlot slot : match.players()) {
            slot.runtime().clearExpiredCommand(currentTick);
        }
    }
}
