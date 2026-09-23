package dev.kardane.autobattle.music;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** A handle for one server-side BGM playback. */
public final class BgmPlayback implements AutoCloseable {
    public enum Status {
        COMPLETED,
        CANCELLED,
        REPLACED,
        FAILED
    }

    public record Result(Status status, String detail) {
    }

    private final CompletableFuture<Result> completion =
        new CompletableFuture<>();
    private final Runnable stop;

    BgmPlayback(Runnable stop) {
        this.stop = stop;
    }

    public CompletionStage<Result> completion() {
        return completion.minimalCompletionStage();
    }

    public boolean done() {
        return completion.isDone();
    }

    void finish(Status status, String detail) {
        completion.complete(new Result(status, detail));
    }

    public void stop() {
        if (!done()) {
            stop.run();
        }
    }

    @Override
    public void close() {
        stop();
    }
}
