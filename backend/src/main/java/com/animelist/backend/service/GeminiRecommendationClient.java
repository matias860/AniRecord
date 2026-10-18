package com.animelist.backend.service;

import com.animelist.backend.dto.assistant.RecommendationIntent;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class GeminiRecommendationClient {
    private static final Semaphore CONCURRENCY = new Semaphore(2);
    private final String apiKey;
    private final String model;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public GeminiRecommendationClient(
            @Value("${app.gemini.api-key:}") String apiKey,
            @Value("${app.gemini.model:gemini-3.1-flash-lite}") String model,
            ObjectMapper objectMapper
    ) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model != null && model.matches("[a-zA-Z0-9._-]{1,80}") ? model : "gemini-3.1-flash-lite";
        this.objectMapper = objectMapper;
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com")
                .requestFactory(factory).build();
    }

    public boolean available() { return !apiKey.isBlank(); }

    public RecommendationIntent interpret(String text, RecommendationIntent previous, List<String> genres, List<String> tags) {
        if (!available()) throw new IllegalStateException("Gemini is not configured");
        String instruction = """
                Convertí el pedido en filtros de anime o manga. Respondé solamente JSON, sin markdown.
                No recomiendes títulos. No inventes valores. Conservá filtros anteriores solo cuando el nuevo mensaje sea un refinamiento.
                Respetá de forma estricta las negaciones: "sin", "evitá", "nada de" y expresiones equivalentes van en excludedGenres o excludedTags, nunca en los filtros incluidos.
                Si el usuario corrige con "no, quiero...", tratá el mensaje como un pedido nuevo. No devuelvas el mismo valor simultáneamente como incluido y excluido.
                Campos: mediaType ANIME|MANGA|null (null significa ambos), genres[], tags[], excludedGenres[], excludedTags[], status FINISHED|RELEASING|NOT_YET_RELEASED|CANCELLED|HIATUS|null,
                country JP|KR|CN|null, format TV|TV_SHORT|MOVIE|SPECIAL|OVA|ONA|MUSIC|MANGA|NOVEL|ONE_SHOT|null,
                sort POPULARITY|SCORE|TRENDING|NEWEST, yearFrom, yearTo, season WINTER|SPRING|SUMMER|FALL|null,
                minimumUnits, maximumUnits, referenceTitle, libraryOnly, libraryStatus PLANNED|IN_PROGRESS|PAUSED|COMPLETED|DROPPED|null.
                Para estados de ánimo elegí géneros o etiquetas equivalentes. Usá únicamente valores de las listas permitidas.
                """;
        String payload;
        try {
            payload = objectMapper.writeValueAsString(Map.of(
                    "pedido", text,
                    "filtrosAnteriores", previous == null ? RecommendationIntent.empty() : previous,
                    "generosPermitidos", genres.stream().limit(30).toList(),
                    "etiquetasPermitidas", tags));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not prepare Gemini request", exception);
        }
        Map<String, Object> body = Map.of(
                "system_instruction", Map.of("parts", List.of(Map.of("text", instruction))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", payload)))),
                "generationConfig", Map.of("temperature", 0.1, "responseMimeType", "application/json", "maxOutputTokens", 1000));
        if (!CONCURRENCY.tryAcquire()) throw new BusyException();
        try {
            JsonNode response = restClient.post()
                    .uri("/v1beta/models/{model}:generateContent", model)
                    .header("x-goog-api-key", apiKey)
                    .body(body).retrieve().body(JsonNode.class);
            JsonNode content = response == null ? null : response.at("/candidates/0/content/parts/0/text");
            if (content == null || !content.isTextual()) throw new IllegalStateException("Gemini returned no structured result");
            return objectMapper.readValue(content.asText(), RecommendationIntent.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Gemini could not interpret the request", exception);
        } finally {
            CONCURRENCY.release();
        }
    }

    public static final class BusyException extends RuntimeException {
        public BusyException() { super("Gemini is busy"); }
    }
}
