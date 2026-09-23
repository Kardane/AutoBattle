package dev.kardane.autobattle.ui;

import dev.kardane.autobattle.AutoBattleMod;
import dev.kardane.autobattle.config.LanguageService;
import dev.kardane.autobattle.doctrine.Doctrine;
import dev.kardane.autobattle.doctrine.DoctrinePresetLibrary;
import dev.kardane.autobattle.doctrine.DoctrinePresetLibrary.DoctrinePreset;
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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

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

    private final LanguageService language;
    private int doctrineMaxLineLength;

    public DialogService(
        int doctrineMaxLineLength,
        LanguageService language
    ) {
        if (doctrineMaxLineLength < 1) {
            throw new IllegalArgumentException(
                "doctrineMaxLineLength must be positive"
            );
        }

        this.doctrineMaxLineLength =
            doctrineMaxLineLength;

        this.language = Objects.requireNonNull(
            language,
            "language"
        );
    }

    public void reloadMaxLineLength(
        int doctrineMaxLineLength
    ) {
        if (doctrineMaxLineLength < 1) {
            throw new IllegalArgumentException(
                "doctrineMaxLineLength must be positive"
            );
        }

        this.doctrineMaxLineLength =
            doctrineMaxLineLength;
    }

    public void openDoctrineSetup(ServerPlayer player) {
        List<DoctrinePreset> examples =
            DoctrinePresetLibrary.suggestions(
                ThreadLocalRandom.current()
            );

        List<DialogBody> body = List.of(
            line(
                language.text(
                    "dialogs.doctrine-setup.body"
                )
            ),
            line(
                language.text(
                    "dialogs.doctrine-setup.examples-hint"
                )
            ),
            line(
                examples.stream()
                    .map(example ->
                        language.format(
                            "dialogs.doctrine-setup.example-entry",
                            "name",
                            example.label(),
                            "lines",
                            String.join(
                                " / ",
                                example.lines()
                            )
                        )
                    )
                    .collect(Collectors.joining("\n"))
            )
        );

        List<String> initialLines = List.of("", "", "");

        List<Input> inputs = List.of(
            doctrineInput(
                "d1",
                language.text(
                    "dialogs.doctrine-setup.input-1"
                ),
                initialLines.get(0)
            ),
            doctrineInput(
                "d2",
                language.text(
                    "dialogs.doctrine-setup.input-2"
                ),
                initialLines.get(1)
            ),
            doctrineInput(
                "d3",
                language.text(
                    "dialogs.doctrine-setup.input-3"
                ),
                initialLines.get(2)
            )
        );

        List<ActionButton> actions = List.of(
            actionButton(
                language.text(
                    "dialogs.doctrine-setup.save"
                ),
                DOCTRINE_SUBMIT,
                null
            )
        );

        MultiActionDialog dialog = new MultiActionDialog(
            common(
                language.component("dialogs.doctrine-setup.title"),
                body,
                inputs,
                false,
                DialogAction.CLOSE
            ),
            actions,
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
                language.format(
                    "dialogs.round-review.summary",
                    "round",
                    summary.round(),
                    "score",
                    summary.roundScore(),
                    "kills",
                    summary.kills(),
                    "deaths",
                    summary.deaths(),
                    "assists",
                    summary.assists()
                )
            )
        );

        body.add(
            line(
                language.format(
                    "dialogs.round-review.metrics",
                    "core_captures",
                    summary.coreCaptures(),
                    "core_hold_seconds",
                    formatOneDecimal(
                        summary.coreHoldTicks()
                            / 20.0D
                    ),
                    "damage_dealt",
                    formatOneDecimal(
                        summary.damageDealt()
                    ),
                    "damage_taken",
                    formatOneDecimal(
                        summary.damageTaken()
                    )
                )
            )
        );

        if (!summary.planPercentages().isEmpty()) {
            String separator = language.text(
                "dialogs.round-review.plan-separator"
            );

            String plans = summary.planPercentages()
                .entrySet()
                .stream()
                .map(entry ->
                    language.format(
                        "dialogs.round-review.plan-entry",
                        "plan",
                        entry.getKey(),
                        "percent",
                        String.format(
                            Locale.ROOT,
                            "%.0f",
                            entry.getValue()
                        )
                    )
                )
                .collect(
                    Collectors.joining(separator)
                );

            body.add(
                line(
                    language.format(
                        "dialogs.round-review.tactical",
                        "plans",
                        plans
                    )
                )
            );
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
                    language.format(
                        "dialogs.round-review.critical",
                        "plan",
                        String.valueOf(
                            decision.selectedPlanId()
                        ),
                        "confidence",
                        String.format(
                            Locale.ROOT,
                            "%.2f",
                            decision.confidence()
                        ),
                        "result",
                        decision.applyResult().name()
                    )
                )
            );

            shown++;
        }

        MultiActionDialog dialog = new MultiActionDialog(
            common(
                language.component("dialogs.round-review.title"),
                body,
                List.of(),
                false,
                DialogAction.WAIT_FOR_RESPONSE
            ),
            List.of(
                actionButton(
                    language.text(
                        "dialogs.round-review.done"
                    ),
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
            line(
                language.format(
                    "dialogs.doctrine-edit.line",
                    "line",
                    1,
                    "text",
                    doctrine.line1()
                )
            ),
            line(
                language.format(
                    "dialogs.doctrine-edit.line",
                    "line",
                    2,
                    "text",
                    doctrine.line2()
                )
            ),
            line(
                language.format(
                    "dialogs.doctrine-edit.line",
                    "line",
                    3,
                    "text",
                    doctrine.line3()
                )
            ),
            line(
                language.text(
                    "dialogs.doctrine-edit.hint"
                )
            )
        );

        List<ActionButton> buttons = new ArrayList<>();

        for (int line = 1; line <= 3; line++) {
            CompoundTag additions = new CompoundTag();
            additions.putInt("line", line);

            buttons.add(
                actionButton(
                    language.format(
                        "dialogs.doctrine-edit.edit-button",
                        "line",
                        line
                    ),
                    DOCTRINE_EDIT_SELECT,
                    additions
                )
            );
        }

        buttons.add(
            actionButton(
                language.text(
                    "dialogs.doctrine-edit.keep-button"
                ),
                DOCTRINE_KEEP,
                null
            )
        );

        MultiActionDialog dialog = new MultiActionDialog(
            common(
                language.component("dialogs.doctrine-edit.title"),
                body,
                List.of(),
                false,
                DialogAction.WAIT_FOR_RESPONSE
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
        String current = doctrine.line(
            oneBasedLine - 1
        );

        CompoundTag additions = new CompoundTag();
        additions.putInt("line", oneBasedLine);

        MultiActionDialog dialog = new MultiActionDialog(
            common(
                language.component("dialogs.doctrine-line.title", "line", oneBasedLine),
                List.of(
                    line(
                        language.text(
                            "dialogs.doctrine-line.body"
                        )
                    )
                ),
                List.of(
                    doctrineInput(
                        "text",
                        language.format(
                            "dialogs.doctrine-line.input-label",
                            "line",
                            oneBasedLine
                        ),
                        current
                    )
                ),
                false,
                DialogAction.CLOSE
            ),
            List.of(
                actionButton(
                    language.text(
                        "dialogs.doctrine-line.save"
                    ),
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

        int redScore = match.teamScore(
            dev.kardane.autobattle.match.BattleTeam.RED
        ).totalScore();
        int blueScore = match.teamScore(
            dev.kardane.autobattle.match.BattleTeam.BLUE
        ).totalScore();

        body.add(
            line(
                match.winnerTeam()
                    .map(team ->
                        language.format(
                            "dialogs.final-result.team-winner",
                            "team",
                            team.name()
                        )
                    )
                    .orElseGet(() ->
                        language.text(
                            "dialogs.final-result.team-draw"
                        )
                    )
            )
        );

        body.add(
            line(
                language.format(
                    "dialogs.final-result.team-score",
                    "red_score",
                    redScore,
                    "blue_score",
                    blueScore
                )
            )
        );

        List<PlayerSlot> standings = match.players()
            .stream()
            .sorted(
                Comparator.comparingInt(
                    (PlayerSlot slot) ->
                        slot.score().totalScore()
                    ).reversed()
                    .thenComparingInt(
                        PlayerSlot::slotIndex
                    )
                )
                .toList();

        int rank = 1;

        for (PlayerSlot slot : standings) {
            body.add(
                line(
                    language.format(
                        "dialogs.final-result.entry",
                        "rank",
                        rank,
                        "color",
                        slot.team().name(),
                        "team",
                        slot.team().name(),
                        "id",
                        slot.playerName(),
                        "player",
                        slot.playerName(),
                        "score",
                        slot.score().totalScore()
                    )
                )
            );
            rank++;
        }

        MultiActionDialog dialog = new MultiActionDialog(
            common(
                language.component("dialogs.final-result.title"),
                body,
                List.of(),
                true,
                DialogAction.CLOSE
            ),
            List.of(
                closeButton(
                    language.text(
                        "dialogs.final-result.close"
                    )
                )
            ),
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
                language.componentText(label),
                true,
                initial,
                doctrineMaxLineLength,
                Optional.empty()
            )
        );
    }

    private CommonDialogData common(
        Component title,
        List<DialogBody> body,
        List<Input> inputs,
        boolean canCloseWithEscape,
        DialogAction afterAction
    ) {
        return new CommonDialogData(
            title,
            Optional.empty(),
            canCloseWithEscape,
            false,
            afterAction,
            body,
            inputs
        );
    }

    private ActionButton closeButton(String label) {
        return new ActionButton(
            new CommonButtonData(
                language.componentText(label),
                BUTTON_WIDTH
            ),
            Optional.empty()
        );
    }

    private ActionButton actionButton(
        String label,
        ResourceLocation id,
        CompoundTag additions
    ) {
        return new ActionButton(
            new CommonButtonData(
                language.componentText(label),
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
            language.componentText(text),
            WIDTH
        );
    }

    private String formatOneDecimal(double value) {
        return String.format(
            Locale.ROOT,
            "%.1f",
            value
        );
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
            AutoBattleMod.MOD_ID,
            path
        );
    }
}
