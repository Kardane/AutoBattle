package dev.kardane.autobattle.jev;

import dev.kardane.autobattle.AutoBattleConstants;
import dev.kardane.autobattle.config.RobotConfig;
import dev.kardane.autobattle.match.MatchSession;
import dev.kardane.autobattle.match.PlayerSlot;
import dev.kardane.autobattle.robot.RobotZombie;
import dev.kardane.autobattle.tactics.RobotController;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class RobotStateSerializer {
    private static final double DISTANCE_TREND_EPSILON =
        0.02D;

    private RobotConfig config;

    public RobotStateSerializer(RobotConfig config) {
        this.config = Objects.requireNonNull(
            config,
            "config"
        );
    }

    public void reloadConfig(RobotConfig config) {
        this.config = Objects.requireNonNull(
            config,
            "config"
        );
    }

    public RobotDecisionSnapshot snapshot(
        MatchSession match,
        RobotController self,
        long currentTick
    ) {
        PlayerSlot selfSlot = match.player(
            self.ownerUuid()
        ).orElseThrow();

        RobotZombie selfEntity = self.entity()
            .orElseThrow();

        Map<UUID, Integer> ranks = buildRanks(match);

        RobotSnapshot selfSnapshot = new RobotSnapshot(
            self.ownerUuid(),
            selfSlot.targetId(),
            selfSlot.team(),
            self.color(),
            selfEntity.getHealth(),
            selfEntity.getMaxHealth(),
            selfSlot.score().roundScore(),
            selfSlot.score().totalScore(),
            ranks.getOrDefault(self.ownerUuid(), 0),
            self.currentPlan()
                .map(plan -> plan.externalId())
                .orElse(null)
        );

        CoreSnapshot coreSnapshot = new CoreSnapshot(
            match.core().state().ownerTeam().orElse(null),
            match.core().state().contested(),
            match.core().distanceTo(selfEntity)
        );

        List<AllySnapshot> allies = new ArrayList<>();
        List<EnemySnapshot> enemies = new ArrayList<>();

        for (PlayerSlot slot : match.players()) {
            if (slot.playerUuid().equals(self.ownerUuid())) {
                continue;
            }

            RobotController enemy = match.robots()
                .byOwner(slot.playerUuid())
                .orElse(null);

            if (slot.team() == selfSlot.team()) {
                allies.add(
                    buildAllySnapshot(
                        match,
                        slot,
                        enemy,
                        selfEntity,
                        currentTick
                    )
                );
                continue;
            }

            boolean alive = enemy != null && enemy.alive();
            float hp = 0.0F;
            float maxHp = 0.0F;
            double hpRatio = 0.0D;
            Double distance = null;
            DistanceTrend trend = null;
            boolean withinEngageRange = false;
            boolean withinChaseRange = false;
            boolean attackingSelf = false;

            if (alive) {
                RobotZombie enemyEntity = enemy.entity()
                    .orElseThrow();

                hp = enemyEntity.getHealth();
                maxHp = enemyEntity.getMaxHealth();
                hpRatio = maxHp <= 0.0F
                    ? 0.0D
                    : hp / maxHp;
                distance =
                    (double) selfEntity.distanceTo(enemyEntity);
                trend = distanceTrend(
                    selfEntity,
                    enemyEntity
                );
                withinEngageRange =
                    distance <= config.engageLeashDistance();
                withinChaseRange =
                    distance <= config.chaseLeashDistance();
                attackingSelf =
                    enemyEntity.getTarget() == selfEntity;
            }

            enemies.add(
                new EnemySnapshot(
                    slot.playerUuid(),
                    slot.targetId(),
                    slot.team(),
                    slot.color(),
                    alive,
                    hp,
                    maxHp,
                    hpRatio,
                    distance,
                    trend,
                    withinEngageRange,
                    withinChaseRange,
                    ranks.getOrDefault(slot.playerUuid(), 0),
                    slot.score().roundScore(),
                    attackingSelf
                )
            );
        }

        int aliveAllies = 0;
        int aliveEnemies = 0;
        int alliesInsideCore = 0;
        int enemiesInsideCore = 0;

        for (RobotController controller :
            match.robots().alive()) {
            boolean ally =
                controller.team() == selfSlot.team();

            if (controller != self) {
                if (ally) {
                    aliveAllies++;
                } else {
                    aliveEnemies++;
                }
            }

            boolean insideCore = controller.entity()
                .filter(match.core()::isInside)
                .isPresent();

            if (insideCore) {
                if (ally) {
                    alliesInsideCore++;
                } else {
                    enemiesInsideCore++;
                }
            }
        }

        TeamContextSnapshot teamContext =
            new TeamContextSnapshot(
                selfSlot.team(),
                match.teamScore(selfSlot.team())
                    .totalScore(),
                match.teamScore(
                    selfSlot.team().opponent()
                ).totalScore(),
                aliveAllies,
                aliveEnemies,
                alliesInsideCore,
                enemiesInsideCore
            );

        int remainingSeconds = (int) Math.ceil(
            match.roundState().remainingTicks(currentTick)
                / 20.0D
        );

        ActiveCommandSnapshot command = selfSlot.runtime()
            .activeCommand()
            .filter(active -> active.active(currentTick))
            .map(active -> new ActiveCommandSnapshot(
                active.type(),
                active.remainingTicks(currentTick)
            ))
            .orElse(null);

        return new RobotDecisionSnapshot(
            match.matchId(),
            match.currentRound(),
            currentTick,
            remainingSeconds,
            selfSnapshot,
            teamContext,
            coreSnapshot,
            allies,
            enemies,
            selfSlot.doctrine().orElseThrow(),
            command
        );
    }

    private AllySnapshot buildAllySnapshot(
        MatchSession match,
        PlayerSlot slot,
        RobotController ally,
        RobotZombie selfEntity,
        long currentTick
    ) {
        boolean alive = ally != null && ally.alive();
        RobotZombie allyEntity = alive
            ? ally.entity().orElse(null)
            : null;

        if (allyEntity == null) {
            alive = false;
        }

        float hp = alive
            ? allyEntity.getHealth()
            : 0.0F;
        float maxHp = alive
            ? allyEntity.getMaxHealth()
            : 0.0F;
        double hpRatio = maxHp <= 0.0F
            ? 0.0D
            : hp / maxHp;
        Double distance = alive
            ? (double) selfEntity.distanceTo(allyEntity)
            : null;
        String currentPlan = alive && ally != null
            ? ally.currentPlan()
                .map(plan -> plan.externalId())
                .orElse(null)
            : null;
        String combatTarget = alive && ally != null
            ? ally.combatTargetOwnerSnapshot()
                .flatMap(ownerUuid ->
                    match.robots().byOwner(ownerUuid)
                )
                .map(RobotController::targetId)
                .orElse(null)
            : null;
        boolean insideCore = alive
            && match.core().isInside(allyEntity);
        boolean underAttack = alive
            && isUnderAttack(ally, currentTick);

        return new AllySnapshot(
            slot.playerUuid(),
            slot.targetId(),
            slot.team(),
            alive,
            hp,
            maxHp,
            hpRatio,
            distance,
            currentPlan,
            combatTarget,
            insideCore,
            underAttack
        );
    }

    private boolean isUnderAttack(
        RobotController controller,
        long currentTick
    ) {
        long lastDamageTick = controller.runtime()
            .lastDamageTick();

        return lastDamageTick != Long.MIN_VALUE
            && currentTick >= lastDamageTick
            && currentTick - lastDamageTick
                <= AutoBattleConstants.UNDER_ATTACK_WINDOW_TICKS;
    }

    private DistanceTrend distanceTrend(
        RobotZombie self,
        RobotZombie enemy
    ) {
        Vec3 relativePosition = enemy.position()
            .subtract(self.position());
        double distance = relativePosition.length();

        if (distance < 1.0E-6D) {
            return DistanceTrend.STABLE;
        }

        Vec3 relativeVelocity = enemy.getDeltaMovement()
            .subtract(self.getDeltaMovement());

        double radialVelocity =
            relativePosition.dot(relativeVelocity)
                / distance;

        if (radialVelocity > DISTANCE_TREND_EPSILON) {
            return DistanceTrend.SEPARATING;
        }

        if (radialVelocity < -DISTANCE_TREND_EPSILON) {
            return DistanceTrend.APPROACHING;
        }

        return DistanceTrend.STABLE;
    }

    private Map<UUID, Integer> buildRanks(
        MatchSession match
    ) {
        List<PlayerSlot> ordered = match.players()
            .stream()
            .sorted(
                Comparator
                    .comparingInt(
                        (PlayerSlot slot) ->
                            slot.score().totalScore()
                    )
                    .reversed()
                    .thenComparingInt(PlayerSlot::slotIndex)
            )
            .toList();

        Map<UUID, Integer> ranks = new HashMap<>();

        for (int index = 0;
             index < ordered.size();
             index++) {
            ranks.put(
                ordered.get(index).playerUuid(),
                index + 1
            );
        }

        return ranks;
    }
}
