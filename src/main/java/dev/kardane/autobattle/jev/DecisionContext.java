package dev.kardane.autobattle.jev;

import java.util.UUID;

public record DecisionContext(
    UUID matchId,
    int round,
    UUID ownerUuid,
    UUID robotEntityUuid,
    int doctrineVersion,
    long generation,
    long requestedTick,
    DecisionTrigger trigger,
    int candidatesHash
) {
}
