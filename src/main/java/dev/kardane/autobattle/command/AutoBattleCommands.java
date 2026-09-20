package dev.kardane.autobattle.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kardane.autobattle.AutoBattleConstants;
import dev.kardane.autobattle.doctrine.DoctrineEditResult;
import dev.kardane.autobattle.doctrine.DoctrineService;
import dev.kardane.autobattle.match.MatchManager;
import dev.kardane.autobattle.match.PlayerSlot;
import dev.kardane.autobattle.robot.RobotColor;
import dev.kardane.autobattle.robot.RobotFactory;
import dev.kardane.autobattle.robot.RobotZombie;
import dev.kardane.autobattle.tactics.PlanExecutor;
import dev.kardane.autobattle.tactics.TacticalPlan;
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
        RobotFactory robotFactory,
        PlanExecutor planExecutor,
        DoctrineService doctrineService,
        PlayerCommandService commandService
    ) {
        CommandRegistrationCallback.EVENT.register(
            (dispatcher, registryAccess, environment) ->
                registerTree(
                    dispatcher,
                    matchManager,
                    robotFactory,
                    planExecutor,
                    doctrineService,
                    commandService
                )
        );
    }

    private static void registerTree(
        CommandDispatcher<CommandSourceStack> dispatcher,
        MatchManager matchManager,
        RobotFactory robotFactory,
        PlanExecutor planExecutor,
        DoctrineService doctrineService,
        PlayerCommandService commandService
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
                    Commands.literal("doctrine")
                        .then(
                            Commands.literal("submit")
                                .then(
                                    Commands.argument(
                                        "line1",
                                        StringArgumentType.string()
                                    )
                                    .then(
                                        Commands.argument(
                                            "line2",
                                            StringArgumentType.string()
                                        )
                                        .then(
                                            Commands.argument(
                                                "line3",
                                                StringArgumentType.string()
                                            )
                                            .executes(context ->
                                                submitDoctrine(
                                                    context.getSource(),
                                                    matchManager,
                                                    doctrineService,
                                                    StringArgumentType.getString(
                                                        context,
                                                        "line1"
                                                    ),
                                                    StringArgumentType.getString(
                                                        context,
                                                        "line2"
                                                    ),
                                                    StringArgumentType.getString(
                                                        context,
                                                        "line3"
                                                    )
                                                )
                                            )
                                        )
                                    )
                                )
                        )
                        .then(
                            Commands.literal("replace")
                                .then(
                                    Commands.argument(
                                        "line",
                                        IntegerArgumentType.integer(1, 3)
                                    )
                                    .then(
                                        Commands.argument(
                                            "text",
                                            StringArgumentType.greedyString()
                                        )
                                        .executes(context ->
                                            replaceDoctrineLine(
                                                context.getSource(),
                                                matchManager,
                                                doctrineService,
                                                IntegerArgumentType.getInteger(
                                                    context,
                                                    "line"
                                                ),
                                                StringArgumentType.getString(
                                                    context,
                                                    "text"
                                                )
                                            )
                                        )
                                    )
                                )
                        )
                )
                .then(
                    Commands.literal("command")
                        .then(
                            Commands.literal("attack")
                                .executes(context ->
                                    usePlayerCommand(
                                        context.getSource(),
                                        matchManager,
                                        commandService,
                                        PlayerCommandType.ATTACK
                                    )
                                )
                        )
                        .then(
                            Commands.literal("capture")
                                .executes(context ->
                                    usePlayerCommand(
                                        context.getSource(),
                                        matchManager,
                                        commandService,
                                        PlayerCommandType.CAPTURE
                                    )
                                )
                        )
                        .then(
                            Commands.literal("survive")
                                .executes(context ->
                                    usePlayerCommand(
                                        context.getSource(),
                                        matchManager,
                                        commandService,
                                        PlayerCommandType.SURVIVE
                                    )
                                )
                        )
                )
                .then(
                    Commands.literal("admin")
                        .requires(source -> source.hasPermission(2))
                        .then(
                            Commands.literal("startround")
                                .executes(context ->
                                    startPrototypeRound(
                                        context.getSource(),
                                        matchManager
                                    )
                                )
                        )
                        .then(
                            Commands.literal("stopround")
                                .executes(context ->
                                    stopPrototypeRound(
                                        context.getSource(),
                                        matchManager
                                    )
                                )
                        )
                        .then(
                            Commands.literal("debug")
                                .then(
                                    Commands.argument(
                                        "color",
                                        StringArgumentType.word()
                                    )
                                    .executes(context ->
                                        debugRobot(
                                            context.getSource(),
                                            matchManager,
                                            planExecutor,
                                            StringArgumentType.getString(
                                                context,
                                                "color"
                                            )
                                        )
                                    )
                                )
                        )
                        .then(
                            Commands.literal("plan")
                                .then(
                                    Commands.argument(
                                        "color",
                                        StringArgumentType.word()
                                    )
                                    .then(
                                        Commands.argument(
                                            "plan",
                                            StringArgumentType.word()
                                        )
                                        .executes(context ->
                                            assignTestPlan(
                                                context.getSource(),
                                                matchManager,
                                                planExecutor,
                                                StringArgumentType.getString(
                                                    context,
                                                    "color"
                                                ),
                                                StringArgumentType.getString(
                                                    context,
                                                    "plan"
                                                ),
                                                null
                                            )
                                        )
                                        .then(
                                            Commands.argument(
                                                "targetColor",
                                                StringArgumentType.word()
                                            )
                                            .executes(context ->
                                                assignTestPlan(
                                                    context.getSource(),
                                                    matchManager,
                                                    planExecutor,
                                                    StringArgumentType.getString(
                                                        context,
                                                        "color"
                                                    ),
                                                    StringArgumentType.getString(
                                                        context,
                                                        "plan"
                                                    ),
                                                    StringArgumentType.getString(
                                                        context,
                                                        "targetColor"
                                                    )
                                                )
                                            )
                                        )
                                    )
                                )
                        )
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
                                            matchManager,
                                            robotFactory,
                                            planExecutor,
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
                                        matchManager,
                                        robotFactory,
                                        planExecutor
                                    )
                                )
                        )
                )
        );
    }




    private static int debugRobot(
        CommandSourceStack source,
        MatchManager matchManager,
        PlanExecutor planExecutor,
        String rawColor
    ) {
        RobotColor color;

        try {
            color = RobotColor.valueOf(
                rawColor.toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException exception) {
            source.sendFailure(
                Component.literal("Unknown robot color.")
            );
            return 0;
        }

        PlayerSlot slot = matchManager.session()
            .players()
            .stream()
            .filter(candidate -> candidate.color() == color)
            .findFirst()
            .orElse(null);

        if (slot == null) {
            source.sendFailure(
                Component.literal(
                    "No participant owns " + color.name() + "."
                )
            );
            return 0;
        }

        var score = slot.score();
        var controller = planExecutor
            .byOwner(slot.playerUuid())
            .orElse(null);

        String robotState;

        if (controller == null) {
            robotState = "unspawned";
        } else if (controller.alive()) {
            float hp = controller.entity()
                .map(RobotZombie::getHealth)
                .orElse(0.0F);

            String plan = controller.currentPlan()
                .map(TacticalPlan::externalId)
                .orElse("none");

            robotState = "alive hp="
                + String.format(Locale.ROOT, "%.1f", hp)
                + " plan=" + plan;
        } else {
            long remaining = Math.max(
                0L,
                controller.runtime().respawnAtTick()
                    - matchManager.serverTick()
            );

            robotState = "dead respawnTicks=" + remaining;
        }

        String message = color.name()
            + " total=" + score.totalScore()
            + " round=" + score.roundScore()
            + " K/D/A="
            + score.roundKills() + "/"
            + score.roundDeaths() + "/"
            + score.roundAssists()
            + " coreCaptures=" + score.roundCoreCaptures()
            + " coreTicks=" + score.roundCoreHoldTicks()
            + " damage="
            + String.format(
                Locale.ROOT,
                "%.1f/%.1f",
                score.roundDamageDealt(),
                score.roundDamageTaken()
            )
            + " " + robotState;

        source.sendSuccess(
            () -> Component.literal(message),
            false
        );

        return 1;
    }

    private static int assignTestPlan(
        CommandSourceStack source,
        MatchManager matchManager,
        PlanExecutor planExecutor,
        String rawColor,
        String rawPlan,
        String rawTargetColor
    ) {
        RobotColor color;

        try {
            color = RobotColor.valueOf(
                rawColor.toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException exception) {
            source.sendFailure(
                Component.literal(
                    "Unknown robot color."
                )
            );
            return 0;
        }

        PlayerSlot slot = matchManager.session()
            .players()
            .stream()
            .filter(candidate -> candidate.color() == color)
            .findFirst()
            .orElse(null);

        if (slot == null) {
            source.sendFailure(
                Component.literal(
                    "No participant owns " + color.name() + "."
                )
            );
            return 0;
        }

        var controller = planExecutor
            .byOwner(slot.playerUuid())
            .orElse(null);

        if (controller == null || !controller.alive()) {
            source.sendFailure(
                Component.literal(
                    color.name() + " robot is not alive."
                )
            );
            return 0;
        }

        long currentTick = matchManager.serverTick();
        long lockTicks = AutoBattleConstants.DECISION_LOCK_TICKS;
        String planName = rawPlan.toUpperCase(Locale.ROOT);

        TacticalPlan plan;

        switch (planName) {
            case "ENGAGE", "CHASE" -> {
                if (rawTargetColor == null) {
                    source.sendFailure(
                        Component.literal(
                            planName + " requires targetColor."
                        )
                    );
                    return 0;
                }

                RobotColor targetColor;

                try {
                    targetColor = RobotColor.valueOf(
                        rawTargetColor.toUpperCase(Locale.ROOT)
                    );
                } catch (IllegalArgumentException exception) {
                    source.sendFailure(
                        Component.literal(
                            "Unknown target robot color."
                        )
                    );
                    return 0;
                }

                PlayerSlot targetSlot = matchManager.session()
                    .players()
                    .stream()
                    .filter(candidate ->
                        candidate.color() == targetColor
                    )
                    .findFirst()
                    .orElse(null);

                if (targetSlot == null
                    || targetSlot.playerUuid()
                        .equals(slot.playerUuid())) {
                    source.sendFailure(
                        Component.literal(
                            "Target must be another participant."
                        )
                    );
                    return 0;
                }

                plan = planName.equals("ENGAGE")
                    ? TacticalPlan.engage(
                        targetSlot.playerUuid(),
                        "ENGAGE_" + targetColor.name(),
                        currentTick,
                        lockTicks
                    )
                    : TacticalPlan.chase(
                        targetSlot.playerUuid(),
                        "CHASE_" + targetColor.name(),
                        currentTick,
                        lockTicks
                    );
            }

            case "CAPTURE" -> plan = TacticalPlan.capture(
                coreCenter(matchManager),
                currentTick,
                lockTicks
            );

            case "DEFEND" -> plan = TacticalPlan.defend(
                coreCenter(matchManager),
                currentTick,
                lockTicks
            );

            case "RETREAT" -> plan = TacticalPlan.retreat(
                currentTick,
                lockTicks
            );

            case "REPOSITION" -> {
                var nodes = matchManager.config()
                    .arena()
                    .repositionNodes();

                if (nodes.isEmpty()) {
                    source.sendFailure(
                        Component.literal(
                            "Arena has no reposition nodes."
                        )
                    );
                    return 0;
                }

                var node = nodes.get(
                    slot.slotIndex() % nodes.size()
                );

                plan = TacticalPlan.reposition(
                    new Vec3(
                        node.getX() + 0.5D,
                        node.getY(),
                        node.getZ() + 0.5D
                    ),
                    currentTick,
                    lockTicks
                );
            }

            default -> {
                source.sendFailure(
                    Component.literal(
                        "Plan must be engage, chase, capture, defend, retreat, or reposition."
                    )
                );
                return 0;
            }
        }

        if (!planExecutor.assignPlan(
            controller,
            plan,
            currentTick
        )) {
            source.sendFailure(
                Component.literal(
                    "Plan change rejected by decision lock."
                )
            );
            return 0;
        }

        source.sendSuccess(
            () -> Component.literal(
                color.name() + " plan = " + plan.externalId()
            ),
            false
        );

        return 1;
    }

    private static Vec3 coreCenter(
        MatchManager matchManager
    ) {
        var pos = matchManager.session()
            .core()
            .position();

        return new Vec3(
            pos.getX() + 0.5D,
            pos.getY() + 0.5D,
            pos.getZ() + 0.5D
        );
    }

    private static int startPrototypeRound(
        CommandSourceStack source,
        MatchManager matchManager
    ) {
        if (!matchManager.startPrototypeRound(source.getServer())) {
            source.sendFailure(
                Component.literal(
                    "Unable to start round. Need at least four active participants, submitted doctrines, and a valid arena dimension."
                )
            );
            return 0;
        }

        source.sendSuccess(
            () -> Component.literal(
                "Started AutoBattle prototype round "
                    + matchManager.session().currentRound()
                    + "."
            ),
            true
        );

        return 1;
    }

    private static int stopPrototypeRound(
        CommandSourceStack source,
        MatchManager matchManager
    ) {
        if (!matchManager.stopPrototypeRound()) {
            source.sendFailure(
                Component.literal(
                    "No active AutoBattle round to stop."
                )
            );
            return 0;
        }

        source.sendSuccess(
            () -> Component.literal(
                "Stopped AutoBattle prototype round."
            ),
            true
        );

        return 1;
    }



    private static int usePlayerCommand(
        CommandSourceStack source,
        MatchManager matchManager,
        PlayerCommandService commandService,
        PlayerCommandType type
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        CommandUseResult result = commandService.use(
            matchManager.session(),
            player,
            type,
            matchManager.serverTick()
        );

        if (result != CommandUseResult.SUCCESS) {
            source.sendFailure(
                Component.literal(
                    "Command rejected: " + result.name()
                )
            );
            return 0;
        }

        source.sendSuccess(
            () -> Component.literal(
                "Command " + type.name() + " activated for 10 seconds."
            ),
            false
        );

        return 1;
    }

    private static int submitDoctrine(
        CommandSourceStack source,
        MatchManager matchManager,
        DoctrineService doctrineService,
        String line1,
        String line2,
        String line3
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        DoctrineEditResult result = doctrineService.submitInitial(
            matchManager.session(),
            player,
            line1,
            line2,
            line3
        );

        if (!result.success()) {
            source.sendFailure(
                Component.literal(
                    "Doctrine rejected: " + result.error().name()
                )
            );
            return 0;
        }

        source.sendSuccess(
            () -> Component.literal(
                "Doctrine v"
                    + result.doctrine().version()
                    + " saved."
            ),
            false
        );

        if (matchManager.beginCountdownIfDoctrinesReady()) {
            source.sendSuccess(
                () -> Component.literal(
                    "All doctrines submitted. Match is ready to start."
                ),
                true
            );
        }

        return 1;
    }

    private static int replaceDoctrineLine(
        CommandSourceStack source,
        MatchManager matchManager,
        DoctrineService doctrineService,
        int oneBasedLine,
        String text
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        DoctrineEditResult result = doctrineService.replaceLine(
            matchManager.session(),
            player,
            oneBasedLine - 1,
            text
        );

        if (!result.success()) {
            source.sendFailure(
                Component.literal(
                    "Doctrine edit rejected: "
                        + result.error().name()
                )
            );
            return 0;
        }

        source.sendSuccess(
            () -> Component.literal(
                "Doctrine line "
                    + oneBasedLine
                    + " updated. Version "
                    + result.doctrine().version()
                    + "."
            ),
            false
        );

        return 1;
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

        if (matchManager.beginDoctrineSetupIfReady()) {
            source.sendSuccess(
                () -> Component.literal(
                    "All players are ready. Doctrine setup started."
                ),
                true
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
        MatchManager matchManager,
        RobotFactory robotFactory,
        PlanExecutor planExecutor
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

        long currentTick = matchManager.serverTick();

        planExecutor.register(red, currentTick);
        planExecutor.register(blue, currentTick);
        long lockTicks = AutoBattleConstants.DECISION_LOCK_TICKS;

        planExecutor.assignPlan(
            red,
            TacticalPlan.engage(
                blue.ownerUuid(),
                "ENGAGE_BLUE",
                currentTick,
                lockTicks
            ),
            currentTick
        );

        planExecutor.assignPlan(
            blue,
            TacticalPlan.engage(
                red.ownerUuid(),
                "ENGAGE_RED",
                currentTick,
                lockTicks
            ),
            currentTick
        );

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
        MatchManager matchManager,
        RobotFactory robotFactory,
        PlanExecutor planExecutor,
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
        planExecutor.register(robot, matchManager.serverTick());

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
