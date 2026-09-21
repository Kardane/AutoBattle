package dev.kardane.autobattle.config;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class LanguageConfigMigrationServiceTest {
    private final LanguageConfigMigrationService service =
        new LanguageConfigMigrationService();

    @Test
    void preservesUserOverridesAndAddsTeamMessages() {
        Map<String, Object> source = map(
            "chat",
            map(
                "round-started",
                "[CUSTOM] Round {round}",
                "robot-killed",
                "[CUSTOM] kill"
            ),
            "commands",
            map(
                "joined",
                "custom joined"
            )
        );

        Map<String, Object> defaults = map(
            "messages-version",
            2,
            "chat",
            map(
                "round-started",
                "[AutoBattle] Round {round} started.",
                "robot-killed",
                "default kill",
                "team-winner",
                "{team} TEAM WINS",
                "team-draw",
                "DRAW"
            ),
            "commands",
            map(
                "joined",
                "default joined",
                "joined-team",
                "Joined {team} as {id}.",
                "auto-ready",
                "Automatically ready."
            )
        );

        LanguageConfigMigrationService.MigrationResult result =
            service.migrate(source, defaults);

        assertTrue(result.changed());
        assertEquals(1, result.fromVersion());
        assertEquals(
            2,
            result.messages().get("messages-version")
        );

        Map<String, Object> chat =
            section(result.messages(), "chat");

        assertEquals(
            "[CUSTOM] Round {round}",
            chat.get("round-started")
        );
        assertEquals(
            "[CUSTOM] kill",
            chat.get("robot-killed")
        );
        assertEquals(
            "{team} TEAM WINS",
            chat.get("team-winner")
        );
        assertEquals("DRAW", chat.get("team-draw"));

        Map<String, Object> commands =
            section(result.messages(), "commands");

        assertEquals(
            "custom joined",
            commands.get("joined")
        );
        assertEquals(
            "Joined {team} as {id}.",
            commands.get("joined-team")
        );
        assertEquals(
            "Automatically ready.",
            commands.get("auto-ready")
        );
    }

    @Test
    void secondMigrationIsIdempotent() {
        Map<String, Object> defaults = map(
            "messages-version",
            2,
            "commands",
            map(
                "joined-team",
                "Joined {team} as {id}."
            )
        );

        LanguageConfigMigrationService.MigrationResult first =
            service.migrate(
                new LinkedHashMap<>(),
                defaults
            );

        LanguageConfigMigrationService.MigrationResult second =
            service.migrate(
                first.messages(),
                defaults
            );

        assertFalse(second.changed());
        assertEquals(2, second.fromVersion());
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
