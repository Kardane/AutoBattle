package dev.kardane.autobattle.command;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CarpetTestCommandsTest {
    @Test
    void hasAtLeastTenStrategyLinePresets() {
        assertTrue(
            CarpetTestCommands.doctrineLinePresetCount() >= 10
        );
    }

    @Test
    void selectsThreeDistinctLinesForEachPlayer() {
        List<String> lines =
            CarpetTestCommands.randomizedDoctrineLines(
                new Random(7)
            );

        assertEquals(3, lines.size());
        assertEquals(3, new HashSet<>(lines).size());
        assertTrue(
            lines.stream().allMatch(line ->
                !line.isBlank()
            )
        );
    }

    @Test
    void usesStableOrderForTheSameRandomSeed() {
        List<String> first =
            CarpetTestCommands.randomizedDoctrineLines(
                new Random(19)
            );
        List<String> second =
            CarpetTestCommands.randomizedDoctrineLines(
                new Random(19)
            );

        assertEquals(first, second);
        assertTrue(
            first.stream().allMatch(index ->
                !index.isBlank()
            )
        );
    }
}
