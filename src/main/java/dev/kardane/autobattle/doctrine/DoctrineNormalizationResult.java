package dev.kardane.autobattle.doctrine;

import java.util.List;
import java.util.Objects;

public record DoctrineNormalizationResult(
    List<String> normalizedLines,
    String sourceHash,
    String model,
    DoctrineNormalizationStatus status,
    String error
) {
    public DoctrineNormalizationResult {
        normalizedLines = List.copyOf(
            Objects.requireNonNull(normalizedLines, "normalizedLines")
        );

        if (normalizedLines.size() != 3) {
            throw new IllegalArgumentException(
                "Doctrine normalization requires exactly three lines"
            );
        }

        sourceHash = Objects.requireNonNull(
            sourceHash,
            "sourceHash"
        );
        status = Objects.requireNonNull(status, "status");
    }
}
