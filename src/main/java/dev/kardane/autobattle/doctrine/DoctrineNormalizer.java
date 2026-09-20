package dev.kardane.autobattle.doctrine;

import java.util.List;

public interface DoctrineNormalizer {
    DoctrineNormalizationResult normalize(
        List<String> sourceLines
    );
}
