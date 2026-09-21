package dev.kardane.autobattle.core;

import dev.kardane.autobattle.match.BattleTeam;

import java.util.Optional;

public final class CoreState {
    private BattleTeam ownerTeam;
    private CoreCaptureState captureState;
    private boolean contested;
    private long nextHoldScoreTick = -1L;

    public Optional<BattleTeam> ownerTeam() {
        return Optional.ofNullable(ownerTeam);
    }

    public boolean contested() {
        return contested;
    }

    public Optional<CoreCaptureState> captureState() {
        return Optional.ofNullable(captureState);
    }

    public long nextHoldScoreTick() {
        return nextHoldScoreTick;
    }

    void setOwnerTeam(BattleTeam ownerTeam) {
        this.ownerTeam = ownerTeam;
    }

    void setCaptureState(CoreCaptureState captureState) {
        this.captureState = captureState;
    }

    void setContested(boolean contested) {
        this.contested = contested;
    }

    void setNextHoldScoreTick(long nextHoldScoreTick) {
        this.nextHoldScoreTick = nextHoldScoreTick;
    }

    void reset() {
        ownerTeam = null;
        captureState = null;
        contested = false;
        nextHoldScoreTick = -1L;
    }
}
