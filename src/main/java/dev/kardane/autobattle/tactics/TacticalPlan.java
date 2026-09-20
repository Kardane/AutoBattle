package dev.kardane.autobattle.tactics;

import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record TacticalPlan(
    TacticalPlanType type,
    UUID targetEntityUuid,
    Vec3 destination,
    long issuedTick,
    long lockUntilTick
) {
    public TacticalPlan {
        Objects.requireNonNull(type, "type");

        if (issuedTick < 0L) {
            throw new IllegalArgumentException("issuedTick must not be negative");
        }

        if (lockUntilTick < issuedTick) {
            throw new IllegalArgumentException(
                "lockUntilTick must be >= issuedTick"
            );
        }

        switch (type) {
            case ENGAGE, CHASE, RETREAT ->
                Objects.requireNonNull(
                    targetEntityUuid,
                    type + " requires targetEntityUuid"
                );
            case CAPTURE, DEFEND, REPOSITION ->
                Objects.requireNonNull(
                    destination,
                    type + " requires destination"
                );
        }
    }

    public static TacticalPlan engage(
        UUID targetEntityUuid,
        long currentTick,
        long lockTicks
    ) {
        return targeted(
            TacticalPlanType.ENGAGE,
            targetEntityUuid,
            currentTick,
            lockTicks
        );
    }

    public static TacticalPlan chase(
        UUID targetEntityUuid,
        long currentTick,
        long lockTicks
    ) {
        return targeted(
            TacticalPlanType.CHASE,
            targetEntityUuid,
            currentTick,
            lockTicks
        );
    }

    public static TacticalPlan retreat(
        UUID threatEntityUuid,
        long currentTick,
        long lockTicks
    ) {
        return targeted(
            TacticalPlanType.RETREAT,
            threatEntityUuid,
            currentTick,
            lockTicks
        );
    }

    public static TacticalPlan capture(
        Vec3 destination,
        long currentTick,
        long lockTicks
    ) {
        return positional(
            TacticalPlanType.CAPTURE,
            destination,
            currentTick,
            lockTicks
        );
    }

    public static TacticalPlan defend(
        Vec3 destination,
        long currentTick,
        long lockTicks
    ) {
        return positional(
            TacticalPlanType.DEFEND,
            destination,
            currentTick,
            lockTicks
        );
    }

    public static TacticalPlan reposition(
        Vec3 destination,
        long currentTick,
        long lockTicks
    ) {
        return positional(
            TacticalPlanType.REPOSITION,
            destination,
            currentTick,
            lockTicks
        );
    }

    public Optional<UUID> targetEntity() {
        return Optional.ofNullable(targetEntityUuid);
    }

    public Optional<Vec3> destinationPosition() {
        return Optional.ofNullable(destination);
    }

    public boolean isLocked(long currentTick) {
        return currentTick < lockUntilTick;
    }

    private static TacticalPlan targeted(
        TacticalPlanType type,
        UUID targetEntityUuid,
        long currentTick,
        long lockTicks
    ) {
        Objects.requireNonNull(targetEntityUuid, "targetEntityUuid");
        validateLockTicks(lockTicks);

        return new TacticalPlan(
            type,
            targetEntityUuid,
            null,
            currentTick,
            currentTick + lockTicks
        );
    }

    private static TacticalPlan positional(
        TacticalPlanType type,
        Vec3 destination,
        long currentTick,
        long lockTicks
    ) {
        Objects.requireNonNull(destination, "destination");
        validateLockTicks(lockTicks);

        return new TacticalPlan(
            type,
            null,
            destination,
            currentTick,
            currentTick + lockTicks
        );
    }

    private static void validateLockTicks(long lockTicks) {
        if (lockTicks < 0L) {
            throw new IllegalArgumentException(
                "lockTicks must not be negative"
            );
        }
    }
}
