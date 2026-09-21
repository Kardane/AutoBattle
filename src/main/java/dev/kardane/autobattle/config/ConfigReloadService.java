package dev.kardane.autobattle.config;

import dev.kardane.autobattle.command.PlayerCommandService;
import dev.kardane.autobattle.doctrine.DoctrineNormalizer;
import dev.kardane.autobattle.doctrine.DoctrineService;
import dev.kardane.autobattle.doctrine.DoctrineValidator;
import dev.kardane.autobattle.jev.JevClient;
import dev.kardane.autobattle.jev.JevDecisionService;
import dev.kardane.autobattle.match.MatchPhase;
import dev.kardane.autobattle.match.MatchManager;
import dev.kardane.autobattle.robot.RobotFactory;
import dev.kardane.autobattle.tactics.PlanExecutor;
import dev.kardane.autobattle.ui.DialogService;
import dev.kardane.autobattle.ui.UiCoordinator;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Function;

public final class ConfigReloadService {
    private static final Logger LOGGER =
        LoggerFactory.getLogger("autobattle");
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
        return reload(null);
    }

    public ConfigReloadResult reload(
        MinecraftServer server
    ) {

        try {
            AutoBattleConfig next =
                AutoBattleConfigLoader.load();

            LanguageConfig nextLanguage =
                LanguageConfigLoader.load();

            JevClient nextClient =
                jevClientFactory.apply(next);
            DoctrineNormalizer nextNormalizer =
                doctrineNormalizerFactory.apply(next);

            boolean emptyLobby =
                matchManager.session().phase() == MatchPhase.LOBBY
                    && matchManager.playerCount() == 0;

            if (emptyLobby) {
                planExecutor.clear();
            }

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

            if (server != null) {
                ui.refreshAfterReload(
                    server,
                    matchManager.session()
                );
            }

            String effective =
                EffectiveConfigSummary.describe(next);

            LOGGER.info(
                "AutoBattle config reloaded from {}: {}",
                AutoBattleConfigLoader.configPath()
                    .toAbsolutePath()
                    .normalize(),
                effective
            );

            return ConfigReloadResult.ok(
                language.format(
                    "commands.reload-effective",
                    "config",
                    AutoBattleConfigLoader.configPath()
                        .toAbsolutePath()
                        .normalize(),
                    "core_y",
                    next.arena().corePos().getY(),
                    "spawn_y",
                    next.arena().teamSpawns().y(),
                    "viewer_y",
                    next.arena().viewerSpawn().y()
                )
            );
        } catch (RuntimeException exception) {
            return ConfigReloadResult.failure(
                language.format(
                    "commands.reload-error",
                    "error",
                    exception.getMessage()
                )
            );
        }
    }
}
