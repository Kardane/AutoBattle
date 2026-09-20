package dev.kardane.autobattle.match;

public final class ScoreState {
    private int totalScore;
    private int roundScore;

    private int roundKills;
    private int roundDeaths;
    private int roundAssists;

    private int totalKills;
    private int totalDeaths;
    private int totalAssists;

    private long roundCoreHoldTicks;
    private int roundCoreCaptures;

    private float roundDamageDealt;
    private float roundDamageTaken;

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

    public int totalKills() {
        return totalKills;
    }

    public int totalDeaths() {
        return totalDeaths;
    }

    public int totalAssists() {
        return totalAssists;
    }

    public long roundCoreHoldTicks() {
        return roundCoreHoldTicks;
    }

    public int roundCoreCaptures() {
        return roundCoreCaptures;
    }

    public float roundDamageDealt() {
        return roundDamageDealt;
    }

    public float roundDamageTaken() {
        return roundDamageTaken;
    }

    public void addKill(int points) {
        roundKills++;
        totalKills++;
        addScore(points);
    }

    public void addDeath() {
        roundDeaths++;
        totalDeaths++;
    }

    public void addAssist(int points) {
        roundAssists++;
        totalAssists++;
        addScore(points);
    }

    public void addCoreCapture(int points) {
        roundCoreCaptures++;
        addScore(points);
    }

    public void addCoreHoldPoint(int points) {
        addScore(points);
    }

    public void addCoreHoldTicks(long ticks) {
        if (ticks < 0L) {
            throw new IllegalArgumentException(
                "Core hold ticks must not be negative."
            );
        }

        roundCoreHoldTicks += ticks;
    }

    public void addDamageDealt(float amount) {
        if (amount < 0.0F) {
            throw new IllegalArgumentException(
                "Damage amount must not be negative."
            );
        }

        roundDamageDealt += amount;
    }

    public void addDamageTaken(float amount) {
        if (amount < 0.0F) {
            throw new IllegalArgumentException(
                "Damage amount must not be negative."
            );
        }

        roundDamageTaken += amount;
    }

    public void addScore(int points) {
        if (points < 0) {
            throw new IllegalArgumentException(
                "Score delta must not be negative."
            );
        }

        roundScore += points;
        totalScore += points;
    }

    public void resetRound() {
        roundScore = 0;
        roundKills = 0;
        roundDeaths = 0;
        roundAssists = 0;
        roundCoreHoldTicks = 0L;
        roundCoreCaptures = 0;
        roundDamageDealt = 0.0F;
        roundDamageTaken = 0.0F;
    }
}
