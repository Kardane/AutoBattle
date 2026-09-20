package dev.kardane.autobattle.robot;

public final class RobotRuntimeState {
    private long lastDamageTick = Long.MIN_VALUE;
    private long nextRegenTick = Long.MAX_VALUE;
    private int currentAttackers;
    private long respawnAtTick = -1L;
    private boolean alive;
    private boolean frozen;
    private long spawnedAtTick;

    public void markDamaged(long tick) {
        lastDamageTick = tick;
    }

    public long lastDamageTick() {
        return lastDamageTick;
    }

    public boolean regenEligible(
        long currentTick,
        int delayTicks
    ) {
        if (!alive) {
            return false;
        }

        if (lastDamageTick == Long.MIN_VALUE) {
            return true;
        }

        return currentTick - lastDamageTick >= delayTicks;
    }

    public long nextRegenTick() {
        return nextRegenTick;
    }

    public void setNextRegenTick(long nextRegenTick) {
        this.nextRegenTick = nextRegenTick;
    }

    public int currentAttackers() {
        return currentAttackers;
    }

    public void setCurrentAttackers(int currentAttackers) {
        this.currentAttackers = Math.max(0, currentAttackers);
    }

    public boolean alive() {
        return alive;
    }

    public void markDead(long respawnTick) {
        alive = false;
        respawnAtTick = respawnTick;
        nextRegenTick = Long.MAX_VALUE;
    }

    public boolean readyToRespawn(long currentTick) {
        return !alive
            && respawnAtTick >= 0L
            && currentTick >= respawnAtTick;
    }

    public long respawnAtTick() {
        return respawnAtTick;
    }

    public void markSpawned(long tick) {
        alive = true;
        spawnedAtTick = tick;
        respawnAtTick = -1L;
        lastDamageTick = Long.MIN_VALUE;
        nextRegenTick = Long.MAX_VALUE;
        currentAttackers = 0;
        frozen = false;
    }

    public long spawnedAtTick() {
        return spawnedAtTick;
    }

    public boolean frozen() {
        return frozen;
    }

    public void setFrozen(boolean frozen) {
        this.frozen = frozen;
    }
}
