package dev.kardane.autobattle.robot;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class RobotRegistry {
    private final Map<UUID, RobotZombie> byEntityUuid = new LinkedHashMap<>();

    public void register(RobotZombie robot) {
        byEntityUuid.put(robot.getUUID(), robot);
    }

    public void unregister(RobotZombie robot) {
        byEntityUuid.remove(robot.getUUID());
    }

    public Optional<RobotZombie> byEntityUuid(UUID entityUuid) {
        return Optional.ofNullable(byEntityUuid.get(entityUuid));
    }

    public Collection<RobotZombie> all() {
        return List.copyOf(byEntityUuid.values());
    }

    public void discardAll() {
        for (RobotZombie robot : byEntityUuid.values()) {
            if (!robot.isRemoved()) {
                robot.discard();
            }
        }

        byEntityUuid.clear();
    }
}
