package dev.kardane.autobattle.review;

import dev.kardane.autobattle.jev.DecisionLog;

public record CriticalDecision(
    DecisionLog decision,
    int importanceScore
) {
}
