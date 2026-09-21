package dev.kardane.autobattle.config;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class LanguageConfigMigrationService {
    static final int CURRENT_MESSAGES_VERSION = 2;

    private static final DateTimeFormatter BACKUP_TIME =
        DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneOffset.UTC);

    MigrationResult migrate(
        Map<String, Object> source,
        Map<String, Object> defaults
    ) {
        Map<String, Object> merged = deepCopyMap(source);
        int fromVersion = versionOf(merged);

        if (fromVersion > CURRENT_MESSAGES_VERSION) {
            throw new IllegalArgumentException(
                "messages-version "
                    + fromVersion
                    + " is newer than supported version "
                    + CURRENT_MESSAGES_VERSION
            );
        }

        mergeMissing(merged, defaults);
        merged.put(
            "messages-version",
            CURRENT_MESSAGES_VERSION
        );

        return new MigrationResult(
            merged,
            fromVersion,
            !merged.equals(source)
        );
    }

    void backupAndWrite(
        Path path,
        MigrationResult result
    ) throws IOException {
        if (!result.changed()) {
            return;
        }

        if (Files.exists(path)) {
            String backupName =
                path.getFileName()
                    + ".bak-v"
                    + result.fromVersion()
                    + "-"
                    + BACKUP_TIME.format(Instant.now());

            Files.copy(
                path,
                path.resolveSibling(backupName),
                StandardCopyOption.COPY_ATTRIBUTES
            );
        }

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(
            DumperOptions.FlowStyle.BLOCK
        );
        options.setPrettyFlow(true);
        options.setIndent(2);

        String yaml = new Yaml(options).dump(
            result.messages()
        );

        Path temp = path.resolveSibling(
            path.getFileName() + ".tmp"
        );

        Files.writeString(
            temp,
            yaml,
            StandardCharsets.UTF_8
        );

        try {
            Files.move(
                temp,
                path,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(
                temp,
                path,
                StandardCopyOption.REPLACE_EXISTING
            );
        }
    }

    private int versionOf(Map<String, Object> root) {
        Object value = root.get("messages-version");

        if (value == null) {
            return 1;
        }

        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(
                "messages-version must be a number"
            );
        }

        return number.intValue();
    }

    private void mergeMissing(
        Map<String, Object> target,
        Map<String, Object> defaults
    ) {
        for (Map.Entry<String, Object> entry :
            defaults.entrySet()) {
            String key = entry.getKey();

            if (!target.containsKey(key)) {
                target.put(
                    key,
                    deepCopy(entry.getValue())
                );
                continue;
            }

            Object current = target.get(key);
            Object fallback = entry.getValue();

            if (current instanceof Map<?, ?>
                && fallback instanceof Map<?, ?>) {
                mergeMissing(
                    castMap(current, key),
                    castMap(fallback, key)
                );
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(
        Object value,
        String path
    ) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException(
                path + " must be a mapping"
            );
        }

        return (Map<String, Object>) map;
    }

    private Map<String, Object> deepCopyMap(
        Map<String, Object> source
    ) {
        Map<String, Object> result =
            new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry :
            source.entrySet()) {
            result.put(
                entry.getKey(),
                deepCopy(entry.getValue())
            );
        }

        return result;
    }

    private Object deepCopy(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result =
                new LinkedHashMap<>();

            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException(
                        "messages mapping contains a non-string key"
                    );
                }

                result.put(
                    key,
                    deepCopy(entry.getValue())
                );
            }

            return result;
        }

        if (value instanceof List<?> list) {
            List<Object> result =
                new ArrayList<>(list.size());

            for (Object item : list) {
                result.add(deepCopy(item));
            }

            return result;
        }

        return value;
    }

    record MigrationResult(
        Map<String, Object> messages,
        int fromVersion,
        boolean changed
    ) {
    }
}
