package dev.kardane.autobattle.doctrine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kardane.autobattle.config.DoctrineNormalizerConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class OpenAiDoctrineNormalizer
        implements DoctrineNormalizer {
    public static final String PROMPT_VERSION =
        "autobattle-doctrine-v3";

    private static final int MAX_NORMALIZED_LINE_LENGTH = 240;

    private static final String INSTRUCTIONS = """
        You normalize player-authored tactical doctrine for a Minecraft AutoBattle robot.

        The input always contains exactly three source rules. The source may be written in any language, including Korean or English.

        Rewrite each source rule into one concise English tactical rule optimized for a downstream tactical decision model. Preserve the player's intent. Do not add goals, conditions, priorities, numbers, thresholds, targets, or restrictions that were not present in the source.

        AutoBattle is a RED-vs-BLUE team battle. Each robot has allies on its own team and enemies on the opposing team.

        Use AutoBattle terminology when it accurately represents the source:
        - TEAM / ALLY / ALLIES: the robot's own team or teammates.
        - ENEMY / ENEMIES: robots on the opposing team.
        - CORE: the central capture objective.
        - ENGAGE: fight a nearby enemy without extended pursuit.
        - CHASE: deliberately pursue an enemy over a longer distance.
        - CAPTURE_CORE: move to and capture CORE.
        - DEFEND_CORE: stay near an owned CORE and defend it.
        - RETREAT: disengage to survive or recover.
        - HP: robot health.

        Do not invent a team color, participant ID, or specific enemy/ally that was not present in the source. Do not turn vague language into a numeric threshold. Preserve explicit numeric thresholds exactly. Keep the three rules separate and in the same order. Output only the schema fields.
        """;

    private final HttpClient httpClient;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int requestTimeoutMs;
    private final int totalTimeoutMs;
    private final int maxAttempts;
    private final int retryBackoffMs;

    private final Map<NormalizationKey, List<String>> cache =
        new ConcurrentHashMap<>();
    private final Map<
        NormalizationKey,
        CompletableFuture<UpstreamResult>
    > inFlight = new ConcurrentHashMap<>();

    public OpenAiDoctrineNormalizer(
        DoctrineNormalizerConfig config,
        String resolvedApiKey
    ) {
        Objects.requireNonNull(config, "config");
        this.apiKey = Objects.requireNonNull(
            resolvedApiKey,
            "resolvedApiKey"
        ).trim();
        this.baseUrl = stripTrailingSlash(
            config.baseUrl()
        );
        this.model = config.model();
        this.requestTimeoutMs = config.requestTimeoutMs();
        this.totalTimeoutMs = config.totalTimeoutMs();
        this.maxAttempts = config.maxAttempts();
        this.retryBackoffMs = config.retryBackoffMs();
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(
                Duration.ofMillis(requestTimeoutMs)
            )
            .build();
    }

    @Override
    public CompletableFuture<DoctrineNormalizationResult>
    normalizeAsync(List<String> sourceLines) {
        List<String> source = List.copyOf(sourceLines);

        if (source.size() != 3) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException(
                    "Doctrine normalization requires exactly three source lines"
                )
            );
        }

        String hash = PassThroughDoctrineNormalizer
            .sourceHash(source);

        NormalizationKey key = new NormalizationKey(
            hash,
            model,
            PROMPT_VERSION
        );

        List<String> cached = cache.get(key);

        if (cached != null) {
            return CompletableFuture.completedFuture(
                success(
                    cached,
                    hash,
                    DoctrineNormalizationStatus.CACHE_HIT,
                    0,
                    0L,
                    null
                )
            );
        }

        long startedNanos = System.nanoTime();
        long deadlineNanos = startedNanos
            + TimeUnit.MILLISECONDS.toNanos(
                totalTimeoutMs
            );

        AtomicBoolean owner = new AtomicBoolean(false);

        CompletableFuture<UpstreamResult> shared =
            inFlight.computeIfAbsent(
                key,
                ignored -> {
                    owner.set(true);
                    return attempt(
                        source,
                        1,
                        startedNanos,
                        deadlineNanos
                    );
                }
            );

        shared.whenComplete((upstream, error) -> {
            if (error == null && upstream != null) {
                cache.put(
                    key,
                    upstream.normalizedLines()
                );
            }

            inFlight.remove(key, shared);
        });

        return shared.handle((upstream, error) -> {
            if (error == null && upstream != null) {
                return success(
                    upstream.normalizedLines(),
                    hash,
                    owner.get()
                        ? DoctrineNormalizationStatus.NORMALIZED
                        : DoctrineNormalizationStatus.SHARED_INFLIGHT,
                    upstream.attemptCount(),
                    upstream.latencyMs(),
                    upstream.httpStatus()
                );
            }

            Throwable root = unwrap(error);

            if (root instanceof NormalizationFailure failure) {
                return fallback(
                    source,
                    hash,
                    failure.getMessage(),
                    failure.attemptCount(),
                    failure.latencyMs(),
                    failure.httpStatus()
                );
            }

            long latencyMs = elapsedMs(startedNanos);

            return fallback(
                source,
                hash,
                root == null
                    ? "Unknown OpenAI normalization failure"
                    : root.getClass().getSimpleName()
                        + ": "
                        + truncate(root.getMessage(), 512),
                0,
                latencyMs,
                null
            );
        });
    }

    private CompletableFuture<UpstreamResult> attempt(
        List<String> source,
        int attemptNumber,
        long startedNanos,
        long deadlineNanos
    ) {
        long remainingMs = remainingMs(deadlineNanos);

        if (remainingMs < 1L) {
            return CompletableFuture.failedFuture(
                failure(
                    "OpenAI normalization total timeout exceeded",
                    attemptNumber - 1,
                    startedNanos,
                    null,
                    null
                )
            );
        }

        long timeoutMs = Math.max(
            1L,
            Math.min(
                requestTimeoutMs,
                remainingMs
            )
        );

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/v1/responses"))
            .timeout(Duration.ofMillis(timeoutMs))
            .header(
                "Authorization",
                "Bearer " + apiKey
            )
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header(
                "User-Agent",
                "AutoBattle/0.2"
            )
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    buildRequest(source).toString()
                )
            )
            .build();

        return httpClient.sendAsync(
                request,
                HttpResponse.BodyHandlers.ofString()
            )
            .handle((response, error) -> {
                if (error != null) {
                    Throwable root = unwrap(error);

                    if (isTransient(root)
                        && canRetry(
                            attemptNumber,
                            deadlineNanos
                        )) {
                        return retryAfterDelay(
                            source,
                            attemptNumber + 1,
                            startedNanos,
                            deadlineNanos
                        );
                    }

                    return CompletableFuture
                        .<UpstreamResult>failedFuture(
                            failure(
                                root == null
                                    ? "OpenAI request failed"
                                    : root.getClass()
                                        .getSimpleName()
                                        + ": "
                                        + truncate(
                                            root.getMessage(),
                                            512
                                        ),
                                attemptNumber,
                                startedNanos,
                                null,
                                root
                            )
                        );
                }

                int status = response.statusCode();

                if (status >= 200 && status < 300) {
                    try {
                        List<String> normalized =
                            parseResponse(
                                response.body()
                            );

                        return CompletableFuture.completedFuture(
                            new UpstreamResult(
                                normalized,
                                attemptNumber,
                                elapsedMs(startedNanos),
                                status
                            )
                        );
                    } catch (RuntimeException exception) {
                        return CompletableFuture
                            .<UpstreamResult>failedFuture(
                                failure(
                                    "OpenAI structured response was invalid: "
                                        + truncate(
                                            exception.getMessage(),
                                            512
                                        ),
                                    attemptNumber,
                                    startedNanos,
                                    status,
                                    exception
                                )
                            );
                    }
                }

                if (isRetryableStatus(status)
                    && canRetry(
                        attemptNumber,
                        deadlineNanos
                    )) {
                    return retryAfterDelay(
                        source,
                        attemptNumber + 1,
                        startedNanos,
                        deadlineNanos
                    );
                }

                return CompletableFuture
                    .<UpstreamResult>failedFuture(
                        failure(
                            "OpenAI HTTP "
                                + status
                                + ": "
                                + truncate(
                                    response.body(),
                                    512
                                ),
                            attemptNumber,
                            startedNanos,
                            status,
                            null
                        )
                    );
            })
            .thenCompose(future -> future);
    }

    private CompletableFuture<UpstreamResult> retryAfterDelay(
        List<String> source,
        int nextAttempt,
        long startedNanos,
        long deadlineNanos
    ) {
        long remainingMs = remainingMs(deadlineNanos);

        if (remainingMs <= retryBackoffMs) {
            return CompletableFuture.failedFuture(
                failure(
                    "OpenAI normalization total timeout exceeded before retry",
                    nextAttempt - 1,
                    startedNanos,
                    null,
                    null
                )
            );
        }

        return CompletableFuture.runAsync(
                () -> {
                },
                CompletableFuture.delayedExecutor(
                    retryBackoffMs,
                    TimeUnit.MILLISECONDS
                )
            )
            .thenCompose(ignored ->
                attempt(
                    source,
                    nextAttempt,
                    startedNanos,
                    deadlineNanos
                )
            );
    }

    private boolean canRetry(
        int attemptNumber,
        long deadlineNanos
    ) {
        return attemptNumber < maxAttempts
            && remainingMs(deadlineNanos)
                > retryBackoffMs + 1L;
    }

    private boolean isRetryableStatus(int status) {
        return status == 408
            || status == 429
            || status >= 500;
    }

    private boolean isTransient(Throwable error) {
        return error instanceof HttpTimeoutException
            || error instanceof IOException;
    }

    private DoctrineNormalizationResult success(
        List<String> normalized,
        String hash,
        DoctrineNormalizationStatus status,
        int attempts,
        long latencyMs,
        Integer httpStatus
    ) {
        return new DoctrineNormalizationResult(
            normalized,
            hash,
            model,
            status,
            null,
            PROMPT_VERSION,
            attempts,
            latencyMs,
            httpStatus
        );
    }

    private DoctrineNormalizationResult fallback(
        List<String> source,
        String hash,
        String error,
        int attempts,
        long latencyMs,
        Integer httpStatus
    ) {
        return new DoctrineNormalizationResult(
            source,
            hash,
            model,
            DoctrineNormalizationStatus.FALLBACK_ERROR,
            error,
            PROMPT_VERSION,
            attempts,
            latencyMs,
            httpStatus
        );
    }

    private NormalizationFailure failure(
        String message,
        int attempts,
        long startedNanos,
        Integer httpStatus,
        Throwable cause
    ) {
        return new NormalizationFailure(
            message,
            attempts,
            elapsedMs(startedNanos),
            httpStatus,
            cause
        );
    }

    private JsonObject buildRequest(
        List<String> sourceLines
    ) {
        JsonObject root = new JsonObject();
        root.addProperty("model", model);
        root.addProperty("store", false);
        root.addProperty("instructions", INSTRUCTIONS);
        root.addProperty(
            "max_output_tokens",
            256
        );

        JsonObject reasoning = new JsonObject();
        reasoning.addProperty("effort", "none");
        root.add("reasoning", reasoning);

        JsonObject source = new JsonObject();
        JsonArray rules = new JsonArray();

        for (String line : sourceLines) {
            rules.add(line);
        }

        source.add("source_rules", rules);
        root.addProperty(
            "input",
            source.toString()
        );

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.addProperty(
            "additionalProperties",
            false
        );

        JsonObject properties = new JsonObject();

        for (int index = 1; index <= 3; index++) {
            JsonObject rule = new JsonObject();
            rule.addProperty("type", "string");
            properties.add("rule_" + index, rule);
        }

        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("rule_1");
        required.add("rule_2");
        required.add("rule_3");
        schema.add("required", required);

        JsonObject format = new JsonObject();
        format.addProperty("type", "json_schema");
        format.addProperty(
            "name",
            "autobattle_doctrine"
        );
        format.addProperty("strict", true);
        format.add("schema", schema);

        JsonObject text = new JsonObject();
        text.add("format", format);
        root.add("text", text);

        return root;
    }

    private List<String> parseResponse(String body) {
        JsonObject root = JsonParser
            .parseString(body)
            .getAsJsonObject();

        JsonElement output = root.get("output");

        if (output == null || !output.isJsonArray()) {
            throw new IllegalStateException(
                "OpenAI response is missing output"
            );
        }

        for (JsonElement itemElement :
            output.getAsJsonArray()) {
            if (!itemElement.isJsonObject()) {
                continue;
            }

            JsonObject item =
                itemElement.getAsJsonObject();

            if (!"message".equals(
                stringOrNull(item, "type")
            )) {
                continue;
            }

            JsonElement content = item.get("content");

            if (content == null
                || !content.isJsonArray()) {
                continue;
            }

            for (JsonElement contentElement :
                content.getAsJsonArray()) {
                if (!contentElement.isJsonObject()) {
                    continue;
                }

                JsonObject part =
                    contentElement.getAsJsonObject();

                if (!"output_text".equals(
                    stringOrNull(part, "type")
                )) {
                    continue;
                }

                String text = stringOrNull(
                    part,
                    "text"
                );

                if (text != null) {
                    return parseNormalizedRules(text);
                }
            }
        }

        throw new IllegalStateException(
            "OpenAI response contains no output_text"
        );
    }

    private List<String> parseNormalizedRules(
        String jsonText
    ) {
        JsonObject object = JsonParser
            .parseString(jsonText)
            .getAsJsonObject();

        return List.of(
            requireRule(object, "rule_1"),
            requireRule(object, "rule_2"),
            requireRule(object, "rule_3")
        );
    }

    private String requireRule(
        JsonObject object,
        String key
    ) {
        String value = stringOrNull(object, key);

        if (value == null) {
            throw new IllegalStateException(
                "OpenAI normalization is missing " + key
            );
        }

        String normalized = value.trim();

        if (normalized.isEmpty()
            || normalized.length()
                > MAX_NORMALIZED_LINE_LENGTH) {
            throw new IllegalStateException(
                "OpenAI normalization produced invalid " + key
            );
        }

        return normalized;
    }

    private long remainingMs(long deadlineNanos) {
        long nanos = deadlineNanos - System.nanoTime();

        if (nanos <= 0L) {
            return 0L;
        }

        return Math.max(
            1L,
            TimeUnit.NANOSECONDS.toMillis(nanos)
        );
    }

    private long elapsedMs(long startedNanos) {
        return Math.max(
            0L,
            TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - startedNanos
            )
        );
    }

    private Throwable unwrap(Throwable error) {
        Throwable current = error;

        while (current instanceof CompletionException
            && current.getCause() != null) {
            current = current.getCause();
        }

        return current;
    }

    private static String stringOrNull(
        JsonObject object,
        String key
    ) {
        JsonElement value = object.get(key);

        if (value == null
            || value.isJsonNull()
            || !value.isJsonPrimitive()) {
            return null;
        }

        return value.getAsString();
    }

    private static String stripTrailingSlash(
        String value
    ) {
        String result = value;

        while (result.endsWith("/")) {
            result = result.substring(
                0,
                result.length() - 1
            );
        }

        return result;
    }

    private static String truncate(
        String value,
        int maxLength
    ) {
        if (value == null
            || value.length() <= maxLength) {
            return value;
        }

        return value.substring(0, maxLength)
            + "...";
    }

    private record NormalizationKey(
        String sourceHash,
        String model,
        String promptVersion
    ) {
    }

    private record UpstreamResult(
        List<String> normalizedLines,
        int attemptCount,
        long latencyMs,
        Integer httpStatus
    ) {
        private UpstreamResult {
            normalizedLines = List.copyOf(
                normalizedLines
            );
        }
    }

    private static final class NormalizationFailure
            extends RuntimeException {
        private final int attemptCount;
        private final long latencyMs;
        private final Integer httpStatus;

        private NormalizationFailure(
            String message,
            int attemptCount,
            long latencyMs,
            Integer httpStatus,
            Throwable cause
        ) {
            super(message, cause);
            this.attemptCount = attemptCount;
            this.latencyMs = latencyMs;
            this.httpStatus = httpStatus;
        }

        private int attemptCount() {
            return attemptCount;
        }

        private long latencyMs() {
            return latencyMs;
        }

        private Integer httpStatus() {
            return httpStatus;
        }
    }
}
