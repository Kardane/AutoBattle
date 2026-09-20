package dev.kardane.autobattle.jev;

import java.util.concurrent.CompletableFuture;

public interface JevClient {
    CompletableFuture<DecisionResponse> decide(
        DecisionRequest request
    );
}
