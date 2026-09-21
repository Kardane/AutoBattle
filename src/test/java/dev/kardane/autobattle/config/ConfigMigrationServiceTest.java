package dev.kardane.autobattle.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class ConfigMigrationServiceTest {
    @TempDir
    Path tempDir;

    private final ConfigMigrationService service =
        new ConfigMigrationService();

    @Test
    void v1MigrationPreservesSecretsAndTunables() {
        Map<String, Object> source = v1Source();
        Map<String, Object> defaults = v2Defaults();

        ConfigMigrationService.MigrationResult result =
            service.migrate(source, defaults);

        assertTrue(result.changed());
        assertEquals(1, result.fromVersion());
        assertEquals(
            2,
            result.config().get("config-version")
        );

        Map<String, Object> typesafe =
            section(result.config(), "typesafe");

        assertEquals(
            "secret-typesafe-key",
            typesafe.get("api-key")
        );
        assertEquals(
            "custom-model",
            typesafe.get("model")
        );

        Map<String, Object> openai =
            section(
                section(result.config(), "openai"),
                "doctrine-normalizer"
            );

        assertEquals(
            "secret-openai-key",
            openai.get("api-key")
        );

        Map<String, Object> ai =
            section(result.config(), "ai");

        assertEquals(
            2450,
            ai.get("request-timeout-ms")
        );

        Map<String, Object> match =
            section(result.config(), "match");

        assertFalse(match.containsKey("minimum-players"));
        assertEquals(1, match.get("min-team-size"));
        assertEquals(8, match.get("max-team-size"));
        assertEquals(9, match.get("rounds"));

        Map<String, Object> arena =
            section(result.config(), "arena");

        assertFalse(arena.containsKey("robot-spawns"));
        assertFalse(arena.containsKey("viewer-spawns"));
        assertTrue(arena.containsKey("team-spawns"));
        assertTrue(arena.containsKey("viewer-spawn"));

        Map<String, Object> core =
            section(arena, "core");

        assertEquals(123, core.get("x"));
        assertEquals(77, core.get("y"));
        assertEquals(-45, core.get("z"));
    }

    @Test
    void migrationIsIdempotent() {
        ConfigMigrationService.MigrationResult first =
            service.migrate(v1Source(), v2Defaults());

        ConfigMigrationService.MigrationResult second =
            service.migrate(
                first.config(),
                v2Defaults()
            );

        assertFalse(second.changed());
        assertEquals(2, second.fromVersion());
        assertEquals(first.config(), second.config());
    }

    @Test
    void changedMigrationBacksUpAndAtomicallyRewritesFile()
        throws Exception {
        Path config = tempDir.resolve("config.yml");

        Files.writeString(
            config,
            "match:\n  minimum-players: 4\n",
            StandardCharsets.UTF_8
        );

        ConfigMigrationService.MigrationResult result =
            service.migrate(v1Source(), v2Defaults());

        service.backupAndWrite(config, result);

        assertTrue(Files.exists(config));
        assertFalse(
            Files.exists(
                tempDir.resolve("config.yml.tmp")
            )
        );

        long backups;
        try (var files = Files.list(tempDir)) {
            backups = files
                .filter(path ->
                    path.getFileName()
                        .toString()
                        .startsWith("config.yml.bak-v1-")
                )
                .count();
        }

        assertEquals(1L, backups);

        String migrated = Files.readString(
            config,
            StandardCharsets.UTF_8
        );

        assertTrue(migrated.contains("config-version: 2"));
        assertTrue(migrated.contains("secret-typesafe-key"));
        assertTrue(migrated.contains("min-team-size"));
        assertFalse(migrated.contains("minimum-players"));
    }

    private Map<String, Object> v1Source() {
        Map<String, Object> root =
            new LinkedHashMap<>();

        root.put(
            "typesafe",
            map(
                "api-key", "secret-typesafe-key",
                "base-url", "https://typesafe.example",
                "model", "custom-model"
            )
        );

        root.put(
            "openai",
            map(
                "doctrine-normalizer",
                map(
                    "enabled", true,
                    "api-key", "secret-openai-key",
                    "model", "custom-normalizer"
                )
            )
        );

        root.put(
            "match",
            map(
                "minimum-players", 4,
                "rounds", 9
            )
        );

        root.put(
            "ai",
            map(
                "request-timeout-ms", 2450
            )
        );

        root.put(
            "arena",
            map(
                "dimension", "minecraft:overworld",
                "core",
                map(
                    "x", 123,
                    "y", 77,
                    "z", -45,
                    "radius", 4.5D
                ),
                "robot-spawns",
                List.of(
                    map(
                        "x", 1.0D,
                        "y", 2.0D,
                        "z", 3.0D
                    )
                ),
                "viewer-spawns",
                List.of(
                    map(
                        "x", 4.0D,
                        "y", 5.0D,
                        "z", 6.0D
                    )
                )
            )
        );

        return root;
    }

    private Map<String, Object> v2Defaults() {
        Map<String, Object> root =
            new LinkedHashMap<>();

        root.put("config-version", 2);
        root.put(
            "match",
            map(
                "min-team-size", 1,
                "max-team-size", 8,
                "rounds", 5
            )
        );

        root.put(
            "typesafe",
            map(
                "api-key", "",
                "base-url", "https://api.typesafe.ai",
                "model", "jev-latest"
            )
        );

        root.put(
            "ai",
            map(
                "request-timeout-ms", 1500,
                "minimum-confidence", 0.35D
            )
        );

        root.put(
            "arena",
            map(
                "dimension", "minecraft:overworld",
                "core",
                map(
                    "x", 0,
                    "y", 80,
                    "z", 0,
                    "radius", 3.0D
                ),
                "team-spawns",
                map(
                    "axis", "x",
                    "distance-from-core", 18.0D,
                    "member-spacing", 3.0D,
                    "y", 80.0D,
                    "swap-sides-each-round", true
                ),
                "viewer-spawn",
                map(
                    "radius", 24.0D,
                    "y", 88.0D
                )
            )
        );

        return root;
    }

    private Map<String, Object> map(Object... values) {
        Map<String, Object> result =
            new LinkedHashMap<>();

        for (int index = 0; index < values.length; index += 2) {
            result.put(
                (String) values[index],
                values[index + 1]
            );
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> section(
        Map<String, Object> parent,
        String key
    ) {
        return (Map<String, Object>) parent.get(key);
    }
}
