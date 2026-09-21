package dev.kardane.autobattle.core;

import dev.kardane.autobattle.config.CoreRulesConfig;
import dev.kardane.autobattle.config.ScoringConfig;
import dev.kardane.autobattle.config.ArenaConfig;
import dev.kardane.autobattle.match.BattleTeam;
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

        long redInside = inside.stream()
            .filter(controller ->
                controller.team() == BattleTeam.RED
            )
            .count();

        long blueInside = inside.size() - redInside;

        CoreOccupancy occupancy = CoreOccupancy.of(
            redInside,
            blueInside
        );

        state.setContested(occupancy.contested());

        occupancy.soleTeam().ifPresent(team ->
            advanceCapture(
                match,
                team,
                currentTick
            )
        );

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
        // CORE ownership and capture progress belong to a team,
        // not an individual participant. The next tick recomputes
        // occupancy/contested state after a player leaves.
    }

    public void reset() {
        state.reset();
    }

    private void advanceCapture(
        MatchSession match,
        BattleTeam team,
        long currentTick
    ) {
        if (state.ownerTeam()
            .filter(team::equals)
            .isPresent()) {
            state.setCaptureState(null);
            return;
        }

        CoreCaptureState capture = state.captureState()
            .filter(existing ->
                existing.capturingTeam() == team
            )
            .map(CoreCaptureState::advance)
            .orElseGet(() ->
                new CoreCaptureState(team, 1)
            );

        if (capture.progressTicks() < captureTicks) {
            state.setCaptureState(capture);
            return;
        }

        state.setOwnerTeam(team);
        state.setCaptureState(null);
        state.setNextHoldScoreTick(
            currentTick + holdScoreIntervalTicks
        );

        match.teamScore(team).addCoreCapture(
            scoring.coreCaptureScore()
        );
    }

    private void tickOwnerHoldScore(
        MatchSession match,
        long currentTick
    ) {
        state.ownerTeam().ifPresent(team -> {
            var teamScore = match.teamScore(team);
            teamScore.addCoreHoldTicks(1L);

            if (state.nextHoldScoreTick() < 0L) {
                state.setNextHoldScoreTick(
                    currentTick + holdScoreIntervalTicks
                );
                return;
            }

            if (currentTick < state.nextHoldScoreTick()) {
                return;
            }

            teamScore.addCoreHoldPoint(
                scoring.coreHoldScore()
            );

            state.setNextHoldScoreTick(
                currentTick + holdScoreIntervalTicks
            );
        });
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
        int color = state.ownerTeam()
            .map(team -> team.robotColor().rgb())
            .orElse(NEUTRAL_CORE_COLOR);

        return new DustParticleOptions(
            color,
            BOUNDARY_PARTICLE_SCALE
        );
    }
}
