package dev.kardane.autobattle.ui;

import dev.kardane.autobattle.config.AutoBattleConfig;
import dev.kardane.autobattle.match.MatchSession;
import dev.kardane.autobattle.match.PlayerSlot;
import dev.kardane.autobattle.tactics.PlanExecutor;
import dev.kardane.autobattle.tactics.RobotController;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.UUID;

public final class UiCoordinator {
    private static final int ACTION_BAR_INTERVAL_TICKS = 5;
    private static final int BOSS_BAR_INTERVAL_TICKS = 20;

    private final AutoBattleConfig config;
    private final PlanExecutor planExecutor;
    private final BossBarUi bossBar = new BossBarUi();
    private final ActionBarUi actionBar = new ActionBarUi();
    private final ChatAnnouncer chat = new ChatAnnouncer();
    private final SidebarUi sidebar = new SidebarUi();

    private UUID lastCoreOwner;

    public UiCoordinator(
        AutoBattleConfig config,
        PlanExecutor planExecutor
    ) {
        this.config = Objects.requireNonNull(config, "config");
        this.planExecutor = Objects.requireNonNull(
            planExecutor,
            "planExecutor"
        );
    }

    public void onRoundStart(
        MinecraftServer server,
        MatchSession match,
        long currentTick
    ) {
        bossBar.clear();

        for (PlayerSlot slot : match.players()) {
            ServerPlayer player = server.getPlayerList()
                .getPlayer(slot.playerUuid());

            if (player != null) {
                bossBar.addPlayer(player);
            }
        }

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
    }

    public void tickRound(
        MinecraftServer server,
        MatchSession match,
        long currentTick
    ) {
        if (currentTick % BOSS_BAR_INTERVAL_TICKS == 0L) {
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
            }

            lastCoreOwner = currentCoreOwner;
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
    }

    public void onRoundEnd(
        MinecraftServer server,
        MatchSession match
    ) {
        chat.roundEnded(server, match);
        bossBar.clear();
        sidebar.clear(server);
        lastCoreOwner = null;
    }

    public void cleanup() {
        bossBar.clear();
        lastCoreOwner = null;
    }
}
