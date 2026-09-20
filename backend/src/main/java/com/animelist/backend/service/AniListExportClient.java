package com.animelist.backend.service;

import com.animelist.backend.exception.BusinessException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Authenticated, bounded requests for the explicit export flow. Never retries writes. */
@Service
public class AniListExportClient {
    private static final Logger log = LoggerFactory.getLogger(AniListExportClient.class);
    private final RestClient client;
    private long nextRequest;
    public record State(String status, int progress, int score) {}
    public record Remote(int id, State value) {}

    public AniListExportClient(@Value("${anilist.api.url:https://graphql.anilist.co}") String url) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        factory.setReadTimeout(Duration.ofSeconds(25));
        client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    public Map<Integer, Remote> read(String token, Integer userId) {
        Map<Integer, Remote> result = new HashMap<>();
        for (int page = 1; page <= 200; page++) {
            Map<String, Object> data = request(token, """
                query($user: Int!, $page: Int!) {
                  Page(page: $page, perPage: 50) {
                    pageInfo { hasNextPage }
                    mediaList(userId: $user, type: ANIME) { id mediaId status progress score(format: POINT_100) }
                  }
                }
                """, Map.of("user", userId, "page", page));
            Map<?, ?> response = (Map<?, ?>) data.get("Page");
            if (response == null || !(response.get("mediaList") instanceof List<?> entries)) throw invalid();
            for (Object entry : entries) {
                Map<?, ?> item = (Map<?, ?>) entry;
                result.put(((Number) item.get("mediaId")).intValue(), remote(item));
            }
            if (response.get("pageInfo") instanceof Map<?, ?> info && Boolean.FALSE.equals(info.get("hasNextPage"))) return result;
        }
        throw new BusinessException("La lista supera el tamaño admitido para esta sincronización.");
    }

    public Remote readEntry(String token, Integer userId, int mediaId) {
        Map<String, Object> data = request(token, """
            query($user: Int!, $media: Int!) {
              Page(page: 1, perPage: 1) {
                mediaList(userId: $user, mediaId: $media, type: ANIME) { id status progress score(format: POINT_100) }
              }
            }
            """, Map.of("user", userId, "media", mediaId));
        if (!(data.get("Page") instanceof Map<?, ?> page) || !(page.get("mediaList") instanceof List<?> entries)) throw invalid();
        return entries.isEmpty() ? null : remote((Map<?, ?>) entries.get(0));
    }

    public void save(String token, int mediaId, Integer entryId, State value) {
        Map<String, Object> variables = new HashMap<>(Map.of("media", mediaId, "status", value.status(), "progress", value.progress(), "score", value.score()));
        if (entryId != null) variables.put("id", entryId);
        String mutation = """
            mutation(%s$media: Int!, $status: MediaListStatus!, $progress: Int!, $score: Int!) {
              SaveMediaListEntry(%smediaId: $media, status: $status, progress: $progress, scoreRaw: $score) {
                mediaId status progress score(format: POINT_100)
              }
            }
            """.formatted(entryId == null ? "" : "$id: Int!, ", entryId == null ? "" : "id: $id, ");
        Map<String, Object> data = request(token, mutation, variables);
        if (!(data.get("SaveMediaListEntry") instanceof Map<?, ?> saved)
                || !Objects.equals(saved.get("mediaId"), mediaId) || !value.equals(state(saved))) throw invalid();
    }

    private static State state(Map<?, ?> value) {
        if (!(value.get("status") instanceof String status) || !(value.get("progress") instanceof Number progress)
                || !(value.get("score") instanceof Number score)) throw invalid();
        return new State(status, progress.intValue(), (int) Math.round(score.doubleValue()));
    }

