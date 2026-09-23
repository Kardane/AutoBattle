package dev.kardane.autobattle.music;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BgmTimelineTest {
    @Test
    void repeatingPlaylistCyclesWithoutUsingServerTicks() {
        BgmTimeline timeline = new BgmTimeline(
            new BgmCatalog.Playlist(
                List.of("one", "two"),
                true
            )
        );

        String first = timeline.next();
        assertTrue(List.of("one", "two").contains(first));
        timeline.started(1.0D, 100L);
        assertTrue(timeline.elapsed(1_000_000_100L));
        String second = timeline.next();
        assertTrue(List.of("one", "two").contains(second));
        assertNotEquals(first, second);
        timeline.started(1.0D, 200L);
        String third = timeline.next();
        assertTrue(List.of("one", "two").contains(third));
        assertNotEquals(second, third);
    }

    @Test
    void nonRepeatingPlaylistCompletesAfterItsLastTrack() {
        BgmTimeline timeline = new BgmTimeline(
            new BgmCatalog.Playlist(
                List.of("only"),
                false
            )
        );

        assertEquals("only", timeline.next());
        timeline.started(0.5D, 0L);
        assertEquals(null, timeline.next());
        assertEquals(null, timeline.next());
    }
}
