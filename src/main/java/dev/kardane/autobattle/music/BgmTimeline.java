package dev.kardane.autobattle.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Uses monotonic time so audio progression is independent of server TPS. */
final class BgmTimeline {
    private final BgmCatalog.Playlist playlist;
    private final List<String> order;
    private int index = -1;
    private long end = Long.MAX_VALUE;
    private boolean finished;
    private String lastTrack;

    BgmTimeline(BgmCatalog.Playlist playlist) {
        this.playlist = playlist;
        this.order = new ArrayList<>(playlist.tracks());
        shuffle();
    }

    String next() {
        if (finished) {
            return null;
        }

        if (++index >= order.size()) {
            if (!playlist.repeat()) {
                finished = true;
                return null;
            }
            index = 0;
            shuffle();
        }

        lastTrack = order.get(index);
        return lastTrack;
    }

    private void shuffle() {
        Collections.shuffle(order, ThreadLocalRandom.current());

        if (order.size() > 1
            && lastTrack != null
            && lastTrack.equals(order.get(0))) {
            int swapIndex = 1 + ThreadLocalRandom.current()
                .nextInt(order.size() - 1);
            Collections.swap(order, 0, swapIndex);
        }
    }

    void started(double seconds, long now) {
        if (!Double.isFinite(seconds)
            || seconds <= 0.0D
            || seconds > 86400.0D * 365.0D) {
            throw new IllegalArgumentException(
                "Invalid BGM track length"
            );
        }

        end = now + (long) (seconds * 1_000_000_000.0D);
    }

    boolean elapsed(long now) {
        return now - end >= 0L;
    }
}
