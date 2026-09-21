package dev.kardane.autobattle.match;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

public record PlayerViewOrigin(
    ResourceKey<Level> dimension,
    Vec3 position,
    float yaw,
    float pitch,
    GameType gameMode
) {
    public PlayerViewOrigin {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(gameMode, "gameMode");
    }
}
