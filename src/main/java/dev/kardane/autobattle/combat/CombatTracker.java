package dev.kardane.autobattle.combat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CombatTracker {
    private final int assistWindowTicks;
    private final Map<UUID, Deque<CombatRecord>>
        recentDamageByVictim = new LinkedHashMap<>();

    public CombatTracker(int assistWindowTicks) {
        if (assistWindowTicks < 1) {
            throw new IllegalArgumentException(
                "assistWindowTicks must be positive"
            );
        }

        this.assistWindowTicks = assistWindowTicks;
    }

    public void recordDamage(
        UUID attackerOwner,
        UUID victimOwner,
        float amount,
        long tick
    ) {
        if (attackerOwner.equals(victimOwner)) {
            return;
        }

        Deque<CombatRecord> records =
            recentDamageByVictim.computeIfAbsent(
                victimOwner,
                ignored -> new ArrayDeque<>()
            );

        prune(records, tick);

        records.addLast(
            new CombatRecord(
                attackerOwner,
                victimOwner,
                amount,
                tick
            )
        );
    }

    public KillResolution resolveDeath(
        UUID victimOwner,
        UUID killerOwner,
        long tick
    ) {
        Deque<CombatRecord> records =
            recentDamageByVictim.remove(victimOwner);

        if (records == null) {
            return new KillResolution(
                victimOwner,
                killerOwner,
                Set.of()
            );
        }

        prune(records, tick);

        Set<UUID> assists = new LinkedHashSet<>();

        for (CombatRecord record : records) {
            UUID attacker = record.attackerOwnerUuid();

            if (attacker.equals(victimOwner)) {
                continue;
            }

            if (killerOwner != null
                && attacker.equals(killerOwner)) {
                continue;
            }

            assists.add(attacker);
        }

        return new KillResolution(
            victimOwner,
            killerOwner,
            assists
        );
    }

    public void clearFor(UUID ownerUuid) {
        recentDamageByVictim.remove(ownerUuid);

        for (Deque<CombatRecord> records :
            recentDamageByVictim.values()) {
            records.removeIf(
                record -> record.attackerOwnerUuid()
                    .equals(ownerUuid)
            );
        }
    }

    public void reset() {
        recentDamageByVictim.clear();
    }

    private void prune(
        Deque<CombatRecord> records,
        long currentTick
    ) {
        long oldestAllowed =
            currentTick - assistWindowTicks;

        while (!records.isEmpty()
            && records.peekFirst().tick() < oldestAllowed) {
            records.removeFirst();
        }
    }
}
