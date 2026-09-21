package dev.kardane.autobattle.robot;

import dev.kardane.autobattle.match.BattleTeam;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.UUID;

public final class RobotZombie extends Zombie {
    public static final String ENTITY_TAG = "autobattle_robot";

    private final UUID ownerUuid;
    private final BattleTeam team;
    private final String targetId;
    private final RobotColor robotColor;
    private final UUID matchId;

    private Component ownerDisplayName =
        Component.literal("Robot");

    private int lastDisplayedHealth = Integer.MIN_VALUE;
    private int lastDisplayedMaxHealth = Integer.MIN_VALUE;

    public RobotZombie(
        Level level,
        UUID ownerUuid,
        BattleTeam team,
        String targetId,
        UUID matchId
    ) {
        super(EntityType.ZOMBIE, level);
        this.ownerUuid = Objects.requireNonNull(ownerUuid, "ownerUuid");
        this.team = Objects.requireNonNull(team, "team");
        this.targetId = Objects.requireNonNull(
            targetId,
            "targetId"
        );
        this.robotColor = team.robotColor();
        this.matchId = Objects.requireNonNull(matchId, "matchId");
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        refreshNameplate();
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

    public void setOwnerDisplayName(Component ownerDisplayName) {
        this.ownerDisplayName = Objects.requireNonNull(
            ownerDisplayName,
            "ownerDisplayName"
        );
        lastDisplayedHealth = Integer.MIN_VALUE;
        lastDisplayedMaxHealth = Integer.MIN_VALUE;
        refreshNameplate();
    }

    public UUID ownerUuid() {
        return ownerUuid;
    }

    public BattleTeam team() {
        return team;
    }

    public String targetId() {
        return targetId;
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

    private void refreshNameplate() {
        int health = Math.max(
            0,
            Math.round(getHealth())
        );

        int maxHealth = Math.max(
            1,
            Math.round(getMaxHealth())
        );

        if (health == lastDisplayedHealth
            && maxHealth == lastDisplayedMaxHealth) {
            return;
        }

        lastDisplayedHealth = health;
        lastDisplayedMaxHealth = maxHealth;

        setCustomName(
            Component.literal(
                "[" + targetId + "] "
            )
            .withStyle(robotColor.chatColor())
            .append(ownerDisplayName.copy())
            .append(
                Component.literal(
                    " | ♥ " + health + "/" + maxHealth
                )
            )
        );

        setCustomNameVisible(true);
    }
}
