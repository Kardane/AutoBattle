package dev.kardane.autobattle.match;

import dev.kardane.autobattle.config.ArenaConfig;
import dev.kardane.autobattle.config.SpawnPoint;
import dev.kardane.autobattle.config.TeamSpawnConfig;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

public final class TeamSpawnResolver {
    public SpawnPoint resolve(
        ArenaConfig arena,
        PlayerSlot slot,
        int teamSize,
        int round
    ) {
        return resolve(
            arena,
            slot,
            teamSize,
            round,
            ThreadLocalRandom.current()
        );
    }

    public SpawnPoint resolve(
        ArenaConfig arena,
        PlayerSlot slot,
        int teamSize,
        int round,
        RandomGenerator random
    ) {
        Objects.requireNonNull(arena, "arena");
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(random, "random");

        if (teamSize < 1 || teamSize > 8) {
            throw new IllegalArgumentException(
                "teamSize must be between 1 and 8"
            );
        }

        TeamSpawnConfig config = arena.teamSpawns();

        double centerX = arena.corePos().getX() + 0.5D;
        double centerZ = arena.corePos().getZ() + 0.5D;
        double forwardOffset = randomOffset(
            random,
            config.randomRadius()
        );
        double memberOffset =
            (slot.memberIndex() - (teamSize - 1) / 2.0D)
                * config.memberSpacing();
        double lateralOffset = memberOffset
            + randomOffset(random, config.randomRadius());

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
            x += side * (
                config.distanceFromCore() + forwardOffset
            );
            z += lateralOffset;
            yaw = negativeSide ? -90.0F : 90.0F;
        } else {
            x += lateralOffset;
            z += side * (
                config.distanceFromCore() + forwardOffset
            );
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

    private double randomOffset(
        RandomGenerator random,
        double radius
    ) {
        if (radius <= 0.0D) {
            return 0.0D;
        }

        return random.nextDouble(-radius, radius);
    }
}
