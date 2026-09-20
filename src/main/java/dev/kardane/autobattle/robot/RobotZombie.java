package dev.kardane.autobattle.robot;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.UUID;

public final class RobotZombie extends Zombie {
    public static final String ENTITY_TAG = "autobattle_robot";

    private final UUID ownerUuid;
    private final RobotColor robotColor;
    private final UUID matchId;

    public RobotZombie(
        Level level,
        UUID ownerUuid,
        RobotColor robotColor,
        UUID matchId
    ) {
        super(EntityType.ZOMBIE, level);
        this.ownerUuid = Objects.requireNonNull(ownerUuid, "ownerUuid");
        this.robotColor = Objects.requireNonNull(robotColor, "robotColor");
        this.matchId = Objects.requireNonNull(matchId, "matchId");
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override
    protected boolean convertsInWater() {
        return false;
    }

    @Override
    protected boolean isSunSensitive() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    public UUID ownerUuid() {
        return ownerUuid;
    }

    public RobotColor robotColor() {
        return robotColor;
    }

    public UUID matchId() {
        return matchId;
    }

    public boolean isAutoBattleRobot() {
        return true;
    }
}
