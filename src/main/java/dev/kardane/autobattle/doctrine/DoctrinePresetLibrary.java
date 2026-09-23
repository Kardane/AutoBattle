package dev.kardane.autobattle.doctrine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

/**
 * Korean, ready-to-submit three-line doctrine bundles for the setup dialog.
 *
 * <p>The test-bot command intentionally keeps its independent line pool. These
 * bundles are UI suggestions: selecting one fills all three doctrine inputs,
 * after which the player can edit the text before saving.</p>
 */
public final class DoctrinePresetLibrary {
    public static final int SUGGESTION_COUNT = 3;

    private static final List<DoctrinePreset> PRESETS = List.of(
        preset(
            "frontline",
            "정면 돌격",
            "아군과 함께 가장 가까운 적을 추격한다.",
            "같은 적에게 집중 공격해 빠르게 처치한다.",
            "체력이 30% 아래면 전투를 멈추고 후퇴한다."
        ),
        preset(
            "core-capture",
            "핵심 점령",
            "중립 CORE를 먼저 점령한다.",
            "우리 팀이 CORE를 차지하면 그 주변을 방어한다.",
            "적이 CORE에 접근하면 공격보다 점령 저지를 우선한다."
        ),
        preset(
            "core-defense",
            "핵심 수비",
            "우리 CORE에서 멀리 벗어나지 않는다.",
            "CORE를 공격하는 적을 먼저 상대한다.",
            "체력이 낮으면 CORE 뒤에서 안전하게 회복한다."
        ),
        preset(
            "mobile-pursuit",
            "기동 추격",
            "가장 약한 적을 찾아 추격한다.",
            "적이 도망가도 아군과 함께 압박한다.",
            "추격 중 CORE가 비면 즉시 CORE로 돌아간다."
        ),
        preset(
            "support",
            "동료 지원",
            "아군 한 명과 항상 가까이 이동한다.",
            "아군이 공격하는 적을 함께 공격한다.",
            "아군이 후퇴하면 CORE 방어로 전환한다."
        ),
        preset(
            "balanced",
            "균형 전술",
            "상황에 따라 CORE 점령과 전투를 균형 있게 선택한다.",
            "가까운 적이 있으면 먼저 교전한다.",
            "불리한 전투는 피하고 아군 쪽으로 이동한다."
        ),
        preset(
            "flank-pressure",
            "측면 압박",
            "적의 측면으로 이동해 고립된 적을 공격한다.",
            "적이 흩어지면 가장 가까운 적을 집중 공격한다.",
            "우리 CORE가 위험하면 측면 공격을 멈추고 복귀한다."
        ),
        preset(
            "survival",
            "생존 우선",
            "체력과 위치를 항상 안전하게 유지한다.",
            "아군이 없는 곳에서는 적과 오래 싸우지 않는다.",
            "체력이 50% 아래면 전투를 피하고 후퇴한다."
        ),
        preset(
            "counterattack",
            "반격 전술",
            "적이 우리 CORE를 공격하면 즉시 반격한다.",
            "적이 깊이 들어오면 아군과 포위한다.",
            "적이 물러나면 CORE를 지키며 재정비한다."
        ),
        preset(
            "vanguard",
            "선봉 전술",
            "전투 시작 시 적 CORE를 향해 전진한다.",
            "적을 만나면 후퇴하지 말고 공격한다.",
            "체력이 낮은 아군이 있으면 그 주변을 지킨다."
        ),
        preset(
            "objective-cycle",
            "거점 순환",
            "CORE를 점령한 뒤 다음 행동을 다시 판단한다.",
            "CORE 근처의 적을 우선 공격한다.",
            "적이 없으면 우리 CORE와 적 CORE 사이를 순찰한다."
        ),
        preset(
            "ambush",
            "거점 매복",
            "CORE 주변에서 대기하며 적의 접근을 감시한다.",
            "적이 가까워지면 먼저 공격해 이동을 막는다.",
            "적이 멀리 있으면 무리하게 추격하지 않는다."
        )
    );

    private DoctrinePresetLibrary() {
    }

    public static List<DoctrinePreset> all() {
        return PRESETS;
    }

    public static Optional<DoctrinePreset> find(String id) {
        Objects.requireNonNull(id, "id");

        return PRESETS.stream()
            .filter(preset -> preset.id().equals(id))
            .findFirst();
    }

    public static List<DoctrinePreset> suggestions(Random random) {
        Objects.requireNonNull(random, "random");

        List<DoctrinePreset> shuffled = new ArrayList<>(PRESETS);
        Collections.shuffle(shuffled, random);

        return List.copyOf(
            shuffled.subList(0, SUGGESTION_COUNT)
        );
    }

    private static DoctrinePreset preset(
        String id,
        String label,
        String line1,
        String line2,
        String line3
    ) {
        return new DoctrinePreset(
            id,
            label,
            List.of(line1, line2, line3)
        );
    }

    public record DoctrinePreset(
        String id,
        String label,
        List<String> lines
    ) {
        public DoctrinePreset {
            id = Objects.requireNonNull(id, "id");
            label = Objects.requireNonNull(label, "label");
            lines = List.copyOf(
                Objects.requireNonNull(lines, "lines")
            );

            if (id.isBlank()
                || label.isBlank()
                || lines.size() != 3
                || lines.stream().anyMatch(String::isBlank)) {
                throw new IllegalArgumentException(
                    "A doctrine preset requires a label and exactly three lines"
                );
            }
        }
    }
}
