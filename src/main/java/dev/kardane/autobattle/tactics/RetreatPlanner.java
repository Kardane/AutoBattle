package dev.kardane.autobattle.tactics;

import dev.kardane.autobattle.config.RobotConfig;
import dev.kardane.autobattle.match.MatchSession;
import dev.kardane.autobattle.match.PlayerSlot;
import dev.kardane.autobattle.robot.RobotZombie;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

final class RetreatPlanner {
    private static final int DIRECTION_COUNT = 8;
    private static final int MAX_PATH_CHECKS = 6;
    private static final int GROUND_SCAN_UP = 3;
    private static final int GROUND_SCAN_DOWN = 6;
    private static final double MIN_CANDIDATE_MOVE = 2.0D;
    private static final double BLOCKED_DESTINATION_RADIUS = 2.0D;
    private static final double SWITCH_SCORE_MARGIN = 3.0D;
    private static final double DANGER_DISTANCE = 4.0D;
    private static final double EDGE_DANGER_MARGIN = 1.5D;

    private final PlanValidityPolicy validityPolicy;
    private RobotConfig config;

    RetreatPlanner(
        PlanValidityPolicy validityPolicy,
        RobotConfig config
    ) {
        this.validityPolicy = Objects.requireNonNull(
            validityPolicy,
            "validityPolicy"
        );
        this.config = Objects.requireNonNull(
            config,
            "config"
        );
    }

    void reload(RobotConfig config) {
        this.config = Objects.requireNonNull(
            config,
            "config"
        );
    }

    Optional<RetreatChoice> choose(
        MatchSession match,
        RobotController self,
        RetreatChoice currentChoice,
        Collection<Vec3> blockedDestinations,
        boolean forceSwitch
    ) {
        Objects.requireNonNull(match, "match");
        Objects.requireNonNull(self, "self");
        Objects.requireNonNull(
            blockedDestinations,
            "blockedDestinations"
        );

        RobotZombie selfEntity =
            self.entity().orElse(null);

        if (selfEntity == null
            || !(selfEntity.level()
                instanceof ServerLevel level)) {
            return Optional.empty();
        }

        List<RobotZombie> enemies =
            eligibleRobots(
                match,
                self,
                true
            );

        if (enemies.isEmpty()) {
            return Optional.empty();
        }

        List<RobotZombie> allies =
            eligibleRobots(
                match,
                self,
                false
            );

        Vec3 selfPosition = selfEntity.position();
        double nearDistance = Math.max(
            MIN_CANDIDATE_MOVE + 1.0D,
            config.retreatDistance() * 0.60D
        );
        double farDistance = Math.max(
            nearDistance + 1.0D,
            config.retreatDistance()
        );

        List<RawCandidate> raw =
            new ArrayList<>(
                DIRECTION_COUNT * 2 + 1
            );

        for (int index = 0;
             index < DIRECTION_COUNT;
             index++) {
            double angle =
                index * (Math.PI * 2.0D / DIRECTION_COUNT);
            Vec3 direction = new Vec3(
                Math.cos(angle),
                0.0D,
                Math.sin(angle)
            );

            addCandidate(
                raw,
                level,
                selfEntity,
                selfPosition,
                selfPosition.add(
                    direction.scale(nearDistance)
                ),
                enemies,
                allies,
                blockedDestinations,
                false
            );

            addCandidate(
                raw,
                level,
                selfEntity,
                selfPosition,
                selfPosition.add(
                    direction.scale(farDistance)
                ),
                enemies,
                allies,
                blockedDestinations,
                false
            );
        }

        if (currentChoice != null
            && !forceSwitch) {
            addCandidate(
                raw,
                level,
                selfEntity,
                selfPosition,
                currentChoice.destination(),
                enemies,
                allies,
                blockedDestinations,
                true
            );
        }

        raw.sort(
            Comparator.comparingDouble(
                RawCandidate::cheapScore
            ).reversed()
        );

        List<RetreatChoice> reachable =
            new ArrayList<>();
        int checked = 0;

        for (RawCandidate candidate : raw) {
            if (checked >= MAX_PATH_CHECKS
                && !candidate.current()) {
                continue;
            }

            checked++;

            Path path = selfEntity.getNavigation()
                .createPath(
                    BlockPos.containing(
                        candidate.destination()
                    ),
                    0
                );

            if (path == null || !path.canReach()) {
                continue;
            }

            PathSafety pathSafety = pathSafety(
                path,
                selfEntity,
                enemies
            );

            double score = candidate.cheapScore()
                + Math.min(
                    10.0D,
                    pathSafety.minimumEnemyDistance()
                ) * 1.4D
                - pathSafety.threatPressure() * 8.0D
                - path.getNodeCount() * 0.08D;

            reachable.add(
                new RetreatChoice(
                    candidate.destination(),
                    score,
                    candidate.minimumEnemyDistance(),
                    pathSafety.minimumEnemyDistance(),
                    candidate.edgeMargin()
                )
            );
        }

        if (reachable.isEmpty()) {
            return Optional.empty();
        }

        reachable.sort(
            Comparator.comparingDouble(
                RetreatChoice::score
            ).reversed()
        );

        RetreatChoice best = reachable.getFirst();

        if (currentChoice == null || forceSwitch) {
            return Optional.of(best);
        }

        RetreatChoice current = reachable.stream()
            .filter(choice ->
                choice.destination().distanceToSqr(
                    currentChoice.destination()
                ) < 0.25D
            )
            .findFirst()
            .orElse(null);

        if (best.destination().distanceToSqr(
            current.destination()
        ) < 0.25D) {
            return Optional.of(current);
        }

        return Optional.of(
            shouldSwitch(current, best)
                ? best
                : current
        );
    }

