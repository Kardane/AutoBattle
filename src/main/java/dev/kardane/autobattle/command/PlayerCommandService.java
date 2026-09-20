package dev.kardane.autobattle.command;

import dev.kardane.autobattle.AutoBattleConstants;
import dev.kardane.autobattle.match.MatchPhase;
import dev.kardane.autobattle.match.MatchSession;
import dev.kardane.autobattle.match.PlayerSlot;
import dev.kardane.autobattle.tactics.PlanExecutor;
import dev.kardane.autobattle.tactics.RobotController;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

public final class PlayerCommandService {
    private final PlanExecutor planExecutor;

    public PlayerCommandService(PlanExecutor planExecutor) {
        this.planExecutor = Objects.requireNonNull(
            planExecutor,
            "planExecutor"
        );
    }

    public CommandUseResult use(
        MatchSession match,
        ServerPlayer player,
        PlayerCommandType type,
        long currentTick
    ) {
        if (match.phase() != MatchPhase.ROUND_ACTIVE) {
            return CommandUseResult.INVALID_PHASE;
        }

        PlayerSlot slot = match.player(
            player.getUUID()
        ).orElse(null);

        if (slot == null) {
            return CommandUseResult.NOT_PARTICIPANT;
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
            return CommandUseResult.ROBOT_UNAVAILABLE;
        }

        slot.runtime().activateCommand(
            type,
            currentTick,
            currentTick + AutoBattleConstants.COMMAND_DURATION_TICKS
        );

        controller.requestRedecision();

        return CommandUseResult.SUCCESS;
    }
}
