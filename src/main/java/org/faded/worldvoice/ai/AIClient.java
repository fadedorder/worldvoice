package org.faded.worldvoice.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * Minimal OpenRouter client. All network calls happen off the server thread.
 */
public class AIClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(AIClient.class);
    private static final URI OPENROUTER_URI = URI.create("https://openrouter.ai/api/v1/chat/completions");
    private static final int MAX_RETRIES = 2;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public Optional<DecisionResponse> requestDecision(AIRequest request) {
        return requestJsonResponse(request).flatMap(AIClient::parseDecisionJson);
    }

    public Optional<JsonObject> requestJsonResponse(AIRequest request) {
        if (request == null || request.apiKey().isBlank()) {
            return Optional.empty();
        }
        String body = buildRequestBody(request);
        int attempts = 0;
        while (attempts <= MAX_RETRIES) {
            try {
                HttpResponse<String> response = sendRequest(request, body);
                int statusBucket = response.statusCode() / 100;
                if (statusBucket == 2) {
                    return parseContent(response.body());
                }
                LOGGER.warn("OpenRouter request failed with status {}: {}", response.statusCode(), response.body());
                if (!shouldRetry(response.statusCode()) || attempts == MAX_RETRIES) {
                    return Optional.empty();
                }
            } catch (IOException | InterruptedException ex) {
                if (ex instanceof InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    LOGGER.warn("OpenRouter request interrupted: {}", interrupted.toString());
                    return Optional.empty();
                }
                LOGGER.warn("OpenRouter request attempt {} failed: {}", attempts + 1, ex.toString());
                if (attempts == MAX_RETRIES) {
                    return Optional.empty();
                }
            } catch (Exception ex) {
                LOGGER.warn("OpenRouter request failed: {}", ex.toString());
                return Optional.empty();
            }
            attempts++;
            try {
                Thread.sleep(500L * attempts);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private HttpResponse<String> sendRequest(AIRequest request, String body) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(OPENROUTER_URI)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + request.apiKey());
        if (request.referer() != null && !request.referer().isBlank()) {
            builder.header("HTTP-Referer", request.referer());
        }
        if (request.title() != null && !request.title().isBlank()) {
            builder.header("X-Title", request.title());
        }
        HttpRequest httpRequest = builder
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(45))
                .build();
        return client.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private boolean shouldRetry(int status) {
        return status == 429 || status / 100 == 5;
    }

    private static String buildRequestBody(AIRequest request) {
        JsonObject root = new JsonObject();
        root.addProperty("model", normalizeModel(request.model()));
        root.addProperty("max_tokens", request.maxTokens());
        root.addProperty("temperature", request.temperature());
        JsonArray messages = new JsonArray();

        JsonObject system = new JsonObject();
        system.addProperty("role", "system");
        system.addProperty("content", request.systemPrompt());
        messages.add(system);

        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", request.userPrompt());
        messages.add(user);

        root.add("messages", messages);
        JsonObject responseFormat = new JsonObject();
        responseFormat.addProperty("type", "json_object");
        root.add("response_format", responseFormat);
        return root.toString();
    }

    private static Optional<JsonObject> parseContent(String body) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        JsonArray choices = root.getAsJsonArray("choices");
        if (choices == null || choices.isEmpty()) return Optional.empty();
        JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
        if (message == null) return Optional.empty();
        JsonElement contentElement = message.get("content");
        if (contentElement == null) return Optional.empty();
        String content;
        if (contentElement.isJsonArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonElement part : contentElement.getAsJsonArray()) {
                if (part.isJsonObject() && part.getAsJsonObject().has("text")) {
                    sb.append(part.getAsJsonObject().get("text").getAsString());
                } else if (part.isJsonPrimitive()) {
                    sb.append(part.getAsString());
                }
            }
            content = sb.toString();
        } else {
            content = contentElement.getAsString();
        }
        content = stripCodeFence(content.trim());
        try {
            return Optional.of(JsonParser.parseString(content).getAsJsonObject());
        } catch (Exception ex) {
            LOGGER.warn("Failed to parse OpenRouter JSON payload: {}", ex.toString());
            return Optional.empty();
        }
    }

    private static Optional<DecisionResponse> parseDecisionJson(JsonObject decisionJson) {
        String rawSkill = decisionJson.has("skillId") ? decisionJson.get("skillId").getAsString() : null;
        if (rawSkill == null || rawSkill.isBlank()) return Optional.empty();
        ResourceLocation skillId = ResourceLocation.tryParse(rawSkill.trim());
        if (skillId == null) return Optional.empty();
        boolean deny = decisionJson.has("deny") && decisionJson.get("deny").getAsBoolean();
        String rationale = decisionJson.has("rationale") ? decisionJson.get("rationale").getAsString() : "";
        String denyReason = decisionJson.has("denyReason") ? decisionJson.get("denyReason").getAsString() : "";
        return Optional.of(new DecisionResponse(skillId, rationale, deny, denyReason));
    }

    private static String normalizeModel(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return trimmed;
        String normalized = trimmed.replace(' ', '-');
        return normalized.toLowerCase(Locale.ROOT);
    }

    private static String stripCodeFence(String content) {
        if (content.startsWith("```")) {
            int firstNewline = content.indexOf('\n');
            if (firstNewline >= 0) {
                content = content.substring(firstNewline + 1);
            }
            int fence = content.lastIndexOf("```");
            if (fence >= 0) {
                content = content.substring(0, fence);
            }
        }
        return content.trim();
    }

    public record AIRequest(String model, String apiKey, int maxTokens, double temperature,
                            String systemPrompt, String userPrompt,
                            String referer, String title) {}

    public record DecisionResponse(ResourceLocation skillId, String rationale, boolean deny, String denyReason) {}
}
