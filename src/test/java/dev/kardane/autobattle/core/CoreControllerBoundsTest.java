package dev.kardane.autobattle.core;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class CoreControllerBoundsTest {
    @Test
    void requiresVerticalProximity() {
        Vec3 center = new Vec3(0.5D, -59.5D, 0.5D);

        assertTrue(
            CoreController.isInsideBounds(
                new Vec3(0.5D, -59.0D, 0.5D),
                center,
                3.0D
            )
        );

        assertFalse(
            CoreController.isInsideBounds(
                new Vec3(0.5D, 80.0D, 0.5D),
                center,
                3.0D
            )
        );
    }

    @Test
    void keepsHorizontalBounds() {
        Vec3 center = new Vec3(0.5D, -59.5D, 0.5D);

        assertFalse(
            CoreController.isInsideBounds(
                new Vec3(4.0D, -59.5D, 0.5D),
                center,
                3.0D
            )
        );
    }
}
