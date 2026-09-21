package dev.kardane.autobattle.core;

import dev.kardane.autobattle.match.BattleTeam;

import java.util.Objects;

public record CoreCaptureState(
    BattleTeam capturingTeam,
    int progressTicks
) {
    public CoreCaptureState {
        Objects.requireNonNull(
            capturingTeam,
            "capturingTeam"
        );

        if (progressTicks < 0) {
            throw new IllegalArgumentException(
                "progressTicks must not be negative"
            );
        }
    }

    public CoreCaptureState advance() {
        return new CoreCaptureState(
            capturingTeam,
            progressTicks + 1
        );
    }
}
