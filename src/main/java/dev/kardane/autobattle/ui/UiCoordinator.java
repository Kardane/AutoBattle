package dev.kardane.autobattle.ui;

import dev.kardane.autobattle.config.AutoBattleConfig;
import dev.kardane.autobattle.config.LanguageService;
import dev.kardane.autobattle.match.MatchSession;
import dev.kardane.autobattle.match.PlayerSlot;
import dev.kardane.autobattle.review.RoundReviewService;
import dev.kardane.autobattle.tactics.PlanExecutor;
import dev.kardane.autobattle.tactics.RobotController;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.UUID;

public final class UiCoordinator {
    private static final int ACTION_BAR_INTERVAL_TICKS = 5;
    private static final int BOSS_BAR_INTERVAL_TICKS = 20;

    private AutoBattleConfig config;
    private final PlanExecutor planExecutor;
    private final DialogService dialogs;
    private final RoundReviewService reviewService;
    private final BossBarUi bossBar;
    private final ActionBarUi actionBar;
    private final ChatAnnouncer chat;
    private final SidebarUi sidebar;
    private final GameSoundService sounds =
        new GameSoundService();

    private UUID lastCoreOwner;

    public UiCoordinator(
        AutoBattleConfig config,
        PlanExecutor planExecutor,
        DialogService dialogs,
        RoundReviewService reviewService,
        LanguageService language
    ) {
        this.config = Objects.requireNonNull(config, "config");
        this.planExecutor = Objects.requireNonNull(
            planExecutor,
            "planExecutor"
        );
        this.dialogs = Objects.requireNonNull(
            dialogs,
            "dialogs"
        );
        this.reviewService = Objects.requireNonNull(
            reviewService,
            "reviewService"
        );

        LanguageService messages =
            Objects.requireNonNull(
                language,
                "language"
            );

        this.bossBar = new BossBarUi(messages);
        this.actionBar = new ActionBarUi(messages);
        this.chat = new ChatAnnouncer(messages);
        this.sidebar = new SidebarUi(messages);
    }

