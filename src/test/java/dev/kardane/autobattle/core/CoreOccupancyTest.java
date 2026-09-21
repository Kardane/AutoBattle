package dev.kardane.autobattle.core;

import dev.kardane.autobattle.match.BattleTeam;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class CoreOccupancyTest {
    @Test
    void emptyCoreIsNotContested() {
        CoreOccupancy occupancy =
            CoreOccupancy.of(0, 0);

        assertEquals(CoreOccupancy.EMPTY, occupancy);
        assertFalse(occupancy.contested());
        assertTrue(occupancy.soleTeam().isEmpty());
    }

    @Test
    void multipleRobotsFromOneTeamStillCapture() {
        CoreOccupancy oneRed =
            CoreOccupancy.of(1, 0);
        CoreOccupancy fiveRed =
            CoreOccupancy.of(5, 0);
        CoreOccupancy eightBlue =
            CoreOccupancy.of(0, 8);

        assertEquals(
            BattleTeam.RED,
            oneRed.soleTeam().orElseThrow()
        );
        assertEquals(
            BattleTeam.RED,
            fiveRed.soleTeam().orElseThrow()
        );
        assertEquals(
            BattleTeam.BLUE,
            eightBlue.soleTeam().orElseThrow()
        );

        assertFalse(fiveRed.contested());
        assertFalse(eightBlue.contested());
    }

    @Test
    void anyMixedTeamPresenceIsContested() {
        for (long[] counts : new long[][]{
            {1, 1},
            {5, 1},
            {1, 8},
            {5, 3},
            {8, 8}
        }) {
            CoreOccupancy occupancy =
                CoreOccupancy.of(
                    counts[0],
                    counts[1]
                );

            assertEquals(
                CoreOccupancy.CONTESTED,
                occupancy
            );
            assertTrue(occupancy.contested());
            assertTrue(occupancy.soleTeam().isEmpty());
        }
    }

    @Test
    void negativeCountsAreRejected() {
        assertThrows(
            IllegalArgumentException.class,
            () -> CoreOccupancy.of(-1, 0)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> CoreOccupancy.of(0, -1)
        );
    }
}
