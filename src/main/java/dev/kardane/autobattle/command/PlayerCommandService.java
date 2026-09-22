package dev.kardane.autobattle.command;

import dev.kardane.autobattle.config.AutoBattleConfig;
import dev.kardane.autobattle.config.LanguageService;
import dev.kardane.autobattle.log.MatchLogService;
import dev.kardane.autobattle.jev.DecisionTrigger;
import dev.kardane.autobattle.match.MatchPhase;
import dev.kardane.autobattle.match.MatchSession;
import dev.kardane.autobattle.match.PlayerSlot;
import dev.kardane.autobattle.tactics.PlanExecutor;
import dev.kardane.autobattle.tactics.RobotController;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Objects;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PlayerCommandService {
    private AutoBattleConfig config;
    private final PlanExecutor planExecutor;
    private final MatchLogService matchLogs;
    private final LanguageService language;
    private final Map<UUID, List<ItemStack>> savedInventories = new HashMap<>();

    public PlayerCommandService(
        AutoBattleConfig config,
        PlanExecutor planExecutor,
        MatchLogService matchLogs,
        LanguageService language
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
        this.language = Objects.requireNonNull(language, "language");
    }

    public void equipItems(ServerPlayer player) {
        var inventory = player.getInventory();
        savedInventories.computeIfAbsent(player.getUUID(), ignored -> {
            List<ItemStack> original = new ArrayList<>();
            for (int index = 0; index < inventory.getContainerSize(); index++) {
                original.add(inventory.getItem(index).copy());
            }
            return original;
        });
        for (int index = 0; index < inventory.getContainerSize(); index++) {
            inventory.setItem(index, ItemStack.EMPTY);
        }
        inventory.setItem(0, commandItem(Items.BLAZE_ROD, "items.command-attack"));
        inventory.setItem(1, commandItem(Items.HEART_OF_THE_SEA, "items.command-capture"));
        inventory.setItem(2, commandItem(Items.TOTEM_OF_UNDYING, "items.command-survive"));
        player.containerMenu.broadcastChanges();
    }

    public void restoreItems(ServerPlayer player) {
        List<ItemStack> original = savedInventories.remove(player.getUUID());
        if (original == null) {
            return;
        }
        var inventory = player.getInventory();
        for (int index = 0; index < original.size(); index++) {
            inventory.setItem(index, original.get(index));
        }
        player.containerMenu.broadcastChanges();
    }

    public boolean useItem(MatchSession match, ServerPlayer player, ItemStack item, long tick) {
        if (!savedInventories.containsKey(player.getUUID())) {
            return false;
        }
        PlayerCommandType type = item.is(Items.BLAZE_ROD) ? PlayerCommandType.ATTACK
            : item.is(Items.HEART_OF_THE_SEA) ? PlayerCommandType.CAPTURE
            : item.is(Items.TOTEM_OF_UNDYING) ? PlayerCommandType.SURVIVE : null;
        if (type == null) {
            return false;
        }
        CommandUseResult result = use(match, player, type, tick);
        String key = result == CommandUseResult.SUCCESS
            ? "commands.player-command-success" : "commands.player-command-rejected";
        player.sendSystemMessage(Component.literal(language.format(
            key,
            "type", type.name(),
            "seconds", String.format(java.util.Locale.ROOT, "%.1f", config.commandDurationTicks() / 20.0D),
            "result", result.name()
        )));
        return true;
    }

    private ItemStack commandItem(net.minecraft.world.item.Item item, String key) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, language.component(key));
        return stack;
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
        controller.requestRedecision(
            DecisionTrigger.PLAYER_COMMAND
        );

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
