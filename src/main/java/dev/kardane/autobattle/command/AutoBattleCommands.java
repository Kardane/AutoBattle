package dev.kardane.autobattle.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kardane.autobattle.config.ConfigReloadResult;
import dev.kardane.autobattle.config.ConfigReloadService;
import dev.kardane.autobattle.config.LanguageService;
import dev.kardane.autobattle.doctrine.DoctrineEditResult;
import dev.kardane.autobattle.doctrine.DoctrineService;
import dev.kardane.autobattle.match.BattleTeam;
import dev.kardane.autobattle.match.MatchManager;
import dev.kardane.autobattle.match.PlayerSlot;
import dev.kardane.autobattle.review.RoundReviewService;
import dev.kardane.autobattle.review.RoundReviewSummary;
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
    private static LanguageService language;

    private AutoBattleCommands() {
    }

    public static void register(
        MatchManager matchManager,
        RobotFactory robotFactory,
        PlanExecutor planExecutor,
        DoctrineService doctrineService,
        PlayerCommandService commandService,
        RoundReviewService reviewService,
        ConfigReloadService configReloadService,
        LanguageService languageService
    ) {
        language = java.util.Objects.requireNonNull(
            languageService,
            "languageService"
        );

        CommandRegistrationCallback.EVENT.register(
            (dispatcher, registryAccess, environment) ->
                registerTree(
                    dispatcher,
                    matchManager,
                    robotFactory,
                    planExecutor,
                    doctrineService,
                    commandService,
                    reviewService,
                    configReloadService
                )
        );
    }

    private static void registerTree(
        CommandDispatcher<CommandSourceStack> dispatcher,
        MatchManager matchManager,
        RobotFactory robotFactory,
        PlanExecutor planExecutor,
        DoctrineService doctrineService,
        PlayerCommandService commandService,
        RoundReviewService reviewService,
        ConfigReloadService configReloadService
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
                    Commands.literal("review")
                        .executes(context ->
                            review(
                                context.getSource(),
                                matchManager,
                                reviewService
                            )
                        )
                        .then(
                            Commands.literal("ready")
                                .executes(context ->
                                    markReviewReady(
                                        context.getSource(),
                                        matchManager
                                    )
                                )
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
                            Commands.literal("keep")
                                .executes(context ->
                                    keepDoctrine(
                                        context.getSource(),
                                        matchManager,
                                        doctrineService
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
                        .then(
                            Commands.literal("view")
                                .requires(source -> source.hasPermission(1))
                                .then(
                                    Commands.argument(
                                        "player",
                                        StringArgumentType.word()
                                    )
                                    .executes(context ->
                                        viewDoctrine(
                                            context.getSource(),
                                            matchManager,
                                            StringArgumentType.getString(
                                                context,
                                                "player"
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
                            Commands.literal("reload")
                                .executes(context ->
                                    reloadConfig(
                                        context.getSource(),
                                        configReloadService
                                    )
                                )
                        )
                        .then(
                            Commands.literal("start")
                                .executes(context ->
                                    startMatch(
                                        context.getSource(),
                                        matchManager
                                    )
                                )
                        )
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
                            Commands.literal("end")
                                .executes(context ->
                                    endMatch(
                                        context.getSource(),
                                        matchManager
                                    )
                                )
                        )
                        .then(
                            Commands.literal("debug")
                                .then(
                                    Commands.argument(
                                        "robotId",
                                        StringArgumentType.word()
                                    )
                                    .executes(context ->
                                        debugRobot(
                                            context.getSource(),
                                            matchManager,
                                            planExecutor,
                                            StringArgumentType.getString(
                                                context,
                                                "robotId"
                                            )
                                        )
                                    )
                                )
                        )
                        .then(
                            Commands.literal("plan")
                                .then(
                                    Commands.argument(
                                        "robotId",
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
                                                    "robotId"
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
                                                "targetId",
                                                StringArgumentType.word()
                                            )
                                            .executes(context ->
                                                assignTestPlan(
                                                    context.getSource(),
                                                    matchManager,
                                                    planExecutor,
                                                    StringArgumentType.getString(
                                                        context,
                                                        "robotId"
                                                    ),
                                                    StringArgumentType.getString(
                                                        context,
                                                        "plan"
                                                    ),
                                                    StringArgumentType.getString(
                                                        context,
                                                        "targetId"
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
                                        "team",
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
                                                "team"
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




    private static Component message(
        String key,
        Object... placeholders
    ) {
        return language.component(
            key,
            placeholders
        );
    }

    private static int debugRobot(
        CommandSourceStack source,
        MatchManager matchManager,
        PlanExecutor planExecutor,
        String rawTargetId
    ) {
        PlayerSlot slot = findSlotByTargetId(
            matchManager,
            rawTargetId
        );

        if (slot == null) {
            source.sendFailure(
                message(
                    "commands.admin.debug-no-owner",
                    "color",
                    rawTargetId.toUpperCase(Locale.ROOT)
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
            robotState = language.text(
                "commands.admin.debug-state-unspawned"
            );
        } else if (controller.alive()) {
            float hp = controller.entity()
                .map(RobotZombie::getHealth)
                .orElse(0.0F);

            String plan = controller.currentPlan()
                .map(TacticalPlan::externalId)
                .orElse("none");

            robotState = language.format(
                "commands.admin.debug-state-alive",
                "hp",
                String.format(Locale.ROOT, "%.1f", hp),
                "plan",
                plan
            );
        } else {
            long remaining = Math.max(
                0L,
                controller.runtime().respawnAtTick()
                    - matchManager.serverTick()
            );

            robotState = language.format(
                "commands.admin.debug-state-dead",
                "ticks",
                remaining
            );
        }

        Component status = message(
            "commands.admin.debug-status",
            "color",
            slot.targetId(),
            "total",
            score.totalScore(),
            "round",
            score.roundScore(),
            "kills",
            score.roundKills(),
            "deaths",
            score.roundDeaths(),
            "assists",
            score.roundAssists(),
            "core_captures",
            score.roundCoreCaptures(),
            "core_ticks",
            score.roundCoreHoldTicks(),
            "damage_dealt",
            String.format(
                Locale.ROOT,
                "%.1f/%.1f",
                score.roundDamageDealt(),
                score.roundDamageTaken()
            ),
            "state",
            robotState
        );

        source.sendSuccess(
            () -> status,
            false
        );

        return 1;
    }

    private static int assignTestPlan(
        CommandSourceStack source,
        MatchManager matchManager,
        PlanExecutor planExecutor,
        String rawTargetId,
        String rawPlan,
        String rawEnemyId
    ) {
        PlayerSlot slot = findSlotByTargetId(
            matchManager,
            rawTargetId
        );

        if (slot == null) {
            source.sendFailure(
                message("commands.admin.debug-unknown-color")
            );
            return 0;
        }

        var controller = planExecutor
            .byOwner(slot.playerUuid())
            .orElse(null);

        if (controller == null || !controller.alive()) {
            source.sendFailure(
                message(
                    "commands.admin.debug-robot-not-alive",
                    "color",
                    slot.targetId()
                )
            );
            return 0;
        }

        long currentTick = matchManager.serverTick();
        long lockTicks = matchManager.config().decisionLockTicks();
        String planName = rawPlan.toUpperCase(Locale.ROOT);

        TacticalPlan plan;

        switch (planName) {
            case "ENGAGE", "CHASE" -> {
                if (rawEnemyId == null) {
                    source.sendFailure(
                        message(
                            "commands.admin.debug-plan-requires-target",
                            "plan",
                            planName
                        )
                    );
                    return 0;
                }

                PlayerSlot targetSlot = findSlotByTargetId(
                    matchManager,
                    rawEnemyId
                );

                if (targetSlot == null
                    || targetSlot.playerUuid()
                        .equals(slot.playerUuid())
                    || targetSlot.team() == slot.team()) {
                    source.sendFailure(
                        message(
                            "commands.admin.debug-target-invalid"
                        )
                    );
                    return 0;
                }

                String externalId =
                    planName + "_" + targetSlot.targetId();

                plan = planName.equals("ENGAGE")
                    ? TacticalPlan.engage(
                        targetSlot.playerUuid(),
                        externalId,
                        currentTick,
                        lockTicks
                    )
                    : TacticalPlan.chase(
                        targetSlot.playerUuid(),
                        externalId,
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

            case "HOLD", "HOLD_POSITION" -> plan =
                TacticalPlan.hold(
                    currentTick,
                    lockTicks
                );

            case "ASSIST" -> {
                if (rawEnemyId == null) {
                    source.sendFailure(
                        message(
                            "commands.admin.debug-plan-requires-target",
                            "plan",
                            planName
                        )
                    );
                    return 0;
                }

                PlayerSlot allySlot = findSlotByTargetId(
                    matchManager,
                    rawEnemyId
                );

                if (allySlot == null
                    || allySlot.playerUuid()
                        .equals(slot.playerUuid())
                    || allySlot.team() != slot.team()) {
                    source.sendFailure(
                        message(
                            "commands.admin.debug-target-invalid"
                        )
                    );
                    return 0;
                }

                plan = TacticalPlan.assist(
                    allySlot.playerUuid(),
                    "ASSIST_" + allySlot.targetId(),
                    currentTick,
                    lockTicks
                );
            }

            default -> {
                source.sendFailure(
                    message("commands.admin.debug-plan-invalid")
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
                message("commands.admin.debug-plan-locked")
            );
            return 0;
        }

        source.sendSuccess(
            () -> message(
                "commands.admin.debug-plan-assigned",
                "color",
                slot.targetId(),
                "plan",
                plan.externalId()
            ),
            false
        );

        return 1;
    }

    private static PlayerSlot findSlotByTargetId(
        MatchManager matchManager,
        String rawTargetId
    ) {
        if (rawTargetId == null) {
            return null;
        }

        return matchManager.session()
            .players()
            .stream()
            .filter(slot ->
                slot.targetId().equalsIgnoreCase(
                    rawTargetId.trim()
                )
            )
            .findFirst()
            .orElse(null);
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

    private static int reloadConfig(
        CommandSourceStack source,
        ConfigReloadService configReloadService
    ) {
        ConfigReloadResult result =
            configReloadService.reload(source.getServer());

        if (!result.success()) {
            source.sendFailure(
                message(
                    "commands.reload-failure",
                    "message",
                    result.message()
                )
            );
            return 0;
        }

        source.sendSuccess(
            () -> message(
                "commands.reload-success",
                "message",
                result.message()
            ),
            true
        );

        return 1;
    }

    private static int startMatch(
        CommandSourceStack source,
        MatchManager matchManager
    ) {
        if (matchManager.session().phase()
            != dev.kardane.autobattle.match.MatchPhase.LOBBY) {
            source.sendFailure(
                message("commands.start-invalid-phase")
            );
            return 0;
        }

        if (!matchManager.beginDoctrineSetupIfReady(
            source.getServer()
        )) {
            source.sendFailure(
                message(
                    matchManager.teamsBalanced()
                        ? "commands.start-failed"
                        : "commands.start-unbalanced"
                )
            );
            return 0;
        }

        source.sendSuccess(
            () -> message(
                "commands.doctrine-setup-started"
            ),
            true
        );

        return 1;
    }

    private static int startPrototypeRound(
        CommandSourceStack source,
        MatchManager matchManager
    ) {
        if (matchManager.session().phase()
            == dev.kardane.autobattle.match.MatchPhase.LOBBY) {
            if (!matchManager.beginDoctrineSetupIfReady(
                source.getServer()
            )) {
                source.sendFailure(
                    message(
                        matchManager.teamsBalanced()
                            ? "commands.start-failed"
                            : "commands.start-unbalanced"
                    )
                );
                return 0;
            }

            source.sendSuccess(
                () -> message(
                    "commands.doctrine-setup-started"
                ),
                true
            );
            return 1;
        }

        if (!matchManager.startPrototypeRound(source.getServer())) {
            source.sendFailure(
                message("commands.start-failed")
            );
            return 0;
        }

        source.sendSuccess(
            () -> message(
                "commands.start-success",
                "round",
                matchManager.session().currentRound()
            ),
            true
        );

        return 1;
    }

    private static int stopPrototypeRound(
        CommandSourceStack source,
        MatchManager matchManager
    ) {
        if (!matchManager.stopPrototypeRound(source.getServer())) {
            source.sendFailure(
                message("commands.stop-failed")
            );
            return 0;
        }

        source.sendSuccess(
            () -> message("commands.stop-success"),
            true
        );

        return 1;
    }

    private static int endMatch(
        CommandSourceStack source,
        MatchManager matchManager
    ) {
        if (!matchManager.endMatch(source.getServer())) {
            source.sendFailure(
                message("commands.end-failed")
            );
            return 0;
        }

        source.sendSuccess(
            () -> message("commands.end-success"),
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
                message(
                    "commands.player-command-rejected",
                    "result",
                    result.name()
                )
            );
            return 0;
        }

        String commandSeconds = String.format(
            Locale.ROOT,
            "%.1f",
            matchManager.config()
                .commandDurationTicks()
                / 20.0D
        );

        source.sendSuccess(
            () -> message(
                "commands.player-command-success",
                "type",
                type.name(),
                "seconds",
                commandSeconds
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

        var future = doctrineService.submitInitialAsync(
            source.getServer(),
            matchManager.session(),
            player,
            line1,
            line2,
            line3
        );

        if (future.isDone()) {
            DoctrineEditResult immediate =
                future.getNow(null);

            if (immediate != null
                && !immediate.success()) {
                source.sendFailure(
                    message(
                        "commands.doctrine-rejected",
                        "error",
                        immediate.error().name()
                    )
                );
                return 0;
            }
        }

        future.thenAccept(result -> {
            if (!result.success()) {
                source.sendFailure(
                    message(
                        "commands.doctrine-rejected",
                        "error",
                        result.error().name()
                    )
                );
                return;
            }

            source.sendSuccess(
                () -> message(
                    "commands.doctrine-saved",
                    "version",
                    result.doctrine().version()
                ),
                false
            );

            if (matchManager.beginCountdownIfDoctrinesReady()) {
                source.sendSuccess(
                    () -> message(
                        "commands.doctrines-ready"
                    ),
                    true
                );
            }
        });

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

        var future = doctrineService.replaceLineAsync(
            source.getServer(),
            matchManager.session(),
            player,
            oneBasedLine - 1,
            text
        );

        if (future.isDone()) {
            DoctrineEditResult immediate =
                future.getNow(null);

            if (immediate != null
                && !immediate.success()) {
                source.sendFailure(
                    message(
                        "commands.doctrine-edit-rejected",
                        "error",
                        immediate.error().name()
                    )
                );
                return 0;
            }
        }

        future.thenAccept(result -> {
            if (!result.success()) {
                source.sendFailure(
                    message(
                        "commands.doctrine-edit-rejected",
                        "error",
                        result.error().name()
                    )
                );
                return;
            }

            source.sendSuccess(
                () -> message(
                    "commands.doctrine-line-updated",
                    "line",
                    oneBasedLine,
                    "version",
                    result.doctrine().version()
                ),
                false
            );

            matchManager.markDoctrineEditDone(player);
        });

        return 1;
    }

    private static int viewDoctrine(
        CommandSourceStack source,
        MatchManager matchManager,
        String playerName
    ) {
        ServerPlayer target = source.getServer()
            .getPlayerList()
            .getPlayerByName(playerName);

        if (target == null) {
            source.sendFailure(
                message(
                    "commands.doctrine-view-player-not-found",
                    "player",
                    playerName
                )
            );
            return 0;
        }

        PlayerSlot slot = matchManager.playerSlot(target.getUUID())
            .orElse(null);

        if (slot == null || slot.forfeited()) {
            source.sendFailure(
                message(
                    "commands.doctrine-view-not-participant",
                    "player",
                    target.getName().getString()
                )
            );
            return 0;
        }

        var doctrine = slot.doctrine().orElse(null);

        if (doctrine == null) {
            source.sendFailure(
                message(
                    "commands.doctrine-view-unavailable",
                    "player",
                    target.getName().getString()
                )
            );
            return 0;
        }

        source.sendSuccess(
            () -> message(
                "commands.doctrine-view-title",
                "player",
                target.getName().getString(),
                "color",
                slot.color().name(),
                "version",
                doctrine.version()
            ),
            false
        );

        for (int index = 0; index < doctrine.lines().size(); index++) {
            int line = index + 1;
            String text = doctrine.line(index);

            source.sendSuccess(
                () -> message(
                    "commands.doctrine-view-line",
                    "line",
                    line,
                    "text",
                    text
                ),
                false
            );
        }

        return 1;
    }

    private static int join(
        CommandSourceStack source,
        MatchManager matchManager
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        if (!matchManager.join(player)) {
            source.sendFailure(
                message("commands.join-failed")
            );
            return 0;
        }

        PlayerSlot slot = matchManager
            .playerSlot(player.getUUID())
            .orElseThrow();

        source.sendSuccess(
            () -> message(
                "commands.joined-team",
                "team",
                slot.team().name(),
                "id",
                slot.targetId()
            ),
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
                message("commands.leave-failed")
            );
            return 0;
        }

        source.sendSuccess(
            () -> message("commands.left"),
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
                message("commands.join-first")
            );
            return 0;
        }

        source.sendSuccess(
            () -> message("commands.auto-ready"),
            false
        );

        return 1;
    }



    private static int markReviewReady(
        CommandSourceStack source,
        MatchManager matchManager
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        if (!matchManager.markReviewReady(player)) {
            source.sendFailure(
                message(
                    "commands.review-ready-invalid"
                )
            );
            return 0;
        }

        source.sendSuccess(
            () -> message("commands.review-ready"),
            false
        );

        return 1;
    }

    private static int keepDoctrine(
        CommandSourceStack source,
        MatchManager matchManager,
        DoctrineService doctrineService
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        if (doctrineService.normalizationPending(
            player.getUUID()
        )) {
            source.sendFailure(
                message(
                    "commands.doctrine-keep-invalid"
                )
            );
            return 0;
        }

        if (!matchManager.markDoctrineEditDone(player)) {
            source.sendFailure(
                message(
                    "commands.doctrine-keep-invalid"
                )
            );
            return 0;
        }

        source.sendSuccess(
            () -> message("commands.doctrine-kept"),
            false
        );

        return 1;
    }

    private static int review(
        CommandSourceStack source,
        MatchManager matchManager,
        RoundReviewService reviewService
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        if (matchManager.session()
            .player(player.getUUID())
            .isEmpty()) {
            source.sendFailure(
                message(
                    "commands.review-not-participant"
                )
            );
            return 0;
        }

        RoundReviewSummary summary = reviewService.build(
            matchManager.session(),
            player.getUUID()
        );

        source.sendSuccess(
            () -> message(
                "commands.review-summary",
                "round",
                summary.round(),
                "score",
                summary.roundScore(),
                "kills",
                summary.kills(),
                "deaths",
                summary.deaths(),
                "assists",
                summary.assists()
            ),
            false
        );

        source.sendSuccess(
            () -> message(
                "commands.review-metrics",
                "core_captures",
                summary.coreCaptures(),
                "core_hold_seconds",
                String.format(
                    Locale.ROOT,
                    "%.1f",
                    summary.coreHoldTicks() / 20.0D
                ),
                "damage_dealt",
                String.format(
                    Locale.ROOT,
                    "%.1f",
                    summary.damageDealt()
                ),
                "damage_taken",
                String.format(
                    Locale.ROOT,
                    "%.1f",
                    summary.damageTaken()
                )
            ),
            false
        );

        if (!summary.planPercentages().isEmpty()) {
            String plans = summary.planPercentages()
                .entrySet()
                .stream()
                .map(entry ->
                    entry.getKey()
                        + " "
                        + String.format(
                            Locale.ROOT,
                            "%.0f%%",
                            entry.getValue()
                        )
                )
                .collect(
                    java.util.stream.Collectors.joining(" | ")
                );

            source.sendSuccess(
                () -> message(
                    "commands.review-plans",
                    "plans",
                    plans
                ),
                false
            );
        }

        int index = 1;

        for (var critical : summary.criticalDecisions()) {
            var decision = critical.decision();

            source.sendSuccess(
                () -> message(
                    "commands.review-critical",
                    "importance",
                    critical.importanceScore(),
                    "tick",
                    decision.serverTick(),
                    "plan",
                    String.valueOf(
                        decision.selectedPlanId()
                    ),
                    "confidence",
                    String.format(
                        Locale.ROOT,
                        "%.2f",
                        decision.confidence()
                    ),
                    "result",
                    decision.applyResult().name()
                ),
                false
            );

            index++;
        }

        return 1;
    }

    private static int status(
        CommandSourceStack source,
        MatchManager matchManager
    ) {
        String coreOwner = matchManager.session()
            .core()
            .state()
            .ownerTeam()
            .map(Enum::name)
            .orElse("none");

        source.sendSuccess(
            () -> message(
                "commands.status",
                "phase",
                matchManager.session().phase().name(),
                "round",
                matchManager.session().currentRound(),
                "players",
                matchManager.playerCount(),
                "red",
                matchManager.teamCount(BattleTeam.RED),
                "blue",
                matchManager.teamCount(BattleTeam.BLUE),
                "balanced",
                matchManager.teamsBalanced(),
                "maximum",
                matchManager.config().maxTeamSize(),
                "core_owner",
                coreOwner
            ),
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
            message("commands.test.fight-red-name"),
            BattleTeam.RED,
            "R1",
            center.add(right.scale(3.0D)),
            player.getYRot()
        );

        RobotZombie blue = robotFactory.spawnRobot(
            level,
            testMatchId,
            UUID.randomUUID(),
            message("commands.test.fight-blue-name"),
            BattleTeam.BLUE,
            "B1",
            center.add(right.scale(-3.0D)),
            player.getYRot()
        );

        long currentTick = matchManager.serverTick();

        planExecutor.register(red, currentTick);
        planExecutor.register(blue, currentTick);
        long lockTicks = matchManager.config().decisionLockTicks();

        planExecutor.assignPlan(
            red,
            TacticalPlan.engage(
                blue.ownerUuid(),
                "ENGAGE_B1",
                currentTick,
                lockTicks
            ),
            currentTick
        );

        planExecutor.assignPlan(
            blue,
            TacticalPlan.engage(
                red.ownerUuid(),
                "ENGAGE_R1",
                currentTick,
                lockTicks
            ),
            currentTick
        );

        source.sendSuccess(
            () -> message("commands.test.fight-success"),
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
        BattleTeam team;

        try {
            team = BattleTeam.valueOf(
                rawColor.toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException exception) {
            source.sendFailure(
                message("commands.test.robot-unknown-color")
            );
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        RobotZombie robot = robotFactory.spawnTestRobot(player, team);
        planExecutor.register(robot, matchManager.serverTick());

        source.sendSuccess(
            () -> message(
                "commands.test.robot-spawned",
                "color",
                team.name(),
                "entity",
                robot.getId()
            ),
            false
        );

        return 1;
    }
}
