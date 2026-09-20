package dev.kardane.autobattle.core;

import java.util.Optional;
import java.util.UUID;

public final class CoreState {
    private UUID ownerUuid;
    private CoreCaptureState captureState;
    private boolean contested;
    private long nextHoldScoreTick = -1L;

    public Optional<UUID> ownerUuid() {
        return Optional.ofNullable(ownerUuid);
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

    void setOwner(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
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
        ownerUuid = null;
        captureState = null;
        contested = false;
        nextHoldScoreTick = -1L;
    }
}
