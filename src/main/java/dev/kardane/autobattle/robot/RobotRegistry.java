package dev.kardane.autobattle.robot;

import dev.kardane.autobattle.tactics.RobotController;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class RobotRegistry {
    private final Map<UUID, RobotController> byOwner =
        new LinkedHashMap<>();

    private final Map<UUID, RobotController> byEntity =
        new LinkedHashMap<>();

    public void register(RobotController controller) {
        byOwner.put(controller.ownerUuid(), controller);
        reindexEntity(controller);
    }

    public void reindexEntity(RobotController controller) {
        byEntity.entrySet().removeIf(
            entry -> entry.getValue() == controller
        );

        controller.entity().ifPresent(
            entity -> byEntity.put(entity.getUUID(), controller)
        );
    }

    public void unregister(RobotController controller) {
        byOwner.remove(controller.ownerUuid(), controller);

        byEntity.entrySet().removeIf(
            entry -> entry.getValue() == controller
        );
    }

    public Optional<RobotController> byOwner(UUID ownerUuid) {
        return Optional.ofNullable(byOwner.get(ownerUuid));
    }

    public Optional<RobotController> byEntity(UUID entityUuid) {
        return Optional.ofNullable(byEntity.get(entityUuid));
    }

    public Collection<RobotController> all() {
        return List.copyOf(byOwner.values());
    }

    public Collection<RobotController> alive() {
        return byOwner.values().stream()
            .filter(RobotController::alive)
            .toList();
    }

    public void clear() {
        byOwner.clear();
        byEntity.clear();
    }
}
