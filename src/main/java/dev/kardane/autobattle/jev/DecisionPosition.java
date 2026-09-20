package dev.kardane.autobattle.jev;

import net.minecraft.world.phys.Vec3;

import java.util.Objects;

public record DecisionPosition(
    double x,
    double y,
    double z
) {
    public static DecisionPosition from(Vec3 position) {
        Objects.requireNonNull(
            position,
            "position"
        );

        return new DecisionPosition(
            position.x,
            position.y,
            position.z
        );
    }
}
