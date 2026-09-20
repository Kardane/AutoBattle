package dev.kardane.autobattle.robot;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.UUID;

public final class RobotFactory {
    public static final double MAX_HEALTH = 100.0D;
    public static final double ATTACK_DAMAGE = 10.0D;
    public static final double MOVEMENT_SPEED = 0.30D;
    public static final double FOLLOW_RANGE = 32.0D;

    public RobotZombie spawnRobot(
        ServerLevel level,
        UUID matchId,
        UUID ownerUuid,
        Component ownerName,
        RobotColor color,
        Vec3 position,
        float yaw
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(matchId, "matchId");
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(ownerName, "ownerName");
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(position, "position");

        RobotZombie robot = new RobotZombie(
            level,
            ownerUuid,
            color,
            matchId
        );

        applyBaseConfiguration(robot);
        applyBaseAttributes(robot);
        equipIdentityChestplate(robot, color);

        robot.setPos(position.x, position.y, position.z);
        robot.setYRot(yaw);
        robot.setXRot(0.0F);
        robot.setCustomName(
            Component.literal("[" + color.name() + "] ")
                .withStyle(color.chatColor())
                .append(ownerName)
        );
        robot.setCustomNameVisible(true);
        robot.addTag(RobotZombie.ENTITY_TAG);

        if (!level.addFreshEntity(robot)) {
            throw new IllegalStateException("Failed to add AutoBattle robot to the level.");
        }

        return robot;
    }

    public RobotZombie spawnTestRobot(
        ServerPlayer owner,
        RobotColor color
    ) {
        Vec3 position = owner.position()
            .add(owner.getLookAngle().scale(3.0D));

        return spawnRobot(
            owner.serverLevel(),
            UUID.randomUUID(),
            owner.getUUID(),
            owner.getName(),
            color,
            position,
            owner.getYRot()
        );
    }

    private void applyBaseConfiguration(RobotZombie robot) {
        robot.setBaby(false);
        robot.setCanBreakDoors(false);
        robot.setCanPickUpLoot(false);
        robot.setPersistenceRequired();
    }

    private void applyBaseAttributes(RobotZombie robot) {
        setBaseValue(robot.getAttribute(Attributes.MAX_HEALTH), MAX_HEALTH);
        setBaseValue(robot.getAttribute(Attributes.ATTACK_DAMAGE), ATTACK_DAMAGE);
        setBaseValue(robot.getAttribute(Attributes.MOVEMENT_SPEED), MOVEMENT_SPEED);
        setBaseValue(robot.getAttribute(Attributes.FOLLOW_RANGE), FOLLOW_RANGE);
        setBaseValue(
            robot.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE),
            0.0D
        );

        robot.setHealth((float) MAX_HEALTH);
    }

    private void equipIdentityChestplate(
        RobotZombie robot,
        RobotColor color
    ) {
        ItemStack chestplate = new ItemStack(Items.LEATHER_CHESTPLATE);

        chestplate.set(
            DataComponents.DYED_COLOR,
            new DyedItemColor(color.rgb())
        );

        chestplate.set(
            DataComponents.ATTRIBUTE_MODIFIERS,
            ItemAttributeModifiers.EMPTY
        );

        robot.setItemSlot(EquipmentSlot.CHEST, chestplate);
        robot.setDropChance(EquipmentSlot.CHEST, 0.0F);
    }

    private void setBaseValue(
        AttributeInstance attribute,
        double value
    ) {
        if (attribute == null) {
            throw new IllegalStateException("Required robot attribute is missing.");
        }

        attribute.setBaseValue(value);
    }
}
