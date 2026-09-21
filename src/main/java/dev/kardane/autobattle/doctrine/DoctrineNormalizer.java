package dev.kardane.autobattle.doctrine;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface DoctrineNormalizer {
    CompletableFuture<DoctrineNormalizationResult> normalizeAsync(
        List<String> sourceLines
    );
}
