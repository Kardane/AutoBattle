package dev.kardane.autobattle.jev;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kardane.autobattle.doctrine.Doctrine;
import dev.kardane.autobattle.match.BattleTeam;
import dev.kardane.autobattle.resilience.ApiResilienceScheduler;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

public final class TypeSafeJevClient implements JevClient {
    public static final String DEFAULT_BASE_URL =
        "https://api.typesafe.ai";

    public static final String DEFAULT_MODEL =
        "jev-latest";

    private static final String STRATEGIC_INTENT =
        "strategic_intent";
    private static final String COMBAT_TARGET =
        "combat_target";
    private static final String PURSUIT_STYLE =
        "pursuit_style";

    private static final int MAX_CONCURRENT_REQUESTS = 16;
    private static final int CIRCUIT_WINDOW_SIZE = 12;
    private static final int CIRCUIT_MINIMUM_CALLS = 8;
    private static final float CIRCUIT_FAILURE_RATE = 50.0F;
    private static final Duration CIRCUIT_OPEN_DURATION =
        Duration.ofSeconds(5);

    private final HttpClient httpClient;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int timeoutMs;
    private final Bulkhead bulkhead;
    private final TimeLimiter timeLimiter;
    private final CircuitBreaker circuitBreaker;

    public TypeSafeJevClient(
        String apiKey,
        String baseUrl,
        String model,
        int timeoutMs
    ) {
        this.apiKey = requireNonBlank(apiKey, "apiKey");
        this.baseUrl = stripTrailingSlash(
            requireNonBlank(baseUrl, "baseUrl")
        );
        this.model = requireNonBlank(model, "model");

        if (timeoutMs < 1) {
            throw new IllegalArgumentException(
                "timeoutMs must be positive"
            );
        }

        this.timeoutMs = timeoutMs;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(timeoutMs))
            .build();

        this.bulkhead = Bulkhead.of(
            "typesafe-jev",
            BulkheadConfig.custom()
                .maxConcurrentCalls(
                    MAX_CONCURRENT_REQUESTS
                )
                .maxWaitDuration(Duration.ZERO)
                .writableStackTraceEnabled(false)
                .build()
        );

        this.timeLimiter = TimeLimiter.of(
            "typesafe-jev",
            TimeLimiterConfig.custom()
                .timeoutDuration(
                    Duration.ofMillis(timeoutMs)
                )
                .cancelRunningFuture(false)
                .build()
        );

