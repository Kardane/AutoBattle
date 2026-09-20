package dev.kardane.autobattle.tactics;

import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record TacticalPlan(
    TacticalPlanType type,
    UUID targetOwnerUuid,
    Vec3 destination,
    String externalId,
    long issuedTick,
    long lockUntilTick
) {
    public TacticalPlan {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(externalId, "externalId");

        if (issuedTick < 0L) {
            throw new IllegalArgumentException(
                "issuedTick must not be negative"
            );
        }

        if (lockUntilTick < issuedTick) {
            throw new IllegalArgumentException(
                "lockUntilTick must be >= issuedTick"
            );
        }

        switch (type) {
            case ENGAGE, CHASE, RETREAT ->
                Objects.requireNonNull(
                    targetOwnerUuid,
                    type + " requires targetOwnerUuid"
                );
            case CAPTURE, DEFEND, REPOSITION ->
                Objects.requireNonNull(
                    destination,
                    type + " requires destination"
                );
        }
    }

    public static TacticalPlan engage(
        UUID targetOwnerUuid,
        String externalId,
        long currentTick,
        long lockTicks
    ) {
        return targeted(
            TacticalPlanType.ENGAGE,
            targetOwnerUuid,
            externalId,
            currentTick,
            lockTicks
        );
    }

    public static TacticalPlan chase(
        UUID targetOwnerUuid,
        String externalId,
        long currentTick,
        long lockTicks
    ) {
        return targeted(
            TacticalPlanType.CHASE,
            targetOwnerUuid,
            externalId,
            currentTick,
            lockTicks
        );
    }

    public static TacticalPlan retreat(
        UUID threatOwnerUuid,
        String externalId,
        long currentTick,
        long lockTicks
    ) {
        return targeted(
            TacticalPlanType.RETREAT,
            threatOwnerUuid,
            externalId,
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
            "CAPTURE_CORE",
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
            "DEFEND_CORE",
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
            "REPOSITION",
            currentTick,
            lockTicks
        );
    }

    public Optional<UUID> targetOwner() {
        return Optional.ofNullable(targetOwnerUuid);
    }

    public Optional<Vec3> destinationPosition() {
        return Optional.ofNullable(destination);
    }

    public boolean isLocked(long currentTick) {
        return currentTick < lockUntilTick;
    }

    private static TacticalPlan targeted(
        TacticalPlanType type,
        UUID targetOwnerUuid,
        String externalId,
        long currentTick,
        long lockTicks
    ) {
        Objects.requireNonNull(targetOwnerUuid, "targetOwnerUuid");
        validateLockTicks(lockTicks);

        return new TacticalPlan(
            type,
            targetOwnerUuid,
            null,
            externalId,
            currentTick,
            currentTick + lockTicks
        );
    }

    private static TacticalPlan positional(
        TacticalPlanType type,
        Vec3 destination,
        String externalId,
        long currentTick,
        long lockTicks
    ) {
        Objects.requireNonNull(destination, "destination");
        validateLockTicks(lockTicks);

        return new TacticalPlan(
            type,
            null,
            destination,
            externalId,
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
