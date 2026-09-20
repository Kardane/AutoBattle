package dev.kardane.autobattle.config;

import dev.kardane.autobattle.command.PlayerCommandService;
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

import java.util.Objects;
import java.util.function.Function;

public final class ConfigReloadService {
    private final MatchManager matchManager;
    private final RobotFactory robotFactory;
    private final PlanExecutor planExecutor;
    private final PlayerCommandService commandService;
    private final JevDecisionService decisionService;
    private final DoctrineValidator doctrineValidator;
    private final DialogService dialogs;
    private final UiCoordinator ui;
    private final LanguageService language;
    private final Function<AutoBattleConfig, JevClient>
        jevClientFactory;

    public ConfigReloadService(
        MatchManager matchManager,
        RobotFactory robotFactory,
        PlanExecutor planExecutor,
        PlayerCommandService commandService,
        JevDecisionService decisionService,
        DoctrineValidator doctrineValidator,
        DialogService dialogs,
        UiCoordinator ui,
        LanguageService language,
        Function<AutoBattleConfig, JevClient> jevClientFactory
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

            return ConfigReloadResult.ok(
                language.format(
                    "commands.reload-applied",
                    "config",
                    AutoBattleConfigLoader.configPath(),
                    "messages",
                    LanguageConfigLoader.messagePath()
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