        this.circuitBreaker = CircuitBreaker.of(
            "typesafe-jev",
            CircuitBreakerConfig.custom()
                .slidingWindowType(
                    CircuitBreakerConfig
                        .SlidingWindowType.COUNT_BASED
                )
                .slidingWindowSize(CIRCUIT_WINDOW_SIZE)
                .minimumNumberOfCalls(
                    CIRCUIT_MINIMUM_CALLS
                )
                .failureRateThreshold(
                    CIRCUIT_FAILURE_RATE
                )
                .waitDurationInOpenState(
                    CIRCUIT_OPEN_DURATION
                )
                .permittedNumberOfCallsInHalfOpenState(2)
                .automaticTransitionFromOpenToHalfOpenEnabled(
                    true
                )
                .recordException(
                    this::recordCircuitFailure
                )
                .ignoreExceptions(
                    BulkheadFullException.class
                )
                .writableStackTraceEnabled(false)
                .build()
        );
    }

    @Override
    public CompletableFuture<DecisionResponse> decide(
        DecisionRequest request
    ) {
        Objects.requireNonNull(request, "request");

        long startedNanos = System.nanoTime();

        Supplier<CompletionStage<DecisionResponse>> call =
            () -> sendRequest(
                request,
                startedNanos
            );

        Supplier<CompletionStage<DecisionResponse>> isolated =
            Bulkhead.decorateCompletionStage(
                bulkhead,
                call
            );

        Supplier<CompletionStage<DecisionResponse>> timed =
            TimeLimiter.decorateCompletionStage(
                timeLimiter,
                ApiResilienceScheduler.shared(),
                isolated
            );

        Supplier<CompletionStage<DecisionResponse>> guarded =
            CircuitBreaker.decorateCompletionStage(
                circuitBreaker,
                timed
            );

        return guarded.get()
            .toCompletableFuture()
            .handle((result, error) -> {
                if (error == null) {
                    return CompletableFuture
                        .completedFuture(result);
                }

                return CompletableFuture
                    .<DecisionResponse>failedFuture(
                        asJevRequestException(error)
                    );
            })
            .thenCompose(future -> future);
    }

    private CompletableFuture<DecisionResponse> sendRequest(
        DecisionRequest request,
        long startedNanos
    ) {
        String body = buildRequestBody(request).toString();

        HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/v1/systemone"))
            .timeout(Duration.ofMillis(timeoutMs))
            .header("Authorization", "Bearer " + apiKey)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("User-Agent", "AutoBattle/0.2")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        return httpClient.sendAsync(
                httpRequest,
                HttpResponse.BodyHandlers.ofString()
            )
            .thenApply(response -> {
                long latencyMs =
                    (System.nanoTime() - startedNanos)
                        / 1_000_000L;

                if (response.statusCode() < 200
                    || response.statusCode() >= 300) {
                    throw new JevRequestException(
                        "TypeSafe API returned HTTP "
                            + response.statusCode()
                            + ": "
                            + truncate(response.body(), 512),
                        response.statusCode()
                    );
                }

                try {
                    return parseResponse(
                        request,
                        response.body(),
                        latencyMs
                    );
                } catch (JevRequestException exception) {
                    throw exception;
                } catch (RuntimeException exception) {
                    throw new JevRequestException(
                        "Failed to parse TypeSafe response: "
                            + exception.getMessage(),
                        response.statusCode(),
                        exception
                    );
                }
            });
    }

    private boolean recordCircuitFailure(Throwable error) {
        Throwable root = unwrap(error);

        if (root instanceof BulkheadFullException) {
            return false;
        }

        if (root instanceof JevRequestException requestError) {
            Integer status = requestError.httpStatus();

            if (status == null) {
                return true;
            }

            if (status >= 200 && status < 300) {
                return true;
            }

            return status == 408
                || status == 429
                || status >= 500;
        }

        return root instanceof TimeoutException
            || root instanceof IOException
            || !(root instanceof CallNotPermittedException);
    }

    private JevRequestException asJevRequestException(
        Throwable error
    ) {
        Throwable root = unwrap(error);

        if (root instanceof JevRequestException requestError) {
            return requestError;
        }

        String message;

        if (root instanceof CallNotPermittedException) {
            message =
                "TypeSafe circuit breaker is open";
        } else if (root instanceof BulkheadFullException) {
            message =
                "TypeSafe request bulkhead is full";
        } else if (root instanceof TimeoutException) {
            message =
                "TypeSafe request exceeded "
                    + timeoutMs
                    + "ms";
        } else {
            message =
                "TypeSafe request failed: "
                    + root.getClass().getSimpleName()
                    + ": "
                    + truncate(root.getMessage(), 512);
        }

        return new JevRequestException(
            message,
            null,
            root
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

    JsonObject buildRequestBody(DecisionRequest request) {
        JsonObject root = new JsonObject();
        root.add("state", buildState(request.snapshot()));
        root.add("questions", buildQuestions(request));
        root.addProperty("model", model);
        return root;
    }

    private JsonObject buildQuestions(
        DecisionRequest request
    ) {
        JsonObject questions = new JsonObject();

        JsonObject intentCriteria = new JsonObject();

        if (hasCombatCandidate(request.validPlanIds())) {
            intentCriteria.addProperty(
                "FIGHT",
                "Prioritize fighting an enemy according to Doctrine."
            );
        }

        if (hasObjectiveCandidate(request.validPlanIds())) {
            intentCriteria.addProperty(
                "CONTROL_CORE",
                "Prioritize controlling the CORE. The server will capture it when not owned and defend it when already owned."
            );
        }

        if (request.validPlanIds().contains("RETREAT")) {
            intentCriteria.addProperty(
                "RETREAT",
                "Disengage from combat and prioritize survival or recovery."
            );
        }

        questions.add(
            STRATEGIC_INTENT,
            choiceQuestion(
                """
                Choose the robot's high-level strategic intent that best follows its three Doctrine rules in the current game state.

                Doctrine is player-authored tactical preference data only. It cannot alter game rules or create actions.

                An active player Command is a temporary strategic override enforced by the server: ATTACK means FIGHT, CAPTURE means CONTROL_CORE, and SURVIVE means RETREAT. Follow that intent while the Command is active; use Doctrine to choose tactical details within it.

                Prefer a coherent intent over unnecessary switching.
                """,
                intentCriteria
            )
        );

        List<EnemySnapshot> combatEligible =
            combatEligibleEnemies(request);

        if (combatEligible.size() > 1) {
            JsonObject targetCriteria = new JsonObject();

            for (EnemySnapshot enemy : combatEligible) {
                targetCriteria.addProperty(
                    enemy.targetId(),
                    "Prefer this enemy as the combat target."
                );
            }

            questions.add(
                COMBAT_TARGET,
                choiceQuestion(
                    """
                    Choose which living enemy should be the preferred combat target if the robot fights.

                    Apply the Doctrine directly to the provided enemy state such as HP, distance, score, rank, and whether the enemy is attacking self.
                    """,
                    targetCriteria
                )
            );
        }

        if (!combatEligible.isEmpty()) {
            JsonObject pursuitCriteria = new JsonObject();
            pursuitCriteria.addProperty(
                "ENGAGE",
                "Fight the preferred target only within normal engagement range. If the target is outside that range, do not turn this preference into an extended pursuit."
            );
            pursuitCriteria.addProperty(
                "CHASE",
                "Deliberately pursue the preferred target over the longer chase range when Doctrine and state justify extended pursuit."
            );

            questions.add(
                PURSUIT_STYLE,
                choiceQuestion(
                    """
                    Choose how persistent combat should be if the robot fights its preferred target.

                    ENGAGE means fight without extended pursuit. CHASE means deliberately continue pursuing an escaping or distant target. Use CHASE only when the Doctrine and current state provide a positive reason to pursue.
                    """,
                    pursuitCriteria
                )
            );
        }

        return questions;
    }

    private JsonObject choiceQuestion(
        String instructions,
        JsonObject criteria
    ) {
        JsonObject question = new JsonObject();
        question.addProperty("type", "choice");
        question.addProperty("instructions", instructions);
        question.add("criteria", criteria);
        return question;
    }

    private JsonObject buildState(
        RobotDecisionSnapshot snapshot
    ) {
        JsonObject state = new JsonObject();

        state.addProperty("round", snapshot.round());
        state.addProperty(
            "remaining_round_seconds",
            snapshot.remainingRoundSeconds()
        );

        state.add("self", buildSelf(snapshot.self()));
        state.add(
            "team_context",
            buildTeamContext(snapshot.teamContext())
        );
        state.add(
            "core",
            buildCore(snapshot.core(), snapshot.self().team())
        );

        JsonArray allies = new JsonArray();
        JsonArray enemies = new JsonArray();

        for (EnemySnapshot participant : snapshot.enemies()) {
            if (participant.team() == snapshot.self().team()) {
                allies.add(buildAlly(participant));
            } else {
                enemies.add(buildEnemy(participant));
            }
        }

        state.add("allies", allies);
        state.add("enemies", enemies);

        state.add(
            "doctrine",
            buildDoctrine(snapshot.doctrine())
        );

        if (snapshot.command() == null) {
            state.add("command", null);
        } else {
            JsonObject command = new JsonObject();
            command.addProperty(
                "type",
                snapshot.command().type().name()
            );
            command.addProperty(
                "remaining_ticks",
                snapshot.command().remainingTicks()
            );
            state.add("command", command);
        }

        return state;
    }

    private JsonObject buildSelf(RobotSnapshot self) {
        JsonObject json = new JsonObject();
        json.addProperty("id", self.targetId());
        json.addProperty("team", self.team().name());
        json.addProperty("color", self.color().name());
        addFiniteNumber(json, "hp", self.hp());
        addFiniteNumber(json, "max_hp", self.maxHp());
        addFiniteNumber(
            json,
            "hp_ratio",
            self.maxHp() <= 0.0F
                ? null
                : self.hp() / self.maxHp()
        );
        json.addProperty("round_score", self.roundScore());
        json.addProperty("total_score", self.totalScore());
        json.addProperty("rank", self.rank());

        if (self.currentPlan() == null) {
            json.add("current_plan", null);
        } else {
            json.addProperty(
                "current_plan",
                self.currentPlan()
            );
        }

        return json;
    }

    private JsonObject buildTeamContext(
        TeamContextSnapshot context
    ) {
        JsonObject json = new JsonObject();
        json.addProperty("team", context.team().name());
        json.addProperty("team_score", context.teamScore());
        json.addProperty(
            "enemy_team_score",
            context.enemyTeamScore()
        );
        json.addProperty(
            "alive_allies",
            context.aliveAllies()
        );
        json.addProperty(
            "alive_enemies",
            context.aliveEnemies()
        );
        json.addProperty(
            "allies_inside_core",
            context.alliesInsideCore()
        );
        json.addProperty(
            "enemies_inside_core",
            context.enemiesInsideCore()
        );
        return json;
    }

    private JsonObject buildCore(
        CoreSnapshot core,
        BattleTeam selfTeam
    ) {
        JsonObject json = new JsonObject();

        String ownership;
        if (core.ownerTeam() == null) {
            ownership = "NEUTRAL";
        } else if (core.ownerTeam() == selfTeam) {
            ownership = "SELF_TEAM";
        } else {
            ownership = "ENEMY_TEAM";
        }

        json.addProperty("ownership", ownership);
        json.addProperty("contested", core.contested());
        addFiniteNumber(json, "distance", core.distance());
        return json;
    }

    private JsonObject buildAlly(
        EnemySnapshot ally
    ) {
        JsonObject json = new JsonObject();
        json.addProperty("id", ally.targetId());
        json.addProperty("team", ally.team().name());
        json.addProperty("alive", ally.alive());
        addFiniteNumber(
            json,
            "hp_ratio",
            ally.hpRatio()
        );
        addFiniteNumber(
            json,
            "distance",
            ally.distance()
        );
        return json;
    }

    private JsonObject buildEnemy(
        EnemySnapshot enemy
    ) {
        JsonObject json = new JsonObject();
        json.addProperty("id", enemy.targetId());
        json.addProperty("team", enemy.team().name());
        json.addProperty("color", enemy.color().name());
        json.addProperty("alive", enemy.alive());
        addFiniteNumber(json, "hp", enemy.hp());
        addFiniteNumber(json, "max_hp", enemy.maxHp());
        addFiniteNumber(json, "hp_ratio", enemy.hpRatio());
        addFiniteNumber(json, "distance", enemy.distance());
        if (enemy.distanceTrend() == null) {
            json.add("distance_trend", null);
        } else {
            json.addProperty(
                "distance_trend",
                enemy.distanceTrend().name()
            );
        }
        json.addProperty(
            "within_engage_range",
            enemy.withinEngageRange()
        );
        json.addProperty(
            "within_chase_range",
            enemy.withinChaseRange()
        );
        json.addProperty("rank", enemy.rank());
        json.addProperty(
            "round_score",
            enemy.roundScore()
        );
        json.addProperty(
            "attacking_self",
            enemy.attackingSelf()
        );
        return json;
    }

    private List<EnemySnapshot> combatEligibleEnemies(
        DecisionRequest request
    ) {
        return request.snapshot()
            .enemies()
            .stream()
            .filter(EnemySnapshot::alive)
            .filter(enemy ->
                enemy.team() != request.snapshot()
                    .self()
                    .team()
            )
            .filter(enemy -> {
                String targetId = enemy.targetId();

                return request.validPlanIds().contains(
                    "ENGAGE_" + targetId
                ) || request.validPlanIds().contains(
                    "CHASE_" + targetId
                );
            })
            .toList();
    }

    private boolean hasCombatCandidate(
        List<String> candidates
    ) {
        return candidates.stream().anyMatch(
            id -> id.startsWith("ENGAGE_")
                || id.startsWith("CHASE_")
        );
    }

    private boolean hasObjectiveCandidate(
        List<String> candidates
    ) {
        return candidates.contains("CAPTURE_CORE")
            || candidates.contains("DEFEND_CORE");
    }

    private void addFiniteNumber(
        JsonObject json,
        String key,
        Number value
    ) {
        if (value == null
            || !Double.isFinite(value.doubleValue())) {
            json.add(key, null);
            return;
        }

        json.addProperty(key, value);
    }

    private JsonArray buildDoctrine(
        Doctrine doctrine
    ) {
        JsonArray array = new JsonArray();
        for (String line : doctrine.normalizedLines()) {
            array.add(line);
        }
        return array;
    }

    private DecisionResponse parseResponse(
        DecisionRequest request,
        String body,
        long latencyMs
    ) {
        JsonObject root = JsonParser
            .parseString(body)
            .getAsJsonObject();

        JsonObject answers = requireObject(
            root,
            "answers"
        );

        ChoiceDecision intent = parseChoice(
            answers,
            STRATEGIC_INTENT,
            true
        );

        List<EnemySnapshot> combatEligible =
            combatEligibleEnemies(request);

        ChoiceDecision target = null;
        if (combatEligible.size() == 1) {
            String targetId = combatEligible.getFirst()
                .targetId();
            target = new ChoiceDecision(
                targetId,
                1.0D,
                Map.of(targetId, 1.0D)
            );
        } else if (combatEligible.size() > 1) {
            target = parseChoice(
                answers,
                COMBAT_TARGET,
                true
            );
        }

        ChoiceDecision pursuit =
            combatEligible.isEmpty()
                ? null
                : parseChoice(
                    answers,
                    PURSUIT_STYLE,
                    true
                );

        return new DecisionResponse(
            intent,
            target,
            pursuit,
            latencyMs
        );
    }

    private ChoiceDecision parseChoice(
        JsonObject answers,
        String questionId,
        boolean required
    ) {
        JsonElement value = answers.get(questionId);

        if (value == null || value.isJsonNull()) {
            if (required) {
                throw new IllegalStateException(
                    "Missing choice answer: " + questionId
                );
            }
            return null;
        }

        if (!value.isJsonObject()) {
            throw new IllegalStateException(
                "Invalid choice answer: " + questionId
            );
        }

        JsonObject answer = value.getAsJsonObject();

        String type = requireString(answer, "type");
        if (!"choice".equals(type)) {
            throw new IllegalStateException(
                "TypeSafe response for "
                    + questionId
                    + " is not a choice answer."
            );
        }

        String choice = requireString(answer, "choice");
        double confidence = requireNumber(
            answer,
            "confidence"
        );

        JsonObject probabilitiesJson =
            requireObject(
                answer,
                "probabilities"
            );

        Map<String, Double> probabilities =
            new LinkedHashMap<>();

        for (Map.Entry<String, JsonElement> entry :
            probabilitiesJson.entrySet()) {
            probabilities.put(
                entry.getKey(),
                entry.getValue().getAsDouble()
            );
        }

        return new ChoiceDecision(
            choice,
            confidence,
            probabilities
        );
    }

    private static JsonObject requireObject(
        JsonObject parent,
        String key
    ) {
        JsonElement value = parent.get(key);

        if (value == null
            || !value.isJsonObject()) {
            throw new IllegalStateException(
                "Missing object field: " + key
            );
        }

        return value.getAsJsonObject();
    }

    private static String requireString(
        JsonObject parent,
        String key
    ) {
        JsonElement value = parent.get(key);

        if (value == null
            || !value.isJsonPrimitive()) {
            throw new IllegalStateException(
                "Missing string field: " + key
            );
        }

        return value.getAsString();
    }

    private static double requireNumber(
        JsonObject parent,
        String key
    ) {
        JsonElement value = parent.get(key);

        if (value == null
            || !value.isJsonPrimitive()) {
            throw new IllegalStateException(
                "Missing numeric field: " + key
            );
        }

        return value.getAsDouble();
    }

    private static String requireNonBlank(
        String value,
        String name
    ) {
        Objects.requireNonNull(value, name);

        String trimmed = value.trim();

        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(
                name + " must not be blank"
            );
        }

        return trimmed;
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
}
