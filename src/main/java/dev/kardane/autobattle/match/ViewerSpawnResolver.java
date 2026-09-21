package dev.kardane.autobattle.match;

import dev.kardane.autobattle.config.ArenaConfig;
import dev.kardane.autobattle.config.SpawnPoint;

public final class ViewerSpawnResolver {
    public SpawnPoint resolve(
        ArenaConfig arena,
        int ordinal,
        int participantCount
    ) {
        if (participantCount < 1
            || participantCount > 16
            || ordinal < 0
            || ordinal >= participantCount) {
            throw new IllegalArgumentException(
                "Viewer ordinal must satisfy 0 <= ordinal < participantCount <= 16"
            );
        }

        double angle =
            2.0D * Math.PI * ordinal / participantCount;

        double centerX = arena.corePos().getX() + 0.5D;
        double centerZ = arena.corePos().getZ() + 0.5D;

        double x = centerX
            + arena.viewerSpawn().radius() * Math.cos(angle);
        double z = centerZ
            + arena.viewerSpawn().radius() * Math.sin(angle);

        double yawRadians = Math.atan2(
            centerZ - z,
            centerX - x
        );

        float yaw = (float) Math.toDegrees(yawRadians) - 90.0F;

        return new SpawnPoint(
            x,
            arena.viewerSpawn().y(),
            z,
            yaw,
            20.0F
        );
    }
}
