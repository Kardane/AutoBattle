package dev.kardane.autobattle.jev;

import dev.kardane.autobattle.doctrine.Doctrine;

import java.util.List;
import java.util.UUID;

public record RobotDecisionSnapshot(
    UUID matchId,
    int round,
    long serverTick,
    int remainingRoundSeconds,
    RobotSnapshot self,
    TeamContextSnapshot teamContext,
    CoreSnapshot core,
    List<AllySnapshot> allies,
    List<EnemySnapshot> enemies,
    Doctrine doctrine,
    ActiveCommandSnapshot command
) {
    public RobotDecisionSnapshot {
        allies = List.copyOf(allies);
        enemies = List.copyOf(enemies);
    }

    /**
     * Compatibility constructor for callers that still provide one
     * participant list containing both allies and enemies.
     */
    public RobotDecisionSnapshot(
        UUID matchId,
        int round,
        long serverTick,
        int remainingRoundSeconds,
        RobotSnapshot self,
        TeamContextSnapshot teamContext,
        CoreSnapshot core,
        List<EnemySnapshot> participants,
        Doctrine doctrine,
        ActiveCommandSnapshot command
    ) {
        this(
            matchId,
            round,
            serverTick,
            remainingRoundSeconds,
            self,
            teamContext,
            core,
            legacyAllies(self, participants),
            participants.stream()
                .filter(participant ->
                    participant.team() != self.team()
                )
                .toList(),
            doctrine,
            command
        );
    }

    private static List<AllySnapshot> legacyAllies(
        RobotSnapshot self,
        List<EnemySnapshot> participants
    ) {
        return participants.stream()
            .filter(participant ->
                participant.team() == self.team()
            )
            .map(participant -> new AllySnapshot(
                participant.ownerUuid(),
                participant.targetId(),
                participant.team(),
                participant.alive(),
                participant.hp(),
                participant.maxHp(),
                participant.hpRatio(),
                participant.distance(),
                null,
                null,
                false,
                false
            ))
            .toList();
    }
}
