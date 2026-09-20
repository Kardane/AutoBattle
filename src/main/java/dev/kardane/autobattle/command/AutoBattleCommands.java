package dev.kardane.autobattle.command;

import com.mojang.brigadier.CommandDispatcher;
import dev.kardane.autobattle.match.MatchManager;
import dev.kardane.autobattle.match.PlayerSlot;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class AutoBattleCommands {
    private AutoBattleCommands() {
    }

    public static void register(MatchManager matchManager) {
        CommandRegistrationCallback.EVENT.register(
            (dispatcher, registryAccess, environment) ->
                registerTree(dispatcher, matchManager)
        );
    }

    private static void registerTree(
        CommandDispatcher<CommandSourceStack> dispatcher,
        MatchManager matchManager
    ) {
        dispatcher.register(
            Commands.literal("autobattle")
                .then(
                    Commands.literal("join")
                        .executes(context ->
                            join(context.getSource(), matchManager)
                        )
                )
                .then(
                    Commands.literal("leave")
                        .executes(context ->
                            leave(context.getSource(), matchManager)
                        )
                )
                .then(
                    Commands.literal("ready")
                        .executes(context ->
                            ready(context.getSource(), matchManager)
                        )
                )
                .then(
                    Commands.literal("status")
                        .executes(context ->
                            status(context.getSource(), matchManager)
                        )
                )
        );
    }

    private static int join(
        CommandSourceStack source,
        MatchManager matchManager
    ) throws Exception {
        ServerPlayer player = source.getPlayerOrException();

        if (!matchManager.join(player)) {
            source.sendFailure(
                Component.literal(
                    "Unable to join AutoBattle. The lobby may be closed or full."
                )
            );
            return 0;
        }

        PlayerSlot slot = matchManager
            .playerSlot(player.getUUID())
            .orElseThrow();

        source.sendSuccess(
            () -> Component.literal("Joined AutoBattle as ")
                .append(slot.color().displayName()),
            false
        );

        return 1;
    }

    private static int leave(
        CommandSourceStack source,
        MatchManager matchManager
    ) throws Exception {
        ServerPlayer player = source.getPlayerOrException();

        if (!matchManager.leave(player)) {
            source.sendFailure(
                Component.literal(
                    "You are not in the lobby, or the match has already started."
                )
            );
            return 0;
        }

        source.sendSuccess(
            () -> Component.literal("Left the AutoBattle lobby."),
            false
        );

        return 1;
    }

    private static int ready(
        CommandSourceStack source,
        MatchManager matchManager
    ) throws Exception {
        ServerPlayer player = source.getPlayerOrException();

        var ready = matchManager.toggleReady(player);

        if (ready.isEmpty()) {
            source.sendFailure(
                Component.literal("Join the AutoBattle lobby first.")
            );
            return 0;
        }

        source.sendSuccess(
            () -> Component.literal(
                ready.get()
                    ? "You are ready."
                    : "You are no longer ready."
            ),
            false
        );

        if (matchManager.canStart()) {
            source.sendSuccess(
                () -> Component.literal(
                    "All players are ready. Doctrine setup can begin."
                ),
                false
            );
        }

        return 1;
    }

    private static int status(
        CommandSourceStack source,
        MatchManager matchManager
    ) {
        source.sendSuccess(
            () -> Component.literal(matchManager.statusLine()),
            false
        );
        return 1;
    }
}