    boolean sufficientlySafe(
        MatchSession match,
        RobotController self
    ) {
        RobotZombie selfEntity =
            self.entity().orElse(null);

        if (selfEntity == null) {
            return false;
        }

        double safeDistance = Math.max(
            config.engageLeashDistance(),
            config.retreatDistance()
        );

        for (RobotZombie enemy :
            eligibleRobots(match, self, true)) {
            if (selfEntity.distanceTo(enemy)
                < safeDistance) {
                return false;
            }
        }

        return true;
    }

    private void addCandidate(
        List<RawCandidate> candidates,
        ServerLevel level,
        RobotZombie selfEntity,
        Vec3 selfPosition,
        Vec3 rawPosition,
        List<RobotZombie> enemies,
        List<RobotZombie> allies,
        Collection<Vec3> blockedDestinations,
        boolean current
    ) {
        Optional<Vec3> adjusted =
            adjustToStandableGround(
                level,
                selfEntity,
                rawPosition
            );

        if (adjusted.isEmpty()) {
            return;
        }

        Vec3 candidate = adjusted.orElseThrow();

        if (!validityPolicy.insideArena(candidate)
            || selfPosition.distanceTo(candidate)
                < MIN_CANDIDATE_MOVE
            || blockedDestinations.stream()
                .anyMatch(blocked ->
                    blocked.distanceToSqr(candidate)
                        <= BLOCKED_DESTINATION_RADIUS
                            * BLOCKED_DESTINATION_RADIUS
                )) {
            return;
        }

        double minEnemyDistance =
            minimumDistance(candidate, enemies);
        double threatPressure =
            threatPressure(candidate, enemies);
        double edgeMargin =
            validityPolicy.arenaRadius()
                - horizontalDistance(
                    validityPolicy.arenaCenter(),
                    candidate
                );
        double nearestAllyDistance =
            minimumDistance(candidate, allies);
        double movementCost =
            selfPosition.distanceTo(candidate);
        double verticalCost =
            Math.abs(candidate.y - selfPosition.y);

        if (verticalCost > 3.0D) {
            return;
        }

        double allySupport = Double.isFinite(
            nearestAllyDistance
        )
            ? Math.max(
                0.0D,
                8.0D - nearestAllyDistance
            )
            : 0.0D;

        double cheapScore =
            Math.min(16.0D, minEnemyDistance) * 2.4D
                - threatPressure * 10.0D
                + Math.min(6.0D, edgeMargin) * 0.9D
                + allySupport * 0.25D
                - movementCost * 0.22D
                - verticalCost * 1.25D;

        if (edgeMargin < EDGE_DANGER_MARGIN) {
            cheapScore -= 10.0D;
        }

        candidates.add(
            new RawCandidate(
                candidate,
                cheapScore,
                minEnemyDistance,
                edgeMargin,
                current
            )
        );
    }

