package dev.kardane.autobattle.doctrine;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class PassThroughDoctrineNormalizer
        implements DoctrineNormalizer {
    private final String reason;

    public PassThroughDoctrineNormalizer(String reason) {
        this.reason = reason;
    }

    @Override
    public CompletableFuture<DoctrineNormalizationResult>
    normalizeAsync(List<String> sourceLines) {
        List<String> lines = List.copyOf(sourceLines);

        return CompletableFuture.completedFuture(
            new DoctrineNormalizationResult(
                lines,
                sourceHash(lines),
                null,
                DoctrineNormalizationStatus.FALLBACK_DISABLED,
                reason,
                null,
                0,
                0L,
                null
            )
        );
    }

    static String sourceHash(List<String> lines) {
        try {
            MessageDigest digest =
                MessageDigest.getInstance("SHA-256");

            for (String line : lines) {
                digest.update(
                    line.getBytes(StandardCharsets.UTF_8)
                );
                digest.update((byte) 0);
            }

            return HexFormat.of().formatHex(
                digest.digest()
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                "SHA-256 is unavailable",
                exception
            );
        }
    }
}
