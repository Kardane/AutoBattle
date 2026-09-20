package dev.kardane.autobattle.doctrine;

import dev.kardane.autobattle.AutoBattleMod;
import dev.kardane.autobattle.config.DoctrineConfig;
import dev.kardane.autobattle.match.MatchPhase;
import dev.kardane.autobattle.match.MatchSession;
import dev.kardane.autobattle.match.PlayerSlot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class DoctrineService {
    private DoctrineValidator validator;
    private DoctrineNormalizer normalizer;

    // Accessed only from the Minecraft server thread. Normalization
    // itself runs off-thread and completion is marshalled back through
    // MinecraftServer#execute before this set or match state is touched.
    private final Set<UUID> pendingNormalization =
        new HashSet<>();

    public DoctrineService(
        DoctrineValidator validator,
        DoctrineNormalizer normalizer
    ) {
        this.validator = Objects.requireNonNull(
            validator,
            "validator"
        );
        this.normalizer = Objects.requireNonNull(
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
        this.normalizer = Objects.requireNonNull(
            normalizer,
            "normalizer"
        );
    }

    public void reloadConfig(DoctrineConfig config) {
        this.validator = new DoctrineValidator(
            config.maxLineLength()
        );
    }

    public boolean normalizationPending(UUID playerUuid) {
        return pendingNormalization.contains(playerUuid);
    }

    public CompletableFuture<DoctrineEditResult>
    submitInitialAsync(
        MinecraftServer server,
        MatchSession match,
        ServerPlayer player,
        String line1,
        String line2,
        String line3
    ) {
        Objects.requireNonNull(server, "server");

        PlayerSlot slot = match.player(
            player.getUUID()
        ).orElse(null);

        if (slot == null) {
            return completedFailure(
                DoctrineEditError.NOT_PARTICIPANT
            );
        }

        if (match.phase() != MatchPhase.DOCTRINE_SETUP) {
            return completedFailure(
                DoctrineEditError.INVALID_PHASE
            );
        }

        if (slot.doctrine().isPresent()) {
            return completedFailure(
                DoctrineEditError.ALREADY_SUBMITTED
            );
        }

        UUID playerUuid = player.getUUID();

        if (pendingNormalization.contains(playerUuid)) {
            return completedFailure(
                DoctrineEditError.NORMALIZATION_PENDING
            );
        }

        DoctrineEditResult validated =
            validator.validateInitial(
                line1,
                line2,
                line3
            );

        if (!validated.success()) {
            return CompletableFuture.completedFuture(
                validated
            );
        }

        Doctrine source = validated.doctrine();
        pendingNormalization.add(playerUuid);

        CompletableFuture<DoctrineEditResult> result =
            new CompletableFuture<>();

        normalizeOffThread(source).whenComplete(
            (normalized, throwable) ->
                server.execute(() -> {
                    pendingNormalization.remove(playerUuid);

                    Doctrine doctrine = throwable == null
                        ? normalized
                        : fallbackAfterUnexpectedFailure(
                            source,
                            throwable
                        );

                    PlayerSlot current = match.player(
                        playerUuid
                    ).orElse(null);

                    if (current == null) {
                        result.complete(
                            DoctrineEditResult.failure(
                                DoctrineEditError.NOT_PARTICIPANT
                            )
                        );
                        return;
                    }

                    if (match.phase()
                        != MatchPhase.DOCTRINE_SETUP) {
                        result.complete(
                            DoctrineEditResult.failure(
                                DoctrineEditError.INVALID_PHASE
                            )
                        );
                        return;
                    }

                    if (current.doctrine().isPresent()) {
                        result.complete(
                            DoctrineEditResult.failure(
                                DoctrineEditError.ALREADY_SUBMITTED
                            )
                        );
                        return;
                    }

                    current.setDoctrine(doctrine);
                    result.complete(
                        DoctrineEditResult.success(doctrine)
                    );
                })
        );

        return result;
    }

    public CompletableFuture<DoctrineEditResult>
    replaceLineAsync(
        MinecraftServer server,
        MatchSession match,
        ServerPlayer player,
        int index,
        String newLine
    ) {
        Objects.requireNonNull(server, "server");

        PlayerSlot slot = match.player(
            player.getUUID()
        ).orElse(null);

        if (slot == null) {
            return completedFailure(
                DoctrineEditError.NOT_PARTICIPANT
            );
        }

        if (match.phase() != MatchPhase.DOCTRINE_EDIT) {
            return completedFailure(
                DoctrineEditError.INVALID_PHASE
            );
        }

        if (slot.runtime().doctrineEditedThisReview()) {
            return completedFailure(
                DoctrineEditError.ALREADY_EDITED
            );
        }

        if (index < 0 || index > 2) {
            return completedFailure(
                DoctrineEditError.INVALID_SLOT
            );
        }

        UUID playerUuid = player.getUUID();

        if (pendingNormalization.contains(playerUuid)) {
            return completedFailure(
                DoctrineEditError.NORMALIZATION_PENDING
            );
        }

        Doctrine current = slot.doctrine()
            .orElse(null);

        if (current == null) {
            return completedFailure(
                DoctrineEditError.INVALID_PHASE
            );
        }

        DoctrineValidationResult validated =
            validator.validateLine(newLine);

        if (!validated.valid()) {
            return completedFailure(
                validated.error()
            );
        }

        Doctrine source = current.replace(
            index,
            validated.normalizedText()
        );

        pendingNormalization.add(playerUuid);

        CompletableFuture<DoctrineEditResult> result =
            new CompletableFuture<>();

        normalizeOffThread(source).whenComplete(
            (normalized, throwable) ->
                server.execute(() -> {
                    pendingNormalization.remove(playerUuid);

                    Doctrine doctrine = throwable == null
                        ? normalized
                        : fallbackAfterUnexpectedFailure(
                            source,
                            throwable
                        );

                    PlayerSlot latest = match.player(
                        playerUuid
                    ).orElse(null);

                    if (latest == null) {
                        result.complete(
                            DoctrineEditResult.failure(
                                DoctrineEditError.NOT_PARTICIPANT
                            )
                        );
                        return;
                    }

                    if (match.phase()
                        != MatchPhase.DOCTRINE_EDIT) {
                        result.complete(
                            DoctrineEditResult.failure(
                                DoctrineEditError.INVALID_PHASE
                            )
                        );
                        return;
                    }

                    if (latest.runtime()
                        .doctrineEditedThisReview()) {
                        result.complete(
                            DoctrineEditResult.failure(
                                DoctrineEditError.ALREADY_EDITED
                            )
                        );
                        return;
                    }

                    Doctrine latestDoctrine = latest.doctrine()
                        .orElse(null);

                    if (latestDoctrine == null
                        || latestDoctrine.version()
                            != source.version() - 1) {
                        result.complete(
                            DoctrineEditResult.failure(
                                DoctrineEditError.INVALID_PHASE
                            )
                        );
                        return;
                    }

                    latest.setDoctrine(doctrine);
                    latest.runtime().markDoctrineEdited();

                    result.complete(
                        DoctrineEditResult.success(doctrine)
                    );
                })
        );

        return result;
    }

    private CompletableFuture<Doctrine> normalizeOffThread(
        Doctrine source
    ) {
        DoctrineNormalizer activeNormalizer = normalizer;

        return CompletableFuture.supplyAsync(
            () -> normalize(
                source,
                activeNormalizer
            )
        );
    }

    private Doctrine normalize(
        Doctrine source,
        DoctrineNormalizer activeNormalizer
    ) {
        DoctrineNormalizationResult normalization =
            activeNormalizer.normalize(
                source.lines()
            );

        logFallback(normalization);
        return source.withNormalization(normalization);
    }

    private Doctrine fallbackAfterUnexpectedFailure(
        Doctrine source,
        Throwable throwable
    ) {
        Throwable root = throwable;

        while (root.getCause() != null
            && root != root.getCause()) {
            root = root.getCause();
        }

        DoctrineNormalizationResult fallback =
            new DoctrineNormalizationResult(
                source.lines(),
                PassThroughDoctrineNormalizer.sourceHash(
                    source.lines()
                ),
                null,
                DoctrineNormalizationStatus.FALLBACK_ERROR,
                root.getClass().getSimpleName()
                    + ": "
                    + String.valueOf(root.getMessage())
            );

        logFallback(fallback);
        return source.withNormalization(fallback);
    }

    private void logFallback(
        DoctrineNormalizationResult result
    ) {
        if (result.status()
            != DoctrineNormalizationStatus.FALLBACK_ERROR) {
            return;
        }

        AutoBattleMod.LOGGER.warn(
            "Doctrine normalization failed (hash={}, model={}): {}",
            result.sourceHash(),
            result.model(),
            result.error()
        );
    }

    private CompletableFuture<DoctrineEditResult>
    completedFailure(DoctrineEditError error) {
        return CompletableFuture.completedFuture(
            DoctrineEditResult.failure(error)
        );
    }
}
