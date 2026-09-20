package dev.kardane.autobattle.jev;

public enum DecisionApplyResult {
    APPLIED,
    STALE_MATCH,
    STALE_ROUND,
    STALE_ENTITY,
    STALE_DOCTRINE,
    STALE_GENERATION,
    STALE_CANDIDATES,
    INVALID_PLAN,
    LOW_CONFIDENCE_FALLBACK,
    API_ERROR_FALLBACK,
    KEPT_CURRENT_PLAN
}
