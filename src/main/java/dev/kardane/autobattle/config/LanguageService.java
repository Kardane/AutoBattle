package dev.kardane.autobattle.config;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class LanguageService {
    private LanguageConfig config;

    public LanguageService(LanguageConfig config) {
        this.config = Objects.requireNonNull(
            config,
            "config"
        );
    }

    public void reload(LanguageConfig config) {
        this.config = Objects.requireNonNull(
            config,
            "config"
        );
    }

    public String text(String key) {
        return config.text(key);
    }

    public String format(
        String key,
        Object... placeholders
    ) {
        return config.format(key, placeholders);
    }

    public Component component(
        String key,
        Object... placeholders
    ) {
        return componentText(
            format(key, placeholders)
        );
    }

    public Component componentText(String text) {
        Objects.requireNonNull(text, "text");

        MutableComponent result = Component.empty();
        StringBuilder segment = new StringBuilder();
        List<ChatFormatting> styles =
            new ArrayList<>();

        for (int index = 0;
             index < text.length();
             index++) {
            char current = text.charAt(index);

            if (current != '&'
                || index + 1 >= text.length()) {
                segment.append(current);
                continue;
            }

            char code = text.charAt(index + 1);

            if (code == '&') {
                segment.append('&');
                index++;
                continue;
            }

            ChatFormatting formatting =
                legacyFormatting(code);

            if (formatting == null) {
                segment.append(current);
                continue;
            }

            appendSegment(
                result,
                segment,
                styles
            );

            if (formatting == ChatFormatting.RESET) {
                styles.clear();
            } else if (formatting.isColor()) {
                styles.clear();
                styles.add(formatting);
            } else if (!styles.contains(formatting)) {
                styles.add(formatting);
            }

            index++;
        }

        appendSegment(
            result,
            segment,
            styles
        );

        return result;
    }

    private static void appendSegment(
        MutableComponent result,
        StringBuilder segment,
        List<ChatFormatting> styles
    ) {
        if (segment.isEmpty()) {
            return;
        }

        MutableComponent component =
            Component.literal(segment.toString());

        if (!styles.isEmpty()) {
            component.withStyle(
                styles.toArray(
                    ChatFormatting[]::new
                )
            );
        }

        result.append(component);
        segment.setLength(0);
    }

    private static ChatFormatting legacyFormatting(
        char rawCode
    ) {
        return switch (
            Character.toLowerCase(rawCode)
        ) {
            case '0' -> ChatFormatting.BLACK;
            case '1' -> ChatFormatting.DARK_BLUE;
            case '2' -> ChatFormatting.DARK_GREEN;
            case '3' -> ChatFormatting.DARK_AQUA;
            case '4' -> ChatFormatting.DARK_RED;
            case '5' -> ChatFormatting.DARK_PURPLE;
            case '6' -> ChatFormatting.GOLD;
            case '7' -> ChatFormatting.GRAY;
            case '8' -> ChatFormatting.DARK_GRAY;
            case '9' -> ChatFormatting.BLUE;
            case 'a' -> ChatFormatting.GREEN;
            case 'b' -> ChatFormatting.AQUA;
            case 'c' -> ChatFormatting.RED;
            case 'd' -> ChatFormatting.LIGHT_PURPLE;
            case 'e' -> ChatFormatting.YELLOW;
            case 'f' -> ChatFormatting.WHITE;
            case 'k' -> ChatFormatting.OBFUSCATED;
            case 'l' -> ChatFormatting.BOLD;
            case 'm' -> ChatFormatting.STRIKETHROUGH;
            case 'n' -> ChatFormatting.UNDERLINE;
            case 'o' -> ChatFormatting.ITALIC;
            case 'r' -> ChatFormatting.RESET;
            default -> null;
        };
    }
}
