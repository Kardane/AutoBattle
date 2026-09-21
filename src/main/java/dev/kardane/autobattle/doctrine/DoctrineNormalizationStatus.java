package dev.kardane.autobattle.doctrine;

public enum DoctrineNormalizationStatus {
    NORMALIZED,
    CACHE_HIT,
    SHARED_INFLIGHT,
    FALLBACK_DISABLED,
    FALLBACK_ERROR
}
