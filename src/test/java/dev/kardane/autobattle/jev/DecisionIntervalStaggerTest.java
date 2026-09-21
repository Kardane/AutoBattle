package dev.kardane.autobattle.jev;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class DecisionIntervalStaggerTest {
    @Test
    void sixteenParticipantsAreDistributedAcrossSixtyTicks() {
        Set<Integer> offsets = new HashSet<>();

        for (int ordinal = 0; ordinal < 16; ordinal++) {
            int offset = DecisionIntervalStagger.offsetFor(
                ordinal,
                16,
                60
            );

            assertTrue(offset >= 0);
            assertTrue(offset < 60);
            offsets.add(offset);
        }

        assertEquals(16, offsets.size());
        assertEquals(0, DecisionIntervalStagger.offsetFor(0, 16, 60));
        assertEquals(56, DecisionIntervalStagger.offsetFor(15, 16, 60));
    }

    @Test
    void fourParticipantsRemainEvenlySpread() {
        assertEquals(
            0,
            DecisionIntervalStagger.offsetFor(0, 4, 60)
        );
        assertEquals(
            15,
            DecisionIntervalStagger.offsetFor(1, 4, 60)
        );
        assertEquals(
            30,
            DecisionIntervalStagger.offsetFor(2, 4, 60)
        );
        assertEquals(
            45,
            DecisionIntervalStagger.offsetFor(3, 4, 60)
        );
    }

    @Test
    void rejectsInvalidParameters() {
        assertThrows(
            IllegalArgumentException.class,
            () -> DecisionIntervalStagger.offsetFor(
                2,
                2,
                60
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> DecisionIntervalStagger.offsetFor(
                0,
                1,
                0
            )
        );
    }
}
