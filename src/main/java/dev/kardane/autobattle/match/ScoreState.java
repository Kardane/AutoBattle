package dev.kardane.autobattle.match;

public final class ScoreState {
    private int totalScore;
    private int roundScore;
    private int roundKills;
    private int roundDeaths;
    private int roundAssists;

    public int totalScore() {
        return totalScore;
    }

    public int roundScore() {
        return roundScore;
    }

    public int roundKills() {
        return roundKills;
    }

    public int roundDeaths() {
        return roundDeaths;
    }

    public int roundAssists() {
        return roundAssists;
    }

    public void addKill(int points) {
        roundKills++;
        addScore(points);
    }

    public void addDeath() {
        roundDeaths++;
    }

    public void addAssist(int points) {
        roundAssists++;
        addScore(points);
    }

    public void addScore(int points) {
        if (points < 0) {
            throw new IllegalArgumentException("Score delta must not be negative.");
        }
        roundScore += points;
        totalScore += points;
    }

    public void resetRound() {
        roundScore = 0;
        roundKills = 0;
        roundDeaths = 0;
        roundAssists = 0;
    }
}
