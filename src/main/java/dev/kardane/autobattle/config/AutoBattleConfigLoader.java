package dev.kardane.autobattle.config;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class AutoBattleConfigLoader {
    public static final String DIRECTORY_NAME = "autobattle";
    public static final String FILE_NAME = "config.yml";
    public static final String LEGACY_FILE_NAME = "autobattle.yml";

    private static final String DEFAULT_YAML = """
        # AutoBattle server configuration
        #
        # This file is created automatically on first launch.
        # Apply changes with /autobattle admin reload at any time,
        # or restart the server.
        #
        # API keys are stored as plain text in this file. Keep your server
        # config directory private and do not commit this file to Git.

        openai:
          doctrine-normalizer:
            enabled: true
            # Leave empty to fall back to OPENAI_API_KEY.
            # If no key is available, source Doctrine is used unchanged.
            api-key: ""
            base-url: "https://api.openai.com"
            model: "gpt-5.6-luna"
            request-timeout-ms: 2500

        typesafe:
          # Leave empty to fall back to TYPESAFE_API_KEY.
          # If both are empty, ScriptedJevClient is used.
          api-key: ""
          base-url: "https://api.typesafe.ai"
          model: "jev-latest"

        match:
          minimum-players: 4
          rounds: 5
          round-duration-seconds: 90
          countdown-seconds: 5
          respawn-seconds: 7
          command-duration-seconds: 10

        ai:
          decision-interval-seconds: 3.0
          decision-lock-seconds: 2.0
          decision-debounce-seconds: 0.5
          request-timeout-ms: 1500
          minimum-confidence: 0.35

          # RETREAT is only offered to Jev at or below this HP ratio.
          # The same threshold is used by server-side fallback logic.
          fallback-retreat-hp-ratio: 0.25

        doctrine:
          max-line-length: 120

        robot:
          max-health: 100.0
          attack-damage: 10.0
          movement-speed: 0.30
          follow-range: 32.0

          regen-delay-seconds: 5.0
          regen-interval-seconds: 1.0
          regen-amount: 8.0

          engage-leash-blocks: 8.0
          chase-leash-blocks: 20.0

          engage-speed: 1.00
          chase-speed: 1.20
          capture-speed: 1.05
          defend-speed: 1.00
          reposition-speed: 1.10
          retreat-speed: 1.20

          position-reached-distance: 1.5
          defend-radius: 3.0
          retreat-distance: 8.0

        scoring:
          kill: 5
          assist: 2
          core-capture: 3
          core-hold: 1
          assist-window-seconds: 5.0

        core:
          capture-seconds: 3.0
          hold-score-interval-seconds: 2.0

        arena:
          dimension: "minecraft:overworld"

          core:
            x: 0
            y: 80
            z: 0
            radius: 3.0

          robot-spawns:
            - { x: 15.0,  y: 80.0, z: 0.0,   yaw: 90.0,  pitch: 0.0 }
            - { x: -15.0, y: 80.0, z: 0.0,   yaw: -90.0, pitch: 0.0 }
            - { x: 0.0,   y: 80.0, z: 15.0,  yaw: 180.0, pitch: 0.0 }
            - { x: 0.0,   y: 80.0, z: -15.0, yaw: 0.0,   pitch: 0.0 }

          viewer-spawns:
            - { x: 20.0,  y: 88.0, z: 20.0,  yaw: 0.0, pitch: 0.0 }
            - { x: -20.0, y: 88.0, z: 20.0,  yaw: 0.0, pitch: 0.0 }
            - { x: -20.0, y: 88.0, z: -20.0, yaw: 0.0, pitch: 0.0 }
            - { x: 20.0,  y: 88.0, z: -20.0, yaw: 0.0, pitch: 0.0 }

          reposition-nodes:
            - { x: 8,  y: 80, z: 8 }
            - { x: -8, y: 80, z: 8 }
            - { x: -8, y: 80, z: -8 }
            - { x: 8,  y: 80, z: -8 }
        """;

    private AutoBattleConfigLoader() {
    }

    public static AutoBattleConfig load() {
        Path path = configPath();

        try {
            Files.createDirectories(path.getParent());
            migrateLegacyConfig(path);

            if (Files.notExists(path)) {
                Files.writeString(
                    path,
                    DEFAULT_YAML,
                    StandardCharsets.UTF_8
                );
            }

            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);

            Yaml yaml = new Yaml(
                new SafeConstructor(options)
            );

            try (Reader reader = Files.newBufferedReader(
                path,
                StandardCharsets.UTF_8
            )) {
                Object raw = yaml.load(reader);
                return parse(
                    asMap(raw, "root")
                );
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                "Failed to load AutoBattle config: " + path,
                exception
            );
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                "Invalid AutoBattle config: "
                    + path
                    + " ("
                    + exception.getMessage()
                    + ")",
                exception
            );
        }
    }

    public static Path configDirectory() {
        return FabricLoader.getInstance()
            .getConfigDir()
            .resolve(DIRECTORY_NAME);
    }

    public static Path configPath() {
        return configDirectory()
            .resolve(FILE_NAME);
    }

    public static Path legacyConfigPath() {
        return FabricLoader.getInstance()
            .getConfigDir()
            .resolve(LEGACY_FILE_NAME);
    }

    private static void migrateLegacyConfig(
        Path target
    ) throws IOException {
        Path legacy = legacyConfigPath();

        if (Files.exists(target)
            || Files.notExists(legacy)) {
            return;
        }

        Files.move(
            legacy,
            target,
            StandardCopyOption.REPLACE_EXISTING
        );
    }

    private static AutoBattleConfig parse(
        Map<String, Object> root
    ) {
        AutoBattleConfig defaults =
            AutoBattleConfig.defaults();

        Map<String, Object> openai =
            section(root, "openai");
        Map<String, Object> normalizer =
            section(openai, "doctrine-normalizer");
        Map<String, Object> typesafe =
            section(root, "typesafe");
        Map<String, Object> match =
            section(root, "match");
        Map<String, Object> ai =
            section(root, "ai");
        Map<String, Object> doctrine =
            section(root, "doctrine");
        Map<String, Object> robot =
            section(root, "robot");
        Map<String, Object> scoring =
            section(root, "scoring");
        Map<String, Object> core =
            section(root, "core");
        Map<String, Object> arena =
            section(root, "arena");

        TypeSafeConfig typeSafeConfig =
            new TypeSafeConfig(
                stringValue(
                    typesafe,
                    "api-key",
                    defaults.typesafe().apiKey()
                ),
                stringValue(
                    typesafe,
                    "base-url",
                    defaults.typesafe().baseUrl()
                ),
                stringValue(
                    typesafe,
                    "model",
                    defaults.typesafe().model()
                )
            );

        MatchRulesConfig matchConfig =
            new MatchRulesConfig(
                intValue(
                    match,
                    "minimum-players",
                    defaults.match().minimumPlayers()
                ),
                intValue(
                    match,
                    "rounds",
                    defaults.match().roundCount()
                ),
                secondsToTicks(
                    doubleValue(
                        match,
                        "round-duration-seconds",
                        ticksToSeconds(
                            defaults.match().roundDurationTicks()
                        )
                    ),
                    "match.round-duration-seconds",
                    false
                ),
                secondsToTicks(
                    doubleValue(
                        match,
                        "countdown-seconds",
                        ticksToSeconds(
                            defaults.match().countdownTicks()
                        )
                    ),
                    "match.countdown-seconds",
                    true
                ),
                secondsToTicks(
                    doubleValue(
                        match,
                        "respawn-seconds",
                        ticksToSeconds(
                            defaults.match().respawnTicks()
                        )
                    ),
                    "match.respawn-seconds",
                    false
                ),
                secondsToTicks(
                    doubleValue(
                        match,
                        "command-duration-seconds",
                        ticksToSeconds(
                            defaults.match().commandDurationTicks()
                        )
                    ),
                    "match.command-duration-seconds",
                    false
                )
            );

        AiConfig aiConfig = new AiConfig(
            secondsToTicks(
                doubleValue(
                    ai,
                    "decision-interval-seconds",
                    ticksToSeconds(
                        defaults.ai().decisionIntervalTicks()
                    )
                ),
                "ai.decision-interval-seconds",
                false
            ),
            secondsToTicks(
                doubleValue(
                    ai,
                    "decision-lock-seconds",
                    ticksToSeconds(
                        defaults.ai().decisionLockTicks()
                    )
                ),
                "ai.decision-lock-seconds",
                true
            ),
            secondsToTicks(
                doubleValue(
                    ai,
                    "decision-debounce-seconds",
                    ticksToSeconds(
                        defaults.ai().decisionDebounceTicks()
                    )
                ),
                "ai.decision-debounce-seconds",
                true
            ),
            intValue(
                ai,
                "request-timeout-ms",
                defaults.ai().requestTimeoutMs()
            ),
            doubleValue(
                ai,
                "minimum-confidence",
                defaults.ai().minimumConfidence()
            ),
            doubleValue(
                ai,
                "fallback-retreat-hp-ratio",
                defaults.ai().fallbackRetreatHpRatio()
            )
        );

        DoctrineConfig doctrineConfig =
            new DoctrineConfig(
                intValue(
                    doctrine,
                    "max-line-length",
                    defaults.doctrine().maxLineLength()
                )
            );

        DoctrineNormalizerConfig doctrineNormalizerConfig =
            new DoctrineNormalizerConfig(
                booleanValue(
                    normalizer,
                    "enabled",
                    defaults.doctrineNormalizer().enabled()
                ),
                stringValue(
                    normalizer,
                    "api-key",
                    defaults.doctrineNormalizer().apiKey()
                ),
                stringValue(
                    normalizer,
                    "base-url",
                    defaults.doctrineNormalizer().baseUrl()
                ),
                stringValue(
                    normalizer,
                    "model",
                    defaults.doctrineNormalizer().model()
                ),
                intValue(
                    normalizer,
                    "request-timeout-ms",
                    defaults.doctrineNormalizer()
                        .requestTimeoutMs()
                )
            );

        RobotConfig robotConfig = new RobotConfig(
            doubleValue(
                robot,
                "max-health",
                defaults.robot().maxHealth()
            ),
            doubleValue(
                robot,
                "attack-damage",
                defaults.robot().attackDamage()
            ),
            doubleValue(
                robot,
                "movement-speed",
                defaults.robot().movementSpeed()
            ),
            doubleValue(
                robot,
                "follow-range",
                defaults.robot().followRange()
            ),
            secondsToTicks(
                doubleValue(
                    robot,
                    "regen-delay-seconds",
                    ticksToSeconds(
                        defaults.robot().regenDelayTicks()
                    )
                ),
                "robot.regen-delay-seconds",
                true
            ),
            secondsToTicks(
                doubleValue(
                    robot,
                    "regen-interval-seconds",
                    ticksToSeconds(
                        defaults.robot().regenIntervalTicks()
                    )
                ),
                "robot.regen-interval-seconds",
                false
            ),
            (float) doubleValue(
                robot,
                "regen-amount",
                defaults.robot().regenAmount()
            ),
            doubleValue(
                robot,
                "engage-leash-blocks",
                defaults.robot().engageLeashDistance()
            ),
            doubleValue(
                robot,
                "chase-leash-blocks",
                defaults.robot().chaseLeashDistance()
            ),
            doubleValue(
                robot,
                "engage-speed",
                defaults.robot().engageSpeed()
            ),
            doubleValue(
                robot,
                "chase-speed",
                defaults.robot().chaseSpeed()
            ),
            doubleValue(
                robot,
                "capture-speed",
                defaults.robot().captureSpeed()
            ),
            doubleValue(
                robot,
                "defend-speed",
                defaults.robot().defendSpeed()
            ),
            doubleValue(
                robot,
                "reposition-speed",
                defaults.robot().repositionSpeed()
            ),
            doubleValue(
                robot,
                "retreat-speed",
                defaults.robot().retreatSpeed()
            ),
            doubleValue(
                robot,
                "position-reached-distance",
                defaults.robot().positionReachedDistance()
            ),
            doubleValue(
                robot,
                "defend-radius",
                defaults.robot().defendRadius()
            ),
            doubleValue(
                robot,
                "retreat-distance",
                defaults.robot().retreatDistance()
            )
        );

        ScoringConfig scoringConfig =
            new ScoringConfig(
                intValue(
                    scoring,
                    "kill",
                    defaults.scoring().killScore()
                ),
                intValue(
                    scoring,
                    "assist",
                    defaults.scoring().assistScore()
                ),
                intValue(
                    scoring,
                    "core-capture",
                    defaults.scoring().coreCaptureScore()
                ),
                intValue(
                    scoring,
                    "core-hold",
                    defaults.scoring().coreHoldScore()
                ),
                secondsToTicks(
                    doubleValue(
                        scoring,
                        "assist-window-seconds",
                        ticksToSeconds(
                            defaults.scoring().assistWindowTicks()
                        )
                    ),
                    "scoring.assist-window-seconds",
                    false
                )
            );

        CoreRulesConfig coreConfig =
            new CoreRulesConfig(
                secondsToTicks(
                    doubleValue(
                        core,
                        "capture-seconds",
                        ticksToSeconds(
                            defaults.core().captureTicks()
                        )
                    ),
                    "core.capture-seconds",
                    false
                ),
                secondsToTicks(
                    doubleValue(
                        core,
                        "hold-score-interval-seconds",
                        ticksToSeconds(
                            defaults.core().holdScoreIntervalTicks()
                        )
                    ),
                    "core.hold-score-interval-seconds",
                    false
                )
            );

        ArenaConfig arenaConfig =
            parseArena(
                arena,
                defaults.arena()
            );

        return new AutoBattleConfig(
            typeSafeConfig,
            matchConfig,
            aiConfig,
            doctrineConfig,
            doctrineNormalizerConfig,
            robotConfig,
            scoringConfig,
            coreConfig,
            arenaConfig
        );
    }

    private static ArenaConfig parseArena(
        Map<String, Object> arena,
        ArenaConfig defaults
    ) {
        String dimensionText = stringValue(
            arena,
            "dimension",
            defaults.dimension().location().toString()
        );

        ResourceLocation dimensionId =
            ResourceLocation.tryParse(dimensionText);

        if (dimensionId == null) {
            throw new IllegalArgumentException(
                "arena.dimension is not a valid resource location: "
                    + dimensionText
            );
        }

        ResourceKey<Level> dimension =
            ResourceKey.create(
                Registries.DIMENSION,
                dimensionId
            );

        Map<String, Object> core =
            section(arena, "core");

        BlockPos corePos = new BlockPos(
            intValue(
                core,
                "x",
                defaults.corePos().getX()
            ),
            intValue(
                core,
                "y",
                defaults.corePos().getY()
            ),
            intValue(
                core,
                "z",
                defaults.corePos().getZ()
            )
        );

        double radius = doubleValue(
            core,
            "radius",
            defaults.coreRadius()
        );

        List<SpawnPoint> robotSpawns =
            spawnPoints(
                arena.get("robot-spawns"),
                defaults.robotSpawns(),
                "arena.robot-spawns"
            );

        List<SpawnPoint> viewerSpawns =
            spawnPoints(
                arena.get("viewer-spawns"),
                defaults.viewerSpawns(),
                "arena.viewer-spawns"
            );

        List<BlockPos> repositionNodes =
            blockPositions(
                arena.get("reposition-nodes"),
                defaults.repositionNodes(),
                "arena.reposition-nodes"
            );

        return new ArenaConfig(
            dimension,
            corePos,
            radius,
            robotSpawns,
            viewerSpawns,
            repositionNodes
        );
    }

    private static List<SpawnPoint> spawnPoints(
        Object raw,
        List<SpawnPoint> fallback,
        String path
    ) {
        if (raw == null) {
            return fallback;
        }

        List<?> list = asList(raw, path);
        List<SpawnPoint> result = new ArrayList<>();

        for (int index = 0; index < list.size(); index++) {
            Map<String, Object> point =
                asMap(
                    list.get(index),
                    path + "[" + index + "]"
                );

            result.add(
                new SpawnPoint(
                    requiredDouble(point, "x", path),
                    requiredDouble(point, "y", path),
                    requiredDouble(point, "z", path),
                    (float) doubleValue(
                        point,
                        "yaw",
                        0.0D
                    ),
                    (float) doubleValue(
                        point,
                        "pitch",
                        0.0D
                    )
                )
            );
        }

        return List.copyOf(result);
    }

    private static List<BlockPos> blockPositions(
        Object raw,
        List<BlockPos> fallback,
        String path
    ) {
        if (raw == null) {
            return fallback;
        }

        List<?> list = asList(raw, path);
        List<BlockPos> result = new ArrayList<>();

        for (int index = 0; index < list.size(); index++) {
            Map<String, Object> point =
                asMap(
                    list.get(index),
                    path + "[" + index + "]"
                );

            result.add(
                new BlockPos(
                    requiredInt(point, "x", path),
                    requiredInt(point, "y", path),
                    requiredInt(point, "z", path)
                )
            );
        }

        return List.copyOf(result);
    }

    private static Map<String, Object> section(
        Map<String, Object> parent,
        String key
    ) {
        Object value = parent.get(key);

        if (value == null) {
            return Map.of();
        }

        return asMap(value, key);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(
        Object value,
        String path
    ) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException(
                path + " must be a YAML mapping"
            );
        }

        for (Object key : map.keySet()) {
            if (!(key instanceof String)) {
                throw new IllegalArgumentException(
                    path + " contains a non-string key"
                );
            }
        }

        return (Map<String, Object>) map;
    }

    private static List<?> asList(
        Object value,
        String path
    ) {
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException(
                path + " must be a YAML list"
            );
        }

        return list;
    }

    private static String stringValue(
        Map<String, Object> map,
        String key,
        String fallback
    ) {
        Object value = map.get(key);

        if (value == null) {
            return fallback;
        }

        return String.valueOf(value);
    }

    private static boolean booleanValue(
        Map<String, Object> map,
        String key,
        boolean fallback
    ) {
        Object value = map.get(key);

        if (value == null) {
            return fallback;
        }

        if (!(value instanceof Boolean booleanValue)) {
            throw new IllegalArgumentException(
                key + " must be a boolean"
            );
        }

        return booleanValue;
    }

    private static int intValue(
        Map<String, Object> map,
        String key,
        int fallback
    ) {
        Object value = map.get(key);

        if (value == null) {
            return fallback;
        }

        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(
                key + " must be a number"
            );
        }

        return number.intValue();
    }

    private static double doubleValue(
        Map<String, Object> map,
        String key,
        double fallback
    ) {
        Object value = map.get(key);

        if (value == null) {
            return fallback;
        }

        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(
                key + " must be a number"
            );
        }

        return number.doubleValue();
    }

    private static double requiredDouble(
        Map<String, Object> map,
        String key,
        String path
    ) {
        if (!map.containsKey(key)) {
            throw new IllegalArgumentException(
                path + " is missing " + key
            );
        }

        return doubleValue(map, key, 0.0D);
    }

    private static int requiredInt(
        Map<String, Object> map,
        String key,
        String path
    ) {
        if (!map.containsKey(key)) {
            throw new IllegalArgumentException(
                path + " is missing " + key
            );
        }

        return intValue(map, key, 0);
    }

    private static int secondsToTicks(
        double seconds,
        String path,
        boolean allowZero
    ) {
        if (!Double.isFinite(seconds)
            || seconds < 0.0D
            || (!allowZero && seconds <= 0.0D)) {
            throw new IllegalArgumentException(
                path + " must be "
                    + (allowZero ? "non-negative" : "positive")
            );
        }

        long ticks = Math.round(seconds * 20.0D);

        if (!allowZero && ticks < 1L) {
            ticks = 1L;
        }

        if (ticks > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                path + " is too large"
            );
        }

        return (int) ticks;
    }

    private static double ticksToSeconds(int ticks) {
        return ticks / 20.0D;
    }
}
