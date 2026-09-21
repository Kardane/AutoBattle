package dev.kardane.autobattle.match;

import dev.kardane.autobattle.core.CoreController;
import dev.kardane.autobattle.robot.RobotRegistry;

import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class MatchSession {
    private final UUID matchId;
    private final Map<UUID, PlayerSlot> players =
        new LinkedHashMap<>();
    private final RobotRegistry robotRegistry;
    private final RoundState roundState = new RoundState();
    private final CoreController coreController;
    private final EnumMap<BattleTeam, TeamScoreState> teamScores =
        new EnumMap<>(BattleTeam.class);

    private MatchPhase phase = MatchPhase.LOBBY;
    private int currentRound;
    private long phaseStartedTick;

    public MatchSession(
        UUID matchId,
        RobotRegistry robotRegistry,
        CoreController coreController
    ) {
        this.matchId = Objects.requireNonNull(
            matchId,
            "matchId"
        );
        this.robotRegistry = Objects.requireNonNull(
            robotRegistry,
            "robotRegistry"
        );
        this.coreController = Objects.requireNonNull(
            coreController,
            "coreController"
        );

        for (BattleTeam team : BattleTeam.values()) {
            teamScores.put(team, new TeamScoreState());
        }
    }

    public UUID matchId() {
        return matchId;
    }

    public MatchPhase phase() {
        return phase;
    }

    public int currentRound() {
        return currentRound;
    }

    public long phaseStartedTick() {
        return phaseStartedTick;
    }

    public RobotRegistry robots() {
        return robotRegistry;
    }

    public RoundState roundState() {
        return roundState;
    }

    public CoreController core() {
        return coreController;
    }

    public TeamScoreState teamScore(BattleTeam team) {
        return teamScores.get(
            Objects.requireNonNull(team, "team")
        );
    }

    public Optional<BattleTeam> winnerTeam() {
        int red = teamScore(BattleTeam.RED).totalScore();
        int blue = teamScore(BattleTeam.BLUE).totalScore();

        if (red == blue) {
            return Optional.empty();
        }

        return Optional.of(
            red > blue
                ? BattleTeam.RED
                : BattleTeam.BLUE
        );
    }

    public boolean draw() {
        return teamScore(BattleTeam.RED).totalScore()
            == teamScore(BattleTeam.BLUE).totalScore();
    }

    public Collection<PlayerSlot> players() {
        return List.copyOf(players.values());
    }

    public Optional<PlayerSlot> player(UUID playerUuid) {
        return Optional.ofNullable(players.get(playerUuid));
    }

    void addPlayer(PlayerSlot slot) {
        players.put(slot.playerUuid(), slot);
    }

    void removePlayer(UUID playerUuid) {
        players.remove(playerUuid);
    }

    void setPhase(MatchPhase phase, long currentTick) {
        this.phase = phase;
        this.phaseStartedTick = currentTick;
    }

    void setCurrentRound(int currentRound) {
        this.currentRound = currentRound;
    }
}
