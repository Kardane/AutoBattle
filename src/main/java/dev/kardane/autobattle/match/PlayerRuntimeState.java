package dev.kardane.autobattle.match;

public final class PlayerRuntimeState {
    private boolean commandUsed;
    private boolean doctrineEditedThisReview;
    private boolean reviewReady;

    public void resetForRound() {
        commandUsed = false;
        doctrineEditedThisReview = false;
        reviewReady = false;
    }

    public boolean commandUsed() {
        return commandUsed;
    }

    public void markCommandUsed() {
        commandUsed = true;
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
