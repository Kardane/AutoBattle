package dev.kardane.autobattle.robot;

import dev.kardane.autobattle.AutoBattleMod;
import dev.kardane.autobattle.match.BattleTeam;
import dev.kardane.autobattle.tactics.TacticalPlanType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;

import java.util.Objects;
import java.util.UUID;

public final class RobotZombie extends Zombie {
    public static final String ENTITY_TAG = "autobattle_robot";
    public static final String NAMEPLATE_TAG =
        "autobattle_robot_nameplate";

    private static final String TAG_LINE_WIDTH = "line_width";
    private static final String TAG_TEXT_OPACITY = "text_opacity";
    private static final String TAG_BACKGROUND = "background";
    private static final String TAG_SHADOW = "shadow";
    private static final String TAG_SEE_THROUGH = "see_through";
    private static final String TAG_DEFAULT_BACKGROUND =
        "default_background";
    private static final String TAG_ALIGNMENT = "alignment";
    private static final int NAMEPLATE_LINE_WIDTH = 320;

    private final UUID ownerUuid;
    private final BattleTeam team;
    private final String targetId;
    private final RobotColor robotColor;
    private final UUID matchId;

    private Component ownerDisplayName =
        Component.literal("Robot");
    private String displayedBehavior = "대기";
    private Display.TextDisplay nameplate;

    private int lastDisplayedHealth = Integer.MIN_VALUE;
    private int lastDisplayedMaxHealth = Integer.MIN_VALUE;
    private String lastDisplayedBehavior;
    private boolean nameplateFailureLogged;

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

        setCustomName(null);
        setCustomNameVisible(false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();

        if (!isAlive()) {
            discardNameplate();
            return;
        }

        ensureNameplate();
        refreshNameplate();
    }

    @Override
    public void onRemoval(Entity.RemovalReason reason) {
        discardNameplate();
        super.onRemoval(reason);
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
        lastDisplayedBehavior = null;
        refreshNameplate();
    }

    public void setDisplayedPlan(TacticalPlanType planType) {
        displayedBehavior = RobotNameplateText.behaviorLabel(
            planType
        );
        lastDisplayedBehavior = null;
        refreshNameplate();
    }

    public void attachNameplate() {
        ensureNameplate();
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
            && maxHealth == lastDisplayedMaxHealth
            && Objects.equals(
                displayedBehavior,
                lastDisplayedBehavior
            )) {
            return;
        }

        lastDisplayedHealth = health;
        lastDisplayedMaxHealth = maxHealth;
        lastDisplayedBehavior = displayedBehavior;

        if (nameplate != null
            && !nameplate.isRemoved()) {
            updateNameplateText(
                RobotNameplateText.build(
                    targetId,
                    ownerDisplayName,
                    team,
                    getHealth(),
                    getMaxHealth(),
                    displayedBehavior
                )
            );
        }
    }

    private void ensureNameplate() {
        if (!(level() instanceof ServerLevel serverLevel)
            || isRemoved()
            || (nameplate != null && !nameplate.isRemoved())) {
            return;
        }

        Display.TextDisplay display = new Display.TextDisplay(
            EntityType.TEXT_DISPLAY,
            serverLevel
        );

        display.setNoGravity(true);
        display.setInvulnerable(true);
        display.setPos(
            getX(),
            getY() + getBbHeight() + 0.35D,
            getZ()
        );
        display.addTag(NAMEPLATE_TAG);

        if (!serverLevel.addFreshEntity(display)) {
            logNameplateFailure(
                "server level rejected the TextDisplay entity"
            );
            return;
        }

        if (!display.startRiding(this, true)) {
            display.discard();
            logNameplateFailure(
                "TextDisplay could not ride the robot zombie"
            );
            return;
        }

        nameplate = display;
        nameplateFailureLogged = false;
        lastDisplayedHealth = Integer.MIN_VALUE;
        lastDisplayedMaxHealth = Integer.MIN_VALUE;
        lastDisplayedBehavior = null;
    }

    private void logNameplateFailure(String reason) {
        if (nameplateFailureLogged) {
            return;
        }

        nameplateFailureLogged = true;
        AutoBattleMod.LOGGER.warn(
            "Failed to create nameplate for robot {}: {}",
            getScoreboardName(),
            reason
        );
    }

    private void updateNameplateText(Component text) {
        if (nameplate == null || nameplate.isRemoved()) {
            return;
        }

        CompoundTag data = new CompoundTag();
        data.store(
            Display.TextDisplay.TAG_TEXT,
            ComponentSerialization.CODEC,
            text
        );
        data.putString(Display.TAG_BILLBOARD, "center");
        data.putFloat(Display.TAG_VIEW_RANGE, 1.0F);
        data.putFloat(Display.TAG_WIDTH, 2.0F);
        data.putFloat(Display.TAG_HEIGHT, 2.0F);
        data.putInt(TAG_LINE_WIDTH, NAMEPLATE_LINE_WIDTH);
        data.putByte(TAG_TEXT_OPACITY, (byte) -1);
        data.putInt(TAG_BACKGROUND, 0);
        data.putBoolean(TAG_SHADOW, true);
        data.putBoolean(TAG_SEE_THROUGH, true);
        data.putBoolean(TAG_DEFAULT_BACKGROUND, false);
        data.putString(TAG_ALIGNMENT, "left");

        nameplate.load(
            TagValueInput.create(
                ProblemReporter.DISCARDING,
                level().registryAccess(),
                data
            )
        );
        nameplate.addTag(NAMEPLATE_TAG);
    }

    private void discardNameplate() {
        Display.TextDisplay display = nameplate;
        nameplate = null;

        if (display == null || display.isRemoved()) {
            return;
        }

        display.stopRiding();
        display.discard();
    }
}
