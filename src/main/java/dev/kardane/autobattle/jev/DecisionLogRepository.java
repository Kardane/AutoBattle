package dev.kardane.autobattle.jev;

import java.util.List;
import java.util.UUID;

public interface DecisionLogRepository extends AutoCloseable {
    void append(DecisionLog log);

    List<DecisionLog> findRound(
        UUID matchId,
        int round,
        UUID ownerUuid
    );

    @Override
    void close();
}
