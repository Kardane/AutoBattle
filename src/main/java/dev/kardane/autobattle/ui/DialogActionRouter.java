package dev.kardane.autobattle.ui;

import dev.kardane.autobattle.AutoBattleMod;
import dev.kardane.autobattle.config.LanguageService;
import dev.kardane.autobattle.doctrine.DoctrineEditResult;
import dev.kardane.autobattle.doctrine.DoctrineService;
import dev.kardane.autobattle.match.MatchManager;
import dev.kardane.autobattle.review.RoundReviewService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.Optional;

public final class DialogActionRouter {
    private final MatchManager matchManager;
    private final DoctrineService doctrineService;
    private final RoundReviewService reviewService;
    private final DialogService dialogs;
    private final LanguageService language;

    public DialogActionRouter(
        MatchManager matchManager,
        DoctrineService doctrineService,
        RoundReviewService reviewService,
        DialogService dialogs,
        LanguageService language
    ) {
        this.matchManager = Objects.requireNonNull(
            matchManager,
            "matchManager"
        );
        this.doctrineService = Objects.requireNonNull(
            doctrineService,
            "doctrineService"
        );
        this.reviewService = Objects.requireNonNull(
            reviewService,
            "reviewService"
        );
        this.dialogs = Objects.requireNonNull(
            dialogs,
            "dialogs"
        );
        this.language = Objects.requireNonNull(
            language,
            "language"
        );
    }

    public boolean handle(
        ServerPlayer player,
        ResourceLocation id,
        Optional<Tag> payload
    ) {
        if (!AutoBattleMod.MOD_ID.equals(
            id.getNamespace()
        )) {
            return false;
        }

        return switch (id.getPath()) {
            case "doctrine_submit" ->
                submitDoctrine(player, compound(payload));

            case "review_ready" ->
                markReviewReady(player);

            case "doctrine_edit_select" ->
                selectDoctrineLine(player, compound(payload));

            case "doctrine_replace" ->
                replaceDoctrineLine(player, compound(payload));

            case "doctrine_keep" ->
                keepDoctrine(player);

            default -> false;
        };
    }

    private boolean submitDoctrine(
        ServerPlayer player,
        CompoundTag payload
    ) {
        DoctrineEditResult result =
            doctrineService.submitInitial(
                matchManager.session(),
                player,
                payload.getStringOr("d1", ""),
                payload.getStringOr("d2", ""),
                payload.getStringOr("d3", "")
            );

        if (!result.success()) {
            player.sendSystemMessage(
                Component.literal(
                    language.format(
                        "chat.doctrine-rejected",
                        "error",
                        result.error().name()
                    )
                )
            );

            dialogs.openDoctrineSetup(player);
            return true;
        }

        player.sendSystemMessage(
            Component.literal(
                language.format(
                    "chat.doctrine-saved",
                    "version",
                    result.doctrine().version()
                )
            )
        );

        matchManager.beginCountdownIfDoctrinesReady();
        return true;
    }

    private boolean markReviewReady(
        ServerPlayer player
    ) {
        if (!matchManager.markReviewReady(player)) {
            player.sendSystemMessage(
                Component.literal(
                    language.text(
                        "chat.review-ready-unavailable"
                    )
                )
            );
            return true;
        }

        return true;
    }

    private boolean selectDoctrineLine(
        ServerPlayer player,
        CompoundTag payload
    ) {
        int line = payload.getIntOr("line", 0);

        var doctrine = matchManager.session()
            .player(player.getUUID())
            .flatMap(slot -> slot.doctrine())
            .orElse(null);

        if (doctrine == null
            || line < 1
            || line > 3) {
            player.sendSystemMessage(
                Component.literal(
                    language.text(
                        "chat.doctrine-selection-invalid"
                    )
                )
            );
            return true;
        }

        dialogs.openDoctrineLine(
            player,
            line,
            doctrine
        );

        return true;
    }

    private boolean replaceDoctrineLine(
        ServerPlayer player,
        CompoundTag payload
    ) {
        int line = payload.getIntOr("line", 0);
        String text = payload.getStringOr(
            "text",
            ""
        );

        DoctrineEditResult result =
            doctrineService.replaceLine(
                matchManager.session(),
                player,
                line - 1,
                text
            );

        if (!result.success()) {
            player.sendSystemMessage(
                Component.literal(
                    language.format(
                        "chat.doctrine-edit-rejected",
                        "error",
                        result.error().name()
                    )
                )
            );

            if (line >= 1
                && line <= 3
                && matchManager.session()
                    .player(player.getUUID())
                    .flatMap(slot -> slot.doctrine())
                    .isPresent()) {
                dialogs.openDoctrineLine(
                    player,
                    line,
                    matchManager.session()
                        .player(player.getUUID())
                        .flatMap(slot -> slot.doctrine())
                        .orElseThrow()
                );
            }

            return true;
        }

        player.sendSystemMessage(
            Component.literal(
                language.format(
                    "chat.doctrine-updated",
                    "line",
                    line,
                    "version",
                    result.doctrine().version()
                )
            )
        );

        matchManager.markDoctrineEditDone(player);
        return true;
    }

    private boolean keepDoctrine(
        ServerPlayer player
    ) {
        if (!matchManager.markDoctrineEditDone(player)) {
            player.sendSystemMessage(
                Component.literal(
                    language.text(
                        "chat.doctrine-keep-unavailable"
                    )
                )
            );
            return true;
        }

        player.sendSystemMessage(
            Component.literal(
                language.text(
                    "chat.doctrine-kept"
                )
            )
        );

        return true;
    }

    private CompoundTag compound(
        Optional<Tag> payload
    ) {
        return payload
            .filter(CompoundTag.class::isInstance)
            .map(CompoundTag.class::cast)
            .orElseGet(CompoundTag::new);
    }
}
