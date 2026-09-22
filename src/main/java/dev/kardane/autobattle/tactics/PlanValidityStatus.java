package dev.kardane.autobattle.tactics;

public enum PlanValidityStatus {
    VALID,
    TARGET_MISSING,
    TARGET_DEAD,
    TARGET_INELIGIBLE,
    ASSIST_CYCLE,
    WRONG_MATCH,
    OUTSIDE_ARENA,
    OUT_OF_RANGE,
    TEMPORARILY_UNREACHABLE
}
