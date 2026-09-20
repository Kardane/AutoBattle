package dev.kardane.autobattle.tactics;

import dev.kardane.autobattle.config.ArenaConfig;
import dev.kardane.autobattle.config.AutoBattleConfig;
import dev.kardane.autobattle.config.RobotConfig;
import dev.kardane.autobattle.robot.RobotRegistry;
import dev.kardane.autobattle.robot.RobotZombie;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class PlanExecutor {
    private final RobotRegistry registry;
    private RobotConfig robotConfig;
    private ArenaConfig arenaConfig;

    public PlanExecutor(
        RobotRegistry registry,
        AutoBattleConfig config
    ) {
        this.registry = Objects.requireNonNull(
            registry,
            "registry"
        );

        reloadConfig(
            Objects.requireNonNull(
                config,
                "config"
            )
        );
    }

    public void reloadConfig(AutoBattleConfig config) {
        Objects.requireNonNull(config, "config");

        this.robotConfig = config.robot();
        this.arenaConfig = config.arena();

        for (RobotController controller : registry.all()) {
            controller.reloadConfig(
                robotConfig,
                arenaConfig
            );
        }
    }

    public RobotController register(
        RobotZombie robot,
        long currentTick
    ) {
        RobotController controller = registry
            .byOwner(robot.ownerUuid())
            .orElseGet(() -> {
                RobotController created = new RobotController(
                    robot.ownerUuid(),
                    robot.robotColor(),
                    registry,
                    robotConfig,
                    arenaConfig
                );

                registry.register(created);
                return created;
            });

        controller.attachEntity(robot, currentTick);
        return controller;
    }

    public void unregisterEntity(RobotZombie robot) {
        registry.byOwner(robot.ownerUuid()).ifPresent(
            controller -> {
                if (controller.entityUuid()
                    .filter(robot.getUUID()::equals)
                    .isPresent()) {
                    controller.detachEntity();
                }
            }
        );
    }

    public void removeOwner(UUID ownerUuid) {
        registry.byOwner(ownerUuid).ifPresent(controller -> {
            controller.entity().ifPresent(robot -> {
                controller.clearPlan();

                if (!robot.isRemoved()) {
                    robot.discard();
                }
            });

            controller.detachEntity();
            registry.unregister(controller);
        });
    }

    public Optional<RobotController> byOwner(UUID ownerUuid) {
        return registry.byOwner(ownerUuid);
    }

    public Optional<RobotController> byEntity(UUID entityUuid) {
        return registry.byEntity(entityUuid);
    }

    public Collection<RobotController> controllers() {
        return registry.all();
    }

    public boolean assignPlan(
        RobotZombie robot,
        TacticalPlan plan,
        long currentTick
    ) {
        RobotController controller = registry
            .byOwner(robot.ownerUuid())
            .orElseGet(() -> register(robot, currentTick));

        return controller.applyPlan(plan, currentTick);
    }

    public boolean assignPlan(
        RobotController controller,
        TacticalPlan plan,
        long currentTick
    ) {
        return controller.applyPlan(plan, currentTick);
    }

    public void tick(long currentTick) {
        for (RobotController controller : registry.all()) {
            controller.tick(currentTick);
        }
    }

    public void clear() {
        for (RobotController controller : registry.all()) {
            controller.entity().ifPresent(robot -> {
                controller.clearPlan();

                if (!robot.isRemoved()) {
                    robot.discard();
                }
            });
        }

        registry.clear();
    }
}