    private Optional<Vec3> adjustToStandableGround(
        ServerLevel level,
        RobotZombie selfEntity,
        Vec3 rawPosition
    ) {
        int x = (int) Math.floor(rawPosition.x);
        int z = (int) Math.floor(rawPosition.z);
        int baseY = (int) Math.floor(rawPosition.y);

        for (int y = baseY + GROUND_SCAN_UP;
             y >= baseY - GROUND_SCAN_DOWN;
             y--) {
            BlockPos ground = new BlockPos(x, y - 1, z);
            BlockPos feet = new BlockPos(x, y, z);
            BlockPos head = feet.above();

            BlockState groundState =
                level.getBlockState(ground);
            BlockState feetState =
                level.getBlockState(feet);
            BlockState headState =
                level.getBlockState(head);

            if (isHazardous(
                groundState,
                feetState,
                headState
            )
                || groundState.getCollisionShape(
                    level,
                    ground
                ).isEmpty()
                || !feetState.getCollisionShape(
                    level,
                    feet
                ).isEmpty()
                || !headState.getCollisionShape(
                    level,
                    head
                ).isEmpty()
                || !level.getFluidState(feet).isEmpty()
                || !level.getFluidState(head).isEmpty()) {
                continue;
            }

            Vec3 stand = new Vec3(
                x + 0.5D,
                y,
                z + 0.5D
            );
            Vec3 offset = stand.subtract(
                selfEntity.position()
            );

            if (!level.noCollision(
                selfEntity,
                selfEntity.getBoundingBox().move(offset)
            )) {
                continue;
            }

            return Optional.of(stand);
        }

        return Optional.empty();
    }

    private boolean isHazardous(
        BlockState ground,
        BlockState feet,
        BlockState head
    ) {
        return hazardousBlock(ground)
            || hazardousBlock(feet)
            || hazardousBlock(head);
    }

    private boolean hazardousBlock(BlockState state) {
        return state.is(Blocks.LAVA)
            || state.is(Blocks.FIRE)
            || state.is(Blocks.SOUL_FIRE)
            || state.is(Blocks.MAGMA_BLOCK)
            || state.is(Blocks.CACTUS)
            || state.is(Blocks.CAMPFIRE)
            || state.is(Blocks.SOUL_CAMPFIRE)
            || state.is(Blocks.POWDER_SNOW)
            || state.is(Blocks.SWEET_BERRY_BUSH)
            || state.is(Blocks.WITHER_ROSE);
    }

    private PathSafety pathSafety(
        Path path,
        RobotZombie selfEntity,
        List<RobotZombie> enemies
    ) {
        if (enemies.isEmpty()) {
            return new PathSafety(
                Double.POSITIVE_INFINITY,
                0.0D
            );
        }

        double minimum = Double.POSITIVE_INFINITY;
        double pressure = 0.0D;
        int nodeCount = path.getNodeCount();
        int stride = Math.max(1, nodeCount / 8);

        for (int index = 0;
             index < nodeCount;
             index += stride) {
            Vec3 position =
                path.getEntityPosAtNode(
                    selfEntity,
                    index
                );
            minimum = Math.min(
                minimum,
                minimumDistance(position, enemies)
            );
            pressure = Math.max(
                pressure,
                threatPressure(position, enemies)
            );
        }

        return new PathSafety(minimum, pressure);
    }

    private List<RobotZombie> eligibleRobots(
        MatchSession match,
        RobotController self,
        boolean enemies
    ) {
        List<RobotZombie> result =
            new ArrayList<>();

        for (RobotController controller :
            match.robots().alive()) {
            if (controller == self) {
                continue;
            }

            boolean isEnemy =
                self.team().isEnemy(controller.team());

            if (isEnemy != enemies) {
                continue;
            }

            PlayerSlot slot = match.player(
                controller.ownerUuid()
            ).orElse(null);

            if (slot == null || slot.forfeited()) {
                continue;
            }

            controller.entity().ifPresent(robot -> {
                if (robot.matchId().equals(match.matchId())
                    && validityPolicy.insideArena(
                        robot.position()
                    )) {
                    result.add(robot);
                }
            });
        }

        return result;
    }

