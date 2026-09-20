package dev.kardane.autobattle.config;

import dev.kardane.autobattle.AutoBattleConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Objects;

public record ArenaConfig(
    ResourceKey<Level> dimension,
    BlockPos corePos,
    double coreRadius,
    List<SpawnPoint> robotSpawns,
    List<SpawnPoint> viewerSpawns,
    List<BlockPos> repositionNodes
) {
    public ArenaConfig {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(corePos, "corePos");

        if (coreRadius <= 0.0D) {
            throw new IllegalArgumentException(
                "coreRadius must be positive"
            );
        }

        robotSpawns = List.copyOf(robotSpawns);
        viewerSpawns = List.copyOf(viewerSpawns);
        repositionNodes = List.copyOf(repositionNodes);

        if (robotSpawns.size() < 4) {
            throw new IllegalArgumentException(
                "MVP arena requires at least four robot spawns"
            );
        }
    }

    public static ArenaConfig defaults() {
        return new ArenaConfig(
            Level.OVERWORLD,
            new BlockPos(0, 80, 0),
            AutoBattleConstants.CORE_RADIUS,
            List.of(
                new SpawnPoint(15, 80, 0, 90.0F, 0.0F),
                new SpawnPoint(-15, 80, 0, -90.0F, 0.0F),
                new SpawnPoint(0, 80, 15, 180.0F, 0.0F),
                new SpawnPoint(0, 80, -15, 0.0F, 0.0F)
            ),
            List.of(
                new SpawnPoint(20, 88, 20, 0.0F, 0.0F),
                new SpawnPoint(-20, 88, 20, 0.0F, 0.0F),
                new SpawnPoint(-20, 88, -20, 0.0F, 0.0F),
                new SpawnPoint(20, 88, -20, 0.0F, 0.0F)
            ),
            List.of(
                new BlockPos(8, 80, 8),
                new BlockPos(-8, 80, 8),
                new BlockPos(-8, 80, -8),
                new BlockPos(8, 80, -8)
            )
        );
    }
}
