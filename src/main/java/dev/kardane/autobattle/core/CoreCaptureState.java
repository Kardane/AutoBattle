package dev.kardane.autobattle.core;

import java.util.UUID;

public record CoreCaptureState(
    UUID capturingOwnerUuid,
    int progressTicks
) {
    public CoreCaptureState {
        if (capturingOwnerUuid == null) {
            throw new NullPointerException("capturingOwnerUuid");
        }

        if (progressTicks < 0) {
            throw new IllegalArgumentException(
                "progressTicks must not be negative"
            );
        }
    }

    public CoreCaptureState advance() {
        return new CoreCaptureState(
            capturingOwnerUuid,
            progressTicks + 1
        );
    }
}
