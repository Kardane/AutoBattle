package dev.kardane.autobattle.doctrine;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.kardane.autobattle.config.DoctrineNormalizerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

final class OpenAiDoctrineNormalizerTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void concurrentIdenticalRequestsShareOneUpstreamCall()
            throws Exception {
        AtomicInteger calls = new AtomicInteger();
        startServer(exchange -> {
            calls.incrementAndGet();
            sleep(200L);
            respond(exchange, 200, validResponse());
        });

        OpenAiDoctrineNormalizer normalizer =
            normalizer(2, 10);

        List<String> source = List.of(
            "Capture the core",
            "Fight the leader",
            "Retreat when weak"
        );

        List<CompletableFuture<DoctrineNormalizationResult>>
            futures = new ArrayList<>();

        for (int index = 0; index < 20; index++) {
            futures.add(normalizer.normalizeAsync(source));
        }

        CompletableFuture.allOf(
            futures.toArray(CompletableFuture[]::new)
        ).join();

        assertEquals(1, calls.get());

        List<String> expected = List.of(
            "CAPTURE_CORE.",
            "ENGAGE the first-place enemy.",
            "RETREAT when HP is low."
        );

        for (var future : futures) {
            assertEquals(
                expected,
                future.join().normalizedLines()
            );
        }

        long normalized = futures.stream()
            .map(CompletableFuture::join)
            .filter(result ->
                result.status()
                    == DoctrineNormalizationStatus.NORMALIZED
            )
            .count();

        long shared = futures.stream()
            .map(CompletableFuture::join)
            .filter(result ->
                result.status()
                    == DoctrineNormalizationStatus.SHARED_INFLIGHT
            )
            .count();

        assertEquals(1L, normalized);
        assertEquals(19L, shared);

        DoctrineNormalizationResult cached =
            normalizer.normalizeAsync(source).join();

        assertEquals(
            DoctrineNormalizationStatus.CACHE_HIT,
            cached.status()
        );
        assertEquals(1, calls.get());
    }

    @Test
    void transientServerErrorRetriesOnce() throws Exception {
        AtomicInteger calls = new AtomicInteger();

        startServer(exchange -> {
            int call = calls.incrementAndGet();

            if (call == 1) {
                respond(
                    exchange,
                    500,
                    "{\"error\":\"temporary\"}"
                );
                return;
            }

            respond(exchange, 200, validResponse());
        });

        OpenAiDoctrineNormalizer normalizer =
            normalizer(2, 10);

        DoctrineNormalizationResult result =
            normalizer.normalizeAsync(
                List.of("a", "b", "c")
            ).join();

        assertEquals(2, calls.get());
        assertEquals(
            DoctrineNormalizationStatus.NORMALIZED,
            result.status()
        );
        assertEquals(2, result.attemptCount());
    }

    @Test
    void ordinaryClientErrorDoesNotRetry() throws Exception {
        AtomicInteger calls = new AtomicInteger();

        startServer(exchange -> {
            calls.incrementAndGet();
            respond(
                exchange,
                400,
                "{\"error\":\"bad request\"}"
            );
        });

        OpenAiDoctrineNormalizer normalizer =
            normalizer(2, 10);

        DoctrineNormalizationResult result =
            normalizer.normalizeAsync(
                List.of("a", "b", "c")
            ).join();

        assertEquals(1, calls.get());
        assertEquals(
            DoctrineNormalizationStatus.FALLBACK_ERROR,
            result.status()
        );
        assertEquals(400, result.httpStatus());
    }

    private OpenAiDoctrineNormalizer normalizer(
        int maxAttempts,
        int backoffMs
    ) {
        int port = server.getAddress().getPort();

        return new OpenAiDoctrineNormalizer(
            new DoctrineNormalizerConfig(
                true,
                "test-key",
                "http://127.0.0.1:" + port,
                "test-model",
                2000,
                3000,
                maxAttempts,
                backoffMs
            ),
            "test-key"
        );
    }

    private void startServer(
        ExchangeHandler handler
    ) throws IOException {
        server = HttpServer.create(
            new InetSocketAddress(
                "127.0.0.1",
                0
            ),
            0
        );

        server.createContext(
            "/v1/responses",
            exchange -> handler.handle(exchange)
        );
        server.start();
    }

    private static void respond(
        HttpExchange exchange,
        int status,
        String body
    ) throws IOException {
        byte[] bytes = body.getBytes(
            StandardCharsets.UTF_8
        );

        exchange.getResponseHeaders().add(
            "Content-Type",
            "application/json"
        );
        exchange.sendResponseHeaders(
            status,
            bytes.length
        );

        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static String validResponse() {
        return """
            {
              "output": [
                {
                  "type": "message",
                  "content": [
                    {
                      "type": "output_text",
                      "text": "{\\\"rule_1\\\":\\\"CAPTURE_CORE.\\\",\\\"rule_2\\\":\\\"ENGAGE the first-place enemy.\\\",\\\"rule_3\\\":\\\"RETREAT when HP is low.\\\"}"
                    }
                  ]
                }
              ]
            }
            """;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(exception);
        }
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange)
            throws IOException;
    }
}
