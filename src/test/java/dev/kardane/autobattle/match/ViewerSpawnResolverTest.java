package dev.kardane.autobattle.match;

import dev.kardane.autobattle.config.ArenaConfig;
import dev.kardane.autobattle.config.SpawnPoint;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class ViewerSpawnResolverTest {
    private final ViewerSpawnResolver resolver =
        new ViewerSpawnResolver();

    @Test
    void sixteenParticipantsProduceUniqueViewerRing() {
        ArenaConfig arena = ArenaConfig.defaults();
        Set<String> positions = new HashSet<>();

        double centerX =
            arena.corePos().getX() + 0.5D;
        double centerZ =
            arena.corePos().getZ() + 0.5D;

        for (int ordinal = 0; ordinal < 16; ordinal++) {
            SpawnPoint spawn = resolver.resolve(
                arena,
                ordinal,
                16
            );

            double dx = spawn.x() - centerX;
            double dz = spawn.z() - centerZ;
            double radius = Math.hypot(dx, dz);

            assertEquals(
                arena.viewerSpawn().radius(),
                radius,
                1.0E-9D
            );
            assertEquals(
                arena.viewerSpawn().y(),
                spawn.y(),
                1.0E-9D
            );
            assertTrue(
                positions.add(
                    spawn.x() + ":" + spawn.z()
                )
            );
        }

        assertEquals(16, positions.size());
    }

    @Test
    void sparseMatchSlotsDoNotAffectActiveOrdinalLayout() {
        ArenaConfig arena = ArenaConfig.defaults();

        SpawnPoint first = resolver.resolve(
            arena,
            0,
            3
        );
        SpawnPoint second = resolver.resolve(
            arena,
            1,
            3
        );
        SpawnPoint third = resolver.resolve(
            arena,
            2,
            3
        );

        assertNotEquals(first.position(), second.position());
        assertNotEquals(second.position(), third.position());
        assertNotEquals(first.position(), third.position());
    }

    @Test
    void rejectsInvalidOrdinalOrParticipantCount() {
        ArenaConfig arena = ArenaConfig.defaults();

        assertThrows(
            IllegalArgumentException.class,
            () -> resolver.resolve(arena, -1, 4)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> resolver.resolve(arena, 4, 4)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> resolver.resolve(arena, 0, 0)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> resolver.resolve(arena, 0, 17)
        );
    }
}
