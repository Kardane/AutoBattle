package dev.kardane.autobattle.combat;

import java.util.Objects;
import java.util.UUID;

public record CombatRecord(
    UUID attackerOwnerUuid,
    UUID victimOwnerUuid,
    float damage,
    long tick
) {
    public CombatRecord {
        Objects.requireNonNull(attackerOwnerUuid, "attackerOwnerUuid");
        Objects.requireNonNull(victimOwnerUuid, "victimOwnerUuid");

        if (damage < 0.0F) {
            throw new IllegalArgumentException(
                "damage must not be negative"
            );
        }
    }
}
