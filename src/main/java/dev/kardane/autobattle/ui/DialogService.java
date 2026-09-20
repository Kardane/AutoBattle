package dev.kardane.autobattle.ui;

import dev.kardane.autobattle.AutoBattleMod;
import dev.kardane.autobattle.doctrine.Doctrine;
import dev.kardane.autobattle.doctrine.DoctrineValidator;
import dev.kardane.autobattle.match.MatchSession;
import dev.kardane.autobattle.match.PlayerSlot;
import dev.kardane.autobattle.review.CriticalDecision;
import dev.kardane.autobattle.review.RoundReviewSummary;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class DialogService {
    public static final ResourceLocation DOCTRINE_SUBMIT =
        id("doctrine_submit");

    public static final ResourceLocation REVIEW_READY =
        id("review_ready");

    public static final ResourceLocation DOCTRINE_EDIT_SELECT =
        id("doctrine_edit_select");

    public static final ResourceLocation DOCTRINE_REPLACE =
        id("doctrine_replace");

    public static final ResourceLocation DOCTRINE_KEEP =
        id("doctrine_keep");

    private static final int WIDTH = 360;
    private static final int BUTTON_WIDTH = 220;

    public void openDoctrineSetup(ServerPlayer player) {
        List<DialogBody> body = List.of(
            new PlainMessage(
                Component.literal(
                    "로봇에게 적용할 전투 원칙을 정확히 3문장으로 작성하세요. "
                        + "게임에 존재하지 않는 능력을 적어도 새로운 능력은 생성되지 않습니다."
                ),
                WIDTH
            )
        );

        List<Input> inputs = List.of(
            doctrineInput(
                "d1",
                "Doctrine 1",
                ""
            ),
            doctrineInput(
                "d2",
                "Doctrine 2",
                ""
            ),
            doctrineInput(
                "d3",
                "Doctrine 3",
                ""
            )
        );

        MultiActionDialog dialog = new MultiActionDialog(
            common(
                Component.literal("Robot Doctrine"),
                body,
                inputs,
                false
            ),
            List.of(
                actionButton(
                    "3문장 저장",
                    DOCTRINE_SUBMIT,
                    null
                )
            ),
            Optional.empty(),
            1
        );

        player.openDialog(Holder.direct(dialog));
    }

    public void openRoundReview(
        ServerPlayer player,
        RoundReviewSummary summary
    ) {
        List<DialogBody> body = new ArrayList<>();

        body.add(
            line(
                "Round "
                    + summary.round()
                    + " 결과 • Score "
                    + summary.roundScore()
                    + " • K/D/A "
                    + summary.kills()
                    + "/"
                    + summary.deaths()
                    + "/"
                    + summary.assists()
            )
        );

        body.add(
            line(
                String.format(
                    Locale.ROOT,
                    "CORE Capture %d • Hold %.1fs • Damage %.1f dealt / %.1f taken",
                    summary.coreCaptures(),
                    summary.coreHoldTicks() / 20.0D,
                    summary.damageDealt(),
                    summary.damageTaken()
                )
            )
        );

        if (!summary.planPercentages().isEmpty()) {
            String plans = summary.planPercentages()
                .entrySet()
                .stream()
                .map(entry ->
                    entry.getKey()
                        + " "
                        + String.format(
                            Locale.ROOT,
                            "%.0f%%",
                            entry.getValue()
                        )
                )
                .collect(
                    java.util.stream.Collectors.joining(" • ")
                );

            body.add(line("Tactical behavior: " + plans));
        }

        int shown = 0;

        for (CriticalDecision critical :
            summary.criticalDecisions()) {
            if (shown >= 3) {
                break;
            }

            var decision = critical.decision();

            body.add(
                line(
                    "Critical: "
                        + String.valueOf(
                            decision.selectedPlanId()
                        )
                        + " • confidence "
                        + String.format(
                            Locale.ROOT,
                            "%.2f",
                            decision.confidence()
                        )
                        + " • "
                        + decision.applyResult().name()
                )
            );

            shown++;
        }

        MultiActionDialog dialog = new MultiActionDialog(
            common(
                Component.literal("Round Review"),
                body,
                List.of(),
                true
            ),
            List.of(
                actionButton(
                    "검토 완료",
                    REVIEW_READY,
                    null
                )
            ),
            Optional.empty(),
            1
        );

        player.openDialog(Holder.direct(dialog));
    }

    public void openDoctrineEditSelect(
        ServerPlayer player,
        Doctrine doctrine
    ) {
        List<DialogBody> body = List.of(
            line("1. " + doctrine.line1()),
            line("2. " + doctrine.line2()),
            line("3. " + doctrine.line3()),
            line(
                "이번 라운드에서는 최대 한 문장만 수정할 수 있습니다."
            )
        );

        List<ActionButton> buttons = new ArrayList<>();

        for (int line = 1; line <= 3; line++) {
            CompoundTag additions = new CompoundTag();
            additions.putInt("line", line);

            buttons.add(
                actionButton(
                    "Doctrine " + line + " 수정",
                    DOCTRINE_EDIT_SELECT,
                    additions
                )
            );
        }

        buttons.add(
            actionButton(
                "전략 유지",
                DOCTRINE_KEEP,
                null
            )
        );

        MultiActionDialog dialog = new MultiActionDialog(
            common(
                Component.literal("Doctrine 수정"),
                body,
                List.of(),
                true
            ),
            buttons,
            Optional.empty(),
            1
        );

        player.openDialog(Holder.direct(dialog));
    }

    public void openDoctrineLine(
        ServerPlayer player,
        int oneBasedLine,
        Doctrine doctrine
    ) {
        String current = doctrine.line(oneBasedLine - 1);

        CompoundTag additions = new CompoundTag();
        additions.putInt("line", oneBasedLine);

        MultiActionDialog dialog = new MultiActionDialog(
            common(
                Component.literal(
                    "Doctrine " + oneBasedLine + " 수정"
                ),
                List.of(
                    line(
                        "기존 문장을 수정하세요. 다른 두 문장은 이번 라운드에 변경할 수 없습니다."
                    )
                ),
                List.of(
                    doctrineInput(
                        "text",
                        "Doctrine " + oneBasedLine,
                        current
                    )
                ),
                false
            ),
            List.of(
                actionButton(
                    "수정 저장",
                    DOCTRINE_REPLACE,
                    additions
                )
            ),
            Optional.empty(),
            1
        );

        player.openDialog(Holder.direct(dialog));
    }

    public void openFinalResult(
        ServerPlayer player,
        MatchSession match
    ) {
        List<DialogBody> body = new ArrayList<>();

        List<PlayerSlot> standings = match.players()
            .stream()
            .sorted(
                Comparator.comparingInt(
                    (PlayerSlot slot) ->
                        slot.score().totalScore()
                ).reversed()
            )
            .toList();

        int rank = 1;

        for (PlayerSlot slot : standings) {
            body.add(
                line(
                    rank
                        + ". "
                        + slot.color().name()
                        + " • "
                        + slot.score().totalScore()
                        + " pts"
                )
            );
            rank++;
        }

        MultiActionDialog dialog = new MultiActionDialog(
            common(
                Component.literal("AutoBattle Final Result"),
                body,
                List.of(),
                true
            ),
            List.of(),
            Optional.empty(),
            1
        );

        player.openDialog(Holder.direct(dialog));
    }

    private Input doctrineInput(
        String key,
        String label,
        String initial
    ) {
        return new Input(
            key,
            new TextInput(
                WIDTH,
                Component.literal(label),
                true,
                initial,
                DoctrineValidator.MAX_LINE_LENGTH,
                Optional.empty()
            )
        );
    }

    private CommonDialogData common(
        Component title,
        List<DialogBody> body,
        List<Input> inputs,
        boolean canCloseWithEscape
    ) {
        return new CommonDialogData(
            title,
            Optional.empty(),
            canCloseWithEscape,
            false,
            DialogAction.CLOSE,
            body,
            inputs
        );
    }

    private ActionButton actionButton(
        String label,
        ResourceLocation id,
        CompoundTag additions
    ) {
        return new ActionButton(
            new CommonButtonData(
                Component.literal(label),
                BUTTON_WIDTH
            ),
            Optional.of(
                new CustomAll(
                    id,
                    Optional.ofNullable(additions)
                )
            )
        );
    }

    private PlainMessage line(String text) {
        return new PlainMessage(
            Component.literal(text),
            WIDTH
        );
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
            AutoBattleMod.MOD_ID,
            path
        );
    }
}
