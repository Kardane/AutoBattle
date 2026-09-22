package dev.kardane.autobattle.doctrine;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.openai.client.OpenAIClientAsync;
import com.openai.client.okhttp.OpenAIOkHttpClientAsync;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIRetryableException;
import com.openai.errors.OpenAIServiceException;
import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.StructuredResponse;
import com.openai.models.responses.StructuredResponseCreateParams;
import dev.kardane.autobattle.config.DoctrineNormalizerConfig;
import dev.kardane.autobattle.resilience.ApiResilienceScheduler;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class OpenAiDoctrineNormalizer
        implements DoctrineNormalizer {
    public static final String PROMPT_VERSION =
        "autobattle-doctrine-v3";

    private static final int MAX_NORMALIZED_LINE_LENGTH = 240;
    private static final long CACHE_MAX_ENTRIES = 4_096L;
    private static final Duration CACHE_EXPIRE_AFTER_ACCESS =
        Duration.ofHours(6);

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

    private final OpenAIClientAsync client;
    private final String model;
    private final int requestTimeoutMs;
    private final Retry retry;
    private final TimeLimiter totalTimeLimiter;

    private final AsyncCache<
        NormalizationKey,
        UpstreamResult
    > normalizationCache = Caffeine.newBuilder()
        .maximumSize(CACHE_MAX_ENTRIES)
        .expireAfterAccess(CACHE_EXPIRE_AFTER_ACCESS)
        .buildAsync();

    public OpenAiDoctrineNormalizer(
        DoctrineNormalizerConfig config,
        String resolvedApiKey
    ) {
        Objects.requireNonNull(config, "config");

        String apiKey = Objects.requireNonNull(
            resolvedApiKey,
            "resolvedApiKey"
        ).trim();

        this.model = config.model();
        this.requestTimeoutMs = config.requestTimeoutMs();

        this.retry = Retry.of(
            "openai-doctrine-normalizer",
            RetryConfig.<UpstreamResult>custom()
                .maxAttempts(config.maxAttempts())
                .waitDuration(
                    Duration.ofMillis(
                        config.retryBackoffMs()
                    )
                )
                .retryOnException(this::isTransient)
                .build()
        );

        this.totalTimeLimiter = TimeLimiter.of(
            "openai-doctrine-normalizer-total",
            TimeLimiterConfig.custom()
                .timeoutDuration(
                    Duration.ofMillis(
                        config.totalTimeoutMs()
                    )
                )
                .cancelRunningFuture(false)
                .build()
        );

        this.client = OpenAIOkHttpClientAsync.builder()
            .apiKey(apiKey)
            .baseUrl(sdkBaseUrl(config.baseUrl()))
            .timeout(Duration.ofMillis(requestTimeoutMs))
            // Resilience4j owns retries. Avoid SDK-level double retries.
            .maxRetries(0)
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

        CompletableFuture<UpstreamResult> existing =
            normalizationCache.getIfPresent(key);

        if (existing != null) {
            if (existing.isDone()
                && !existing.isCompletedExceptionally()
                && !existing.isCancelled()) {
                UpstreamResult cached =
                    existing.getNow(null);

                if (cached != null) {
                    return CompletableFuture.completedFuture(
                        success(
                            cached.normalizedLines(),
                            hash,
                            DoctrineNormalizationStatus.CACHE_HIT,
                            0,
                            0L,
                            null
                        )
                    );
                }
            }

            if (!existing.isCompletedExceptionally()
                && !existing.isCancelled()) {
                return mapSharedResult(
                    existing,
                    source,
                    hash,
                    DoctrineNormalizationStatus
                        .SHARED_INFLIGHT,
                    System.nanoTime()
                );
            }

            normalizationCache.synchronous()
                .invalidate(key);
        }

        long startedNanos = System.nanoTime();
        AtomicBoolean owner = new AtomicBoolean(false);

        CompletableFuture<UpstreamResult> shared =
            normalizationCache.get(
                key,
                (ignored, executor) -> {
                    owner.set(true);
                    return executeResilientRequest(
                        source,
                        startedNanos
                    );
                }
            );

        DoctrineNormalizationStatus status =
            owner.get()
                ? DoctrineNormalizationStatus.NORMALIZED
                : DoctrineNormalizationStatus.SHARED_INFLIGHT;

        return mapSharedResult(
            shared,
            source,
            hash,
            status,
            startedNanos
        );
    }

    private CompletableFuture<DoctrineNormalizationResult>
    mapSharedResult(
        CompletableFuture<UpstreamResult> shared,
        List<String> source,
        String hash,
        DoctrineNormalizationStatus status,
        long startedNanos
    ) {
        return shared.handle((upstream, error) -> {
            if (error == null && upstream != null) {
                return success(
                    upstream.normalizedLines(),
                    hash,
                    status,
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
                httpStatus(root)
            );
        });
    }

    private CompletableFuture<UpstreamResult>
    executeResilientRequest(
        List<String> source,
        long startedNanos
    ) {
        AtomicInteger attempts = new AtomicInteger();

        Supplier<CompletionStage<UpstreamResult>>
            singleAttempt = () -> invokeOnce(
                source,
                attempts.incrementAndGet(),
                startedNanos
            );

        Supplier<CompletionStage<UpstreamResult>>
            retried = Retry.decorateCompletionStage(
                retry,
                ApiResilienceScheduler.shared(),
                singleAttempt
            );

        Supplier<CompletionStage<UpstreamResult>>
            timeLimited =
                TimeLimiter.decorateCompletionStage(
                    totalTimeLimiter,
                    ApiResilienceScheduler.shared(),
                    retried
                );

        CompletionStage<UpstreamResult> stage =
            timeLimited.get();

        return stage.handle((result, error) -> {
            if (error == null) {
                return CompletableFuture.completedFuture(
                    result
                );
            }

            Throwable root = unwrap(error);

            if (root instanceof NormalizationFailure failure) {
                return CompletableFuture
                    .<UpstreamResult>failedFuture(failure);
            }

            Integer status = httpStatus(root);
            String message = root == null
                ? "OpenAI normalization failed"
                : root.getClass().getSimpleName()
                    + ": "
                    + truncate(root.getMessage(), 512);

            return CompletableFuture
                .<UpstreamResult>failedFuture(
                    failure(
                        message,
                        attempts.get(),
                        startedNanos,
                        status,
                        root
                    )
                );
        })
            .thenCompose(future -> future)
            .toCompletableFuture();
    }

    private CompletableFuture<UpstreamResult> invokeOnce(
        List<String> source,
        int attemptNumber,
        long startedNanos
    ) {
        StructuredResponseCreateParams<NormalizedDoctrine>
            request = buildRequest(source);

        return client.responses()
            .withOptions(options ->
                options.timeout(
                    Duration.ofMillis(requestTimeoutMs)
                )
            )
            .create(request)
            .thenApply(response -> {
                try {
                    List<String> normalized =
                        parseResponse(response);

                    return new UpstreamResult(
                        normalized,
                        attemptNumber,
                        elapsedMs(startedNanos),
                        200
                    );
                } catch (RuntimeException exception) {
                    throw failure(
                        "OpenAI structured response was invalid: "
                            + truncate(
                                exception.getMessage(),
                                512
                            ),
                        attemptNumber,
                        startedNanos,
                        200,
                        exception
                    );
                }
            });
    }

    private boolean isRetryableStatus(int status) {
        return status == 408
            || status == 429
            || status >= 500;
    }

    private boolean isTransient(Throwable error) {
        Throwable root = unwrap(error);

        if (root instanceof NormalizationFailure) {
            return false;
        }

        Integer status = httpStatus(root);

        if (status != null) {
            return isRetryableStatus(status);
        }

        return root instanceof OpenAIIoException
            || root instanceof OpenAIRetryableException;
    }

    private Integer httpStatus(Throwable error) {
        Throwable root = unwrap(error);

        return root instanceof OpenAIServiceException service
            ? service.statusCode()
            : null;
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

    private StructuredResponseCreateParams<NormalizedDoctrine>
    buildRequest(List<String> sourceLines) {
        JsonObject source = new JsonObject();
        JsonArray rules = new JsonArray();

        for (String line : sourceLines) {
            rules.add(line);
        }

        source.add("source_rules", rules);

        return ResponseCreateParams.builder()
            .model(model)
            .store(false)
            .instructions(INSTRUCTIONS)
            .maxOutputTokens(256)
            .reasoning(
                Reasoning.builder()
                    .effort(ReasoningEffort.NONE)
                    .build()
            )
            .input(source.toString())
            .text(NormalizedDoctrine.class)
            .build();
    }

    private List<String> parseResponse(
        StructuredResponse<NormalizedDoctrine> response
    ) {
        NormalizedDoctrine normalized =
            response.output().stream()
                .flatMap(item ->
                    item.message().stream()
                )
                .flatMap(message ->
                    message.content().stream()
                )
                .flatMap(content ->
                    content.outputText().stream()
                )
                .findFirst()
                .orElseThrow(() ->
                    new IllegalStateException(
                        "OpenAI response contains no structured output_text"
                    )
                );

        return List.of(
            requireRule(normalized.rule1, "rule_1"),
            requireRule(normalized.rule2, "rule_2"),
            requireRule(normalized.rule3, "rule_3")
        );
    }

    private String requireRule(
        String value,
        String key
    ) {
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

    private static String sdkBaseUrl(String configuredBaseUrl) {
        String base = stripTrailingSlash(
            Objects.requireNonNull(
                configuredBaseUrl,
                "configuredBaseUrl"
            )
        );

        return base.endsWith("/v1")
            ? base
            : base + "/v1";
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

    public static final class NormalizedDoctrine {
        @JsonProperty("rule_1")
        public String rule1;

        @JsonProperty("rule_2")
        public String rule2;

        @JsonProperty("rule_3")
        public String rule3;
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
