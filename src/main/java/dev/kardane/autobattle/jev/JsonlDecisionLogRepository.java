package dev.kardane.autobattle.jev;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class JsonlDecisionLogRepository
        implements DecisionLogRepository {
    private final Gson gson = new GsonBuilder()
        .disableHtmlEscaping()
        .create();

    private final ExecutorService ioExecutor =
        Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(
                runnable,
                "autobattle-decision-log"
            );
            thread.setDaemon(true);
            return thread;
        });

    private final Path logDirectory;
    private final CopyOnWriteArrayList<DecisionLog> memory =
        new CopyOnWriteArrayList<>();

    public JsonlDecisionLogRepository(Path logDirectory) {
        this.logDirectory = logDirectory;
    }

    @Override
    public void append(DecisionLog log) {
        memory.add(log);

        ioExecutor.submit(() -> {
            try {
                Files.createDirectories(logDirectory);

                Path file = logDirectory.resolve(
                    log.matchId() + ".jsonl"
                );

                Files.writeString(
                    file,
                    gson.toJson(log) + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
                );
            } catch (IOException exception) {
                System.err.println(
                    "[AutoBattle] Failed to write decision log: "
                        + exception.getMessage()
                );
            }
        });
    }

    @Override
    public List<DecisionLog> findRound(
        UUID matchId,
        int round,
        UUID ownerUuid
    ) {
        return memory.stream()
            .filter(log -> log.matchId().equals(matchId))
            .filter(log -> log.round() == round)
            .filter(log -> log.ownerUuid().equals(ownerUuid))
            .toList();
    }

    @Override
    public void close() {
        ioExecutor.shutdown();
    }
}
