package dev.kardane.autobattle;

import dev.kardane.autobattle.command.AutoBattleCommands;
import dev.kardane.autobattle.command.CarpetTestCommands;
import dev.kardane.autobattle.command.PlayerCommandService;
import dev.kardane.autobattle.config.AutoBattleConfig;
import dev.kardane.autobattle.config.AutoBattleConfigLoader;
import dev.kardane.autobattle.config.ConfigReloadService;
import dev.kardane.autobattle.config.EffectiveConfigSummary;
import dev.kardane.autobattle.config.LanguageConfig;
import dev.kardane.autobattle.config.LanguageConfigLoader;
import dev.kardane.autobattle.config.LanguageService;
import dev.kardane.autobattle.doctrine.DoctrineNormalizer;
import dev.kardane.autobattle.doctrine.DoctrineService;
import dev.kardane.autobattle.doctrine.DoctrineValidator;
import dev.kardane.autobattle.doctrine.OpenAiDoctrineNormalizer;
import dev.kardane.autobattle.doctrine.PassThroughDoctrineNormalizer;
import dev.kardane.autobattle.event.AutoBattleEvents;
import dev.kardane.autobattle.jev.JevClient;
import dev.kardane.autobattle.jev.JevDecisionService;
import dev.kardane.autobattle.jev.JsonlDecisionLogRepository;
import dev.kardane.autobattle.jev.RobotStateSerializer;
import dev.kardane.autobattle.jev.ScriptedJevClient;
import dev.kardane.autobattle.jev.TypeSafeJevClient;
import dev.kardane.autobattle.log.MatchLogService;
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
        AutoBattleConfig config =
            AutoBattleConfigLoader.load();

        LanguageConfig languageConfig =
            LanguageConfigLoader.load();

        LanguageService language =
            new LanguageService(languageConfig);

        RobotFactory robotFactory = new RobotFactory(
            config.robot()
        );
        RobotRegistry robotRegistry = new RobotRegistry();
        planExecutor = new PlanExecutor(
            robotRegistry,
            config
        );
        DialogService dialogs = new DialogService(
            config.doctrine().maxLineLength(),
            language
        );

        MatchLogService matchLogs =
            new MatchLogService(
                Path.of(
                    "logs",
                    "autobattle",
                    "matches"
                )
            );

        PlayerCommandService commandService =
            new PlayerCommandService(
                config,
                planExecutor,
                matchLogs
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
                new RobotStateSerializer(config.robot()),
                new ValidPlanFactory(
                    config,
                    planExecutor.validityPolicy()
                ),
                planExecutor,
                decisionLogs,
                config
            );
        DoctrineValidator doctrineValidator =
            new DoctrineValidator(
                config.doctrine().maxLineLength()
            );

        DoctrineService doctrineService = new DoctrineService(
            doctrineValidator,
            createDoctrineNormalizer(config)
        );
        RoundReviewService reviewService =
            new RoundReviewService(decisionLogs);

        UiCoordinator ui = new UiCoordinator(
            config,
            planExecutor,
            dialogs,
            reviewService,
            language
        );

        matchManager = new MatchManager(
            config,
            robotRegistry,
            robotFactory,
            planExecutor,
            commandService,
            decisionService,
            ui,
            matchLogs
        );

        dialogActionRouter = new DialogActionRouter(
            matchManager,
            doctrineService,
            reviewService,
            dialogs,
            language
        );

        ConfigReloadService configReloadService =
            new ConfigReloadService(
                matchManager,
                robotFactory,
                planExecutor,
                commandService,
                decisionService,
                doctrineService,
                doctrineValidator,
                dialogs,
                ui,
                language,
                this::createJevClient,
                this::createDoctrineNormalizer
            );

        AutoBattleCommands.register(
            matchManager,
            robotFactory,
            planExecutor,
            doctrineService,
            commandService,
            reviewService,
            configReloadService,
            language
        );

        CarpetTestCommands.register(
            matchManager,
            doctrineService,
            commandService,
            language
        );

        AutoBattleEvents.register(
            matchManager,
            planExecutor,
            decisionService,
            matchLogs
        );

        LOGGER.info(
            "AutoBattle initialized (config={}, messages={}, rounds={}, effective={})",
            AutoBattleConfigLoader.configPath().toAbsolutePath().normalize(),
            LanguageConfigLoader.messagePath().toAbsolutePath().normalize(),
            config.roundCount(),
            EffectiveConfigSummary.describe(config)
        );
    }


    private DoctrineNormalizer createDoctrineNormalizer(
        AutoBattleConfig config
    ) {
        var normalizerConfig = config.doctrineNormalizer();

        if (!normalizerConfig.enabled()) {
            LOGGER.info(
                "OpenAI Doctrine Normalizer is disabled."
            );
            return new PassThroughDoctrineNormalizer(
                "Doctrine normalizer is disabled"
            );
        }

        String apiKey = normalizerConfig.apiKey();

        if (apiKey.isBlank()) {
            String environmentKey = System.getenv(
                "OPENAI_API_KEY"
            );

            if (environmentKey != null
                && !environmentKey.isBlank()) {
                apiKey = environmentKey.trim();
            }
        }

        if (apiKey.isBlank()) {
            LOGGER.warn(
                "OpenAI Doctrine Normalizer is enabled but no API key "
                    + "is configured. Source Doctrine will be used unchanged."
            );
            return new PassThroughDoctrineNormalizer(
                "OPENAI_API_KEY is not configured"
            );
        }

        LOGGER.info(
            "Using OpenAI Doctrine Normalizer (model={}, baseUrl={})",
            normalizerConfig.model(),
            normalizerConfig.baseUrl()
        );

        return new OpenAiDoctrineNormalizer(
            normalizerConfig,
            apiKey
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
