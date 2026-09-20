package dev.kardane.autobattle.doctrine;

import java.util.Optional;

public record DoctrineEditResult(
    boolean success,
    DoctrineEditError error,
    Doctrine doctrine
) {
    public static DoctrineEditResult success(Doctrine doctrine) {
        return new DoctrineEditResult(
            true,
            DoctrineEditError.NONE,
            doctrine
        );
    }

    public static DoctrineEditResult failure(
        DoctrineEditError error
    ) {
        return new DoctrineEditResult(
            false,
            error,
            null
        );
    }

    public Optional<Doctrine> doctrineOptional() {
        return Optional.ofNullable(doctrine);
    }
}
