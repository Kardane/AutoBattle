package dev.kardane.autobattle.match;

import dev.kardane.autobattle.AutoBattleConstants;
import dev.kardane.autobattle.command.PlayerCommandService;
import dev.kardane.autobattle.combat.CombatTracker;
import dev.kardane.autobattle.combat.DamageRules;
import dev.kardane.autobattle.combat.KillResolution;
import dev.kardane.autobattle.config.AutoBattleConfig;
import dev.kardane.autobattle.config.SpawnPoint;
import dev.kardane.autobattle.core.CoreController;
import dev.kardane.autobattle.robot.RobotColor;
import dev.kardane.autobattle.robot.RobotFactory;
import dev.kardane.autobattle.robot.RobotRegistry;
import dev.kardane.autobattle.robot.RobotRespawnManager;
import dev.kardane.autobattle.robot.RobotRuntimeState;
import dev.kardane.autobattle.robot.RobotZombie;
import dev.kardane.autobattle.jev.JevDecisionService;
import dev.kardane.autobattle.tactics.PlanExecutor;
import dev.kardane.autobattle.tactics.RobotController;
import dev.kardane.autobattle.ui.UiCoordinator;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class MatchManager {
    private final AutoBattleConfig config;
    private final RobotFactory robotFactory;
    private final PlanExecutor planExecutor;
    private final DamageRules damageRules = new DamageRules();
    private final CombatTracker combatTracker = new CombatTracker();
    private final RobotRespawnManager respawnManager;
    private final PlayerCommandService commandService;
    private final JevDecisionService decisionService;
    private final UiCoordinator ui;
    private final MatchSession session;
    private final Map<UUID, PendingRobotDamage> pendingDamage =
        new HashMap<>();

    private long serverTick;

    public MatchManager(
        AutoBattleConfig config,
        RobotRegistry robotRegistry,
        RobotFactory robotFactory,
        PlanExecutor planExecutor,
        PlayerCommandService commandService,
        JevDecisionService decisionService,
        UiCoordinator ui
    ) {
        this.config = config;
        this.robotFactory = robotFactory;
        this.planExecutor = planExecutor;
        this.commandService = commandService;
        this.decisionService = decisionService;
        this.ui = ui;
        this.respawnManager = new RobotRespawnManager(
            config,
            robotFactory,
            planExecutor
        );
        this.session = new MatchSession(
            UUID.randomUUID(),
            robotRegistry,
            new CoreController(config.arena())
        );
    }

    public AutoBattleConfig config() {
        return config;
    }

    public MatchSession session() {
        return session;
    }

    public long serverTick() {
        return serverTick;
    }

    public void tick(MinecraftServer server) {
        serverTick = server.getTickCount();

        if (session.phase() == MatchPhase.COUNTDOWN) {
            if (serverTick - session.phaseStartedTick()
                >= AutoBattleConstants.COUNTDOWN_TICKS) {
                startPrototypeRound(server);
            }
            return;
        }

        if (session.phase() != MatchPhase.ROUND_ACTIVE) {
            return;
        }

        commandService.tick(session, serverTick);
        respawnManager.tick(server, session, serverTick);
        tickRegen();
        session.core().tick(session, serverTick);
        decisionService.tick(
            server,
            session,
            serverTick
        );
        ui.tickRound(server, session, serverTick);

        if (session.roundState().expired(serverTick)) {
            stopPrototypeRound(server);
        }
    }

    public boolean allowDamage(
        LivingEntity victim,
        DamageSource source,
        float amount
    ) {
        boolean allowed = damageRules.allowDamage(
            victim,
            source,
            this
        );

        if (!allowed) {
            return false;
        }

        if (!(victim instanceof RobotZombie victimRobot)) {
            return true;
        }

        Entity attackerEntity = source.getEntity();

        if (!(attackerEntity instanceof RobotZombie attackerRobot)) {
            return true;
        }

        if (amount <= 0.0F) {
            return true;
        }

        pendingDamage.put(
            victimRobot.getUUID(),
            new PendingRobotDamage(
                attackerRobot.ownerUuid(),
                victimRobot.ownerUuid(),
                amount,
                serverTick
            )
        );

        return true;
    }

    public void handleAfterDamage(
        LivingEntity victim,
        DamageSource source,
        float baseDamageTaken,
        float damageTaken,
        boolean blocked
    ) {
        if (!(victim instanceof RobotZombie victimRobot)) {
            return;
        }

        PendingRobotDamage pending =
            pendingDamage.remove(victimRobot.getUUID());

        if (pending == null || blocked || damageTaken <= 0.0F) {
            return;
        }

        applyRobotDamage(
            pending,
            damageTaken
        );
    }

    public void handleAfterDeath(
        LivingEntity victim,
        DamageSource source
    ) {
        if (!(victim instanceof RobotZombie victimRobot)) {
            return;
        }

        if (!victimRobot.matchId().equals(session.matchId())) {
            return;
        }

        PendingRobotDamage pending =
            pendingDamage.remove(victimRobot.getUUID());

        if (pending != null) {
            applyRobotDamage(
                pending,
                (float) Math.min(
                    pending.amount(),
                    RobotFactory.MAX_HEALTH
                )
            );
        }

        UUID killerOwner = null;
        Entity killerEntity = source.getEntity();

        if (killerEntity instanceof RobotZombie killerRobot
            && killerRobot.matchId().equals(session.matchId())
            && !killerRobot.ownerUuid()
                .equals(victimRobot.ownerUuid())) {
            killerOwner = killerRobot.ownerUuid();
        }

        KillResolution resolution = combatTracker.resolveDeath(
            victimRobot.ownerUuid(),
            killerOwner,
            serverTick
        );

        session.player(victimRobot.ownerUuid()).ifPresent(
            slot -> slot.score().addDeath()
        );

        resolution.killerOwner().flatMap(session::player).ifPresent(
            slot -> slot.score().addKill(
                AutoBattleConstants.KILL_SCORE
            )
        );

        PlayerSlot victimSlot = session.player(
            victimRobot.ownerUuid()
        ).orElse(null);

        PlayerSlot killerSlot = resolution.killerOwner()
            .flatMap(session::player)
            .orElse(null);

        if (killerSlot != null
            && victimSlot != null
            && victimRobot.level() instanceof ServerLevel level) {
            ui.onRobotKilled(
                level.getServer(),
                session,
                killerSlot,
                victimSlot
            );
        }

        for (UUID assistOwner : resolution.assistOwnerUuids()) {
            session.player(assistOwner).ifPresent(
                slot -> slot.score().addAssist(
                    AutoBattleConstants.ASSIST_SCORE
                )
            );
        }

        RobotController deadController = planExecutor
            .byOwner(victimRobot.ownerUuid())
            .orElse(null);

        if (deadController != null) {
            respawnManager.schedule(
                deadController,
                serverTick
            );
        }

        requestRedecisionForTargetDeath(
            victimRobot.ownerUuid()
        );
    }

    public boolean join(ServerPlayer player) {
        UUID uuid = player.getUUID();

        if (session.phase() != MatchPhase.LOBBY) {
            return false;
        }

        if (session.player(uuid).isPresent()) {
            return true;
        }

        int slotIndex = nextFreeSlot();
        RobotColor[] colors = RobotColor.values();

        if (slotIndex < 0 || slotIndex >= colors.length) {
            return false;
        }

        session.addPlayer(
            new PlayerSlot(uuid, colors[slotIndex], slotIndex)
        );

        return true;
    }

    public boolean leave(ServerPlayer player) {
        UUID uuid = player.getUUID();

        if (session.phase() != MatchPhase.LOBBY) {
            session.player(uuid).ifPresent(PlayerSlot::forfeit);
            return false;
        }

        if (session.player(uuid).isEmpty()) {
            return false;
        }

        session.removePlayer(uuid);
        return true;
    }

    public Optional<Boolean> toggleReady(ServerPlayer player) {
        return session.player(player.getUUID()).map(slot -> {
            if (session.phase() != MatchPhase.LOBBY) {
                return slot.ready();
            }

            boolean next = !slot.ready();
            slot.setReady(next);
            return next;
        });
    }

    public Optional<PlayerSlot> playerSlot(UUID playerUuid) {
        return session.player(playerUuid);
    }

    public int playerCount() {
        return session.players().size();
    }

    public int readyCount() {
        return (int) session.players().stream()
            .filter(PlayerSlot::ready)
            .count();
    }

    public boolean canStart() {
        return playerCount() >= config.minimumPlayers()
            && readyCount() == playerCount();
    }

    public boolean beginDoctrineSetupIfReady() {
        if (session.phase() != MatchPhase.LOBBY
            || !canStart()) {
            return false;
        }

        session.setPhase(
            MatchPhase.DOCTRINE_SETUP,
            serverTick
        );

        return true;
    }

    public boolean allDoctrinesSubmitted() {
        return playerCount() >= config.minimumPlayers()
            && session.players().stream()
                .filter(slot -> !slot.forfeited())
                .allMatch(slot -> slot.doctrine().isPresent());
    }

    public boolean beginCountdownIfDoctrinesReady() {
        if (session.phase() != MatchPhase.DOCTRINE_SETUP
            || !allDoctrinesSubmitted()) {
            return false;
        }

        session.setPhase(
            MatchPhase.COUNTDOWN,
            serverTick
        );

        return true;
    }

    public boolean markReviewReady(ServerPlayer player) {
        PlayerSlot slot = session.player(
            player.getUUID()
        ).orElse(null);

        if (slot == null
            || slot.forfeited()
            || session.phase() != MatchPhase.ROUND_REVIEW) {
            return false;
        }

        slot.runtime().setReviewReady(true);

        if (!allActivePlayersReadyForReview()) {
            return true;
        }

        if (session.currentRound() >= config.roundCount()) {
            session.setPhase(
                MatchPhase.FINISHED,
                serverTick
            );
            return true;
        }

        resetReviewReady();

        session.setPhase(
            MatchPhase.DOCTRINE_EDIT,
            serverTick
        );

        return true;
    }

    public boolean markDoctrineEditDone(
        ServerPlayer player
    ) {
        PlayerSlot slot = session.player(
            player.getUUID()
        ).orElse(null);

        if (slot == null
            || slot.forfeited()
            || session.phase() != MatchPhase.DOCTRINE_EDIT) {
            return false;
        }

        slot.runtime().setReviewReady(true);

        if (allActivePlayersReadyForReview()) {
            session.setPhase(
                MatchPhase.COUNTDOWN,
                serverTick
            );
        }

        return true;
    }

    private boolean allActivePlayersReadyForReview() {
        return session.players()
            .stream()
            .filter(slot -> !slot.forfeited())
            .allMatch(slot -> slot.runtime().reviewReady());
    }

    private void resetReviewReady() {
        for (PlayerSlot slot : session.players()) {
            slot.runtime().setReviewReady(false);
        }
    }

    public boolean startPrototypeRound(MinecraftServer server) {
        if (session.phase() == MatchPhase.ROUND_ACTIVE) {
            return false;
        }

        long eligiblePlayers = session.players().stream()
            .filter(slot -> !slot.forfeited())
            .count();

        if (eligiblePlayers < config.minimumPlayers()) {
            return false;
        }

        if (session.currentRound() == 0
            && !allDoctrinesSubmitted()) {
            return false;
        }

        ServerLevel level = server.getLevel(
            config.arena().dimension()
        );

        if (level == null) {
            return false;
        }

        planExecutor.clear();
        combatTracker.reset();
        pendingDamage.clear();
        session.core().reset();

        int nextRound = session.currentRound() <= 0
            ? 1
            : Math.min(
                session.currentRound() + 1,
                config.roundCount()
            );

        session.setCurrentRound(nextRound);

        for (PlayerSlot slot : session.players()) {
            if (slot.forfeited()) {
                continue;
            }

            SpawnPoint spawn = config.arena()
                .robotSpawns()
                .get(slot.slotIndex());

            ServerPlayer owner = server.getPlayerList()
                .getPlayer(slot.playerUuid());

            Component ownerName = owner != null
                ? owner.getName()
                : slot.color().displayName();

            slot.score().resetRound();
            slot.runtime().resetForRound();

            RobotZombie robot = robotFactory.spawnRobot(
                level,
                session.matchId(),
                slot.playerUuid(),
                ownerName,
                slot.color(),
                spawn.position(),
                spawn.yaw()
            );

            planExecutor.register(robot, serverTick);
        }

        session.roundState().start(
            nextRound,
            serverTick,
            config.roundDurationTicks()
        );

        session.setPhase(
            MatchPhase.ROUND_ACTIVE,
            serverTick
        );

        ui.onRoundStart(
            server,
            session,
            serverTick
        );

        return true;
    }

    public boolean stopPrototypeRound() {
        return stopPrototypeRound(null);
    }

    public boolean stopPrototypeRound(
        MinecraftServer server
    ) {
        if (session.phase() != MatchPhase.ROUND_ACTIVE) {
            return false;
        }

        session.roundState().stop();
        respawnManager.cancelAll(session);
        planExecutor.clear();
        combatTracker.reset();
        pendingDamage.clear();

        resetReviewReady();

        session.setPhase(
            MatchPhase.ROUND_REVIEW,
            serverTick
        );

        if (server != null) {
            ui.onRoundEnd(server, session);
        } else {
            ui.cleanup();
        }

        return true;
    }

    public String statusLine() {
        return "phase=" + session.phase()
            + ", round=" + session.currentRound()
            + ", players=" + playerCount()
            + ", ready=" + readyCount()
            + ", minimum=" + config.minimumPlayers()
            + ", coreOwner="
            + session.core().state().ownerUuid()
                .flatMap(session::player)
                .map(slot -> slot.color().name())
                .orElse("none");
    }

    public void handleDisconnect(ServerPlayer player) {
        leave(player);
    }

    private void applyRobotDamage(
        PendingRobotDamage pending,
        float amount
    ) {
        combatTracker.recordDamage(
            pending.attackerOwnerUuid(),
            pending.victimOwnerUuid(),
            amount,
            serverTick
        );

        session.player(pending.attackerOwnerUuid()).ifPresent(
            slot -> slot.score().addDamageDealt(amount)
        );

        session.player(pending.victimOwnerUuid()).ifPresent(
            slot -> slot.score().addDamageTaken(amount)
        );

        planExecutor.byOwner(
            pending.victimOwnerUuid()
        ).ifPresent(controller -> {
            RobotRuntimeState runtime = controller.runtime();
            runtime.markDamaged(serverTick);
            controller.requestRedecision();
        });
    }

    private void tickRegen() {
        for (RobotController controller :
            session.robots().alive()) {
            RobotZombie robot = controller.entity()
                .orElse(null);

            if (robot == null) {
                continue;
            }

            if (robot.getHealth() >= robot.getMaxHealth()) {
                controller.runtime().setNextRegenTick(
                    Long.MAX_VALUE
                );
                continue;
            }

            RobotRuntimeState runtime = controller.runtime();

            if (!runtime.regenEligible(
                serverTick,
                AutoBattleConstants.REGEN_DELAY_TICKS
            )) {
                continue;
            }

            if (runtime.nextRegenTick() == Long.MAX_VALUE) {
                runtime.setNextRegenTick(serverTick);
            }

            if (serverTick < runtime.nextRegenTick()) {
                continue;
            }

            robot.setHealth(
                Math.min(
                    robot.getMaxHealth(),
                    robot.getHealth()
                        + AutoBattleConstants.REGEN_AMOUNT
                )
            );

            runtime.setNextRegenTick(
                serverTick
                    + AutoBattleConstants.REGEN_INTERVAL_TICKS
            );
        }
    }

    private void requestRedecisionForTargetDeath(
        UUID deadOwnerUuid
    ) {
        for (RobotController controller :
            session.robots().all()) {
            controller.currentPlan().ifPresent(plan -> {
                if (deadOwnerUuid.equals(
                    plan.targetOwnerUuid()
                )) {
                    controller.requestRedecision();
                }
            });
        }
    }

    private int nextFreeSlot() {
        Set<Integer> used = new HashSet<>();

        for (PlayerSlot slot : session.players()) {
            used.add(slot.slotIndex());
        }

        for (int index = 0;
             index < RobotColor.values().length;
             index++) {
            if (!used.contains(index)) {
                return index;
            }
        }

        return -1;
    }

    private record PendingRobotDamage(
        UUID attackerOwnerUuid,
        UUID victimOwnerUuid,
        float amount,
        long tick
    ) {
    }
}
