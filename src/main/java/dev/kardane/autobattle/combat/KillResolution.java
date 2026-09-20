package dev.kardane.autobattle.combat;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record KillResolution(
    UUID victimOwnerUuid,
    UUID killerOwnerUuid,
    Set<UUID> assistOwnerUuids
) {
    public KillResolution {
        Objects.requireNonNull(victimOwnerUuid, "victimOwnerUuid");
        assistOwnerUuids = Set.copyOf(assistOwnerUuids);
    }

    public Optional<UUID> killerOwner() {
        return Optional.ofNullable(killerOwnerUuid);
    }
}
