package dev.kardane.autobattle.doctrine;

import java.util.List;
import java.util.Objects;

public record Doctrine(
    int version,
    String line1,
    String line2,
    String line3,
    String normalizedLine1,
    String normalizedLine2,
    String normalizedLine3,
    String normalizationHash,
    String normalizerModel,
    DoctrineNormalizationStatus normalizationStatus,
    String normalizationError
) {
    public Doctrine {
        if (version < 1) {
            throw new IllegalArgumentException(
                "Doctrine version must be positive."
            );
        }

        line1 = Objects.requireNonNull(line1, "line1");
        line2 = Objects.requireNonNull(line2, "line2");
        line3 = Objects.requireNonNull(line3, "line3");
        normalizedLine1 = Objects.requireNonNull(
            normalizedLine1,
            "normalizedLine1"
        );
        normalizedLine2 = Objects.requireNonNull(
            normalizedLine2,
            "normalizedLine2"
        );
        normalizedLine3 = Objects.requireNonNull(
            normalizedLine3,
            "normalizedLine3"
        );
        normalizationHash = Objects.requireNonNull(
            normalizationHash,
            "normalizationHash"
        );
        normalizationStatus = Objects.requireNonNull(
            normalizationStatus,
            "normalizationStatus"
        );
    }

    public Doctrine(
        int version,
        String line1,
        String line2,
        String line3
    ) {
        this(
            version,
            line1,
            line2,
            line3,
            line1,
            line2,
            line3,
            PassThroughDoctrineNormalizer.sourceHash(
                List.of(line1, line2, line3)
            ),
            null,
            DoctrineNormalizationStatus.FALLBACK_DISABLED,
            null
        );
    }

    public List<String> lines() {
        return List.of(line1, line2, line3);
    }

    public List<String> normalizedLines() {
        return List.of(
            normalizedLine1,
            normalizedLine2,
            normalizedLine3
        );
    }

    public String line(int index) {
        return switch (index) {
            case 0 -> line1;
            case 1 -> line2;
            case 2 -> line3;
            default -> throw new IndexOutOfBoundsException(
                "Doctrine index: " + index
            );
        };
    }

    public Doctrine replace(
        int index,
        String newLine
    ) {
        Objects.requireNonNull(newLine, "newLine");

        return switch (index) {
            case 0 -> new Doctrine(
                version + 1,
                newLine,
                line2,
                line3
            );
            case 1 -> new Doctrine(
                version + 1,
                line1,
                newLine,
                line3
            );
            case 2 -> new Doctrine(
                version + 1,
                line1,
                line2,
                newLine
            );
            default -> throw new IndexOutOfBoundsException(
                "Doctrine index: " + index
            );
        };
    }

    public Doctrine withNormalization(
        DoctrineNormalizationResult result
    ) {
        Objects.requireNonNull(result, "result");

        List<String> normalized =
            result.normalizedLines();

        return new Doctrine(
            version,
            line1,
            line2,
            line3,
            normalized.get(0),
            normalized.get(1),
            normalized.get(2),
            result.sourceHash(),
            result.model(),
            result.status(),
            result.error()
        );
    }
}
