package dev.kardane.autobattle.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class CoreTelemetryTest {
    @Test
    void recordsEachOccupancyBucketAndResets() {
        CoreTelemetry telemetry = new CoreTelemetry();

        telemetry.record(CoreOccupancy.EMPTY);
        telemetry.record(CoreOccupancy.RED_ONLY);
        telemetry.record(CoreOccupancy.RED_ONLY);
        telemetry.record(CoreOccupancy.BLUE_ONLY);
        telemetry.record(CoreOccupancy.CONTESTED);
        telemetry.record(CoreOccupancy.CONTESTED);
        telemetry.record(CoreOccupancy.CONTESTED);

        assertEquals(1L, telemetry.emptyTicks());
        assertEquals(2L, telemetry.redOnlyTicks());
        assertEquals(1L, telemetry.blueOnlyTicks());
        assertEquals(3L, telemetry.contestedTicks());
        assertEquals(7L, telemetry.totalTicks());

        telemetry.reset();

        assertEquals(0L, telemetry.emptyTicks());
        assertEquals(0L, telemetry.redOnlyTicks());
        assertEquals(0L, telemetry.blueOnlyTicks());
        assertEquals(0L, telemetry.contestedTicks());
        assertEquals(0L, telemetry.totalTicks());
    }
}
