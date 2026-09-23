package dev.kardane.autobattle.doctrine;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DoctrinePresetLibraryTest {
    @Test
    void containsTwelveKoreanThreeLinePresets() {
        assertEquals(12, DoctrinePresetLibrary.all().size());
        assertTrue(
            DoctrinePresetLibrary.all().stream().allMatch(preset ->
                preset.lines().size() == 3
                    && preset.label().chars().anyMatch(value ->
                        value >= 0xAC00 && value <= 0xD7A3
                    )
                    && preset.lines().stream().allMatch(line ->
                        !line.isBlank()
                    )
            )
        );
    }

    @Test
    void returnsThreeDistinctSuggestions() {
        List<DoctrinePresetLibrary.DoctrinePreset> suggestions =
            DoctrinePresetLibrary.suggestions(new Random(7));

        assertEquals(
            DoctrinePresetLibrary.SUGGESTION_COUNT,
            suggestions.size()
        );
        assertEquals(
            suggestions.size(),
            new HashSet<>(
                suggestions.stream()
                    .map(DoctrinePresetLibrary.DoctrinePreset::id)
                    .toList()
            ).size()
        );
    }

    @Test
    void suggestionsAreStableForTheSameSeed() {
        assertEquals(
            DoctrinePresetLibrary.suggestions(new Random(19)),
            DoctrinePresetLibrary.suggestions(new Random(19))
        );
        assertFalse(
            DoctrinePresetLibrary.find("missing").isPresent()
        );
    }
}
