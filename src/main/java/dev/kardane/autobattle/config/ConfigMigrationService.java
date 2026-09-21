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

final class ConfigMigrationService {
    static final int CURRENT_CONFIG_VERSION = 2;

    private static final DateTimeFormatter BACKUP_TIME =
        DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneOffset.UTC);

    MigrationResult migrate(
        Map<String, Object> source,
        Map<String, Object> defaults
    ) {
        Map<String, Object> migrated =
            deepCopyMap(source);

        int fromVersion = versionOf(migrated);

        if (fromVersion > CURRENT_CONFIG_VERSION) {
            throw new IllegalArgumentException(
                "config-version "
                    + fromVersion
                    + " is newer than supported version "
                    + CURRENT_CONFIG_VERSION
            );
        }

        if (fromVersion < 2) {
            migrateV1ToV2(migrated, defaults);
        }

        mergeMissing(migrated, defaults);
        migrated.put(
            "config-version",
            CURRENT_CONFIG_VERSION
        );

        return new MigrationResult(
            migrated,
            fromVersion,
            !migrated.equals(source)
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
            result.config()
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

    private void migrateV1ToV2(
        Map<String, Object> root,
        Map<String, Object> defaults
    ) {
        Map<String, Object> match =
            mutableSection(root, "match");

        match.remove("minimum-players");
        match.putIfAbsent("min-team-size", 1);
        match.putIfAbsent("max-team-size", 8);

        Map<String, Object> arena =
            mutableSection(root, "arena");

        arena.remove("robot-spawns");
        arena.remove("viewer-spawns");

        Map<String, Object> defaultArena =
            mutableSection(defaults, "arena");

        arena.putIfAbsent(
            "team-spawns",
            deepCopy(
                defaultArena.get("team-spawns")
            )
        );
        arena.putIfAbsent(
            "viewer-spawn",
            deepCopy(
                defaultArena.get("viewer-spawn")
            )
        );

        root.put("config-version", 2);
    }

    private int versionOf(Map<String, Object> root) {
        Object value = root.get("config-version");

        if (value == null) {
            return 1;
        }

        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(
                "config-version must be a number"
            );
        }

        int version = number.intValue();

        if (version < 1) {
            throw new IllegalArgumentException(
                "config-version must be positive"
            );
        }

        return version;
    }

    private void mergeMissing(
        Map<String, Object> target,
        Map<String, Object> defaults
    ) {
        for (Map.Entry<String, Object> entry :
            defaults.entrySet()) {
            String key = entry.getKey();
            Object defaultValue = entry.getValue();

            if (!target.containsKey(key)) {
                target.put(
                    key,
                    deepCopy(defaultValue)
                );
                continue;
            }

            Object existing = target.get(key);

            if (existing instanceof Map<?, ?>
                && defaultValue instanceof Map<?, ?>) {
                mergeMissing(
                    castMutableMap(existing, key),
                    castMutableMap(defaultValue, key)
                );
            }
        }
    }

    private Map<String, Object> mutableSection(
        Map<String, Object> parent,
        String key
    ) {
        Object existing = parent.get(key);

        if (existing == null) {
            Map<String, Object> created =
                new LinkedHashMap<>();
            parent.put(key, created);
            return created;
        }

        return castMutableMap(existing, key);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMutableMap(
        Object value,
        String path
    ) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException(
                path + " must be a YAML mapping"
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
                        "YAML mapping contains a non-string key"
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
        Map<String, Object> config,
        int fromVersion,
        boolean changed
    ) {
    }
}