    static boolean shouldSwitch(
        RetreatChoice current,
        RetreatChoice best
    ) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(best, "best");

        return current.risky()
            || best.score()
                >= current.score() + SWITCH_SCORE_MARGIN;
    }

    static double geometryScore(
        Vec3 selfPosition,
        Vec3 candidate,
        List<Vec3> enemies,
        List<Vec3> allies,
        Vec3 arenaCenter,
        double arenaRadius
    ) {
        double minEnemyDistance =
            minimumDistancePositions(
                candidate,
                enemies
            );
        double threatPressure =
            threatPressurePositions(
                candidate,
                enemies
            );
        double edgeMargin =
            arenaRadius - horizontalDistance(
                arenaCenter,
                candidate
            );
        double nearestAllyDistance =
            minimumDistancePositions(
                candidate,
                allies
            );
        double movementCost =
            selfPosition.distanceTo(candidate);

        double allySupport = Double.isFinite(
            nearestAllyDistance
        )
            ? Math.max(
                0.0D,
                8.0D - nearestAllyDistance
            )
            : 0.0D;

        double score =
            Math.min(16.0D, minEnemyDistance) * 2.4D
                - threatPressure * 10.0D
                + Math.min(6.0D, edgeMargin) * 0.9D
                + allySupport * 0.25D
                - movementCost * 0.22D;

        if (edgeMargin < EDGE_DANGER_MARGIN) {
            score -= 10.0D;
        }

        return score;
    }

    private static double minimumDistance(
        Vec3 position,
        List<RobotZombie> robots
    ) {
        double minimum = Double.POSITIVE_INFINITY;

        for (RobotZombie robot : robots) {
            minimum = Math.min(
                minimum,
                position.distanceTo(robot.position())
            );
        }

        return minimum;
    }

    private static double threatPressure(
        Vec3 position,
        List<RobotZombie> enemies
    ) {
        double pressure = 0.0D;

        for (RobotZombie enemy : enemies) {
            double distance =
                position.distanceTo(enemy.position());

            pressure += 1.0D / Math.max(
                1.0D,
                distance
            );
        }

        return pressure;
    }

    private static double minimumDistancePositions(
        Vec3 position,
        List<Vec3> positions
    ) {
        double minimum = Double.POSITIVE_INFINITY;

        for (Vec3 other : positions) {
            minimum = Math.min(
                minimum,
                position.distanceTo(other)
            );
        }

        return minimum;
    }

    private static double threatPressurePositions(
        Vec3 position,
        List<Vec3> enemies
    ) {
        double pressure = 0.0D;

        for (Vec3 enemy : enemies) {
            double distance =
                position.distanceTo(enemy);

            pressure += 1.0D / Math.max(
                1.0D,
                distance
            );
        }

        return pressure;
    }

    private static double horizontalDistance(
        Vec3 first,
        Vec3 second
    ) {
        double dx = first.x - second.x;
        double dz = first.z - second.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    record RetreatChoice(
        Vec3 destination,
        double score,
        double minimumEnemyDistance,
        double pathMinimumEnemyDistance,
        double edgeMargin
    ) {
        RetreatChoice {
            Objects.requireNonNull(
                destination,
                "destination"
            );
        }

        boolean risky() {
            return minimumEnemyDistance < DANGER_DISTANCE
                || pathMinimumEnemyDistance
                    < DANGER_DISTANCE
                || edgeMargin < EDGE_DANGER_MARGIN;
        }
    }

    private record RawCandidate(
        Vec3 destination,
        double cheapScore,
        double minimumEnemyDistance,
        double edgeMargin,
        boolean current
    ) {
    }

    private record PathSafety(
        double minimumEnemyDistance,
        double threatPressure
    ) {
    }
}
