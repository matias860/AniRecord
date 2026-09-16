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

/** Authenticated, bounded requests for the explicit export flow. Never retries writes. */
@Service
public class AniListExportClient {
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
        variables.put("id", entryId);
        Map<String, Object> data = request(token, """
            mutation($id: Int, $media: Int!, $status: MediaListStatus!, $progress: Int!, $score: Int!) {
              SaveMediaListEntry(id: $id, mediaId: $media, status: $status, progress: $progress, scoreRaw: $score) {
                mediaId status progress score(format: POINT_100)
              }
            }
            """, variables);
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
            if (body == null || body.get("errors") instanceof List<?> errors && !errors.isEmpty()
                    || !(body.get("data") instanceof Map<?, ?>)) throw invalid();
            return (Map<String, Object>) body.get("data");
        } catch (RestClientException exception) {
            throw new BusinessException("AniList no pudo completar la solicitud. Espera un minuto y vuelve a revisar los cambios; si persiste, inicia sesión de nuevo con AniList.");
        }
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
