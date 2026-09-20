package dev.kardane.autobattle.tactics;

import dev.kardane.autobattle.robot.RobotRegistry;
import dev.kardane.autobattle.robot.RobotZombie;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.Optional;

public final class RobotController {
    private static final double ENGAGE_SPEED = 1.00D;
    private static final double CHASE_SPEED = 1.20D;
    private static final double CAPTURE_SPEED = 1.05D;
    private static final double DEFEND_SPEED = 1.00D;
    private static final double REPOSITION_SPEED = 1.10D;
    private static final double RETREAT_SPEED = 1.20D;

    private static final double POSITION_REACHED_DISTANCE_SQR = 2.25D;
    private static final double DEFEND_RADIUS_SQR = 9.0D;
    private static final double RETREAT_DISTANCE = 8.0D;

    private final RobotZombie robot;
    private final RobotRegistry registry;

    private TacticalPlan currentPlan;
    private long lastAppliedTick = -1L;

    public RobotController(
        RobotZombie robot,
        RobotRegistry registry
    ) {
        this.robot = Objects.requireNonNull(robot, "robot");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public RobotZombie robot() {
        return robot;
    }

    public Optional<TacticalPlan> currentPlan() {
        return Optional.ofNullable(currentPlan);
    }

    public long lastAppliedTick() {
        return lastAppliedTick;
    }

    public boolean assignPlan(
        TacticalPlan plan,
        long currentTick
    ) {
        Objects.requireNonNull(plan, "plan");

        if (currentPlan != null && currentPlan.isLocked(currentTick)) {
            return false;
        }

        currentPlan = plan;
        return true;
    }

    public void clearPlan() {
        currentPlan = null;
        robot.setTarget(null);
        robot.getNavigation().stop();
    }

    public void tick(long currentTick) {
        if (!isActive()) {
            return;
        }

        if (currentPlan == null) {
            robot.setTarget(null);
            return;
        }

        switch (currentPlan.type()) {
            case ENGAGE -> engage();
            case CHASE -> chase();
            case CAPTURE -> moveToPosition(
                currentPlan.destination(),
                CAPTURE_SPEED,
                POSITION_REACHED_DISTANCE_SQR
            );
            case DEFEND -> defend(currentPlan.destination());
            case RETREAT -> retreat();
            case REPOSITION -> moveToPosition(
                currentPlan.destination(),
                REPOSITION_SPEED,
                POSITION_REACHED_DISTANCE_SQR
            );
        }

        lastAppliedTick = currentTick;
    }

    public boolean isActive() {
        return !robot.isRemoved() && robot.isAlive();
    }

    private void engage() {
        resolvePlanTarget().ifPresentOrElse(
            target -> {
                robot.setTarget(target);
                robot.getNavigation().moveTo(
                    target,
                    ENGAGE_SPEED
                );
            },
            this::clearCombatIntent
        );
    }

    private void chase() {
        resolvePlanTarget().ifPresentOrElse(
            target -> {
                robot.setTarget(target);
                robot.getNavigation().moveTo(
                    target,
                    CHASE_SPEED
                );
            },
            this::clearCombatIntent
        );
    }

    private void defend(Vec3 destination) {
        robot.setTarget(null);

        if (robot.position().distanceToSqr(destination) > DEFEND_RADIUS_SQR) {
            robot.getNavigation().moveTo(
                destination.x,
                destination.y,
                destination.z,
                DEFEND_SPEED
            );
        } else {
            robot.getNavigation().stop();
        }
    }

    private void retreat() {
        robot.setTarget(null);

        resolvePlanTarget().ifPresentOrElse(
            threat -> {
                Vec3 away = robot.position()
                    .subtract(threat.position());

                Vec3 horizontal = new Vec3(
                    away.x,
                    0.0D,
                    away.z
                );

                if (horizontal.lengthSqr() < 1.0E-4D) {
                    horizontal = new Vec3(1.0D, 0.0D, 0.0D);
                } else {
                    horizontal = horizontal.normalize();
                }

                Vec3 destination = robot.position()
                    .add(horizontal.scale(RETREAT_DISTANCE));

                robot.getNavigation().moveTo(
                    destination.x,
                    destination.y,
                    destination.z,
                    RETREAT_SPEED
                );
            },
            () -> robot.getNavigation().stop()
        );
    }

    private void moveToPosition(
        Vec3 destination,
        double speed,
        double reachedDistanceSqr
    ) {
        robot.setTarget(null);

        if (robot.position().distanceToSqr(destination) <= reachedDistanceSqr) {
            robot.getNavigation().stop();
            return;
        }

        robot.getNavigation().moveTo(
            destination.x,
            destination.y,
            destination.z,
            speed
        );
    }

    private Optional<RobotZombie> resolvePlanTarget() {
        if (currentPlan == null || currentPlan.targetEntityUuid() == null) {
            return Optional.empty();
        }

        return registry
            .byEntityUuid(currentPlan.targetEntityUuid())
            .filter(target -> target != robot)
            .filter(RobotZombie::isAlive)
            .filter(target -> !target.isRemoved())
            .filter(target -> target.matchId().equals(robot.matchId()));
    }

    private void clearCombatIntent() {
        robot.setTarget(null);
        robot.getNavigation().stop();
    }
}
