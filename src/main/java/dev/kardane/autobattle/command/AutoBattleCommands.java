package dev.kardane.autobattle.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kardane.autobattle.match.MatchManager;
import dev.kardane.autobattle.match.PlayerSlot;
import dev.kardane.autobattle.robot.RobotColor;
import dev.kardane.autobattle.robot.RobotFactory;
import dev.kardane.autobattle.robot.RobotZombie;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.UUID;

public final class AutoBattleCommands {
    private AutoBattleCommands() {
    }

    public static void register(
        MatchManager matchManager,
        RobotFactory robotFactory
    ) {
        CommandRegistrationCallback.EVENT.register(
            (dispatcher, registryAccess, environment) ->
                registerTree(dispatcher, matchManager, robotFactory)
        );
    }

    private static void registerTree(
        CommandDispatcher<CommandSourceStack> dispatcher,
        MatchManager matchManager,
        RobotFactory robotFactory
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
                .then(
                    Commands.literal("admin")
                        .requires(source -> source.hasPermission(2))
                        .then(
                            Commands.literal("testrobot")
                                .then(
                                    Commands.argument(
                                        "color",
                                        StringArgumentType.word()
                                    )
                                    .executes(context ->
                                        spawnTestRobot(
                                            context.getSource(),
                                            robotFactory,
                                            StringArgumentType.getString(
                                                context,
                                                "color"
                                            )
                                        )
                                    )
                                )
                        )
                        .then(
                            Commands.literal("testfight")
                                .executes(context ->
                                    spawnTestFight(
                                        context.getSource(),
                                        robotFactory
                                    )
                                )
                        )
                )
        );
    }

    private static int join(
        CommandSourceStack source,
        MatchManager matchManager
    ) throws CommandSyntaxException {
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
    ) throws CommandSyntaxException {
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
    ) throws CommandSyntaxException {
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


    private static int spawnTestFight(
        CommandSourceStack source,
        RobotFactory robotFactory
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = (ServerLevel) player.level();

        Vec3 forward = player.getLookAngle();
        Vec3 horizontal = new Vec3(forward.x, 0.0D, forward.z);

        if (horizontal.lengthSqr() < 1.0E-4D) {
            horizontal = new Vec3(0.0D, 0.0D, 1.0D);
        } else {
            horizontal = horizontal.normalize();
        }

        Vec3 right = new Vec3(
            -horizontal.z,
            0.0D,
            horizontal.x
        );

        Vec3 center = player.position()
            .add(horizontal.scale(7.0D));

        UUID testMatchId = UUID.randomUUID();

        RobotZombie red = robotFactory.spawnRobot(
            level,
            testMatchId,
            UUID.randomUUID(),
            Component.literal("Test RED"),
            RobotColor.RED,
            center.add(right.scale(3.0D)),
            player.getYRot()
        );

        RobotZombie blue = robotFactory.spawnRobot(
            level,
            testMatchId,
            UUID.randomUUID(),
            Component.literal("Test BLUE"),
            RobotColor.BLUE,
            center.add(right.scale(-3.0D)),
            player.getYRot()
        );

        red.setTarget(blue);
        blue.setTarget(red);

        source.sendSuccess(
            () -> Component.literal(
                "Spawned RED vs BLUE test fight."
            ),
            false
        );

        return 1;
    }

    private static int spawnTestRobot(
        CommandSourceStack source,
        RobotFactory robotFactory,
        String rawColor
    ) throws CommandSyntaxException {
        RobotColor color;

        try {
            color = RobotColor.valueOf(
                rawColor.toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException exception) {
            source.sendFailure(
                Component.literal(
                    "Unknown robot color. Use red, blue, green, or yellow."
                )
            );
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        RobotZombie robot = robotFactory.spawnTestRobot(player, color);

        source.sendSuccess(
            () -> Component.literal("Spawned test robot ")
                .append(color.displayName())
                .append(
                    Component.literal(
                        " (entity " + robot.getId() + ")"
                    )
                ),
            false
        );

        return 1;
    }
}
