package dev.kardane.autobattle.music;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.kardane.autobattle.AutoBattleMod;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.List;

public final class BgmCommands {
    private static boolean registered;

    private BgmCommands() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;

        CommandRegistrationCallback.EVENT.register(
            (dispatcher, registryAccess, environment) ->
                dispatcher.register(
                    Commands.literal("autobattle")
                        .then(
                            Commands.literal("music")
                                .requires(source ->
                                    source.hasPermission(2)
                                )
                                .then(
                                    Commands.literal("list")
                                        .executes(context -> list(
                                            context.getSource()
                                        ))
                                )
                                .then(playCommand())
                                .then(stopCommand())
                                .then(nextCommand())
                                .then(
                                    Commands.literal("global")
                                        .then(
                                            Commands.literal("play")
                                                .then(
                                                    Commands.argument(
                                                        "music",
                                                        StringArgumentType
                                                            .word()
                                                    ).executes(context ->
                                                        globalPlay(
                                                            context.getSource(),
                                                            StringArgumentType
                                                                .getString(
                                                                    context,
                                                                    "music"
                                                                )
                                                        )
                                                    )
                                                )
                                        )
                                        .then(
                                            Commands.literal("stop")
                                                .executes(context -> {
                                                    BgmRuntime.service(
                                                        context.getSource()
                                                            .getServer()
                                                    ).stopGlobal();
                                                    context.getSource()
                                                        .sendSuccess(
                                                            () -> Component
                                                                .literal(
                                                                    "BGM stopped"
                                                                ),
                                                            true
                                                        );
                                                    return 1;
                                                })
                                        )
                                        .then(
                                            Commands.literal("next")
                                                .executes(context -> {
                                                    BgmRuntime.service(
                                                        context.getSource()
                                                            .getServer()
                                                    ).nextGlobal();
                                                    context.getSource()
                                                        .sendSuccess(
                                                            () -> Component
                                                                .literal(
                                                                    "BGM advanced"
                                                                ),
                                                            true
                                                        );
                                                    return 1;
                                                })
                                        )
                                )
                        )
                )
        );
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<
        CommandSourceStack
    > playCommand() {
        return Commands.literal("play")
            .then(
                Commands.argument(
                    "music",
                    StringArgumentType.word()
                )
                .executes(context -> play(
                    context.getSource(),
                    StringArgumentType.getString(context, "music"),
                    List.of(context.getSource().getPlayerOrException())
                ))
                .then(
                    Commands.argument(
                        "players",
                        EntityArgument.players()
                    ).executes(context -> play(
                        context.getSource(),
                        StringArgumentType.getString(
                            context,
                            "music"
                        ),
                        EntityArgument.getPlayers(context, "players")
                    ))
                )
            );
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<
        CommandSourceStack
    > stopCommand() {
        return Commands.literal("stop")
            .executes(context -> stop(
                context.getSource(),
                List.of(context.getSource().getPlayerOrException())
            ))
            .then(
                Commands.argument(
                    "players",
                    EntityArgument.players()
                ).executes(context -> stop(
                    context.getSource(),
                    EntityArgument.getPlayers(context, "players")
                ))
            );
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<
        CommandSourceStack
    > nextCommand() {
        return Commands.literal("next")
            .executes(context -> next(
                context.getSource(),
                List.of(context.getSource().getPlayerOrException())
            ))
            .then(
                Commands.argument(
                    "players",
                    EntityArgument.players()
                ).executes(context -> next(
                    context.getSource(),
                    EntityArgument.getPlayers(context, "players")
                ))
            );
    }

    private static int list(CommandSourceStack source) {
        BgmCatalog catalog = BgmRuntime.service(
            source.getServer()
        ).catalog();
        source.sendSuccess(
            () -> Component.literal(
                "BGM tracks: " + catalog.tracks().size()
            ),
            false
        );
        catalog.tracks().values().forEach(track ->
            source.sendSuccess(
                () -> Component.literal(
                    track.id()
                        + " | "
                        + track.name()
                        + " | "
                        + String.format(
                            java.util.Locale.ROOT,
                            "%.2fs",
                            track.seconds()
                        )
                        + " | "
                        + track.path()
                ),
                false
            )
        );
        catalog.playlists().forEach((id, playlist) ->
            source.sendSuccess(
                () -> Component.literal(
                    "playlist "
                        + id
                        + " ("
                        + playlist.tracks().size()
                        + " tracks, repeat="
                        + playlist.repeat()
                        + ")"
                ),
                false
            )
        );
        return catalog.tracks().size();
    }

    private static int play(
        CommandSourceStack source,
        String id,
        Collection<ServerPlayer> players
    ) {
        try {
            BgmRuntime.service(source.getServer()).play(id, players);
            source.sendSuccess(
                () -> Component.literal("BGM started: " + id),
                true
            );
            return 1;
        } catch (RuntimeException error) {
            return failure(source, error);
        }
    }

    private static int globalPlay(
        CommandSourceStack source,
        String id
    ) {
        try {
            BgmRuntime.service(source.getServer()).playGlobal(id);
            source.sendSuccess(
                () -> Component.literal(
                    "Global BGM started: " + id
                ),
                true
            );
            return 1;
        } catch (RuntimeException error) {
            return failure(source, error);
        }
    }

    private static int stop(
        CommandSourceStack source,
        Collection<ServerPlayer> players
    ) {
        BgmRuntime.service(source.getServer()).stop(players);
        source.sendSuccess(
            () -> Component.literal("BGM stopped"),
            true
        );
        return 1;
    }

    private static int next(
        CommandSourceStack source,
        Collection<ServerPlayer> players
    ) {
        BgmRuntime.service(source.getServer()).next(players);
        source.sendSuccess(
            () -> Component.literal("BGM advanced"),
            true
        );
        return 1;
    }

    private static int failure(
        CommandSourceStack source,
        RuntimeException error
    ) {
        AutoBattleMod.LOGGER.warn(
            "BGM command failed: {}",
            error.getMessage()
        );
        source.sendFailure(Component.literal(
            "BGM failed: " + error.getMessage()
        ));
        return 0;
    }
}
