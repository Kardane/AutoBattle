package dev.kardane.autobattle.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kardane.autobattle.config.LanguageService;
import dev.kardane.autobattle.doctrine.DoctrineEditResult;
import dev.kardane.autobattle.doctrine.DoctrineService;
import dev.kardane.autobattle.match.BattleTeam;
import dev.kardane.autobattle.match.MatchManager;
import dev.kardane.autobattle.match.MatchPhase;
import dev.kardane.autobattle.match.PlayerSlot;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class CarpetTestCommands {
    private static final int DEFAULT_TEST_TEAM_SIZE = 4;

    private static final String[] BOT_NAMES = {
        "ABot1", "ABot2", "ABot3", "ABot4",
        "ABot5", "ABot6", "ABot7", "ABot8",
        "ABot9", "ABot10", "ABot11", "ABot12",
        "ABot13", "ABot14", "ABot15", "ABot16"
    };

    private static final String[][] DOCTRINES = {
        {
            "Prioritize capturing CORE when it is neutral.",
            "Engage nearby enemies when the objective is secure.",
            "Retreat when health is low."
        },
        {
            "Defend CORE when our side controls it.",
            "Attack enemies contesting the objective.",
            "Retreat when health is low."
        },
        {
            "Pressure nearby enemies aggressively.",
            "Chase vulnerable targets that disengage.",
            "Capture CORE when no enemy is in immediate range."
        },
        {
            "Stay close to CORE and deny captures.",
            "Engage enemies that enter the objective area.",
            "Do not chase enemies too far; prefer ENGAGE over CHASE."
        }
    };

    private CarpetTestCommands() {
    }

    public static void register(
        MatchManager matchManager,
        DoctrineService doctrineService,
        PlayerCommandService commandService,
        LanguageService language
    ) {
        Objects.requireNonNull(matchManager, "matchManager");
        Objects.requireNonNull(doctrineService, "doctrineService");
        Objects.requireNonNull(commandService, "commandService");
        Objects.requireNonNull(language, "language");

        CommandRegistrationCallback.EVENT.register(
            (dispatcher, registryAccess, environment) ->
                registerTree(
                    dispatcher,
                    matchManager,
                    doctrineService,
                    commandService,
                    language
                )
        );
    }

    private static void registerTree(
        CommandDispatcher<CommandSourceStack> dispatcher,
        MatchManager matchManager,
        DoctrineService doctrineService,
        PlayerCommandService commandService,
        LanguageService language
    ) {
        dispatcher.register(
            Commands.literal("autobattle")
                .then(
                    Commands.literal("admin")
                        .requires(source -> source.hasPermission(2))
                        .then(
                            Commands.literal("test")
                                .then(
                                    Commands.literal("spawn")
                                        .executes(context ->
                                            spawnBots(
                                                context.getSource(),
                                                language,
                                                DEFAULT_TEST_TEAM_SIZE
                                            )
                                        )
                                        .then(
                                            Commands.argument(
                                                "teamSize",
                                                IntegerArgumentType.integer(1, 8)
                                            )
                                            .executes(context ->
                                                spawnBots(
                                                    context.getSource(),
                                                    language,
                                                    IntegerArgumentType.getInteger(
                                                        context,
                                                        "teamSize"
                                                    )
                                                )
                                            )
                                        )
                                )
                                .then(
                                    Commands.literal("setup")
                                        .executes(context ->
                                            setupBots(
                                                context.getSource(),
                                                matchManager,
                                                doctrineService,
                                                language,
                                                DEFAULT_TEST_TEAM_SIZE
                                            )
                                        )
                                        .then(
                                            Commands.argument(
                                                "teamSize",
                                                IntegerArgumentType.integer(1, 8)
                                            )
                                            .executes(context ->
                                                setupBots(
                                                    context.getSource(),
                                                    matchManager,
                                                    doctrineService,
                                                    language,
                                                    IntegerArgumentType.getInteger(
                                                        context,
                                                        "teamSize"
                                                    )
                                                )
                                            )
                                        )
                                )
                                .then(
                                    Commands.literal("review")
                                        .executes(context ->
                                            reviewBots(
                                                context.getSource(),
                                                matchManager,
                                                language
                                            )
                                        )
                                )
                                .then(
                                    Commands.literal("keep")
                                        .executes(context ->
                                            keepDoctrines(
                                                context.getSource(),
                                                matchManager,
                                                language
                                            )
                                        )
                                )
                                .then(
                                    Commands.literal("next")
                                        .executes(context ->
                                            advance(
                                                context.getSource(),
                                                matchManager,
                                                language
                                            )
                                        )
                                )
                                .then(
                                    Commands.literal("cleanup")
                                        .executes(context ->
                                            cleanup(
                                                context.getSource(),
                                                matchManager,
                                                language
                                            )
                                        )
                                )
                                .then(
                                    Commands.literal("status")
                                        .executes(context ->
                                            status(
                                                context.getSource(),
                                                matchManager,
                                                language
                                            )
                                        )
                                )
                                .then(
                                    Commands.literal("verify")
                                        .executes(context ->
                                            verifyTeamBattle(
                                                context.getSource(),
                                                matchManager,
                                                language,
                                                DEFAULT_TEST_TEAM_SIZE
                                            )
                                        )
                                        .then(
                                            Commands.argument(
                                                "teamSize",
                                                IntegerArgumentType.integer(1, 8)
                                            )
                                            .executes(context ->
                                                verifyTeamBattle(
                                                    context.getSource(),
                                                    matchManager,
                                                    language,
                                                    IntegerArgumentType.getInteger(
                                                        context,
                                                        "teamSize"
                                                    )
                                                )
                                            )
                                        )
                                )
                                .then(
                                    Commands.literal("command")
                                        .then(
                                            Commands.argument(
                                                "bot",
                                                IntegerArgumentType.integer(
                                                    1,
                                                    BOT_NAMES.length
                                                )
                                            )
                                            .then(
                                                Commands.literal("attack")
                                                    .executes(context ->
                                                        useCommand(
                                                            context.getSource(),
                                                            matchManager,
                                                            commandService,
                                                            language,
                                                            IntegerArgumentType.getInteger(
                                                                context,
                                                                "bot"
                                                            ),
                                                            PlayerCommandType.ATTACK
                                                        )
                                                    )
                                            )
                                            .then(
                                                Commands.literal("capture")
                                                    .executes(context ->
                                                        useCommand(
                                                            context.getSource(),
                                                            matchManager,
                                                            commandService,
                                                            language,
                                                            IntegerArgumentType.getInteger(
                                                                context,
                                                                "bot"
                                                            ),
                                                            PlayerCommandType.CAPTURE
                                                        )
                                                    )
                                            )
                                            .then(
                                                Commands.literal("survive")
                                                    .executes(context ->
                                                        useCommand(
                                                            context.getSource(),
                                                            matchManager,
                                                            commandService,
                                                            language,
                                                            IntegerArgumentType.getInteger(
                                                                context,
                                                                "bot"
                                                            ),
                                                            PlayerCommandType.SURVIVE
                                                        )
                                                    )
                                            )
                                        )
                                )
                        )
                )
        );
    }

    private static int spawnBots(
        CommandSourceStack source,
        LanguageService language,
        int teamSize
    ) {
        int botCount = teamSize * 2;
        var dispatcher = source.getServer()
            .getCommands()
            .getDispatcher();

        if (dispatcher.getRoot().getChild("player") == null) {
            source.sendFailure(
                message(
                    language,
                    "commands.test.carpet-missing"
                )
            );
            return 0;
        }

        int spawned = 0;
        int existing = 0;

        for (int index = 0; index < botCount; index++) {
            String botName = BOT_NAMES[index];

            if (source.getServer().getPlayerList()
                .getPlayerByName(botName) != null) {
                existing++;
                continue;
            }

            if (runServerCommand(
                source,
                language,
                "player "
                    + botName
                    + " spawn in spectator"
            ) <= 0) {
                source.sendFailure(
                    message(
                        language,
                        "commands.test.spawn-failed",
                        "bot",
                        botName
                    )
                );
                return 0;
            }

            spawned++;
        }

        int finalSpawned = spawned;
        int finalExisting = existing;

        source.sendSuccess(
            () -> message(
                language,
                "commands.test.spawn-success",
                "spawned",
                finalSpawned,
                "existing",
                finalExisting
            ),
            true
        );

        return 1;
    }

    private static int setupBots(
        CommandSourceStack source,
        MatchManager matchManager,
        DoctrineService doctrineService,
        LanguageService language,
        int teamSize
    ) {
        int botCount = teamSize * 2;
        MatchPhase phase = matchManager.session().phase();

        if (phase != MatchPhase.LOBBY
            && phase != MatchPhase.DOCTRINE_SETUP) {
            source.sendFailure(
                message(
                    language,
                    "commands.test.setup-invalid-phase",
                    "phase",
                    phase.name()
                )
            );
            return 0;
        }

        ServerPlayer[] players =
            new ServerPlayer[botCount];

        for (int index = 0;
             index < botCount;
             index++) {
            String botName = BOT_NAMES[index];
            ServerPlayer player = source.getServer()
                .getPlayerList()
                .getPlayerByName(botName);

            if (player == null) {
                source.sendFailure(
                    message(
                        language,
                        "commands.test.bot-missing",
                        "bot",
                        botName
                    )
                );
                return 0;
            }

            players[index] = player;
        }

        if (phase == MatchPhase.LOBBY) {
            for (ServerPlayer player : players) {
                if (!matchManager.join(player)) {
                    source.sendFailure(
                        message(
                            language,
                            "commands.test.join-failed",
                            "bot",
                            player.getName().getString()
                        )
                    );
                    return 0;
                }
            }

            if (!matchManager.beginDoctrineSetupIfReady(
                source.getServer()
            )) {
                source.sendFailure(
                    message(
                        language,
                        "commands.test.ready-failed"
                    )
                );
                return 0;
            }
        }

        if (matchManager.session().phase()
            != MatchPhase.DOCTRINE_SETUP) {
            source.sendFailure(
                message(
                    language,
                    "commands.test.setup-invalid-phase",
                    "phase",
                    matchManager.session().phase().name()
                )
            );
            return 0;
        }

        List<CompletableFuture<DoctrineEditResult>>
            submissions = new ArrayList<>();
        List<String> submittedBots = new ArrayList<>();

        for (int index = 0;
             index < players.length;
             index++) {
            ServerPlayer player = players[index];
            PlayerSlot slot = matchManager
                .playerSlot(player.getUUID())
                .orElseThrow();

            if (slot.doctrine().isPresent()) {
                continue;
            }

            String[] doctrine =
                DOCTRINES[index % DOCTRINES.length];

            CompletableFuture<DoctrineEditResult> future =
                doctrineService.submitInitialAsync(
                    source.getServer(),
                    matchManager.session(),
                    player,
                    doctrine[0],
                    doctrine[1],
                    doctrine[2]
                );

            if (future.isDone()) {
                DoctrineEditResult immediate =
                    future.getNow(null);

                if (immediate != null
                    && !immediate.success()) {
                    source.sendFailure(
                        message(
                            language,
                            "commands.test.doctrine-failed",
                            "bot",
                            BOT_NAMES[index],
                            "error",
                            immediate.error().name()
                        )
                    );
                    return 0;
                }
            }

            submissions.add(future);
            submittedBots.add(BOT_NAMES[index]);
        }

        if (submissions.isEmpty()) {
            matchManager.beginCountdownIfDoctrinesReady();

            MatchPhase nextPhase =
                matchManager.session().phase();

            source.sendSuccess(
                () -> message(
                    language,
                    "commands.test.setup-success",
                    "phase",
                    nextPhase.name()
                ),
                true
            );

            return nextPhase == MatchPhase.COUNTDOWN
                ? 1
                : 0;
        }

        CompletableFuture.allOf(
            submissions.toArray(
                CompletableFuture[]::new
            )
        ).thenRun(() ->
            source.getServer().execute(() -> {
                for (int index = 0;
                     index < submissions.size();
                     index++) {
                    DoctrineEditResult result =
                        submissions.get(index)
                            .getNow(null);

                    if (result == null
                        || !result.success()) {
                        source.sendFailure(
                            message(
                                language,
                                "commands.test.doctrine-failed",
                                "bot",
                                submittedBots.get(index),
                                "error",
                                result == null
                                    ? "UNKNOWN"
                                    : result.error().name()
                            )
                        );
                        return;
                    }
                }

                matchManager.beginCountdownIfDoctrinesReady();

                MatchPhase nextPhase =
                    matchManager.session().phase();

                source.sendSuccess(
                    () -> message(
                        language,
                        "commands.test.setup-success",
                        "phase",
                        nextPhase.name()
                    ),
                    true
                );
            })
        );

        return 1;
    }

    private static int reviewBots(
        CommandSourceStack source,
        MatchManager matchManager,
        LanguageService language
    ) {
        if (matchManager.session().phase()
            != MatchPhase.ROUND_REVIEW) {
            source.sendFailure(
                message(
                    language,
                    "commands.test.review-invalid-phase",
                    "phase",
                    matchManager.session().phase().name()
                )
            );
            return 0;
        }

        markReviewReady(
            source,
            matchManager
        );

        source.sendSuccess(
            () -> message(
                language,
                "commands.test.review-success",
                "phase",
                matchManager.session().phase().name()
            ),
            true
        );

        return 1;
    }

    private static int keepDoctrines(
        CommandSourceStack source,
        MatchManager matchManager,
        LanguageService language
    ) {
        if (matchManager.session().phase()
            != MatchPhase.DOCTRINE_EDIT) {
            source.sendFailure(
                message(
                    language,
                    "commands.test.keep-invalid-phase",
                    "phase",
                    matchManager.session().phase().name()
                )
            );
            return 0;
        }

        markDoctrineEditDone(
            source,
            matchManager
        );

        source.sendSuccess(
            () -> message(
                language,
                "commands.test.keep-success",
                "phase",
                matchManager.session().phase().name()
            ),
            true
        );

        return 1;
    }

    private static int advance(
        CommandSourceStack source,
        MatchManager matchManager,
        LanguageService language
    ) {
        MatchPhase initial =
            matchManager.session().phase();

        if (initial == MatchPhase.ROUND_REVIEW) {
            markReviewReady(
                source,
                matchManager
            );
        }

        if (matchManager.session().phase()
            == MatchPhase.DOCTRINE_EDIT) {
            markDoctrineEditDone(
                source,
                matchManager
            );
        }

        MatchPhase result =
            matchManager.session().phase();

        if (result == MatchPhase.COUNTDOWN
            || result == MatchPhase.LOBBY) {
            source.sendSuccess(
                () -> message(
                    language,
                    "commands.test.next-success",
                    "phase",
                    result.name()
                ),
                true
            );
            return 1;
        }

        source.sendFailure(
            message(
                language,
                "commands.test.next-invalid-phase",
                "phase",
                initial.name()
            )
        );

        return 0;
    }

    private static void markReviewReady(
        CommandSourceStack source,
        MatchManager matchManager
    ) {
        for (String botName : BOT_NAMES) {
            if (matchManager.session().phase()
                != MatchPhase.ROUND_REVIEW) {
                break;
            }

            ServerPlayer player = source.getServer()
                .getPlayerList()
                .getPlayerByName(botName);

            if (player != null
                && matchManager.playerSlot(
                    player.getUUID()
                ).isPresent()) {
                matchManager.markReviewReady(player);
            }
        }
    }

    private static void markDoctrineEditDone(
        CommandSourceStack source,
        MatchManager matchManager
    ) {
        for (String botName : BOT_NAMES) {
            if (matchManager.session().phase()
                != MatchPhase.DOCTRINE_EDIT) {
                break;
            }

            ServerPlayer player = source.getServer()
                .getPlayerList()
                .getPlayerByName(botName);

            if (player != null
                && matchManager.playerSlot(
                    player.getUUID()
                ).isPresent()) {
                matchManager.markDoctrineEditDone(
                    player
                );
            }
        }
    }

    private static int cleanup(
        CommandSourceStack source,
        MatchManager matchManager,
        LanguageService language
    ) {
        var dispatcher = source.getServer()
            .getCommands()
            .getDispatcher();

        if (dispatcher.getRoot().getChild("player") == null) {
            source.sendFailure(
                message(
                    language,
                    "commands.test.carpet-missing"
                )
            );
            return 0;
        }

        int cleaned = 0;

        for (String botName : BOT_NAMES) {
            ServerPlayer player = source.getServer()
                .getPlayerList()
                .getPlayerByName(botName);

            if (player == null) {
                continue;
            }

            if (matchManager.playerSlot(
                player.getUUID()
            ).isPresent()) {
                matchManager.leave(
                    player,
                    source.getServer()
                );
            }

            if (runServerCommand(
                source,
                language,
                "player " + botName + " kill"
            ) > 0) {
                cleaned++;
            }
        }

        int finalCleaned = cleaned;

        source.sendSuccess(
            () -> message(
                language,
                "commands.test.cleanup-success",
                "cleaned",
                finalCleaned
            ),
            true
        );

        return 1;
    }

    private static int status(
        CommandSourceStack source,
        MatchManager matchManager,
        LanguageService language
    ) {
        String coreOwner = matchManager.session()
            .core()
            .state()
            .ownerTeam()
            .map(Enum::name)
            .orElse("none");

        String status = language.format(
            "commands.status",
            "phase",
            matchManager.session().phase().name(),
            "round",
            matchManager.session().currentRound(),
            "players",
            matchManager.playerCount(),
            "red",
            matchManager.teamCount(
                dev.kardane.autobattle.match.BattleTeam.RED
            ),
            "blue",
            matchManager.teamCount(
                dev.kardane.autobattle.match.BattleTeam.BLUE
            ),
            "balanced",
            matchManager.teamsBalanced(),
            "maximum",
            matchManager.config().maxTeamSize(),
            "core_owner",
            coreOwner
        );

        source.sendSuccess(
            () -> message(
                language,
                "commands.test.match-status",
                "status",
                status
            ),
            false
        );

        for (String botName : BOT_NAMES) {
            ServerPlayer player = source.getServer()
                .getPlayerList()
                .getPlayerByName(botName);

            if (player == null) {
                source.sendSuccess(
                    () -> message(
                        language,
                        "commands.test.bot-status",
                        "bot",
                        botName,
                        "state",
                        "offline",
                        "color",
                        "-",
                        "ready",
                        "-",
                        "doctrine",
                        "-"
                    ),
                    false
                );
                continue;
            }

            PlayerSlot slot = matchManager
                .playerSlot(player.getUUID())
                .orElse(null);

            String color = slot == null
                ? "-"
                : slot.team().name()
                    + " "
                    + slot.targetId();

            String ready = slot == null
                ? "-"
                : Boolean.toString(slot.ready());

            String doctrine = slot == null
                ? "-"
                : Boolean.toString(
                    slot.doctrine().isPresent()
                );

            source.sendSuccess(
                () -> message(
                    language,
                    "commands.test.bot-status",
                    "bot",
                    botName,
                    "state",
                    "online",
                    "color",
                    color,
                    "ready",
                    ready,
                    "doctrine",
                    doctrine
                ),
                false
            );
        }

        return 1;
    }

    private static int verifyTeamBattle(
        CommandSourceStack source,
        MatchManager matchManager,
        LanguageService language,
        int expectedTeamSize
    ) {
        var match = matchManager.session();
        List<String> problems = new ArrayList<>();

        List<PlayerSlot> active = match.players()
            .stream()
            .filter(slot -> !slot.forfeited())
            .toList();

        long red = active.stream()
            .filter(slot -> slot.team() == BattleTeam.RED)
            .count();
        long blue = active.stream()
            .filter(slot -> slot.team() == BattleTeam.BLUE)
            .count();

        if (red != expectedTeamSize
            || blue != expectedTeamSize) {
            problems.add(
                "team counts RED=" + red
                    + ", BLUE=" + blue
                    + ", expected="
                    + expectedTeamSize
            );
        }

        if (active.size() != expectedTeamSize * 2) {
            problems.add(
                "active participant count="
                    + active.size()
                    + ", expected="
                    + (expectedTeamSize * 2)
            );
        }

        Set<String> ids = new HashSet<>();
        int robotsChecked = 0;

        for (PlayerSlot slot : active) {
            String expectedId = slot.team()
                .targetId(slot.memberIndex());

            if (!slot.targetId().equals(expectedId)) {
                problems.add(
                    "slot identity mismatch: "
                        + slot.targetId()
                        + " expected "
                        + expectedId
                );
            }

            if (!ids.add(slot.targetId())) {
                problems.add(
                    "duplicate targetId "
                        + slot.targetId()
                );
            }

            if (!slot.ready()) {
                problems.add(
                    slot.targetId()
                        + " is not auto-ready"
                );
            }

            if (match.phase() != MatchPhase.ROUND_ACTIVE) {
                continue;
            }

            var controller = match.robots()
                .byOwner(slot.playerUuid())
                .orElse(null);

            if (controller == null) {
                problems.add(
                    slot.targetId()
                        + " has no robot controller"
                );
                continue;
            }

            robotsChecked++;

            if (controller.team() != slot.team()) {
                problems.add(
                    slot.targetId()
                        + " controller team mismatch"
                );
            }

            if (!controller.targetId()
                .equals(slot.targetId())) {
                problems.add(
                    slot.targetId()
                        + " controller targetId mismatch"
                );
            }

            var entity = controller.entity().orElse(null);

            if (entity == null || !controller.alive()) {
                problems.add(
                    slot.targetId()
                        + " robot is not alive"
                );
            } else {
                if (entity.team() != slot.team()) {
                    problems.add(
                        slot.targetId()
                            + " entity team mismatch"
                    );
                }

                if (!entity.targetId()
                    .equals(slot.targetId())) {
                    problems.add(
                        slot.targetId()
                            + " entity targetId mismatch"
                    );
                }
            }

            controller.currentPlan().ifPresent(plan -> {
                if (plan.targetOwnerUuid() == null) {
                    return;
                }

                PlayerSlot target = match.player(
                    plan.targetOwnerUuid()
                ).orElse(null);

                if (target == null) {
                    problems.add(
                        slot.targetId()
                            + " plan targets missing participant"
                    );
                    return;
                }

                if (target.team() == slot.team()) {
                    problems.add(
                        slot.targetId()
                            + " has friendly combat target "
                            + target.targetId()
                    );
                }

                String externalId = plan.externalId();

                if ((externalId.startsWith("ENGAGE_")
                    || externalId.startsWith("CHASE_"))
                    && !externalId.endsWith(
                        "_" + target.targetId()
                    )) {
                    problems.add(
                        slot.targetId()
                            + " plan ID/target mismatch: "
                            + externalId
                    );
                }
            });
        }

        if (!matchManager.teamsBalanced()) {
            problems.add("match reports teams as unbalanced");
        }

        if (!problems.isEmpty()) {
            source.sendFailure(
                message(
                    language,
                    "commands.test.verify-failure",
                    "errors",
                    String.join("; ", problems)
                )
            );
            return 0;
        }

        int finalRobotsChecked = robotsChecked;

        source.sendSuccess(
            () -> message(
                language,
                "commands.test.verify-success",
                "team_size",
                expectedTeamSize,
                "phase",
                match.phase().name(),
                "participants",
                active.size(),
                "robots_checked",
                finalRobotsChecked
            ),
            true
        );

        return 1;
    }

    private static int useCommand(
        CommandSourceStack source,
        MatchManager matchManager,
        PlayerCommandService commandService,
        LanguageService language,
        int oneBasedBot,
        PlayerCommandType type
    ) {
        String botName =
            BOT_NAMES[oneBasedBot - 1];

        ServerPlayer player = source.getServer()
            .getPlayerList()
            .getPlayerByName(botName);

        if (player == null) {
            source.sendFailure(
                message(
                    language,
                    "commands.test.bot-missing",
                    "bot",
                    botName
                )
            );
            return 0;
        }

        CommandUseResult result = commandService.use(
            matchManager.session(),
            player,
            type,
            matchManager.serverTick()
        );

        if (result != CommandUseResult.SUCCESS) {
            source.sendFailure(
                message(
                    language,
                    "commands.test.command-rejected",
                    "bot",
                    botName,
                    "result",
                    result.name()
                )
            );
            return 0;
        }

        source.sendSuccess(
            () -> message(
                language,
                "commands.test.command-success",
                "bot",
                botName,
                "type",
                type.name()
            ),
            true
        );

        return 1;
    }

    private static int runServerCommand(
        CommandSourceStack source,
        LanguageService language,
        String command
    ) {
        try {
            return source.getServer()
                .getCommands()
                .getDispatcher()
                .execute(
                    command,
                    source
                );
        } catch (CommandSyntaxException exception) {
            source.sendFailure(
                message(
                    language,
                    "commands.test.command-error",
                    "command",
                    command,
                    "error",
                    exception.getMessage()
                )
            );
            return 0;
        }
    }

    private static Component message(
        LanguageService language,
        String key,
        Object... placeholders
    ) {
        return language.component(
            key,
            placeholders
        );
    }
}
