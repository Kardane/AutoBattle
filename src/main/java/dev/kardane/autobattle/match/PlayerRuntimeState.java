package dev.kardane.autobattle.match;

import dev.kardane.autobattle.command.ActiveCommand;

import java.util.Optional;

public final class PlayerRuntimeState {
    private boolean commandUsed;
    private ActiveCommand activeCommand;
    private boolean doctrineEditedThisReview;
    private boolean reviewReady;

    public void resetForRound() {
        commandUsed = false;
        activeCommand = null;
        doctrineEditedThisReview = false;
        reviewReady = false;
    }

    public boolean commandUsed() {
        return commandUsed;
    }

    public Optional<ActiveCommand> activeCommand() {
        return Optional.ofNullable(activeCommand);
    }

    public void activateCommand(ActiveCommand command) {
        commandUsed = true;
        activeCommand = command;
    }

    public void clearExpiredCommand(long currentTick) {
        if (activeCommand != null
            && !activeCommand.active(currentTick)) {
            activeCommand = null;
        }
    }

    public boolean doctrineEditedThisReview() {
        return doctrineEditedThisReview;
    }

    public void markDoctrineEdited() {
        doctrineEditedThisReview = true;
    }

    public boolean reviewReady() {
        return reviewReady;
    }

    public void setReviewReady(boolean reviewReady) {
        this.reviewReady = reviewReady;
    }
}
