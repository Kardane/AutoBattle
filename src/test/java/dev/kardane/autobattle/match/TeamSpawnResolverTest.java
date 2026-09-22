package dev.kardane.autobattle.match;

import dev.kardane.autobattle.config.ArenaConfig;
import dev.kardane.autobattle.config.SpawnPoint;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class TeamSpawnResolverTest {
    private final TeamSpawnResolver resolver =
        new TeamSpawnResolver();

    @Test
    void teamSizesOneThroughEightProduceUniqueSymmetricSpawns() {
        ArenaConfig arena = ArenaConfig.defaults();

        for (int teamSize = 1; teamSize <= 8; teamSize++) {
            Set<String> positions = new HashSet<>();

            for (int member = 0; member < teamSize; member++) {
                PlayerSlot red = slot(BattleTeam.RED, member, member);
                PlayerSlot blue = slot(
                    BattleTeam.BLUE,
                    member,
                    8 + member
                );

                SpawnPoint redSpawn = resolver.resolve(
                    arena,
                    red,
                    teamSize,
                    1,
                    new Random(1000L + teamSize * 100L + member)
                );
                SpawnPoint blueSpawn = resolver.resolve(
                    arena,
                    blue,
                    teamSize,
                    1,
                    new Random(1000L + teamSize * 100L + member)
                );

                assertTrue(redSpawn.x() < arena.corePos().getX() + 0.5D);
                assertTrue(blueSpawn.x() > arena.corePos().getX() + 0.5D);
                assertEquals(
                    (arena.corePos().getX() + 0.5D) * 2.0D,
                    redSpawn.x() + blueSpawn.x(),
                    1.0E-9D
                );
                assertEquals(
                    redSpawn.z(),
                    blueSpawn.z(),
                    1.0E-9D
                );

                assertTrue(
                    positions.add(key(redSpawn)),
                    "duplicate RED spawn at team size " + teamSize
                );
                assertTrue(
                    positions.add(key(blueSpawn)),
                    "duplicate BLUE spawn at team size " + teamSize
                );
            }
        }
    }

    @Test
    void evenRoundsSwapPhysicalSidesButNotTeamIdentity() {
        ArenaConfig arena = ArenaConfig.defaults();
        PlayerSlot red = slot(BattleTeam.RED, 0, 0);
        PlayerSlot blue = slot(BattleTeam.BLUE, 0, 8);

        SpawnPoint redRoundOne =
            resolver.resolve(
                arena,
                red,
                4,
                1,
                new Random(10L)
            );
        SpawnPoint blueRoundOne =
            resolver.resolve(
                arena,
                blue,
                4,
                1,
                new Random(10L)
            );

        SpawnPoint redRoundTwo =
            resolver.resolve(
                arena,
                red,
                4,
                2,
                new Random(10L)
            );
        SpawnPoint blueRoundTwo =
            resolver.resolve(
                arena,
                blue,
                4,
                2,
                new Random(10L)
            );

        assertTrue(redRoundOne.x() < 0.5D);
        assertTrue(blueRoundOne.x() > 0.5D);
        assertTrue(redRoundTwo.x() > 0.5D);
        assertTrue(blueRoundTwo.x() < 0.5D);

        assertEquals("R1", red.targetId());
        assertEquals("B1", blue.targetId());
    }

    @Test
    void randomizedSpawnsStayWithinConfiguredRegion() {
        ArenaConfig arena = ArenaConfig.defaults();

        SpawnPoint first = resolver.resolve(
            arena,
            slot(BattleTeam.RED, 0, 0),
            8,
            1,
            new Random(20L)
        );
        SpawnPoint second = resolver.resolve(
            arena,
            slot(BattleTeam.RED, 1, 1),
            8,
            1,
            new Random(21L)
        );

        double centerX = arena.corePos().getX() + 0.5D;
        double centerZ = arena.corePos().getZ() + 0.5D;
        double radius = arena.teamSpawns().randomRadius();
        double distance = arena.teamSpawns().distanceFromCore();
        double firstMemberOffset =
            (0 - (8 - 1) / 2.0D)
                * arena.teamSpawns().memberSpacing();
        double secondMemberOffset =
            (1 - (8 - 1) / 2.0D)
                * arena.teamSpawns().memberSpacing();

        assertTrue(
            Math.abs(
                Math.abs(first.x() - centerX) - distance
            ) <= radius
        );
        assertTrue(
            Math.abs(
                first.z() - centerZ - firstMemberOffset
            ) <= radius
        );
        assertTrue(
            Math.abs(
                Math.abs(second.x() - centerX) - distance
            ) <= radius
        );
        assertTrue(
            Math.abs(
                second.z() - centerZ - secondMemberOffset
            ) <= radius
        );
        assertNotEquals(
            key(first),
            key(second)
        );
    }

    private PlayerSlot slot(
        BattleTeam team,
        int memberIndex,
        int slotIndex
    ) {
        return new PlayerSlot(
            UUID.randomUUID(),
            team,
            memberIndex,
            slotIndex
        );
    }

    private String key(SpawnPoint spawn) {
        return spawn.x() + ":" + spawn.y() + ":" + spawn.z();
    }
}
