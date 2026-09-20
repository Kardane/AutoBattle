package dev.kardane.autobattle.core;

import dev.kardane.autobattle.config.CoreRulesConfig;
import dev.kardane.autobattle.config.ScoringConfig;
import dev.kardane.autobattle.config.ArenaConfig;
import dev.kardane.autobattle.match.MatchPhase;
import dev.kardane.autobattle.match.MatchSession;
import dev.kardane.autobattle.robot.RobotZombie;
import dev.kardane.autobattle.tactics.RobotController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class CoreController {
    private static final int BOUNDARY_PARTICLE_INTERVAL_TICKS = 10;
    private static final int BOUNDARY_PARTICLE_POINTS = 48;
    private static final double BOUNDARY_PARTICLE_Y_OFFSET = 0.15D;
    private static final float BOUNDARY_PARTICLE_SCALE = 1.0F;
    private static final int NEUTRAL_CORE_COLOR = 0xFFFFFF;

    private ResourceKey<Level> dimension;
    private BlockPos corePos;
    private double radius;
    private int captureTicks;
    private int holdScoreIntervalTicks;
    private ScoringConfig scoring;
    private final CoreState state = new CoreState();

    public CoreController(
        ArenaConfig arena,
        CoreRulesConfig rules,
        ScoringConfig scoring
    ) {
        Objects.requireNonNull(arena, "arena");
        Objects.requireNonNull(rules, "rules");
        this.scoring = Objects.requireNonNull(
            scoring,
            "scoring"
        );

        this.dimension = arena.dimension();
        this.corePos = arena.corePos();
        this.radius = arena.coreRadius();
        this.captureTicks = rules.captureTicks();
        this.holdScoreIntervalTicks =
            rules.holdScoreIntervalTicks();
    }

    public void reloadConfig(
        ArenaConfig arena,
        CoreRulesConfig rules,
        ScoringConfig scoring
    ) {
        Objects.requireNonNull(arena, "arena");
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(scoring, "scoring");

        boolean coreLocationChanged =
            !dimension.equals(arena.dimension())
                || !corePos.equals(arena.corePos());

        dimension = arena.dimension();
        corePos = arena.corePos();
        radius = arena.coreRadius();
        captureTicks = rules.captureTicks();
        holdScoreIntervalTicks =
            rules.holdScoreIntervalTicks();
        this.scoring = scoring;

        if (coreLocationChanged) {
            state.reset();
        }
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

    public void renderBoundary(
        MinecraftServer server,
        MatchSession match,
        long currentTick
    ) {
        if (currentTick % BOUNDARY_PARTICLE_INTERVAL_TICKS != 0L) {
            return;
        }

        ServerLevel level = server.getLevel(dimension);

        if (level == null) {
            return;
        }

        double centerX = corePos.getX() + 0.5D;
        double centerY =
            corePos.getY() + BOUNDARY_PARTICLE_Y_OFFSET;
        double centerZ = corePos.getZ() + 0.5D;
        double minX = centerX - radius;
        double maxX = centerX + radius;
        double minZ = centerZ - radius;
        double maxZ = centerZ + radius;
        int pointsPerSide = BOUNDARY_PARTICLE_POINTS / 4;
        ParticleOptions particle = boundaryParticle(match);

        for (int side = 0; side < 4; side++) {
            for (int index = 0;
                 index < pointsPerSide;
                 index++) {
                double progress = (double) index
                    / pointsPerSide;
                double x;
                double z;

                if (side == 0) {
                    x = minX + (maxX - minX) * progress;
                    z = minZ;
                } else if (side == 1) {
                    x = maxX;
                    z = minZ + (maxZ - minZ) * progress;
                } else if (side == 2) {
                    x = maxX - (maxX - minX) * progress;
                    z = maxZ;
                } else {
                    x = minX;
                    z = maxZ - (maxZ - minZ) * progress;
                }

                level.sendParticles(
                    particle,
                    x,
                    centerY,
                    z,
                    1,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D
                );
            }
        }
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

        Vec3 center = center();
        double deltaX = Math.abs(
            robot.position().x - center.x
        );
        double deltaZ = Math.abs(
            robot.position().z - center.z
        );

        return deltaX <= radius && deltaZ <= radius;
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
                scoring.coreCaptureScore()
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
                    scoring.coreHoldScore()
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

    private ParticleOptions boundaryParticle(
        MatchSession match
    ) {
        int color = state.ownerUuid()
            .flatMap(match::player)
            .map(slot -> slot.color().rgb())
            .orElse(NEUTRAL_CORE_COLOR);

        return new DustParticleOptions(
            color,
            BOUNDARY_PARTICLE_SCALE
        );
    }
}
