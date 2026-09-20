package dev.kardane.autobattle.core;

import dev.kardane.autobattle.AutoBattleConstants;
import dev.kardane.autobattle.config.ArenaConfig;
import dev.kardane.autobattle.match.MatchPhase;
import dev.kardane.autobattle.match.MatchSession;
import dev.kardane.autobattle.robot.RobotZombie;
import dev.kardane.autobattle.tactics.RobotController;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class CoreController {
    private final ResourceKey<Level> dimension;
    private final BlockPos corePos;
    private final double radius;
    private final double radiusSqr;
    private final int captureTicks;
    private final int holdScoreIntervalTicks;
    private final CoreState state = new CoreState();

    public CoreController(ArenaConfig arena) {
        Objects.requireNonNull(arena, "arena");

        this.dimension = arena.dimension();
        this.corePos = arena.corePos();
        this.radius = arena.coreRadius();
        this.radiusSqr = radius * radius;
        this.captureTicks =
            AutoBattleConstants.CORE_CAPTURE_TICKS;
        this.holdScoreIntervalTicks =
            AutoBattleConstants.CORE_HOLD_SCORE_INTERVAL;
    }

    public void tick(
        MatchSession match,
        long currentTick
    ) {
        if (match.phase() != MatchPhase.ROUND_ACTIVE) {
            return;
        }

        List<RobotController> inside = match.robots()
            .alive()
            .stream()
            .filter(controller ->
                controller.entity()
                    .filter(this::isInside)
                    .isPresent()
            )
            .toList();

        if (inside.isEmpty()) {
            state.setContested(false);
        } else if (inside.size() > 1) {
            state.setContested(true);
        } else {
            state.setContested(false);
            advanceCapture(
                match,
                inside.getFirst(),
                currentTick
            );
        }

        tickOwnerHoldScore(match, currentTick);
    }

    public CoreState state() {
        return state;
    }

    public BlockPos position() {
        return corePos;
    }

    public double radius() {
        return radius;
    }

    public boolean isInside(RobotZombie robot) {
        if (!robot.level().dimension().equals(dimension)) {
            return false;
        }

        return robot.position().distanceToSqr(center()) <= radiusSqr;
    }

    public double distanceTo(RobotZombie robot) {
        return robot.position().distanceTo(center());
    }

    public void removeParticipant(UUID ownerUuid) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");

        if (state.ownerUuid()
            .filter(ownerUuid::equals)
            .isPresent()) {
            state.setOwner(null);
            state.setNextHoldScoreTick(-1L);
        }

        if (state.captureState()
            .filter(capture ->
                capture.capturingOwnerUuid().equals(ownerUuid)
            )
            .isPresent()) {
            state.setCaptureState(null);
        }

        state.setContested(false);
    }

    public void reset() {
        state.reset();
    }

    private void advanceCapture(
        MatchSession match,
        RobotController controller,
        long currentTick
    ) {
        UUID ownerUuid = controller.ownerUuid();

        if (state.ownerUuid()
            .filter(ownerUuid::equals)
            .isPresent()) {
            state.setCaptureState(null);
            return;
        }

        CoreCaptureState capture = state.captureState()
            .filter(existing ->
                existing.capturingOwnerUuid().equals(ownerUuid)
            )
            .map(CoreCaptureState::advance)
            .orElseGet(() ->
                new CoreCaptureState(ownerUuid, 1)
            );

        if (capture.progressTicks() < captureTicks) {
            state.setCaptureState(capture);
            return;
        }

        state.setOwner(ownerUuid);
        state.setCaptureState(null);
        state.setNextHoldScoreTick(
            currentTick + holdScoreIntervalTicks
        );

        match.player(ownerUuid).ifPresent(
            slot -> slot.score().addCoreCapture(
                AutoBattleConstants.CORE_CAPTURE_SCORE
            )
        );
    }

    private void tickOwnerHoldScore(
        MatchSession match,
        long currentTick
    ) {
        state.ownerUuid().ifPresent(ownerUuid ->
            match.player(ownerUuid).ifPresent(slot -> {
                slot.score().addCoreHoldTicks(1L);

                if (state.nextHoldScoreTick() < 0L) {
                    state.setNextHoldScoreTick(
                        currentTick + holdScoreIntervalTicks
                    );
                    return;
                }

                if (currentTick < state.nextHoldScoreTick()) {
                    return;
                }

                slot.score().addCoreHoldPoint(
                    AutoBattleConstants.CORE_HOLD_SCORE
                );

                state.setNextHoldScoreTick(
                    currentTick + holdScoreIntervalTicks
                );
            })
        );
    }

    private Vec3 center() {
        return new Vec3(
            corePos.getX() + 0.5D,
            corePos.getY() + 0.5D,
            corePos.getZ() + 0.5D
        );
    }
}
