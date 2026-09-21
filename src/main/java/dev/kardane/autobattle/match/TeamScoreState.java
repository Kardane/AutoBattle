package dev.kardane.autobattle.match;

public final class TeamScoreState {
    private int totalScore;
    private int roundScore;
    private int totalKills;
    private int roundKills;
    private int totalAssists;
    private int roundAssists;
    private int totalCoreCaptures;
    private int roundCoreCaptures;
    private long roundCoreHoldTicks;

    public int totalScore() {
        return totalScore;
    }

    public int roundScore() {
        return roundScore;
    }

    public int totalKills() {
        return totalKills;
    }

    public int roundKills() {
        return roundKills;
    }

    public int totalAssists() {
        return totalAssists;
    }

    public int roundAssists() {
        return roundAssists;
    }

    public int totalCoreCaptures() {
        return totalCoreCaptures;
    }

    public int roundCoreCaptures() {
        return roundCoreCaptures;
    }

    public long roundCoreHoldTicks() {
        return roundCoreHoldTicks;
    }

    public void addKill(int points) {
        roundKills++;
        totalKills++;
        addScore(points);
    }

    public void addAssist(int points) {
        roundAssists++;
        totalAssists++;
        addScore(points);
    }

    public void addCoreCapture(int points) {
        roundCoreCaptures++;
        totalCoreCaptures++;
        addScore(points);
    }

    public void addCoreHoldPoint(int points) {
        addScore(points);
    }

    public void addCoreHoldTicks(long ticks) {
        if (ticks < 0L) {
            throw new IllegalArgumentException(
                "Core hold ticks must not be negative"
            );
        }

        roundCoreHoldTicks += ticks;
    }

    public void resetRound() {
        roundScore = 0;
        roundKills = 0;
        roundAssists = 0;
        roundCoreCaptures = 0;
        roundCoreHoldTicks = 0L;
    }

    private void addScore(int points) {
        if (points < 0) {
            throw new IllegalArgumentException(
                "Score delta must not be negative"
            );
        }

        roundScore += points;
        totalScore += points;
    }
}
