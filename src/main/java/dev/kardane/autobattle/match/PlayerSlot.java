package dev.kardane.autobattle.match;

import dev.kardane.autobattle.doctrine.Doctrine;
import dev.kardane.autobattle.robot.RobotColor;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class PlayerSlot {
    private final UUID playerUuid;
    private final BattleTeam team;
    private final int memberIndex;
    private final int slotIndex;
    private final ScoreState score = new ScoreState();
    private final PlayerRuntimeState runtime = new PlayerRuntimeState();

    private Doctrine doctrine;
    private boolean forfeited;
    private String playerName;

    public PlayerSlot(
        UUID playerUuid,
        BattleTeam team,
        int memberIndex,
        int slotIndex
    ) {
        this.playerUuid = Objects.requireNonNull(
            playerUuid,
            "playerUuid"
        );
        this.team = Objects.requireNonNull(team, "team");

        if (memberIndex < 0 || memberIndex >= 8) {
            throw new IllegalArgumentException(
                "memberIndex must be between 0 and 7"
            );
        }

        if (slotIndex < 0 || slotIndex >= 16) {
            throw new IllegalArgumentException(
                "slotIndex must be between 0 and 15"
            );
        }

        this.memberIndex = memberIndex;
        this.slotIndex = slotIndex;
    }

    public UUID playerUuid() {
        return playerUuid;
    }

    public String playerName() {
        return playerName != null ? playerName : targetId();
    }

    public void setPlayerName(String playerName) {
        this.playerName = Objects.requireNonNull(playerName, "playerName");
    }

    public BattleTeam team() {
        return team;
    }

    public int memberIndex() {
        return memberIndex;
    }

    public String targetId() {
        return team.targetId(memberIndex);
    }

    public RobotColor color() {
        return team.robotColor();
    }

    public int slotIndex() {
        return slotIndex;
    }

    public ScoreState score() {
        return score;
    }

    public PlayerRuntimeState runtime() {
        return runtime;
    }

    public Optional<Doctrine> doctrine() {
        return Optional.ofNullable(doctrine);
    }

    public void setDoctrine(Doctrine doctrine) {
        this.doctrine = Objects.requireNonNull(
            doctrine,
            "doctrine"
        );
    }

    /**
     * Lobby participants are ready immediately after joining.
     */
    public boolean ready() {
        return !forfeited;
    }

    /**
     * Compatibility no-op. Lobby readiness is automatic in team mode.
     */
    public void setReady(boolean ready) {
        // Intentionally ignored.
    }

    public boolean forfeited() {
        return forfeited;
    }

    public void forfeit() {
        forfeited = true;
    }
}
