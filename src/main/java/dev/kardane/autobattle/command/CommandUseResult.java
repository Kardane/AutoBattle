package dev.kardane.autobattle.command;

public enum CommandUseResult {
    SUCCESS,
    NOT_PARTICIPANT,
    INVALID_PHASE,
    ALREADY_USED,
    ROBOT_DEAD,
    FORFEITED
}
