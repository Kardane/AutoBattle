package dev.kardane.autobattle.ui;

import dev.kardane.autobattle.config.LanguageService;
import dev.kardane.autobattle.match.MatchPhase;
import dev.kardane.autobattle.match.MatchSession;
import dev.kardane.autobattle.match.PlayerSlot;
import dev.kardane.autobattle.tactics.RobotController;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.Objects;

public final class ActionBarUi {
    private final LanguageService language;

    public ActionBarUi(LanguageService language) {
        this.language = Objects.requireNonNull(
            language,
            "language"
        );
    }

    public void updateIntermission(
        ServerPlayer player,
        PlayerSlot slot,
        MatchSession match,
        MatchPhase phase,
        int currentRound,
        int totalRounds
    ) {
        String status = switch (phase) {
            case ROUND_REVIEW ->
                language.text(
                    "actionbar.status.review"
                );
            case DOCTRINE_EDIT ->
                language.text(
                    "actionbar.status.doctrine-edit"
                );
            case COUNTDOWN ->
                language.text(
                    "actionbar.status.countdown"
                );
            default ->
                language.format(
                    "actionbar.status.default",
                    "phase",
                    phase.name()
                );
        };

        player.displayClientMessage(
            language.component(
                "actionbar.intermission",
                "status",
                status,
                "round",
                currentRound,
                "total_rounds",
                totalRounds,
                "total_score",
                match.teamScore(slot.team()).totalScore(),
                "id",
                slot.targetId(),
                "team",
                slot.team().name()
            ),
            true
        );
    }

    public void updatePlayer(
        ServerPlayer player,
        PlayerSlot slot,
        MatchSession match,
        RobotController controller,
        long currentTick
    ) {
        String text;

        if (controller == null) {
            text = language.text(
                "actionbar.unavailable"
            );
        } else if (controller.alive()) {
            float hp = controller.entity()
                .map(entity -> entity.getHealth())
                .orElse(0.0F);

            float maxHp = controller.entity()
                .map(entity -> entity.getMaxHealth())
                .orElse(100.0F);

            String plan = controller.currentPlan()
                .map(tacticalPlan ->
                    tacticalPlan.externalId()
                )
                .orElseGet(() ->
                    language.text(
                        "actionbar.waiting-plan"
                    )
                );

            String commandState = slot.runtime()
                .activeCommand()
                .map(active ->
                    active.type().name()
                )
                .orElseGet(() ->
                    slot.runtime().commandUsed()
                        ? language.text(
                            "actionbar.command.used"
                        )
                        : language.text(
                            "actionbar.command.ready"
                        )
                );

            text = language.format(
                "actionbar.alive",
                "id",
                slot.targetId(),
                "team",
                slot.team().name(),
                "hp",
                Math.round(hp),
                "max_hp",
                Math.round(maxHp),
                "plan",
                plan,
                "command",
                commandState,
                "score",
                slot.score().roundScore(),
                "team_score",
                match.teamScore(slot.team()).roundScore()
            );
        } else {
            long remainingTicks = Math.max(
                0L,
                controller.runtime().respawnAtTick()
                    - currentTick
            );

            String remainingSeconds = String.format(
                Locale.ROOT,
                "%.1f",
                remainingTicks / 20.0D
            );

            text = language.format(
                "actionbar.dead",
                "id",
                slot.targetId(),
                "team",
                slot.team().name(),
                "respawn_seconds",
                remainingSeconds,
                "score",
                slot.score().roundScore(),
                "team_score",
                match.teamScore(slot.team()).roundScore()
            );
        }

        player.displayClientMessage(
            language.componentText(text),
            true
        );
    }
}
