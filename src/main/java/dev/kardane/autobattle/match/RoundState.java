package dev.kardane.autobattle.match;

public final class RoundState {
    private int roundNumber;
    private long startedTick;
    private long endsAtTick;
    private boolean active;

    public void start(int roundNumber, long currentTick, int durationTicks) {
        if (roundNumber < 1) {
            throw new IllegalArgumentException("roundNumber must be positive");
        }
        if (durationTicks < 1) {
            throw new IllegalArgumentException("durationTicks must be positive");
        }

        this.roundNumber = roundNumber;
        this.startedTick = currentTick;
        this.endsAtTick = currentTick + durationTicks;
        this.active = true;
    }

    public void stop() {
        active = false;
    }

    public void reloadDuration(
        int durationTicks,
        long currentTick
    ) {
        if (durationTicks < 1) {
            throw new IllegalArgumentException(
                "durationTicks must be positive"
            );
        }

        if (!active) {
            return;
        }

        endsAtTick = Math.max(
            currentTick,
            startedTick + durationTicks
        );
    }

    public int roundNumber() {
        return roundNumber;
    }

    public long startedTick() {
        return startedTick;
    }

    public boolean active() {
        return active;
    }

    public long remainingTicks(long currentTick) {
        return active ? Math.max(0L, endsAtTick - currentTick) : 0L;
    }

    public boolean expired(long currentTick) {
        return active && currentTick >= endsAtTick;
    }
}
