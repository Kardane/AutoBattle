package dev.kardane.autobattle.core;

import dev.kardane.autobattle.match.BattleTeam;

import java.util.Optional;

public enum CoreOccupancy {
    EMPTY,
    RED_ONLY,
    BLUE_ONLY,
    CONTESTED;

    public static CoreOccupancy of(
        long redInside,
        long blueInside
    ) {
        if (redInside < 0L || blueInside < 0L) {
            throw new IllegalArgumentException(
                "CORE occupancy counts must not be negative"
            );
        }

        if (redInside == 0L && blueInside == 0L) {
            return EMPTY;
        }

        if (redInside > 0L && blueInside > 0L) {
            return CONTESTED;
        }

        return redInside > 0L
            ? RED_ONLY
            : BLUE_ONLY;
    }

    public boolean contested() {
        return this == CONTESTED;
    }

    public Optional<BattleTeam> soleTeam() {
        return switch (this) {
            case RED_ONLY -> Optional.of(BattleTeam.RED);
            case BLUE_ONLY -> Optional.of(BattleTeam.BLUE);
            case EMPTY, CONTESTED -> Optional.empty();
        };
    }
}