    public void reloadConfig(AutoBattleConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public void onDoctrineSetup(
        MinecraftServer server,
        MatchSession match
    ) {
        forEachActivePlayer(
            server,
            match,
            dialogs::openDoctrineSetup
        );
    }

    public void onRoundStart(
        MinecraftServer server,
        MatchSession match,
        long currentTick
    ) {
        syncBossBarPlayers(server);

        lastCoreOwner = match.core()
            .state()
            .ownerUuid()
            .orElse(null);

        bossBar.updateRound(
            match,
            currentTick,
            config.roundCount(),
            config.roundDurationTicks()
        );

        sidebar.create(server);
        sidebar.update(server, match);
        chat.roundStarted(server, match);
        sounds.roundStarted(server, match);
    }

    public void tickRound(
        MinecraftServer server,
        MatchSession match,
        long currentTick
    ) {
        if (currentTick % BOSS_BAR_INTERVAL_TICKS == 0L) {
            syncBossBarPlayers(server);

            bossBar.updateRound(
                match,
                currentTick,
                config.roundCount(),
                config.roundDurationTicks()
            );
            sidebar.update(server, match);
        }

        if (currentTick % ACTION_BAR_INTERVAL_TICKS == 0L) {
            for (PlayerSlot slot : match.players()) {
                ServerPlayer player = server.getPlayerList()
                    .getPlayer(slot.playerUuid());

                if (player == null) {
                    continue;
                }

                RobotController controller = planExecutor
                    .byOwner(slot.playerUuid())
                    .orElse(null);

                actionBar.updatePlayer(
                    player,
                    slot,
                    controller,
                    currentTick
                );
            }
        }

        UUID currentCoreOwner = match.core()
            .state()
            .ownerUuid()
            .orElse(null);

        if (!Objects.equals(
            currentCoreOwner,
            lastCoreOwner
        )) {
            if (currentCoreOwner != null) {
                match.player(currentCoreOwner).ifPresent(
                    owner -> chat.coreCaptured(
                        server,
                        match,
                        owner
                    )
                );
                sounds.coreCaptured(server, match);
            }

            lastCoreOwner = currentCoreOwner;
        }
    }

    public void tickBetweenRounds(
        MinecraftServer server,
        MatchSession match,
        long currentTick
    ) {
        if (currentTick % BOSS_BAR_INTERVAL_TICKS == 0L) {
            syncBossBarPlayers(server);

            bossBar.updateIntermission(
                match,
                currentTick,
                config.roundCount(),
                config.countdownTicks()
            );

            sidebar.create(server);
            sidebar.update(server, match);
        }

        if (currentTick % ACTION_BAR_INTERVAL_TICKS == 0L) {
            for (PlayerSlot slot : match.players()) {
                if (slot.forfeited()) {
                    continue;
                }

                ServerPlayer player = server.getPlayerList()
                    .getPlayer(slot.playerUuid());

                if (player == null) {
                    continue;
                }

                actionBar.updateIntermission(
                    player,
                    slot,
                    match.phase(),
                    match.currentRound(),
                    config.roundCount()
                );
            }
        }
    }

    public void onRobotKilled(
        MinecraftServer server,
        MatchSession match,
        PlayerSlot killer,
        PlayerSlot victim
    ) {
        chat.robotKilled(
            server,
            match,
            killer,
            victim
        );
        sounds.robotKilled(server, match);
    }

    public void onRoundEnd(
        MinecraftServer server,
        MatchSession match
    ) {
        chat.roundEnded(server, match);
        sounds.roundEnded(server, match);
        syncBossBarPlayers(server);
        bossBar.updateIntermission(
            match,
            match.phaseStartedTick(),
            config.roundCount(),
            config.countdownTicks()
        );
        sidebar.create(server);
        sidebar.update(server, match);

        for (PlayerSlot slot : match.players()) {
            if (slot.forfeited()) {
                continue;
            }

            ServerPlayer player = server.getPlayerList()
                .getPlayer(slot.playerUuid());

            if (player == null) {
                continue;
            }

            dialogs.openRoundReview(
                player,
                reviewService.build(
                    match,
                    slot.playerUuid()
                )
            );
        }
    }

    public void onReviewCompleted(
        MinecraftServer server,
        MatchSession match,
        ServerPlayer player
    ) {
        chat.reviewCompleted(server, match, player);
    }

    public void onDoctrineEditCompleted(
        MinecraftServer server,
        MatchSession match,
        ServerPlayer player
    ) {
        chat.doctrineEditCompleted(server, match, player);
    }

    public void onDoctrineEdit(
        MinecraftServer server,
        MatchSession match
    ) {
        for (PlayerSlot slot : match.players()) {
            if (slot.forfeited()) {
                continue;
            }

            ServerPlayer player = server.getPlayerList()
                .getPlayer(slot.playerUuid());

            if (player == null
                || slot.doctrine().isEmpty()) {
                continue;
            }

            dialogs.openDoctrineEditSelect(
                player,
                slot.doctrine().orElseThrow()
            );
        }
    }

    public void onFinished(
        MinecraftServer server,
        MatchSession match
    ) {
        sounds.matchFinished(server, match);
        chat.finalStandings(server, match);

        forEachActivePlayer(
            server,
            match,
            player -> dialogs.openFinalResult(
                player,
                match
            )
        );
    }

    public void cleanup() {
        bossBar.clear();
        lastCoreOwner = null;
    }

    public void cleanup(MinecraftServer server) {
        bossBar.clear();
        sidebar.clear(server);
        lastCoreOwner = null;
    }

    private void syncBossBarPlayers(
        MinecraftServer server
    ) {
        for (ServerPlayer player :
            server.getPlayerList().getPlayers()) {
            bossBar.addPlayer(player);
        }
    }

    private void forEachActivePlayer(
        MinecraftServer server,
        MatchSession match,
        java.util.function.Consumer<ServerPlayer> action
    ) {
        for (PlayerSlot slot : match.players()) {
            if (slot.forfeited()) {
                continue;
            }

            ServerPlayer player = server.getPlayerList()
                .getPlayer(slot.playerUuid());

            if (player != null) {
                action.accept(player);
            }
        }
    }
}
