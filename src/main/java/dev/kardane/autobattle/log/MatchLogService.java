package dev.kardane.autobattle.log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.kardane.autobattle.AutoBattleMod;
import dev.kardane.autobattle.command.PlayerCommandType;
import dev.kardane.autobattle.config.AutoBattleConfig;
import dev.kardane.autobattle.match.BattleTeam;
import dev.kardane.autobattle.match.MatchSession;
import dev.kardane.autobattle.match.PlayerSlot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class MatchLogService {
    private final Gson gson = new GsonBuilder()
        .disableHtmlEscaping()
        .create();

    private final ExecutorService ioExecutor =
        Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(
                runnable,
                "autobattle-match-log"
            );
            thread.setDaemon(true);
            return thread;
        });

    private final Path logDirectory;

    public MatchLogService(Path logDirectory) {
        this.logDirectory = logDirectory;
    }

    public void matchStarted(
        MinecraftServer server,
        MatchSession match,
        AutoBattleConfig config,
        long serverTick
    ) {
        Map<String, Object> payload =
            new LinkedHashMap<>();

        payload.put("mode", "TEAM_BATTLE");
        payload.put(
            "teamSize",
            (int) match.players().stream()
                .filter(slot -> !slot.forfeited())
                .filter(slot ->
                    slot.team() == BattleTeam.RED
                )
                .count()
        );
        payload.put(
            "participants",
            playerSnapshots(server, match)
        );
        payload.put(
            "teamScores",
            teamScores(match)
        );

        Map<String, Object> rules =
            new LinkedHashMap<>();

        rules.put("roundCount", config.roundCount());
        rules.put(
            "roundDurationTicks",
            config.roundDurationTicks()
        );
        rules.put(
            "countdownTicks",
            config.countdownTicks()
        );
        rules.put(
            "respawnTicks",
            config.respawnTicks()
        );
        rules.put(
            "commandDurationTicks",
            config.commandDurationTicks()
        );
        rules.put(
            "decisionIntervalTicks",
            config.decisionIntervalTicks()
        );
        rules.put(
            "decisionLockTicks",
            config.decisionLockTicks()
        );
        rules.put(
            "arenaDimension",
            config.arena()
                .dimension()
                .location()
                .toString()
        );

        payload.put("rules", rules);

        append(
            match,
            "match_started",
            serverTick,
            payload
        );
    }

    public void roundStarted(
        MinecraftServer server,
        MatchSession match,
        long serverTick
    ) {
        Map<String, Object> payload =
            new LinkedHashMap<>();

        payload.put(
            "participants",
            playerSnapshots(server, match)
        );
        payload.put(
            "teamScores",
            teamScores(match)
        );

        append(
            match,
            "round_started",
            serverTick,
            payload
        );
    }

    public void roundEnded(
        MinecraftServer server,
        MatchSession match,
        long serverTick
    ) {
        Map<String, Object> payload =
            new LinkedHashMap<>();

        payload.put(
            "participants",
            playerSnapshots(server, match)
        );
        payload.put(
            "teamScores",
            teamScores(match)
        );

        var coreTelemetry = match.core().telemetry();
        Map<String, Object> core =
            new LinkedHashMap<>();

        core.put(
            "emptyTicks",
            coreTelemetry.emptyTicks()
        );
        core.put(
            "redOnlyTicks",
            coreTelemetry.redOnlyTicks()
        );
        core.put(
            "blueOnlyTicks",
            coreTelemetry.blueOnlyTicks()
        );
        core.put(
            "contestedTicks",
            coreTelemetry.contestedTicks()
        );
        core.put(
            "totalTicks",
            coreTelemetry.totalTicks()
        );

        payload.put("coreOccupancy", core);

        append(
            match,
            "round_ended",
            serverTick,
            payload
        );
    }

    public void robotKilled(
        MatchSession match,
        UUID victimOwnerUuid,
        UUID killerOwnerUuid,
        List<UUID> assistOwnerUuids,
        long serverTick
    ) {
        Map<String, Object> payload =
            new LinkedHashMap<>();

        payload.put(
            "victimOwnerUuid",
            victimOwnerUuid.toString()
        );

        match.player(victimOwnerUuid).ifPresent(slot -> {
            payload.put("victimTargetId", slot.targetId());
            payload.put("victimTeam", slot.team().name());
        });

        payload.put(
            "killerOwnerUuid",
            killerOwnerUuid == null
                ? null
                : killerOwnerUuid.toString()
        );

        if (killerOwnerUuid != null) {
            match.player(killerOwnerUuid).ifPresent(slot -> {
                payload.put("killerTargetId", slot.targetId());
                payload.put("killerTeam", slot.team().name());
            });
        }

        payload.put(
            "assistOwnerUuids",
            assistOwnerUuids.stream()
                .map(UUID::toString)
                .toList()
        );
        payload.put(
            "assistTargetIds",
            assistOwnerUuids.stream()
                .map(match::player)
                .flatMap(java.util.Optional::stream)
                .map(PlayerSlot::targetId)
                .toList()
        );
        payload.put(
            "teamScores",
            teamScores(match)
        );

        append(
            match,
            "robot_killed",
            serverTick,
            payload
        );
    }

    public void coreCaptured(
        MatchSession match,
        BattleTeam team,
        long serverTick
    ) {
        append(
            match,
            "core_captured",
            serverTick,
            Map.of(
                "team",
                team.name(),
                "teamScore",
                match.teamScore(team).totalScore()
            )
        );
    }

    public void playerCommand(
        MatchSession match,
        PlayerSlot slot,
        PlayerCommandType type,
        long serverTick
    ) {
        append(
            match,
            "player_command",
            serverTick,
            Map.of(
                "ownerUuid",
                slot.playerUuid().toString(),
                "targetId",
                slot.targetId(),
                "team",
                slot.team().name(),
                "color",
                slot.color().name(),
                "command",
                type.name()
            )
        );
    }

    public void playerForfeited(
        MatchSession match,
        PlayerSlot slot,
        long serverTick
    ) {
        append(
            match,
            "player_forfeited",
            serverTick,
            Map.of(
                "ownerUuid",
                slot.playerUuid().toString(),
                "targetId",
                slot.targetId(),
                "team",
                slot.team().name(),
                "color",
                slot.color().name()
            )
        );
    }

    public void matchFinished(
        MinecraftServer server,
        MatchSession match,
        long serverTick
    ) {
        List<Map<String, Object>> standings =
            match.players()
                .stream()
                .sorted(
                    Comparator.comparingInt(
                        (PlayerSlot slot) ->
                            slot.score().totalScore()
                    ).reversed()
                )
                .map(slot ->
                    playerSnapshot(server, slot)
                )
                .toList();

        Map<String, Object> payload =
            new LinkedHashMap<>();

        payload.put(
            "winnerTeam",
            match.winnerTeam()
                .map(Enum::name)
                .orElse(null)
        );
        payload.put("draw", match.draw());
        payload.put("teamScores", teamScores(match));
        payload.put("standings", standings);

        append(
            match,
            "match_finished",
            serverTick,
            payload
        );
    }

    public void matchAborted(
        MinecraftServer server,
        MatchSession match,
        long serverTick,
        String reason
    ) {
        append(
            match,
            "match_aborted",
            serverTick,
            Map.of(
                "reason",
                reason,
                "participants",
                playerSnapshots(server, match)
            )
        );
    }

    public void close() {
        ioExecutor.shutdown();

        try {
            if (!ioExecutor.awaitTermination(
                3L,
                TimeUnit.SECONDS
            )) {
                ioExecutor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            ioExecutor.shutdownNow();
        }
    }

    private void append(
        MatchSession match,
        String type,
        long serverTick,
        Map<String, Object> payload
    ) {
        Map<String, Object> event =
            new LinkedHashMap<>();

        event.put(
            "timestamp",
            Instant.now().toString()
        );
        event.put(
            "matchId",
            match.matchId().toString()
        );
        event.put("type", type);
        event.put(
            "round",
            match.currentRound()
        );
        event.put("serverTick", serverTick);
        event.put("phase", match.phase().name());
        event.put("data", payload);

        String line = gson.toJson(event);
        UUID matchId = match.matchId();

        ioExecutor.submit(() ->
            writeLine(matchId, line)
        );
    }

    private void writeLine(
        UUID matchId,
        String line
    ) {
        try {
            Files.createDirectories(logDirectory);

            Path file = logDirectory.resolve(
                matchId + ".jsonl"
            );

            Files.writeString(
                file,
                line + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
        } catch (IOException exception) {
            AutoBattleMod.LOGGER.error(
                "Failed to write match log for {}",
                matchId,
                exception
            );
        }
    }

    private Map<String, Object> teamScores(
        MatchSession match
    ) {
        Map<String, Object> result =
            new LinkedHashMap<>();

        for (BattleTeam team : BattleTeam.values()) {
            var score = match.teamScore(team);
            Map<String, Object> snapshot =
                new LinkedHashMap<>();

            snapshot.put("totalScore", score.totalScore());
            snapshot.put("roundScore", score.roundScore());
            snapshot.put("totalKills", score.totalKills());
            snapshot.put("roundKills", score.roundKills());
            snapshot.put(
                "totalAssists",
                score.totalAssists()
            );
            snapshot.put(
                "roundAssists",
                score.roundAssists()
            );
            snapshot.put(
                "roundCoreCaptures",
                score.roundCoreCaptures()
            );
            snapshot.put(
                "roundCoreHoldTicks",
                score.roundCoreHoldTicks()
            );

            result.put(team.name(), snapshot);
        }

        return result;
    }

    private List<Map<String, Object>> playerSnapshots(
        MinecraftServer server,
        MatchSession match
    ) {
        List<Map<String, Object>> players =
            new ArrayList<>();

        for (PlayerSlot slot : match.players()) {
            players.add(
                playerSnapshot(server, slot)
            );
        }

        return List.copyOf(players);
    }

    private Map<String, Object> playerSnapshot(
        MinecraftServer server,
        PlayerSlot slot
    ) {
        Map<String, Object> player =
            new LinkedHashMap<>();

        player.put(
            "ownerUuid",
            slot.playerUuid().toString()
        );

        ServerPlayer online = server.getPlayerList()
            .getPlayer(slot.playerUuid());

        player.put(
            "name",
            online == null
                ? null
                : online.getName().getString()
        );
        player.put(
            "color",
            slot.color().name()
        );
        player.put("team", slot.team().name());
        player.put("targetId", slot.targetId());
        player.put(
            "memberIndex",
            slot.memberIndex()
        );
        player.put(
            "slotIndex",
            slot.slotIndex()
        );
        player.put(
            "forfeited",
            slot.forfeited()
        );

        slot.doctrine().ifPresent(doctrine -> {
            player.put(
                "doctrineVersion",
                doctrine.version()
            );
            player.put(
                "doctrine",
                doctrine.lines()
            );
            player.put(
                "doctrineNormalized",
                doctrine.normalizedLines()
            );
            player.put(
                "doctrineNormalizationHash",
                doctrine.normalizationHash()
            );
            player.put(
                "doctrineNormalizerModel",
                doctrine.normalizerModel()
            );
            player.put(
                "doctrineNormalizationStatus",
                doctrine.normalizationStatus().name()
            );
            player.put(
                "doctrineNormalizationError",
                doctrine.normalizationError()
            );
            player.put(
                "doctrineNormalizationPromptVersion",
                doctrine.normalizationPromptVersion()
            );
            player.put(
                "doctrineNormalizationAttemptCount",
                doctrine.normalizationAttemptCount()
            );
            player.put(
                "doctrineNormalizationLatencyMs",
                doctrine.normalizationLatencyMs()
            );
            player.put(
                "doctrineNormalizationHttpStatus",
                doctrine.normalizationHttpStatus()
            );
        });

        var score = slot.score();
        Map<String, Object> scores =
            new LinkedHashMap<>();

        scores.put(
            "totalScore",
            score.totalScore()
        );
        scores.put(
            "roundScore",
            score.roundScore()
        );
        scores.put(
            "roundKills",
            score.roundKills()
        );
        scores.put(
            "roundDeaths",
            score.roundDeaths()
        );
        scores.put(
            "roundAssists",
            score.roundAssists()
        );
        scores.put(
            "totalKills",
            score.totalKills()
        );
        scores.put(
            "totalDeaths",
            score.totalDeaths()
        );
        scores.put(
            "totalAssists",
            score.totalAssists()
        );
        scores.put(
            "roundCoreCaptures",
            score.roundCoreCaptures()
        );
        scores.put(
            "roundCoreHoldTicks",
            score.roundCoreHoldTicks()
        );
        scores.put(
            "roundDamageDealt",
            score.roundDamageDealt()
        );
        scores.put(
            "roundDamageTaken",
            score.roundDamageTaken()
        );

        player.put("score", scores);

        return player;
    }
}
