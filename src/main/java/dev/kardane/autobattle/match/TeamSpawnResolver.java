package dev.kardane.autobattle.match;

import dev.kardane.autobattle.config.ArenaConfig;
import dev.kardane.autobattle.config.SpawnPoint;
import dev.kardane.autobattle.config.TeamSpawnConfig;

public final class TeamSpawnResolver {
    public SpawnPoint resolve(
        ArenaConfig arena,
        PlayerSlot slot,
        int teamSize,
        int round
    ) {
        if (teamSize < 1 || teamSize > 8) {
            throw new IllegalArgumentException(
                "teamSize must be between 1 and 8"
            );
        }

        TeamSpawnConfig config = arena.teamSpawns();

        double centerX = arena.corePos().getX() + 0.5D;
        double centerZ = arena.corePos().getZ() + 0.5D;
        double offset =
            (slot.memberIndex() - (teamSize - 1) / 2.0D)
                * config.memberSpacing();

        boolean swapped =
            config.swapSidesEachRound()
                && round > 0
                && round % 2 == 0;

        boolean negativeSide =
            slot.team() == BattleTeam.RED;

        if (swapped) {
            negativeSide = !negativeSide;
        }

        double side = negativeSide ? -1.0D : 1.0D;
        double x = centerX;
        double z = centerZ;
        float yaw;

        if (config.axis().equals("x")) {
            x += side * config.distanceFromCore();
            z += offset;
            yaw = negativeSide ? -90.0F : 90.0F;
        } else {
            x += offset;
            z += side * config.distanceFromCore();
            yaw = negativeSide ? 0.0F : 180.0F;
        }

        return new SpawnPoint(
            x,
            config.y(),
            z,
            yaw,
            0.0F
        );
    }
}
