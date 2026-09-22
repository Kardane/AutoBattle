package dev.kardane.autobattle.tactics;

import net.minecraft.world.phys.Vec3;

import java.util.Objects;

final class MovementRecoveryTracker {
    enum Action {
        NONE,
        REFRESH_DIRECT,
        TRY_LEFT_DETOUR,
        TRY_RIGHT_DETOUR,
        ABANDON
    }

    private enum Stage {
        DIRECT,
        REPATHED,
        DETOUR_LEFT,
        DETOUR_RIGHT
    }

    private final int refreshIntervalTicks;
    private final int stalledTicks;
    private final double meaningfulMoveDistanceSqr;
    private final double destinationRefreshDistanceSqr;
    private final double waypointReachedDistanceSqr;

    private Vec3 lastObservedPosition;
    private Vec3 trackedDestination;
    private Vec3 activeWaypoint;
    private long lastMeaningfulMovementTick = -1L;
    private long lastPathAttemptTick = -1L;
    private int consecutivePathStartFailures;
    private Stage stage = Stage.DIRECT;

    MovementRecoveryTracker(
        int refreshIntervalTicks,
        int stalledTicks,
        double meaningfulMoveDistance,
        double destinationRefreshDistance,
        double waypointReachedDistance
    ) {
        if (refreshIntervalTicks < 1
            || stalledTicks < 1
            || meaningfulMoveDistance <= 0.0D
            || destinationRefreshDistance <= 0.0D
            || waypointReachedDistance <= 0.0D) {
            throw new IllegalArgumentException(
                "Invalid movement recovery settings"
            );
        }

        this.refreshIntervalTicks = refreshIntervalTicks;
        this.stalledTicks = stalledTicks;
        this.meaningfulMoveDistanceSqr =
            meaningfulMoveDistance * meaningfulMoveDistance;
        this.destinationRefreshDistanceSqr =
            destinationRefreshDistance
                * destinationRefreshDistance;
        this.waypointReachedDistanceSqr =
            waypointReachedDistance
                * waypointReachedDistance;
    }

    Action evaluate(
        Vec3 currentPosition,
        Vec3 destination,
        boolean allowedToStop,
        boolean navigationDone,
        long currentTick
    ) {
        Objects.requireNonNull(
            currentPosition,
            "currentPosition"
        );
        Objects.requireNonNull(
            destination,
            "destination"
        );

        if (trackedDestination == null) {
            initialize(
                currentPosition,
                destination,
                currentTick
            );
            return allowedToStop
                ? Action.NONE
                : Action.REFRESH_DIRECT;
        }

        if (trackedDestination.distanceToSqr(destination)
            >= destinationRefreshDistanceSqr) {
            initialize(
                currentPosition,
                destination,
                currentTick
            );
            return allowedToStop
                ? Action.NONE
                : Action.REFRESH_DIRECT;
        }

        if (lastObservedPosition == null
            || lastObservedPosition.distanceToSqr(
                currentPosition
            ) >= meaningfulMoveDistanceSqr) {
            lastObservedPosition = currentPosition;
            lastMeaningfulMovementTick = currentTick;
            consecutivePathStartFailures = 0;

            if (activeWaypoint == null) {
                stage = Stage.DIRECT;
            }
        }

        if (allowedToStop) {
            activeWaypoint = null;
            stage = Stage.DIRECT;
            consecutivePathStartFailures = 0;
            lastMeaningfulMovementTick = currentTick;
            return Action.NONE;
        }

        if (activeWaypoint != null
            && currentPosition.distanceToSqr(
                activeWaypoint
            ) <= waypointReachedDistanceSqr) {
            activeWaypoint = null;
            stage = Stage.DIRECT;
            consecutivePathStartFailures = 0;
            lastMeaningfulMovementTick = currentTick;
            return Action.REFRESH_DIRECT;
        }

        if (navigationDone) {
            return switch (stage) {
                case DIRECT -> Action.REFRESH_DIRECT;
                case REPATHED -> {
                    stage = Stage.DETOUR_LEFT;
                    yield Action.TRY_LEFT_DETOUR;
                }
                case DETOUR_LEFT -> {
                    stage = Stage.DETOUR_RIGHT;
                    yield Action.TRY_RIGHT_DETOUR;
                }
                case DETOUR_RIGHT -> Action.ABANDON;
            };
        }

        if (lastMeaningfulMovementTick >= 0L
            && currentTick - lastMeaningfulMovementTick
                >= stalledTicks) {
            lastMeaningfulMovementTick = currentTick;

            return switch (stage) {
                case DIRECT -> {
                    stage = Stage.REPATHED;
                    yield Action.REFRESH_DIRECT;
                }
                case REPATHED -> {
                    stage = Stage.DETOUR_LEFT;
                    yield Action.TRY_LEFT_DETOUR;
                }
                case DETOUR_LEFT -> {
                    stage = Stage.DETOUR_RIGHT;
                    yield Action.TRY_RIGHT_DETOUR;
                }
                case DETOUR_RIGHT -> Action.ABANDON;
            };
        }

        if (activeWaypoint == null
            && currentTick - lastPathAttemptTick
                >= refreshIntervalTicks) {
            return Action.REFRESH_DIRECT;
        }

        return Action.NONE;
    }

    Action recordPathAttempt(
        Action attempted,
        boolean success,
        Vec3 waypoint,
        long currentTick
    ) {
        Objects.requireNonNull(attempted, "attempted");

        if (attempted == Action.NONE
            || attempted == Action.ABANDON) {
            throw new IllegalArgumentException(
                "Action is not a path attempt: " + attempted
            );
        }

        lastPathAttemptTick = currentTick;

        if (success) {
            consecutivePathStartFailures = 0;

            if (attempted == Action.TRY_LEFT_DETOUR
                || attempted == Action.TRY_RIGHT_DETOUR) {
                activeWaypoint = Objects.requireNonNull(
                    waypoint,
                    "waypoint"
                );
            } else {
                activeWaypoint = null;
            }

            return Action.NONE;
        }

        consecutivePathStartFailures++;

        return switch (attempted) {
            case REFRESH_DIRECT -> {
                if (stage == Stage.REPATHED
                    || consecutivePathStartFailures >= 2) {
                    stage = Stage.DETOUR_LEFT;
                    yield Action.TRY_LEFT_DETOUR;
                }

                yield Action.NONE;
            }
            case TRY_LEFT_DETOUR -> {
                stage = Stage.DETOUR_RIGHT;
                yield Action.TRY_RIGHT_DETOUR;
            }
            case TRY_RIGHT_DETOUR -> Action.ABANDON;
            case NONE, ABANDON ->
                throw new IllegalStateException(
                    "Unexpected path attempt action"
                );
        };
    }

    void reset() {
        lastObservedPosition = null;
        trackedDestination = null;
        activeWaypoint = null;
        lastMeaningfulMovementTick = -1L;
        lastPathAttemptTick = -1L;
        consecutivePathStartFailures = 0;
        stage = Stage.DIRECT;
    }

    private void initialize(
        Vec3 currentPosition,
        Vec3 destination,
        long currentTick
    ) {
        lastObservedPosition = currentPosition;
        trackedDestination = destination;
        activeWaypoint = null;
        lastMeaningfulMovementTick = currentTick;
        lastPathAttemptTick =
            currentTick - refreshIntervalTicks;
        consecutivePathStartFailures = 0;
        stage = Stage.DIRECT;
    }
}
