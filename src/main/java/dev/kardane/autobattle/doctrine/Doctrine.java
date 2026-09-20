package dev.kardane.autobattle.doctrine;

import java.util.List;
import java.util.Objects;

public record Doctrine(
    int version,
    String line1,
    String line2,
    String line3
) {
    public Doctrine {
        if (version < 1) {
            throw new IllegalArgumentException("Doctrine version must be positive.");
        }
        line1 = Objects.requireNonNull(line1, "line1");
        line2 = Objects.requireNonNull(line2, "line2");
        line3 = Objects.requireNonNull(line3, "line3");
    }

    public List<String> lines() {
        return List.of(line1, line2, line3);
    }

    public String line(int index) {
        return switch (index) {
            case 0 -> line1;
            case 1 -> line2;
            case 2 -> line3;
            default -> throw new IndexOutOfBoundsException("Doctrine index: " + index);
        };
    }

    public Doctrine replace(int index, String newLine) {
        Objects.requireNonNull(newLine, "newLine");
        return switch (index) {
            case 0 -> new Doctrine(version + 1, newLine, line2, line3);
            case 1 -> new Doctrine(version + 1, line1, newLine, line3);
            case 2 -> new Doctrine(version + 1, line1, line2, newLine);
            default -> throw new IndexOutOfBoundsException("Doctrine index: " + index);
        };
    }
}
