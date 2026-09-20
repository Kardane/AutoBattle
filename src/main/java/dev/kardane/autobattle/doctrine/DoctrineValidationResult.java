package dev.kardane.autobattle.doctrine;

public record DoctrineValidationResult(
    boolean valid,
    DoctrineEditError error,
    String normalizedText
) {
    public static DoctrineValidationResult ok(String text) {
        return new DoctrineValidationResult(
            true,
            DoctrineEditError.NONE,
            text
        );
    }

    public static DoctrineValidationResult error(
        DoctrineEditError error
    ) {
        return new DoctrineValidationResult(
            false,
            error,
            null
        );
    }
}
