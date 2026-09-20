package dev.kardane.autobattle.match;

import dev.kardane.autobattle.doctrine.Doctrine;
import dev.kardane.autobattle.robot.RobotColor;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class PlayerSlot {
    private final UUID playerUuid;
    private final RobotColor color;
    private final int slotIndex;
    private final ScoreState score = new ScoreState();
    private final PlayerRuntimeState runtime = new PlayerRuntimeState();

    private Doctrine doctrine;
    private boolean ready;
    private boolean forfeited;

    public PlayerSlot(UUID playerUuid, RobotColor color, int slotIndex) {
        this.playerUuid = Objects.requireNonNull(playerUuid, "playerUuid");
        this.color = Objects.requireNonNull(color, "color");
        this.slotIndex = slotIndex;
    }

    public UUID playerUuid() {
        return playerUuid;
    }

    public RobotColor color() {
        return color;
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
        this.doctrine = Objects.requireNonNull(doctrine, "doctrine");
    }

    public boolean ready() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public boolean forfeited() {
        return forfeited;
    }

    public void forfeit() {
        forfeited = true;
        ready = false;
    }
}
