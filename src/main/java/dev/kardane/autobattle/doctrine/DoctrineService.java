package dev.kardane.autobattle.doctrine;

import dev.kardane.autobattle.match.MatchPhase;
import dev.kardane.autobattle.match.MatchSession;
import dev.kardane.autobattle.match.PlayerSlot;
import dev.kardane.autobattle.config.DoctrineConfig;
import net.minecraft.server.level.ServerPlayer;

public final class DoctrineService {
    private DoctrineValidator validator;
    private DoctrineNormalizer normalizer;

    public DoctrineService(
        DoctrineValidator validator,
        DoctrineNormalizer normalizer
    ) {
        this.validator = java.util.Objects.requireNonNull(
            validator,
            "validator"
        );
        this.normalizer = java.util.Objects.requireNonNull(
            normalizer,
            "normalizer"
        );
    }

    public DoctrineService(DoctrineValidator validator) {
        this(
            validator,
            new PassThroughDoctrineNormalizer(
                "Doctrine normalizer is not configured"
            )
        );
    }

    public void reloadNormalizer(
        DoctrineNormalizer normalizer
    ) {
        this.normalizer = java.util.Objects.requireNonNull(
            normalizer,
            "normalizer"
        );
    }

    public void reloadConfig(DoctrineConfig config) {
        this.validator = new DoctrineValidator(
            config.maxLineLength()
        );
    }

    public DoctrineEditResult submitInitial(
        MatchSession match,
        ServerPlayer player,
        String line1,
        String line2,
        String line3
    ) {
        PlayerSlot slot = match.player(
            player.getUUID()
        ).orElse(null);

        if (slot == null) {
            return DoctrineEditResult.failure(
                DoctrineEditError.NOT_PARTICIPANT
            );
        }

        if (match.phase() != MatchPhase.DOCTRINE_SETUP) {
            return DoctrineEditResult.failure(
                DoctrineEditError.INVALID_PHASE
            );
        }

        DoctrineEditResult validated =
            validator.validateInitial(
                line1,
                line2,
                line3
            );

        if (!validated.success()) {
            return validated;
        }

        Doctrine doctrine = normalize(
            validated.doctrine()
        );
        slot.setDoctrine(doctrine);

        return DoctrineEditResult.success(doctrine);
    }

    private Doctrine normalize(Doctrine source) {
        DoctrineNormalizationResult result =
            normalizer.normalize(
                source.lines()
            );

        return source.withNormalization(result);
    }

    public DoctrineEditResult replaceLine(
        MatchSession match,
        ServerPlayer player,
        int index,
        String newLine
    ) {
        PlayerSlot slot = match.player(
            player.getUUID()
        ).orElse(null);

        if (slot == null) {
            return DoctrineEditResult.failure(
                DoctrineEditError.NOT_PARTICIPANT
            );
        }

        if (match.phase() != MatchPhase.DOCTRINE_EDIT) {
            return DoctrineEditResult.failure(
                DoctrineEditError.INVALID_PHASE
            );
        }

        if (slot.runtime().doctrineEditedThisReview()) {
            return DoctrineEditResult.failure(
                DoctrineEditError.ALREADY_EDITED
            );
        }

        if (index < 0 || index > 2) {
            return DoctrineEditResult.failure(
                DoctrineEditError.INVALID_SLOT
            );
        }

        Doctrine current = slot.doctrine()
            .orElse(null);

        if (current == null) {
            return DoctrineEditResult.failure(
                DoctrineEditError.INVALID_PHASE
            );
        }

        DoctrineValidationResult validated =
            validator.validateLine(newLine);

        if (!validated.valid()) {
            return DoctrineEditResult.failure(
                validated.error()
            );
        }

        Doctrine updated = normalize(
            current.replace(
                index,
                validated.normalizedText()
            )
        );

        slot.setDoctrine(updated);
        slot.runtime().markDoctrineEdited();

        return DoctrineEditResult.success(updated);
    }
}
