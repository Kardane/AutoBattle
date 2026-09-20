package dev.kardane.autobattle.tactics;

import dev.kardane.autobattle.robot.RobotRegistry;
import dev.kardane.autobattle.robot.RobotZombie;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class PlanExecutor {
    private final RobotRegistry registry;
    private final Map<UUID, RobotController> controllers =
        new LinkedHashMap<>();

    public PlanExecutor(RobotRegistry registry) {
        this.registry = registry;
    }

    public RobotController register(RobotZombie robot) {
        registry.register(robot);

        RobotController controller = new RobotController(
            robot,
            registry
        );

        controllers.put(robot.getUUID(), controller);
        return controller;
    }

    public void unregister(RobotZombie robot) {
        RobotController controller = controllers.remove(robot.getUUID());

        if (controller != null) {
            controller.clearPlan();
        }

        registry.unregister(robot);
    }

    public Optional<RobotController> controller(UUID entityUuid) {
        return Optional.ofNullable(controllers.get(entityUuid));
    }

    public Collection<RobotController> controllers() {
        return List.copyOf(controllers.values());
    }

    public boolean assignPlan(
        RobotZombie robot,
        TacticalPlan plan,
        long currentTick
    ) {
        RobotController controller = controllers.get(robot.getUUID());

        if (controller == null) {
            controller = register(robot);
        }

        return controller.assignPlan(plan, currentTick);
    }

    public void tick(long currentTick) {
        Iterator<Map.Entry<UUID, RobotController>> iterator =
            controllers.entrySet().iterator();

        while (iterator.hasNext()) {
            RobotController controller = iterator.next().getValue();

            if (!controller.isActive()) {
                registry.unregister(controller.robot());
                iterator.remove();
                continue;
            }

            controller.tick(currentTick);
        }
    }

    public void clear() {
        for (RobotController controller : controllers.values()) {
            controller.clearPlan();

            if (!controller.robot().isRemoved()) {
                controller.robot().discard();
            }
        }

        controllers.clear();
        registry.discardAll();
    }
}
