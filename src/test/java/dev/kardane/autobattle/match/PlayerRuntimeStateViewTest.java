package dev.kardane.autobattle.match;

import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class PlayerRuntimeStateViewTest {
    @Test
    void remembersOnlyTheOriginalViewAcrossRoundResets() {
        PlayerRuntimeState runtime =
            new PlayerRuntimeState();

        PlayerViewOrigin first = new PlayerViewOrigin(
            Level.OVERWORLD,
            new Vec3(1.0D, 70.0D, 2.0D),
            30.0F,
            10.0F,
            GameType.SURVIVAL
        );

        PlayerViewOrigin later = new PlayerViewOrigin(
            Level.NETHER,
            new Vec3(3.0D, 80.0D, 4.0D),
            90.0F,
            0.0F,
            GameType.CREATIVE
        );

        runtime.rememberViewOrigin(first);
        runtime.resetForRound();
        runtime.rememberViewOrigin(later);

        assertEquals(
            first,
            runtime.viewOrigin().orElseThrow()
        );

        runtime.clearViewOrigin();
        assertTrue(runtime.viewOrigin().isEmpty());
    }
}
