package dev.kardane.autobattle.config;

import dev.kardane.autobattle.AutoBattleConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Objects;

public record ArenaConfig(
    ResourceKey<Level> dimension,
    BlockPos corePos,
    double coreRadius,
    TeamSpawnConfig teamSpawns,
    ViewerSpawnConfig viewerSpawn
) {
    public ArenaConfig {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(corePos, "corePos");
        Objects.requireNonNull(teamSpawns, "teamSpawns");
        Objects.requireNonNull(viewerSpawn, "viewerSpawn");

        if (coreRadius <= 0.0D) {
            throw new IllegalArgumentException(
                "coreRadius must be positive"
            );
        }
    }

    public static ArenaConfig defaults() {
        return new ArenaConfig(
            Level.OVERWORLD,
            new BlockPos(0, 80, 0),
            AutoBattleConstants.CORE_RADIUS,
            new TeamSpawnConfig(
                "x",
                18.0D,
                3.0D,
                80.0D,
                true
            ),
            new ViewerSpawnConfig(
                24.0D,
                88.0D
            )
        );
    }
}
