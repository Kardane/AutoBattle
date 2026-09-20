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
        # Edit this file and run /autobattle admin reload in an empty lobby.

        sidebar:
          title: "AUTO BATTLE"
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
          # {phase}
          phase: "AUTO BATTLE | {phase}"
          neutral-core: "NEUTRAL"

        actionbar:
          # {status}, {round}, {total_rounds}, {total_score}
          intermission: "{status} | ROUND {round}/{total_rounds} | TOTAL SCORE {total_score}"
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
          # {hp}, {max_hp}, {plan}, {command}, {score}
          alive: "HP {hp}/{max_hp} | {plan} | COMMAND {command} | SCORE {score}"
          # {respawn_seconds}, {score}
          dead: "ROBOT DESTROYED | RESPAWN {respawn_seconds}s | SCORE {score}"

        chat:
          # {round}
          round-started: "[AutoBattle] Round {round} started."
          round-ended: "[AutoBattle] Round {round} ended."
          # {killer_color}, {victim_color}
          robot-killed: "[AutoBattle] {killer_color} destroyed {victim_color}."
          # {color}
          core-captured: "[AutoBattle] {color} captured CORE."

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
                Object raw = yaml.load(reader);

                if (!(raw instanceof Map<?, ?> map)) {
                    throw new IllegalArgumentException(
                        "messages.yml root must be a YAML mapping"
                    );
                }

                Map<String, String> values =
                    new LinkedHashMap<>();

                flatten(
                    "",
                    map,
                    values
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
