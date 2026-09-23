package dev.kardane.autobattle.config;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class AutoBattleConfigLoaderTest {
    @Test
    void preservesNegativeArenaYValues() {
        Map<String, Object> root = new LinkedHashMap<>();

        Map<String, Object> arena = new LinkedHashMap<>();
        arena.put("dimension", "minecraft:overworld");
        arena.put("radius", 30.0D);

        Map<String, Object> core = new LinkedHashMap<>();
        core.put("x", 0);
        core.put("y", -60);
        core.put("z", 0);
        core.put("radius", 3.0D);
        arena.put("core", core);

        Map<String, Object> teamSpawns =
            new LinkedHashMap<>();
        teamSpawns.put("axis", "x");
        teamSpawns.put("distance-from-core", 18.0D);
        teamSpawns.put("member-spacing", 3.0D);
        teamSpawns.put("y", -59.0D);
        teamSpawns.put("swap-sides-each-round", true);
        teamSpawns.put("random-radius", 2.5D);
        arena.put("team-spawns", teamSpawns);

        Map<String, Object> viewer =
            new LinkedHashMap<>();
        viewer.put("radius", 24.0D);
        viewer.put("y", -53.0D);
        arena.put("viewer-spawn", viewer);

        root.put("arena", arena);

        Map<String, Object> robot = new LinkedHashMap<>();
        robot.put("hold-reaction-range", 18.0D);
        root.put("robot", robot);

        AutoBattleConfig parsed =
            AutoBattleConfigLoader.parse(root);

        assertEquals(-60, parsed.arena().corePos().getY());
        assertEquals(
            -59.0D,
            parsed.arena().teamSpawns().y()
        );
        assertEquals(
            30.0D,
            parsed.arena().arenaRadius()
        );
        assertEquals(
            2.5D,
            parsed.arena().teamSpawns().randomRadius()
        );
        assertEquals(
            -53.0D,
            parsed.arena().viewerSpawn().y()
        );
        assertEquals(
            18.0D,
            parsed.robot().holdReactionRange()
        );
        assertEquals(
            12.0D,
            AutoBattleConfig.defaults().robot().holdReactionRange()
        );
    }
}
