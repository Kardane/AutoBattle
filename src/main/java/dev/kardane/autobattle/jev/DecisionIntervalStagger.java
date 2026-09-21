package dev.kardane.autobattle.jev;

import dev.kardane.autobattle.match.MatchSession;
import dev.kardane.autobattle.match.PlayerSlot;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class DecisionIntervalStagger {
    public boolean eligible(
        MatchSession match,
        UUID ownerUuid,
        long currentTick,
        int intervalTicks
    ) {
        if (intervalTicks <= 1) {
            return true;
        }

        List<PlayerSlot> active = match.players()
            .stream()
            .filter(slot -> !slot.forfeited())
            .sorted(
                Comparator.comparingInt(
                    PlayerSlot::slotIndex
                )
            )
            .toList();

        if (active.size() <= 1) {
            return true;
        }

        int ordinal = -1;

        for (int index = 0;
             index < active.size();
             index++) {
            if (active.get(index)
                .playerUuid()
                .equals(ownerUuid)) {
                ordinal = index;
                break;
            }
        }

        if (ordinal < 0) {
            return true;
        }

        int offset = offsetFor(
            ordinal,
            active.size(),
            intervalTicks
        );

        return Math.floorMod(
            currentTick,
            intervalTicks
        ) == offset;
    }

    static int offsetFor(
        int ordinal,
        int participantCount,
        int intervalTicks
    ) {
        if (ordinal < 0
            || participantCount < 1
            || ordinal >= participantCount
            || intervalTicks < 1) {
            throw new IllegalArgumentException(
                "Invalid stagger parameters"
            );
        }

        return (int) (
            (long) ordinal * intervalTicks
                / participantCount
        );
    }
}
