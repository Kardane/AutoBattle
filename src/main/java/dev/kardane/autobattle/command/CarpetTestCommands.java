package dev.kardane.autobattle.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kardane.autobattle.config.LanguageService;
import dev.kardane.autobattle.doctrine.DoctrineEditResult;
import dev.kardane.autobattle.doctrine.DoctrineService;
import dev.kardane.autobattle.match.MatchManager;
import dev.kardane.autobattle.match.MatchPhase;
import dev.kardane.autobattle.match.PlayerSlot;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class CarpetTestCommands {
    private static final String[] BOT_NAMES = {
        "ABot1",
        "ABot2",
        "ABot3",
        "ABot4"
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
                                                language
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
                                                language
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

        int spawned = 0;
        int existing = 0;

        for (String botName : BOT_NAMES) {
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
        LanguageService language
    ) {
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
            new ServerPlayer[BOT_NAMES.length];

        for (int index = 0;
             index < BOT_NAMES.length;
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

            for (ServerPlayer player : players) {
                PlayerSlot slot = matchManager
                    .playerSlot(player.getUUID())
                    .orElseThrow();

                if (!slot.ready()) {
                    matchManager.toggleReady(player);
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

            String[] doctrine = DOCTRINES[index];

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
            .ownerUuid()
            .flatMap(matchManager::playerSlot)
            .map(slot -> slot.color().name())
            .orElse("none");

        String status = language.format(
            "commands.status",
            "phase",
            matchManager.session().phase().name(),
            "round",
            matchManager.session().currentRound(),
            "players",
            matchManager.playerCount(),
            "ready",
            matchManager.readyCount(),
            "minimum",
            matchManager.config().minimumPlayers(),
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
                : slot.color().name();

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
