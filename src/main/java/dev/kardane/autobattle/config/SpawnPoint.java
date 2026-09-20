package dev.kardane.autobattle.config;

import net.minecraft.world.phys.Vec3;

public record SpawnPoint(
    double x,
    double y,
    double z,
    float yaw,
    float pitch
) {
    public Vec3 position() {
        return new Vec3(x, y, z);
    }
}
