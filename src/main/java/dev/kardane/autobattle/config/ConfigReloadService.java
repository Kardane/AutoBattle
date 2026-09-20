package dev.kardane.autobattle.config;

import dev.kardane.autobattle.command.PlayerCommandService;
import dev.kardane.autobattle.doctrine.DoctrineNormalizer;
import dev.kardane.autobattle.doctrine.DoctrineService;
import dev.kardane.autobattle.doctrine.DoctrineValidator;
import dev.kardane.autobattle.jev.JevClient;
import dev.kardane.autobattle.jev.JevDecisionService;
import dev.kardane.autobattle.match.MatchManager;
import dev.kardane.autobattle.robot.RobotFactory;
import dev.kardane.autobattle.tactics.PlanExecutor;
import dev.kardane.autobattle.ui.DialogService;
import dev.kardane.autobattle.ui.UiCoordinator;

import java.util.Objects;
import java.util.function.Function;

public final class ConfigReloadService {
    private final MatchManager matchManager;
    private final RobotFactory robotFactory;
    private final PlanExecutor planExecutor;
    private final PlayerCommandService commandService;
    private final JevDecisionService decisionService;
    private final DoctrineService doctrineService;
    private final DoctrineValidator doctrineValidator;
    private final DialogService dialogs;
    private final UiCoordinator ui;
    private final LanguageService language;
    private final Function<AutoBattleConfig, JevClient>
        jevClientFactory;
    private final Function<AutoBattleConfig, DoctrineNormalizer>
        doctrineNormalizerFactory;

    public ConfigReloadService(
        MatchManager matchManager,
        RobotFactory robotFactory,
        PlanExecutor planExecutor,
        PlayerCommandService commandService,
        JevDecisionService decisionService,
        DoctrineService doctrineService,
        DoctrineValidator doctrineValidator,
        DialogService dialogs,
        UiCoordinator ui,
        LanguageService language,
        Function<AutoBattleConfig, JevClient> jevClientFactory,
        Function<AutoBattleConfig, DoctrineNormalizer>
            doctrineNormalizerFactory
    ) {
        this.matchManager = Objects.requireNonNull(
            matchManager,
            "matchManager"
        );
        this.robotFactory = Objects.requireNonNull(
            robotFactory,
            "robotFactory"
        );
        this.planExecutor = Objects.requireNonNull(
            planExecutor,
            "planExecutor"
        );
        this.commandService = Objects.requireNonNull(
            commandService,
            "commandService"
        );
        this.decisionService = Objects.requireNonNull(
            decisionService,
            "decisionService"
        );
        this.doctrineService = Objects.requireNonNull(
            doctrineService,
            "doctrineService"
        );
        this.doctrineValidator = Objects.requireNonNull(
            doctrineValidator,
            "doctrineValidator"
        );
        this.dialogs = Objects.requireNonNull(
            dialogs,
            "dialogs"
        );
        this.ui = Objects.requireNonNull(ui, "ui");
        this.language = Objects.requireNonNull(
            language,
            "language"
        );
        this.jevClientFactory = Objects.requireNonNull(
            jevClientFactory,
            "jevClientFactory"
        );
        this.doctrineNormalizerFactory =
            Objects.requireNonNull(
                doctrineNormalizerFactory,
                "doctrineNormalizerFactory"
            );
    }

    public ConfigReloadResult reload() {
        if (!matchManager.canReloadConfig()) {
            return ConfigReloadResult.failure(
                "Config reload is only allowed in an empty LOBBY."
            );
        }

        try {
            AutoBattleConfig next =
                AutoBattleConfigLoader.load();

            LanguageConfig nextLanguage =
                LanguageConfigLoader.load();

            JevClient nextClient =
                jevClientFactory.apply(next);
            DoctrineNormalizer nextNormalizer =
                doctrineNormalizerFactory.apply(next);

            planExecutor.clear();
            robotFactory.reloadConfig(next.robot());
            planExecutor.reloadConfig(next);
            commandService.reloadConfig(next);
            decisionService.reload(nextClient, next);
            doctrineService.reloadNormalizer(
                nextNormalizer
            );
            doctrineValidator.reloadMaxLineLength(
                next.doctrine().maxLineLength()
            );
            dialogs.reloadMaxLineLength(
                next.doctrine().maxLineLength()
            );
            language.reload(nextLanguage);
            ui.reloadConfig(next);
            matchManager.reloadConfig(next);

            return ConfigReloadResult.ok(
                "Reloaded "
                    + AutoBattleConfigLoader.configPath()
                    + " and "
                    + LanguageConfigLoader.messagePath()
            );
        } catch (RuntimeException exception) {
            return ConfigReloadResult.failure(
                "Config reload failed: "
                    + exception.getMessage()
            );
        }
    }
}
