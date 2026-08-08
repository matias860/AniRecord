package com.animelist.backend.service;

import com.animelist.backend.dto.anilist.AiringInfoResponse;
import com.animelist.backend.dto.anilist.AniListMediaResponse;
import com.animelist.backend.dto.anilist.AniListLibraryExport;
import com.animelist.backend.dto.anilist.AniListLibraryItem;
import com.animelist.backend.dto.anilist.AniListSearchResponse;
import com.animelist.backend.dto.anilist.MediaRelationResponse;
import com.animelist.backend.dto.anilist.MediaCharacterResponse;
import com.animelist.backend.dto.anilist.MediaTagResponse;
import com.animelist.backend.enums.MediaType;
import com.animelist.backend.enums.ReleaseSeason;
import com.animelist.backend.enums.ReleaseStatus;
import com.animelist.backend.enums.TrackingStatus;
import com.animelist.backend.exception.AniListClientException;
import java.net.http.HttpClient;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class AniListClient {

    private static final String SEARCH_QUERY = """
            query SearchMedia($search: String!, $type: MediaType!, $page: Int!, $perPage: Int!) {
              Page(page: $page, perPage: $perPage) {
                pageInfo {
                  currentPage
                  hasNextPage
                }
                media(search: $search, type: $type, sort: POPULARITY_DESC) {
                  id
                  type
                  title { romaji english native }
                  coverImage { large }
                  bannerImage
                  description
                  genres
                  episodes
                  chapters
                  status
                  season
                  seasonYear
                  startDate { year month day }
                  endDate { year month day }
                  nextAiringEpisode { episode airingAt timeUntilAiring }
                  tags { name rank description category isMediaSpoiler }
                }
              }
            }
            """;

    private static final String DETAIL_QUERY = """
            query MediaDetail($id: Int!) {
              Media(id: $id) {
                id
                type
                format
                title { romaji english native }
                coverImage { extraLarge large }
                bannerImage
                description
                genres
                episodes
                chapters
                status
                season
                seasonYear
                startDate { year month day }
                endDate { year month day }
                nextAiringEpisode { episode airingAt timeUntilAiring }
                tags { name rank description category isMediaSpoiler }
                relations {
                  edges {
                    relationType(version: 2)
                    node {
                      id
                      type
                      format
                      title { romaji english native }
                      coverImage { large }
                    }
                  }
                }
                characters(page: 1, perPage: 50, sort: [ROLE, RELEVANCE, ID]) {
                  pageInfo { currentPage hasNextPage }
                  edges {
                    role
                    node {
                      id
                      name { full }
                      image { large }
                    }
                  }
                }
              }
            }
            """;

    private static final String AIRING_QUERY = """
            query LibraryAiring($ids: [Int], $perPage: Int!) {
              Page(page: 1, perPage: $perPage) {
                media(id_in: $ids, type: ANIME) {
                  id
                  type
                  title { romaji english native }
                  coverImage { large }
                  nextAiringEpisode { episode airingAt timeUntilAiring }
                }
              }
            }
            """;

    private static final String CATALOG_QUERY_TEMPLATE = """
            query MediaCatalog(%s) {
              Page(page: $page, perPage: $perPage) {
                pageInfo { currentPage hasNextPage }
                media(%s) {
                  id
                  type
                  title { romaji english native }
                  coverImage { extraLarge large }
                  bannerImage
                  description
                  genres
                  episodes
                  chapters
                  status
                  season
                  seasonYear
                  startDate { year month day }
                  endDate { year month day }
                  nextAiringEpisode { episode airingAt timeUntilAiring }
                  tags { name rank description category isMediaSpoiler }
                }
              }
            }
            """;

    private static final String GENRES_QUERY = """
            query CatalogGenres { GenreCollection }
            """;

    private static final String USER_LIBRARY_QUERY = """
            query UserLibrary($userName: String!, $type: MediaType!, $chunk: Int!, $perChunk: Int!) {
              User(name: $userName) {
                name
                avatar { large }
                bannerImage
              }
              collection: MediaListCollection(userName: $userName, type: $type, chunk: $chunk, perChunk: $perChunk, forceSingleCompletedList: true) {
                lists { entries { ...LibraryEntryFields } }
              }
            }
            fragment LibraryEntryFields on MediaList {
              status
              score(format: POINT_10_DECIMAL)
              progress
              repeat
              startedAt { year month day }
              completedAt { year month day }
              media {
                id
                type
                title { romaji english native }
                coverImage { extraLarge large }
                bannerImage
                description
                genres
                episodes
                chapters
                status
                season
                seasonYear
                startDate { year month day }
                endDate { year month day }
              }
            }
            """;

    private static final String CHARACTER_PAGE_QUERY = """
            query MediaCharacters($id: Int!, $page: Int!) {
              Media(id: $id) {
                characters(page: $page, perPage: 25, sort: [ROLE, RELEVANCE, ID]) {
                  pageInfo { currentPage hasNextPage }
                  edges {
                    role
                    node {
                      id
                      name { full }
                      image { large }
                    }
                  }
                }
              }
            }
            """;

    private static final String SEASON_QUERY = """
            query CurrentSeason($season: MediaSeason!, $year: Int!, $perPage: Int!) {
              Page(page: 1, perPage: $perPage) {
                media(type: ANIME, season: $season, seasonYear: $year, sort: FAVOURITES_DESC, isAdult: false) {
                  id
                  type
                  title { romaji english native }
                  coverImage { extraLarge large }
                  bannerImage
                  description
                  genres
                  episodes
                  status
                  season
                  seasonYear
                  favourites
                  startDate { year month day }
                  endDate { year month day }
                  nextAiringEpisode { episode airingAt timeUntilAiring }
                }
              }
            }
            """;

    private final RestClient restClient;

    public AniListClient(
            @Value("${anilist.api.url:https://graphql.anilist.co}") String apiUrl,
            @Value("${anilist.api.connect-timeout:5s}") Duration connectTimeout,
            @Value("${anilist.api.read-timeout:15s}") Duration readTimeout
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);

        this.restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public AniListSearchResponse search(
            String query,
            MediaType type,
            int page,
            int perPage
    ) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("search", query);
        variables.put("type", type.name());
        variables.put("page", page);
        variables.put("perPage", perPage);

        Map<String, Object> data = execute(SEARCH_QUERY, variables);
        Map<String, Object> pageData = asMap(data.get("Page"));
        Map<String, Object> pageInfo = asMap(pageData.get("pageInfo"));
        List<AniListMediaResponse> results = asList(pageData.get("media")).stream()
                .map(this::asMap)
                .map(this::mapMedia)
                .toList();

        return new AniListSearchResponse(
                asInteger(pageInfo.get("currentPage")),
                Boolean.TRUE.equals(pageInfo.get("hasNextPage")),
                results
        );
    }

    public AniListMediaResponse getMedia(Integer anilistId) {
        Map<String, Object> data = execute(DETAIL_QUERY, Map.of("id", anilistId));
        Object mediaData = data.get("Media");
        if (mediaData == null) {
            throw new AniListClientException("AniList no encontró la obra solicitada");
        }
        Map<String, Object> media = new LinkedHashMap<>(asMap(mediaData));
        Map<String, Object> characters = new LinkedHashMap<>(asMap(media.get("characters")));
        List<Object> characterEdges = new ArrayList<>(asList(characters.get("edges")));
        Map<String, Object> pageInfo = asMap(characters.get("pageInfo"));
        int page = asInteger(pageInfo.get("currentPage")) == null ? 1 : asInteger(pageInfo.get("currentPage"));

        while (Boolean.TRUE.equals(pageInfo.get("hasNextPage"))) {
            page++;
            Map<String, Object> pageData = execute(CHARACTER_PAGE_QUERY, Map.of("id", anilistId, "page", page));
            Map<String, Object> pageMedia = asMap(pageData.get("Media"));
            Map<String, Object> pageCharacters = asMap(pageMedia.get("characters"));
            characterEdges.addAll(asList(pageCharacters.get("edges")));
            pageInfo = asMap(pageCharacters.get("pageInfo"));
        }

        characters.put("edges", characterEdges);
        media.put("characters", characters);
        return mapMedia(media);
    }

    public List<AniListMediaResponse> getAiringMedia(List<Integer> anilistIds) {
        if (anilistIds.isEmpty()) {
            return List.of();
        }

        List<AniListMediaResponse> result = new ArrayList<>();
        for (int start = 0; start < anilistIds.size(); start += 50) {
            List<Integer> batch = anilistIds.subList(start, Math.min(start + 50, anilistIds.size()));
            Map<String, Object> variables = Map.of(
                    "ids", batch,
                    "perPage", batch.size()
            );
            Map<String, Object> data = execute(AIRING_QUERY, variables);
            Map<String, Object> pageData = asMap(data.get("Page"));
            asList(pageData.get("media")).stream()
                    .map(this::asMap)
                    .map(this::mapMedia)
                    .filter(media -> media.nextAiringEpisode() != null)
                    .forEach(result::add);
        }
        return result;
    }

    public AniListSearchResponse browse(
            MediaType type, int page, int perPage, String search, Integer year,
            ReleaseSeason season, List<String> genres, ReleaseStatus status, String sort
    ) {
        Map<String, Object> variables = new LinkedHashMap<>();
        StringBuilder declarations = new StringBuilder(
                "$type: MediaType!, $page: Int!, $perPage: Int!, $sort: [MediaSort]"
        );
        StringBuilder arguments = new StringBuilder("type: $type, sort: $sort, isAdult: false");
        variables.put("type", type.name());
        variables.put("page", page);
        variables.put("perPage", perPage);
        if (search != null && !search.isBlank()) {
            declarations.append(", $search: String");
            arguments.append(", search: $search");
            variables.put("search", search.trim());
        }
        if (year != null) {
            declarations.append(", $year: String");
            arguments.append(", startDate_like: $year");
            variables.put("year", year + "%");
        }
        if (season != null) {
            declarations.append(", $season: MediaSeason");
            arguments.append(", season: $season");
            variables.put("season", season.name());
        }
        if (genres != null && !genres.isEmpty()) {
            declarations.append(", $genres: [String]");
            arguments.append(", genre_in: $genres");
            variables.put("genres", genres);
        }
        if (status != null) {
            declarations.append(", $status: MediaStatus");
            arguments.append(", status: $status");
            variables.put("status", status.name());
        }
        variables.put("sort", switch (sort) {
            case "SCORE" -> List.of("SCORE_DESC", "POPULARITY_DESC");
            case "TRENDING" -> List.of("TRENDING_DESC", "POPULARITY_DESC");
            case "NEWEST" -> List.of("START_DATE_DESC", "POPULARITY_DESC");
            default -> List.of("POPULARITY_DESC", "SCORE_DESC");
        });
        String query = CATALOG_QUERY_TEMPLATE.formatted(declarations, arguments);
        Map<String, Object> data = execute(query, variables);
        Map<String, Object> pageData = asMap(data.get("Page"));
        Map<String, Object> pageInfo = asMap(pageData.get("pageInfo"));
        return new AniListSearchResponse(
                asInteger(pageInfo.get("currentPage")),
                Boolean.TRUE.equals(pageInfo.get("hasNextPage")),
                asList(pageData.get("media")).stream().map(this::asMap).map(this::mapMedia).toList()
        );
    }

    public List<String> getGenres() {
        return asList(execute(GENRES_QUERY, Map.of()).get("GenreCollection")).stream()
                .map(String::valueOf)
                .toList();
    }

    public AniListLibraryExport getUserLibrary(String username, String accessToken) {
        Map<String, Object> user = new LinkedHashMap<>();
        Map<Integer, AniListLibraryItem> uniqueEntries = new LinkedHashMap<>();
        collectLibraryChunks(username, MediaType.ANIME, accessToken, uniqueEntries, user);
        collectLibraryChunks(username, MediaType.MANGA, accessToken, uniqueEntries, user);
        if (user.isEmpty()) {
            throw new AniListClientException("AniList no encontró el usuario solicitado");
        }
        return new AniListLibraryExport(
                asString(user.get("name")),
                asString(asMap(user.get("avatar")).get("large")),
                asString(user.get("bannerImage")),
                List.copyOf(uniqueEntries.values())
        );
    }

    private void collectLibraryChunks(String username, MediaType type, String accessToken,
            Map<Integer, AniListLibraryItem> target, Map<String, Object> userTarget) {
        final int perChunk = 500;
        for (int chunk = 1; ; chunk++) {
            Map<String, Object> data = execute(USER_LIBRARY_QUERY, Map.of(
                    "userName", username,
                    "type", type.name(),
                    "chunk", chunk,
                    "perChunk", perChunk
            ), accessToken);
            if (userTarget.isEmpty()) {
                userTarget.putAll(asMap(data.get("User")));
            }
            int sizeBefore = target.size();
            collectLibraryEntries(data.get("collection"), target);
            if (target.size() - sizeBefore < perChunk) {
                return;
            }
        }
    }

    private void collectLibraryEntries(Object collectionValue, Map<Integer, AniListLibraryItem> target) {
        for (Object listValue : asList(asMap(collectionValue).get("lists"))) {
            for (Object entryValue : asList(asMap(listValue).get("entries"))) {
                Map<String, Object> entry = asMap(entryValue);
                AniListMediaResponse media = mapMedia(asMap(entry.get("media")));
                BigDecimal score = decimalValue(entry.get("score"));
                target.putIfAbsent(media.anilistId(), new AniListLibraryItem(
                        media,
                        integerOrZero(entry.get("progress")),
                        score != null && score.signum() > 0 ? score : null,
                        integerOrZero(entry.get("repeat")),
                        mapTrackingStatus(asString(entry.get("status"))),
                        mapDate(entry.get("startedAt")),
                        mapDate(entry.get("completedAt"))
                ));
            }
        }
    }

    private TrackingStatus mapTrackingStatus(String status) {
        return switch (status == null ? "PLANNING" : status) {
            case "CURRENT", "REPEATING" -> TrackingStatus.IN_PROGRESS;
            case "COMPLETED" -> TrackingStatus.COMPLETED;
            case "PAUSED" -> TrackingStatus.PAUSED;
            case "DROPPED" -> TrackingStatus.DROPPED;
            default -> TrackingStatus.PLANNED;
        };
    }

    private BigDecimal decimalValue(Object value) {
        return value instanceof Number number ? BigDecimal.valueOf(number.doubleValue()) : null;
    }

    private int integerOrZero(Object value) {
        Integer result = asInteger(value);
        return result == null ? 0 : result;
    }

    public List<AniListMediaResponse> getCurrentSeason(int perPage) {
        LocalDate today = LocalDate.now();
        ReleaseSeason season = switch ((today.getMonthValue() - 1) / 3) {
            case 0 -> ReleaseSeason.WINTER;
            case 1 -> ReleaseSeason.SPRING;
            case 2 -> ReleaseSeason.SUMMER;
            default -> ReleaseSeason.FALL;
        };
        return getSeason(season, today.getYear(), perPage);
    }

    public List<AniListMediaResponse> getSeason(ReleaseSeason season, int year, int perPage) {
        Map<String, Object> data = execute(SEASON_QUERY, Map.of(
                "season", season.name(),
                "year", year,
                "perPage", perPage
        ));
        return asList(asMap(data.get("Page")).get("media")).stream()
                .map(this::asMap)
                .map(this::mapMedia)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> execute(String query, Map<String, Object> variables) {
        return execute(query, variables, null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> execute(String query, Map<String, Object> variables, String accessToken) {
        try {
            RestClient.RequestBodySpec request = restClient.post();
            if (accessToken != null && !accessToken.isBlank()) {
                request.header("Authorization", "Bearer " + accessToken);
            }
            Map<String, Object> response = request.body(Map.of("query", query, "variables", variables))
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                throw new AniListClientException("AniList devolvió una respuesta vacía");
            }
            if (response.get("errors") instanceof List<?> errors && !errors.isEmpty()) {
                Map<String, Object> firstError = asMap(errors.get(0));
                throw new AniListClientException(
                        "AniList rechazó la consulta: " + firstError.getOrDefault("message", "error desconocido")
                );
            }
            return asMap(response.get("data"));
        } catch (AniListClientException exception) {
            throw exception;
        } catch (HttpStatusCodeException exception) {
            if (exception.getStatusCode().value() == 429) {
                throw new AniListClientException(
                        "AniList alcanzó temporalmente su límite de solicitudes. Intenta nuevamente en un minuto.",
                        exception
                );
            }
            throw new AniListClientException(
                    "AniList respondió con el estado HTTP " + exception.getStatusCode().value(),
                    exception
            );
        } catch (ResourceAccessException exception) {
            throw new AniListClientException(
                    "AniList tardó demasiado en responder o no se pudo establecer la conexión",
                    exception
            );
        } catch (RestClientException exception) {
            throw new AniListClientException("No se pudo conectar con AniList", exception);
        }
    }

    private AniListMediaResponse mapMedia(Map<String, Object> media) {
        MediaType mediaType = enumValue(MediaType.class, asString(media.get("type")));
        Map<String, Object> coverImage = asMap(media.get("coverImage"));
        Map<String, Object> titles = asMap(media.get("title"));
        String cover = firstNonBlank(
                asString(coverImage.get("extraLarge")),
                asString(coverImage.get("large"))
        );
        Integer totalUnits = mediaType == MediaType.ANIME
                ? asInteger(media.get("episodes"))
                : asInteger(media.get("chapters"));

        return new AniListMediaResponse(
                asInteger(media.get("id")),
                mediaType,
                selectTitle(titles),
                asString(titles.get("romaji")),
                cover,
                asString(media.get("bannerImage")),
                asString(media.get("description")),
                totalUnits,
                mapDate(media.get("startDate")),
                mapDate(media.get("endDate")),
                enumValue(ReleaseStatus.class, asString(media.get("status"))),
                enumValue(ReleaseSeason.class, asString(media.get("season"))),
                asInteger(media.get("seasonYear")),
                asInteger(media.get("favourites")),
                mapAiring(media.get("nextAiringEpisode")),
                mapRelations(media.get("relations")),
                mapCharacters(media.get("characters")),
                asList(media.get("genres")).stream().map(String::valueOf).toList(),
                mapTags(media.get("tags"))
        );
    }

    private List<MediaTagResponse> mapTags(Object tagsValue) {
        return asList(tagsValue).stream()
                .map(this::asMap)
                .filter(tag -> !Boolean.TRUE.equals(tag.get("isMediaSpoiler")))
                .sorted((first, second) -> Integer.compare(
                        asInteger(second.get("rank")) == null ? 0 : asInteger(second.get("rank")),
                        asInteger(first.get("rank")) == null ? 0 : asInteger(first.get("rank"))))
                .limit(5)
                .map(tag -> new MediaTagResponse(
                        asString(tag.get("name")),
                        asInteger(tag.get("rank")),
                        asString(tag.get("description")),
                        asString(tag.get("category"))))
                .toList();
    }

    private List<MediaCharacterResponse> mapCharacters(Object charactersValue) {
        return asList(asMap(charactersValue).get("edges")).stream()
                .map(this::asMap)
                .filter(edge -> "MAIN".equals(asString(edge.get("role"))))
                .map(edge -> {
                    Map<String, Object> node = asMap(edge.get("node"));
                    return new MediaCharacterResponse(
                            asInteger(node.get("id")),
                            asString(asMap(node.get("name")).get("full")),
                            asString(asMap(node.get("image")).get("large")),
                            asString(edge.get("role"))
                    );
                })
                .toList();
    }

    private List<MediaRelationResponse> mapRelations(Object relationsValue) {
        Map<String, Object> relations = asMap(relationsValue);
        return asList(relations.get("edges")).stream()
                .map(this::asMap)
                .map(edge -> {
                    Map<String, Object> node = asMap(edge.get("node"));
                    Map<String, Object> cover = asMap(node.get("coverImage"));
                    return new MediaRelationResponse(
                            asInteger(node.get("id")),
                            asString(edge.get("relationType")),
                            enumValue(MediaType.class, asString(node.get("type"))),
                            asString(node.get("format")),
                            selectTitle(asMap(node.get("title"))),
                            asString(cover.get("large"))
                    );
                })
                .toList();
    }

    private AiringInfoResponse mapAiring(Object airingValue) {
        if (airingValue == null) {
            return null;
        }
        Map<String, Object> airing = asMap(airingValue);
        return new AiringInfoResponse(
                asInteger(airing.get("episode")),
                asLong(airing.get("airingAt")),
                asLong(airing.get("timeUntilAiring"))
        );
    }

    private LocalDate mapDate(Object dateValue) {
        Map<String, Object> date = asMap(dateValue);
        Integer year = asInteger(date.get("year"));
        Integer month = asInteger(date.get("month"));
        Integer day = asInteger(date.get("day"));
        if (year == null || month == null || day == null) {
            return null;
        }
        try {
            return LocalDate.of(year, month, day);
        } catch (DateTimeException ignored) {
            return null;
        }
    }

    private String selectTitle(Map<String, Object> titles) {
        return firstNonBlank(
                asString(titles.get("english")),
                asString(titles.get("romaji")),
                asString(titles.get("native")),
                "Sin título"
        );
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer asInteger(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private Long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Collections.emptyMap();
    }

    private List<?> asList(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private <E extends Enum<E>> E enumValue(Class<E> enumType, String value) {
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
