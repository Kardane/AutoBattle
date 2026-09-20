package dev.kardane.autobattle.doctrine;

public final class DoctrineValidator {
    private int maxLineLength;

    public DoctrineValidator(int maxLineLength) {
        if (maxLineLength < 1) {
            throw new IllegalArgumentException(
                "maxLineLength must be positive"
            );
        }

        this.maxLineLength = maxLineLength;
    }

    public void reloadMaxLineLength(int maxLineLength) {
        if (maxLineLength < 1) {
            throw new IllegalArgumentException(
                "maxLineLength must be positive"
            );
        }

        this.maxLineLength = maxLineLength;
    }

    public int maxLineLength() {
        return maxLineLength;
    }

    public DoctrineValidationResult validateLine(String text) {
        if (text == null) {
            return DoctrineValidationResult.error(
                DoctrineEditError.EMPTY_LINE
            );
        }

        String normalized = text.trim();

        if (normalized.isEmpty()) {
            return DoctrineValidationResult.error(
                DoctrineEditError.EMPTY_LINE
            );
        }

        if (normalized.length() > maxLineLength) {
            return DoctrineValidationResult.error(
                DoctrineEditError.TOO_LONG
            );
        }

        for (int index = 0;
             index < normalized.length();
             index++) {
            char ch = normalized.charAt(index);

            if (Character.isISOControl(ch)) {
                return DoctrineValidationResult.error(
                    DoctrineEditError.INVALID_TEXT
                );
            }
        }

        return DoctrineValidationResult.ok(normalized);
    }

    public DoctrineEditResult validateInitial(
        String line1,
        String line2,
        String line3
    ) {
        DoctrineValidationResult first =
            validateLine(line1);
        DoctrineValidationResult second =
            validateLine(line2);
        DoctrineValidationResult third =
            validateLine(line3);

        if (!first.valid()) {
            return DoctrineEditResult.failure(first.error());
        }

        if (!second.valid()) {
            return DoctrineEditResult.failure(second.error());
        }

        if (!third.valid()) {
            return DoctrineEditResult.failure(third.error());
        }

        return DoctrineEditResult.success(
            new Doctrine(
                1,
                first.normalizedText(),
                second.normalizedText(),
                third.normalizedText()
            )
        );
    }
}
