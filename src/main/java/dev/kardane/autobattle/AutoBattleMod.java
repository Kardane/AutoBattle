package dev.kardane.autobattle;

import dev.kardane.autobattle.command.AutoBattleCommands;
import dev.kardane.autobattle.command.PlayerCommandService;
import dev.kardane.autobattle.config.AutoBattleConfig;
import dev.kardane.autobattle.doctrine.DoctrineService;
import dev.kardane.autobattle.doctrine.DoctrineValidator;
import dev.kardane.autobattle.event.AutoBattleEvents;
import dev.kardane.autobattle.jev.JevClient;
import dev.kardane.autobattle.jev.JevDecisionService;
import dev.kardane.autobattle.jev.JsonlDecisionLogRepository;
import dev.kardane.autobattle.jev.RobotStateSerializer;
import dev.kardane.autobattle.jev.ScriptedJevClient;
import dev.kardane.autobattle.jev.TypeSafeJevClient;
import dev.kardane.autobattle.match.MatchManager;
import dev.kardane.autobattle.review.RoundReviewService;
import dev.kardane.autobattle.robot.RobotFactory;
import dev.kardane.autobattle.robot.RobotRegistry;
import dev.kardane.autobattle.tactics.PlanExecutor;
import dev.kardane.autobattle.tactics.ValidPlanFactory;
import dev.kardane.autobattle.ui.UiCoordinator;
import net.fabricmc.api.DedicatedServerModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public final class AutoBattleMod implements DedicatedServerModInitializer {
    public static final String MOD_ID = "autobattle";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static MatchManager matchManager;
    private static PlanExecutor planExecutor;

    @Override
    public void onInitializeServer() {
        AutoBattleConfig config = AutoBattleConfig.defaults();
        RobotFactory robotFactory = new RobotFactory();
        RobotRegistry robotRegistry = new RobotRegistry();
        planExecutor = new PlanExecutor(robotRegistry);
        UiCoordinator ui = new UiCoordinator(
            config,
            planExecutor
        );
        PlayerCommandService commandService =
            new PlayerCommandService(
                config,
                planExecutor
            );
        JsonlDecisionLogRepository decisionLogs =
            new JsonlDecisionLogRepository(
                Path.of(
                    "logs",
                    "autobattle",
                    "decisions"
                )
            );

        JevClient jevClient = createJevClient(config);

        JevDecisionService decisionService =
            new JevDecisionService(
                jevClient,
                new RobotStateSerializer(),
                new ValidPlanFactory(),
                planExecutor,
                decisionLogs,
                config
            );
        DoctrineService doctrineService = new DoctrineService(
            new DoctrineValidator()
        );
        RoundReviewService reviewService =
            new RoundReviewService(decisionLogs);

        matchManager = new MatchManager(
            config,
            robotRegistry,
            robotFactory,
            planExecutor,
            commandService,
            decisionService,
            ui
        );

        AutoBattleCommands.register(
            matchManager,
            robotFactory,
            planExecutor,
            doctrineService,
            commandService,
            reviewService
        );

        AutoBattleEvents.register(
            matchManager,
            planExecutor,
            decisionService
        );

        LOGGER.info(
            "AutoBattle initialized (minimumPlayers={}, rounds={})",
            config.minimumPlayers(),
            config.roundCount()
        );
    }


    private JevClient createJevClient(
        AutoBattleConfig config
    ) {
        String apiKey = System.getenv(
            "TYPESAFE_API_KEY"
        );

        if (apiKey == null || apiKey.isBlank()) {
            LOGGER.warn(
                "TYPESAFE_API_KEY is not configured. "
                    + "AutoBattle will use ScriptedJevClient."
            );
            return new ScriptedJevClient();
        }

        String baseUrl = environmentOrDefault(
            "TYPESAFE_BASE_URL",
            TypeSafeJevClient.DEFAULT_BASE_URL
        );

        String model = environmentOrDefault(
            "TYPESAFE_DEFAULT_MODEL",
            TypeSafeJevClient.DEFAULT_MODEL
        );

        LOGGER.info(
            "Using TypeSafe Jev (model={}, baseUrl={})",
            model,
            baseUrl
        );

        return new TypeSafeJevClient(
            apiKey,
            baseUrl,
            model,
            config.jevTimeoutMs()
        );
    }

    private String environmentOrDefault(
        String name,
        String fallback
    ) {
        String value = System.getenv(name);

        if (value == null || value.isBlank()) {
            return fallback;
        }

        return value.trim();
    }

    public static MatchManager matchManager() {
        if (matchManager == null) {
            throw new IllegalStateException(
                "AutoBattle has not been initialized yet."
            );
        }

        return matchManager;
    }

    public static PlanExecutor planExecutor() {
        if (planExecutor == null) {
            throw new IllegalStateException(
                "AutoBattle has not been initialized yet."
            );
        }

        return planExecutor;
    }
}
