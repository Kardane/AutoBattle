package dev.kardane.autobattle.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LanguageConfigLoader {
    public static final String FILE_NAME = "messages.yml";

    private static final String DEFAULT_YAML = """
        # AutoBattle UI language configuration
        #
        # Supported placeholders are documented next to each section.
        # Edit this file and run /autobattle admin reload at any time.

        messages-version: 2

        sidebar:
          title: "AUTO BATTLE"
          # {team}, {score}
          team-entry: "{team}  {score}"
          # Legacy FFA key retained for migrated files.
          # {rank}, {color}, {score}
          entry: "{rank}. {color}"

        bossbar:
          # {round}, {total_rounds}, {seconds}, {core_owner}, {contested_suffix}
          round: "ROUND {round}/{total_rounds} | {seconds}s | CORE {core_owner}{contested_suffix}"
          contested-suffix: " | CONTESTED"
          # {round}, {total_rounds}, {seconds}
          countdown: "ROUND {round}/{total_rounds} | STARTING IN {seconds}s"
          # {round}, {total_rounds}
          review: "ROUND {round}/{total_rounds} | REVIEW"
          doctrine-edit: "ROUND {round}/{total_rounds} | DOCTRINE EDIT"
          # {round}, {total_rounds}, {phase}
          intermission: "ROUND {round}/{total_rounds} | {phase}"
          # {phase}
          phase: "AUTO BATTLE | {phase}"
          neutral-core: "NEUTRAL"

        actionbar:
          # {id}, {team}, {status}, {round}, {total_rounds}, {total_score}
          intermission: "{id} | {team} | {status} | ROUND {round}/{total_rounds} | TEAM {total_score}"
          status:
            review: "ROUND REVIEW"
            doctrine-edit: "DOCTRINE EDIT"
            countdown: "NEXT ROUND"
            default: "{phase}"
          unavailable: "ROBOT UNAVAILABLE"
          waiting-plan: "WAITING"
          command:
            ready: "READY"
            used: "USED"
          # {id}, {team}, {hp}, {max_hp}, {plan}, {command}, {score}, {team_score}
          alive: "{id} | {team} | HP {hp}/{max_hp} | {plan} | COMMAND {command} | TEAM {team_score}"
          # {id}, {team}, {respawn_seconds}, {score}, {team_score}
          dead: "{id} | {team} | DESTROYED | RESPAWN {respawn_seconds}s | TEAM {team_score}"

        chat:
          # {round}
          round-started: "[AutoBattle] Round {round} started."
          round-ended: "[AutoBattle] Round {round} ended."
          # {killer_color}, {killer_id}, {victim_color}, {victim_id}
          robot-killed: "[AutoBattle] {killer_color} {killer_id} destroyed {victim_color} {victim_id}."
          # {color}
          core-captured: "[AutoBattle] {color} captured CORE."
          # {player}, {round}
          review-completed: "[AutoBattle] {player} completed the review for round {round}."
          # {player}, {round}
          doctrine-edit-completed: "[AutoBattle] {player} completed doctrine editing for round {round}."
          final-standings-title: "[AutoBattle] Final standings"
          # {rank}, {color}, {score}
          final-standing: "{rank}. {color} - {score} points"
          # {error}
          doctrine-rejected: "Doctrine rejected: {error}"
          # {version}
          doctrine-saved: "Doctrine v{version} saved."
          review-ready-unavailable: "Review ready is unavailable in the current phase."
          doctrine-selection-invalid: "Doctrine edit selection is invalid."
          # {error}
          doctrine-edit-rejected: "Doctrine edit rejected: {error}"
          # {line}, {version}
          doctrine-updated: "Doctrine {line} updated to version {version}."
          doctrine-keep-unavailable: "Doctrine keep is unavailable in the current phase."
          doctrine-kept: "Doctrine kept unchanged."

        commands:
          # {message}
          reload-success: "{message}"
          reload-failure: "{message}"
          # {config}, {messages}
          reload-applied: "Reloaded {config} and {messages}."
          # {error}
          reload-error: "Config reload failed: {error}"
          start-failed: "Unable to start round. Check active participants, submitted doctrines, and arena dimension."
          # {round}
          start-success: "Started AutoBattle prototype round {round}."
          stop-failed: "No active AutoBattle round to stop."
          stop-success: "Stopped AutoBattle prototype round."
          end-failed: "No active AutoBattle match can be ended immediately."
          end-success: "Ended the AutoBattle match immediately."
          # {result}
          player-command-rejected: "Command rejected: {result}"
          # {type}, {seconds}
          player-command-success: "Command {type} activated for {seconds} seconds."
          # {error}
          doctrine-rejected: "Doctrine rejected: {error}"
          # {version}
          doctrine-saved: "Doctrine v{version} saved."
          doctrines-ready: "All doctrines submitted. Match is ready to start."
          # {error}
          doctrine-edit-rejected: "Doctrine edit rejected: {error}"
          # {line}, {version}
          doctrine-line-updated: "Doctrine line {line} updated. Version {version}."
          # {player}
          doctrine-view-player-not-found: "Player {player} is not online."
          # {player}
          doctrine-view-not-participant: "{player} is not an active AutoBattle participant."
          # {player}
          doctrine-view-unavailable: "{player} has not submitted a Doctrine yet."
          # {player}, {color}, {version}
          doctrine-view-title: "{player}'s Doctrine ({color}, version {version})"
          # {line}, {text}
          doctrine-view-line: "  {line}. {text}"
          join-failed: "Unable to join AutoBattle. The lobby may be closed or one team may be full."
          # {team}, {id}
          joined-team: "Joined {team} as {id}. You are ready."
          auto-ready: "Lobby participants are ready automatically after joining."
          start-unbalanced: "Cannot start: RED and BLUE must have the same number of players (1-8 per team)."
          # Legacy key kept for migrated custom message files.
          # {color}
          joined: "Joined AutoBattle as {color}."
          leave-failed: "You are not an active AutoBattle participant."
          left: "Left AutoBattle. Active robots are forfeited immediately."
          join-first: "Join the AutoBattle lobby first."
          ready: "You are ready."
          not-ready: "You are no longer ready."
          doctrine-setup-started: "All players are ready. Doctrine setup started."
          review-ready-invalid: "Review ready is only available during ROUND_REVIEW."
          review-ready: "Round review marked ready."
          doctrine-keep-invalid: "Doctrine keep is only available during DOCTRINE_EDIT."
          doctrine-kept: "Doctrine kept unchanged for the next round."
          review-not-participant: "You are not an AutoBattle participant."
          # {round}, {score}, {kills}, {deaths}, {assists}
          review-summary: "Round {round} | Score {score} | K/D/A {kills}/{deaths}/{assists}"
          # {core_captures}, {core_hold_seconds}, {damage_dealt}, {damage_taken}
          review-metrics: "CORE captures {core_captures} | Hold {core_hold_seconds}s | Damage {damage_dealt} dealt / {damage_taken} taken"
          # {plans}
          review-plans: "Plans: {plans}"
          # {importance}, {tick}, {plan}, {confidence}, {result}
          review-critical: "Critical #{importance} @ tick {tick}: {plan} confidence={confidence} result={result}"
          # {phase}, {round}, {players}, {red}, {blue}, {balanced}, {maximum}, {core_owner}
          status: "phase={phase}, round={round}, RED={red}/{maximum}, BLUE={blue}/{maximum}, balanced={balanced}, coreOwner={core_owner}"
          admin:
            # {color}
            debug-no-owner: "No participant owns {color}."
            debug-unknown-color: "Unknown robot color."
            # {color}
            debug-robot-not-alive: "{color} robot is not alive."
            # {plan}
            debug-plan-requires-target: "{plan} requires targetColor."
            debug-unknown-target-color: "Unknown target robot color."
            debug-target-invalid: "Target must be another participant."
            debug-no-reposition-nodes: "Arena has no reposition nodes."
            debug-plan-invalid: "Plan must be engage, chase, capture, defend, retreat, or reposition."
            debug-plan-locked: "Plan change rejected by decision lock."
            # {color}, {total}, {round}, {kills}, {deaths}, {assists}, {core_captures}, {core_ticks}, {damage_dealt}, {state}
            debug-status: "{color} total={total} round={round} K/D/A={kills}/{deaths}/{assists} coreCaptures={core_captures} coreTicks={core_ticks} damage={damage_dealt} {state}"
            # {hp}, {plan}
            debug-state-alive: "alive hp={hp} plan={plan}"
            # {ticks}
            debug-state-dead: "dead respawnTicks={ticks}"
            debug-state-unspawned: "unspawned"
            # {color}, {plan}
            debug-plan-assigned: "{color} plan = {plan}"
          test:
            carpet-missing: "Fabric Carpet /player command is unavailable."
            # {bot}
            spawn-failed: "Failed to spawn Carpet test bot {bot}."
            # {spawned}, {existing}
            spawn-success: "Carpet test bots ready: {spawned} spawned, {existing} already online."
            # {phase}
            setup-invalid-phase: "Test setup requires LOBBY or DOCTRINE_SETUP; current phase is {phase}."
            # {bot}
            bot-missing: "Test bot {bot} is not online. Run /autobattle admin test spawn first."
            # {bot}
            join-failed: "Failed to join test bot {bot} to AutoBattle."
            ready-failed: "Failed to enter Doctrine setup after readying test bots."
            # {bot}, {error}
            doctrine-failed: "Failed to submit Doctrine for {bot}: {error}"
            # {phase}
            setup-success: "Test lobby initialized; phase={phase}."
            review-invalid-phase: "Test review requires ROUND_REVIEW; current phase is {phase}."
            review-success: "All test bots marked review-ready; phase={phase}."
            keep-invalid-phase: "Test keep requires DOCTRINE_EDIT; current phase is {phase}."
            keep-success: "All test bots kept their Doctrine; phase={phase}."
            next-invalid-phase: "Test next requires ROUND_REVIEW or DOCTRINE_EDIT; starting phase was {phase}."
            next-success: "Advanced test match; phase={phase}."
            # {cleaned}
            cleanup-success: "Cleaned up {cleaned} Carpet test bots."
            # {status}
            match-status: "Test match: {status}"
            # {bot}, {state}, {color}, {ready}, {doctrine}
            bot-status: "{bot}: {state} | color={color} | ready={ready} | doctrine={doctrine}"
            # {bot}, {result}
            command-rejected: "{bot} command rejected: {result}"
            # {bot}, {type}
            command-success: "{bot} command {type} activated."
            # {command}, {error}
            command-error: "Failed to execute '{command}': {error}"
            fight-red-name: "Test RED"
            fight-blue-name: "Test BLUE"
            fight-success: "Spawned RED vs BLUE test fight."
            robot-unknown-color: "Unknown robot color. Use red, blue, green, or yellow."
            # {color}, {entity}
            robot-spawned: "Spawned test robot {color} (entity {entity})."

        dialogs:
          doctrine-setup:
            title: "Robot Doctrine"
            body: "로봇에게 적용할 전투 원칙을 정확히 3문장으로 작성하세요. 게임에 존재하지 않는 능력을 적어도 새로운 능력은 생성되지 않습니다."
            input-1: "Doctrine 1"
            input-2: "Doctrine 2"
            input-3: "Doctrine 3"
            save: "3문장 저장"

          round-review:
            title: "Round Review"
            # {round}, {score}, {kills}, {deaths}, {assists}
            summary: "Round {round} 결과 • Score {score} • K/D/A {kills}/{deaths}/{assists}"
            # {core_captures}, {core_hold_seconds}, {damage_dealt}, {damage_taken}
            metrics: "CORE Capture {core_captures} • Hold {core_hold_seconds}s • Damage {damage_dealt} dealt / {damage_taken} taken"
            # {plans}
            tactical: "Tactical behavior: {plans}"
            # {plan}, {percent}
            plan-entry: "{plan} {percent}%"
            plan-separator: " • "
            # {plan}, {confidence}, {result}
            critical: "Critical: {plan} • confidence {confidence} • {result}"
            done: "검토 완료"

          doctrine-edit:
            title: "Doctrine 수정"
            # {line}, {text}
            line: "{line}. {text}"
            hint: "이번 라운드에서는 최대 한 문장만 수정할 수 있습니다."
            # {line}
            edit-button: "Doctrine {line} 수정"
            keep-button: "전략 유지"

          doctrine-line:
            # {line}
            title: "Doctrine {line} 수정"
            body: "기존 문장을 수정하세요. 다른 두 문장은 이번 라운드에 변경할 수 없습니다."
            input-label: "Doctrine {line}"
            save: "수정 저장"

          final-result:
            title: "AutoBattle Final Result"
            # {rank}, {color}, {score}
            entry: "{rank}. {color} • {score} pts"
            close: "결과 닫기"
        """;

    private LanguageConfigLoader() {
    }

    public static LanguageConfig load() {
        Path path = messagePath();

        try {
            Files.createDirectories(path.getParent());

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
                Object defaultsRaw = yaml.load(
                    DEFAULT_YAML
                );

                Map<String, Object> defaults =
                    stringMap(
                        defaultsRaw,
                        "default messages"
                    );

                Object raw = yaml.load(reader);

                Map<String, Object> existing =
                    stringMap(
                        raw,
                        "messages.yml"
                    );

                LanguageConfigMigrationService migrations =
                    new LanguageConfigMigrationService();

                LanguageConfigMigrationService.MigrationResult migrated =
                    migrations.migrate(
                        existing,
                        defaults
                    );

                Map<String, String> values =
                    new LinkedHashMap<>();

                flatten(
                    "",
                    migrated.messages(),
                    values
                );

                migrations.backupAndWrite(
                    path,
                    migrated
                );

                return new LanguageConfig(values);
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                "Failed to load AutoBattle messages: "
                    + path,
                exception
            );
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                "Invalid AutoBattle messages: "
                    + path
                    + " ("
                    + exception.getMessage()
                    + ")",
                exception
            );
        }
    }

    public static Path messagePath() {
        return AutoBattleConfigLoader.configDirectory()
            .resolve(FILE_NAME);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> stringMap(
        Object value,
        String path
    ) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException(
                path + " root must be a YAML mapping"
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

    private static void flatten(
        String prefix,
        Map<?, ?> source,
        Map<String, String> target
    ) {
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException(
                    "messages.yml contains a non-string key"
                );
            }

            String path = prefix.isEmpty()
                ? key
                : prefix + "." + key;

            Object value = entry.getValue();

            if (value instanceof Map<?, ?> nested) {
                flatten(path, nested, target);
                continue;
            }

            if (value == null) {
                target.put(path, "");
                continue;
            }

            if (!(value instanceof String
                || value instanceof Number
                || value instanceof Boolean)) {
                throw new IllegalArgumentException(
                    path + " must be a scalar value"
                );
            }

            target.put(path, String.valueOf(value));
        }
    }
}
