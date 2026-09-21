package dev.kardane.autobattle.core;

public final class CoreTelemetry {
    private long emptyTicks;
    private long redOnlyTicks;
    private long blueOnlyTicks;
    private long contestedTicks;

    public void record(CoreOccupancy occupancy) {
        switch (occupancy) {
            case EMPTY -> emptyTicks++;
            case RED_ONLY -> redOnlyTicks++;
            case BLUE_ONLY -> blueOnlyTicks++;
            case CONTESTED -> contestedTicks++;
        }
    }

    public long emptyTicks() {
        return emptyTicks;
    }

    public long redOnlyTicks() {
        return redOnlyTicks;
    }

    public long blueOnlyTicks() {
        return blueOnlyTicks;
    }

    public long contestedTicks() {
        return contestedTicks;
    }

    public long totalTicks() {
        return emptyTicks
            + redOnlyTicks
            + blueOnlyTicks
            + contestedTicks;
    }

    public void reset() {
        emptyTicks = 0L;
        redOnlyTicks = 0L;
        blueOnlyTicks = 0L;
        contestedTicks = 0L;
    }
}
