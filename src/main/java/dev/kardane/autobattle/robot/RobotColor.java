package dev.kardane.autobattle.robot;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public enum RobotColor {
    RED(0xB02E26, ChatFormatting.RED),
    BLUE(0x3C44AA, ChatFormatting.BLUE),
    GREEN(0x5E7C16, ChatFormatting.GREEN),
    YELLOW(0xFED83D, ChatFormatting.YELLOW);

    private final int rgb;
    private final ChatFormatting chatColor;

    RobotColor(int rgb, ChatFormatting chatColor) {
        this.rgb = rgb;
        this.chatColor = chatColor;
    }

    public int rgb() {
        return rgb;
    }

    public ChatFormatting chatColor() {
        return chatColor;
    }

    public Component displayName() {
        return Component.literal(name()).withStyle(chatColor);
    }
}
