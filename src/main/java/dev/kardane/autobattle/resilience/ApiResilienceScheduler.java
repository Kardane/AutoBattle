package dev.kardane.autobattle.resilience;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

public final class ApiResilienceScheduler {
    private static final ScheduledExecutorService SHARED =
        Executors.newScheduledThreadPool(
            2,
            daemonThreadFactory()
        );

    private ApiResilienceScheduler() {
    }

    public static ScheduledExecutorService shared() {
        return SHARED;
    }

    private static ThreadFactory daemonThreadFactory() {
        return runnable -> {
            Thread thread = new Thread(
                runnable,
                "autobattle-api-resilience"
            );
            thread.setDaemon(true);
            return thread;
        };
    }
}
