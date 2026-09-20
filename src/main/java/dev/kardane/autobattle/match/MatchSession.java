package dev.kardane.autobattle.match;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class MatchSession {
    private final UUID matchId;
    private final Map<UUID, PlayerSlot> players = new LinkedHashMap<>();
    private final RoundState roundState = new RoundState();

    private MatchPhase phase = MatchPhase.LOBBY;
    private int currentRound;
    private long phaseStartedTick;

    public MatchSession(UUID matchId) {
        this.matchId = matchId;
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

    public RoundState roundState() {
        return roundState;
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