    private static Remote remote(Map<?, ?> value) {
        if (!(value.get("id") instanceof Number id)) throw invalid();
        return new Remote(id.intValue(), state(value));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> request(String token, String query, Map<String, Object> variables) {
        reserveRequest();
        try {
            Map<?, ?> body = client.post().header("Authorization", "Bearer " + token)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(Map.of("query", query, "variables", variables)).retrieve().body(Map.class);
            if (body != null && body.get("errors") instanceof List<?> errors && !errors.isEmpty()) {
                String validation = validationDetail(body, token);
                if (errors.stream().anyMatch(error -> error instanceof Map<?, ?> detail
                        && ("validation".equals(detail.get("message")) || detail.get("status") instanceof Number status && status.intValue() == 400)))
                    throw new BusinessException("AniList rechazó esta entrada (400): " + validation);
                for (Object error : errors) {
                    if (error instanceof Map<?, ?> detail && detail.get("status") instanceof Number status) {
                        log.warn("AniList export GraphQL failure: status={}, media={}", status, variables.get("media"));
                        throw upstreamFailure(status.intValue(), null);
                    }
                }
                log.warn("AniList export GraphQL failure without status: media={}", variables.get("media"));
                throw invalid();
            }
            if (body == null || !(body.get("data") instanceof Map<?, ?>)) throw invalid();
            return (Map<String, Object>) body.get("data");
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            String detail = validationDetail(exception, token);
            log.warn("AniList export HTTP failure: status={}, operation={}, media={}, detail={}", status,
                    query.stripLeading().startsWith("mutation") ? "save" : "read", variables.get("media"), detail);
            if (status == 400 && !detail.isBlank())
                throw new BusinessException("AniList rechazó esta entrada (400): " + detail);
            String retryAfter = exception.getResponseHeaders() == null ? null : exception.getResponseHeaders().getFirst("Retry-After");
            throw upstreamFailure(status, retryAfter);
        } catch (RestClientException exception) {
            log.warn("AniList export connection failure: type={}, media={}", exception.getClass().getSimpleName(), variables.get("media"));
            throw new BusinessException("Se interrumpió la comunicación con AniList. La última operación puede haberse guardado: revisa los cambios de nuevo antes de continuar.");
        }
    }

    private static String validationDetail(RestClientResponseException exception, String token) {
        if (exception.getStatusCode().value() != 400) return "";
        try {
            Map<?, ?> body = exception.getResponseBodyAs(Map.class);
            return validationDetail(body, token);
        } catch (RuntimeException ignored) {
            return ""; // Non-JSON responses must not hide the original HTTP status.
        }
    }

    private static String validationDetail(Map<?, ?> body, String token) {
        if (body == null || !(body.get("errors") instanceof List<?> errors)) return "";
        List<String> messages = new ArrayList<>();
        for (Object error : errors) {
            if (!(error instanceof Map<?, ?> detail)) continue;
            Object validation = detail.get("validation");
            if (validation == null && detail.get("extensions") instanceof Map<?, ?> extensions)
                validation = extensions.get("validation");
            if (validation instanceof Map<?, ?> fields) {
                for (var field : fields.entrySet()) {
                    if (!(field.getKey() instanceof String name)) continue;
                    List<?> reasons = field.getValue() instanceof List<?> list ? list : Collections.singletonList(field.getValue());
                    for (Object reason : reasons) {
                        if (reason instanceof String message && messages.size() < 5)
                            messages.add(safeDetail(name + ": " + message, token));
                    }
                }
            } else if (detail.get("message") instanceof String message && messages.size() < 5) {
                messages.add(safeDetail(message, token));
            }
            if (messages.size() >= 5) break;
        }
        return String.join("; ", messages);
    }

    private static String safeDetail(String message, String token) {
        String safe = message.replace(token, "[redacted]").replaceAll("[\\p{Cntrl}]", " ");
        return safe.substring(0, Math.min(300, safe.length()));
    }

    private static BusinessException upstreamFailure(int status, String retryAfter) {
        if (status == 429) {
            long seconds = 60;
            try { if (retryAfter != null) seconds = Math.max(1, Long.parseLong(retryAfter)); }
            catch (NumberFormatException ignored) { /* Use the default AniList cooldown. */ }
            return new BusinessException("AniList alcanzó su límite de solicitudes. Espera " + seconds + " segundos y revisa los cambios de nuevo para continuar con los pendientes.");
        }
        if (status == 401) return new BusinessException("AniList rechazó la autorización. Inicia sesión de nuevo con AniList y vuelve a revisar los cambios.");
        if (status == 403) return new BusinessException("AniList denegó esta solicitud (403). Revisa el acceso a tu cuenta antes de volver a intentarlo.");
        if (status >= 500) return new BusinessException("AniList tiene un problema temporal (" + status + "). Espera un minuto y revisa los cambios de nuevo; la última operación puede haberse guardado.");
        return new BusinessException("AniList rechazó la solicitud (" + status + "). Revisa los datos del anime indicado antes de continuar.");
    }

    private synchronized void reserveRequest() {
        long delay = nextRequest - System.currentTimeMillis();
        if (delay > 0) {
            try { Thread.sleep(delay); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new BusinessException("Sincronización interrumpida."); }
        }
        // Stay below AniList's degraded 30 requests/minute limit for this flow.
        nextRequest = System.currentTimeMillis() + 2500;
    }

    private static BusinessException invalid() {
        return new BusinessException("AniList no confirmó la operación. Vuelve a revisar los cambios antes de continuar.");
    }
}
