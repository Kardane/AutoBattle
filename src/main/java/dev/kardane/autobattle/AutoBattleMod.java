package dev.kardane.autobattle;

import dev.kardane.autobattle.command.AutoBattleCommands;
import dev.kardane.autobattle.command.PlayerCommandService;
import dev.kardane.autobattle.config.AutoBattleConfig;
import dev.kardane.autobattle.config.AutoBattleConfigLoader;
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
import dev.kardane.autobattle.ui.DialogActionRouter;
import dev.kardane.autobattle.ui.DialogService;
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
    private static DialogActionRouter dialogActionRouter;

    @Override
    public void onInitializeServer() {
        AutoBattleConfig config = AutoBattleConfigLoader.load();
        RobotFactory robotFactory = new RobotFactory(
            config.robot()
        );
        RobotRegistry robotRegistry = new RobotRegistry();
        planExecutor = new PlanExecutor(
            robotRegistry,
            config.robot()
        );
        DialogService dialogs = new DialogService(
            config.doctrine().maxLineLength()
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
            new DoctrineValidator(
                config.doctrine().maxLineLength()
            )
        );
        RoundReviewService reviewService =
            new RoundReviewService(decisionLogs);

        UiCoordinator ui = new UiCoordinator(
            config,
            planExecutor,
            dialogs,
            reviewService
        );

        matchManager = new MatchManager(
            config,
            robotRegistry,
            robotFactory,
            planExecutor,
            commandService,
            decisionService,
            ui
        );

        dialogActionRouter = new DialogActionRouter(
            matchManager,
            doctrineService,
            reviewService,
            dialogs
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
            "AutoBattle initialized (config={}, minimumPlayers={}, rounds={})",
            AutoBattleConfigLoader.configPath(),
            config.minimumPlayers(),
            config.roundCount()
        );
    }


    private JevClient createJevClient(
        AutoBattleConfig config
    ) {
        String apiKey = config.typesafe().apiKey();

        if (apiKey.isBlank()) {
            String environmentKey = System.getenv(
                "TYPESAFE_API_KEY"
            );

            if (environmentKey != null
                && !environmentKey.isBlank()) {
                apiKey = environmentKey.trim();
            }
        }

        if (apiKey.isBlank()) {
            LOGGER.warn(
                "typesafe.api-key is empty and TYPESAFE_API_KEY "
                    + "is not configured. AutoBattle will use "
                    + "ScriptedJevClient."
            );
            return new ScriptedJevClient();
        }

        LOGGER.info(
            "Using TypeSafe Jev (model={}, baseUrl={})",
            config.typesafe().model(),
            config.typesafe().baseUrl()
        );

        return new TypeSafeJevClient(
            apiKey,
            config.typesafe().baseUrl(),
            config.typesafe().model(),
            config.jevTimeoutMs()
        );
    }

    public static DialogActionRouter dialogActionRouter() {
        if (dialogActionRouter == null) {
            throw new IllegalStateException(
                "AutoBattle dialog router has not been initialized yet."
            );
        }

        return dialogActionRouter;
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
