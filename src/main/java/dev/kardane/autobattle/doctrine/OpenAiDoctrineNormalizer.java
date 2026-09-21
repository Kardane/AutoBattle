package dev.kardane.autobattle.doctrine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kardane.autobattle.config.DoctrineNormalizerConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class OpenAiDoctrineNormalizer
        implements DoctrineNormalizer {
    private static final int MAX_NORMALIZED_LINE_LENGTH = 240;

    private static final String INSTRUCTIONS = """
        You normalize player-authored tactical doctrine for a Minecraft AutoBattle robot.

        The input always contains exactly three source rules. The source may be written in any language, including Korean or English.

        Rewrite each source rule into one concise English tactical rule optimized for a downstream tactical decision model. Preserve the player's intent. Do not add goals, conditions, priorities, numbers, thresholds, targets, or restrictions that were not present in the source.

        Use AutoBattle terminology when it accurately represents the source:
        - CORE: the central capture objective.
        - ENGAGE: fight a nearby enemy without extended pursuit.
        - CHASE: pursue an enemy over a longer distance.
        - CAPTURE_CORE: move to and capture CORE.
        - DEFEND_CORE: stay near an owned CORE and defend it.
        - RETREAT: disengage to survive or recover.
        - HP: robot health.

        Do not invent team colors or specific enemies. Do not turn vague language into a numeric threshold. Preserve explicit numeric thresholds exactly. Keep the three rules separate and in the same order. Output only the schema fields.
        """;

    private final HttpClient httpClient;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int timeoutMs;
    private final Map<String, List<String>> cache =
        new ConcurrentHashMap<>();

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
        this.timeoutMs = config.requestTimeoutMs();
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(
                Duration.ofMillis(timeoutMs)
            )
            .build();
    }

    @Override
    public DoctrineNormalizationResult normalize(
        List<String> sourceLines
    ) {
        List<String> source = List.copyOf(sourceLines);

        if (source.size() != 3) {
            throw new IllegalArgumentException(
                "Doctrine normalization requires exactly three source lines"
            );
        }

        String hash = PassThroughDoctrineNormalizer
            .sourceHash(source);

        List<String> cached = cache.get(hash);

        if (cached != null) {
            return new DoctrineNormalizationResult(
                cached,
                hash,
                model,
                DoctrineNormalizationStatus.CACHE_HIT,
                null
            );
        }

        try {
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
                    "AutoBattle/0.1"
                )
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        buildRequest(source).toString()
                    )
                )
                .build();

            HttpResponse<String> response =
                httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
                );

            if (response.statusCode() < 200
                || response.statusCode() >= 300) {
                return fallback(
                    source,
                    hash,
                    "OpenAI HTTP "
                        + response.statusCode()
                        + ": "
                        + truncate(response.body(), 512)
                );
            }

            List<String> normalized = parseResponse(
                response.body()
            );

            cache.put(hash, normalized);

            return new DoctrineNormalizationResult(
                normalized,
                hash,
                model,
                DoctrineNormalizationStatus.NORMALIZED,
                null
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return fallback(
                source,
                hash,
                "OpenAI normalization interrupted"
            );
        } catch (Exception exception) {
            return fallback(
                source,
                hash,
                exception.getClass().getSimpleName()
                    + ": "
                    + truncate(
                        exception.getMessage(),
                        512
                    )
            );
        }
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

        List<String> result = List.of(
            requireRule(object, "rule_1"),
            requireRule(object, "rule_2"),
            requireRule(object, "rule_3")
        );

        return result;
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

    private DoctrineNormalizationResult fallback(
        List<String> source,
        String hash,
        String error
    ) {
        return new DoctrineNormalizationResult(
            source,
            hash,
            model,
            DoctrineNormalizationStatus.FALLBACK_ERROR,
            error
        );
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
}
