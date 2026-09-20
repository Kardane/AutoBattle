package dev.kardane.autobattle.jev;

import dev.kardane.autobattle.match.MatchSession;
import dev.kardane.autobattle.match.PlayerSlot;
import dev.kardane.autobattle.robot.RobotZombie;
import dev.kardane.autobattle.tactics.RobotController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RobotStateSerializer {
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
            match.core().state().ownerUuid().orElse(null),
            match.core().state().contested(),
            match.core().distanceTo(selfEntity)
        );

        List<EnemySnapshot> enemies = new ArrayList<>();

        for (PlayerSlot slot : match.players()) {
            if (slot.playerUuid().equals(self.ownerUuid())) {
                continue;
            }

            RobotController enemy = match.robots()
                .byOwner(slot.playerUuid())
                .orElse(null);

            boolean alive = enemy != null && enemy.alive();
            float hp = 0.0F;
            Double distance = null;
            boolean attackingSelf = false;

            if (alive) {
                RobotZombie enemyEntity = enemy.entity()
                    .orElseThrow();

                hp = enemyEntity.getHealth();
                distance = (double) selfEntity.distanceTo(enemyEntity);
                attackingSelf =
                    enemyEntity.getTarget() == selfEntity;
            }

            enemies.add(
                new EnemySnapshot(
                    slot.playerUuid(),
                    slot.color(),
                    alive,
                    hp,
                    distance,
                    ranks.getOrDefault(slot.playerUuid(), 0),
                    slot.score().roundScore(),
                    attackingSelf,
                    0
                )
            );
        }

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
            coreSnapshot,
            enemies,
            selfSlot.doctrine().orElseThrow(),
            command
        );
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
