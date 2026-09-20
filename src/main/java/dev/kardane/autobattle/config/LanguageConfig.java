package dev.kardane.autobattle.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class LanguageConfig {
    private final Map<String, String> values;

    public LanguageConfig(Map<String, String> values) {
        Objects.requireNonNull(values, "values");
        this.values = Map.copyOf(
            new LinkedHashMap<>(values)
        );
    }

    public String text(String key) {
        Objects.requireNonNull(key, "key");

        String value = values.get(key);

        if (value == null) {
            return key;
        }

        return value;
    }

    public String format(
        String key,
        Object... placeholders
    ) {
        if (placeholders.length % 2 != 0) {
            throw new IllegalArgumentException(
                "Placeholders must be key/value pairs."
            );
        }

        String result = text(key);

        for (int index = 0;
             index < placeholders.length;
             index += 2) {
            String placeholder = String.valueOf(
                placeholders[index]
            );

            String value = String.valueOf(
                placeholders[index + 1]
            );

            result = result.replace(
                "{" + placeholder + "}",
                value
            );
        }

        return result;
    }

    public Map<String, String> values() {
        return values;
    }
}
