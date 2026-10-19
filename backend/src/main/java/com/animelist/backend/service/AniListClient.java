package com.animelist.backend.service;

import com.animelist.backend.dto.anilist.AiringInfoResponse;
import com.animelist.backend.dto.anilist.AniListMediaResponse;
import com.animelist.backend.dto.anilist.AniListLibraryExport;
import com.animelist.backend.dto.anilist.AniListLibraryItem;
import com.animelist.backend.dto.anilist.AniListPersonFavorite;
import com.animelist.backend.dto.anilist.AniListSearchResponse;
import com.animelist.backend.dto.anilist.MediaRelationResponse;
import com.animelist.backend.dto.anilist.MediaRelationsResponse;
import com.animelist.backend.dto.anilist.MediaCharacterResponse;
import com.animelist.backend.dto.anilist.MediaCreditResponse;
import com.animelist.backend.dto.anilist.MediaTagResponse;
import com.animelist.backend.dto.anilist.MediaStreamingLinkResponse;
import com.animelist.backend.dto.anilist.CharacterDetailResponse;
import com.animelist.backend.dto.anilist.CatalogCategoriesResponse;
import com.animelist.backend.dto.anilist.CatalogTagResponse;
import com.animelist.backend.dto.anilist.PersonMediaResponse;
import com.animelist.backend.dto.anilist.StaffCharacterResponse;
import com.animelist.backend.dto.anilist.StaffDetailResponse;
import com.animelist.backend.dto.anilist.VoiceActorResponse;
import com.animelist.backend.enums.MediaType;
import com.animelist.backend.enums.PersonType;
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
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class AniListClient {

    private static final String RELATIONS_QUERY = """
            query MediaRelations($ids: [Int!]!, $perPage: Int!) {
              Page(page: 1, perPage: $perPage) {
                media(id_in: $ids) {
                  id
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
                }
              }
            }
            """;

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
                  format
                  countryOfOrigin
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
                  volumes
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
                countryOfOrigin
                title { romaji english native }
                coverImage { extraLarge large }
                bannerImage
                externalLinks { site url type language color icon notes isDisabled }
                trailer { id site }
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
                volumes
                staff(page: 1, perPage: 40, sort: [RELEVANCE, ID]) {
                  edges {
                    role
                    node { id name { full } image { large } }
                  }
                }
                studios {
                  edges {
                    isMain
                    node { id name isAnimationStudio }
                  }
                }
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
                mainCharacters: characters(page: 1, perPage: 50, role: MAIN, sort: [RELEVANCE, ID]) {
                  edges {
                    role
                    node {
                      id
                      name { full }
                      image { large }
                    }
                    voiceActors(language: JAPANESE, sort: [RELEVANCE, ID]) {
                      id name { full native } image { large } languageV2
                    }
                  }
                }
                supportingCharacters: characters(page: 1, perPage: 50, role: SUPPORTING, sort: [RELEVANCE, ID]) {
                  edges {
                    role
                    node {
                      id
                      name { full }
                      image { large }
                    }
                    voiceActors(language: JAPANESE, sort: [RELEVANCE, ID]) {
                      id name { full native } image { large } languageV2
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
                  format
                  countryOfOrigin
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
                  format
                  countryOfOrigin
                  title { romaji english native }
                  coverImage { large }
                  genres
                  startDate { year }
                  episodes
                  chapters
                  status
                  season
                  seasonYear
                  tags { name rank description category isMediaSpoiler }
                  volumes
                }
              }
            }
            """;
    private static final String MIXED_ASSISTANT_QUERY_TEMPLATE = """
            query MixedAssistantCatalog(%s) {
              anime: Page(page: $page, perPage: $perPage) {
                pageInfo { currentPage hasNextPage }
                media(type: ANIME, %s) { ...AssistantMediaFields }
              }
              manga: Page(page: $page, perPage: $perPage) {
                pageInfo { currentPage hasNextPage }
                media(type: MANGA, %s) { ...AssistantMediaFields }
              }
            }
            fragment AssistantMediaFields on Media {
              id
              type
              format
              countryOfOrigin
              title { romaji english native }
              coverImage { large }
              genres
              startDate { year }
              episodes
              chapters
              status
              season
              seasonYear
              tags { name rank description category isMediaSpoiler }
              volumes
            }
            """;

    private static final String LIBRARY_MEDIA_QUERY = """
            query LibraryMedia($id: Int!) {
              Media(id: $id) {
                id
                type
                format
                countryOfOrigin
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
                volumes
              }
            }
            """;

    private static final String CATEGORIES_QUERY = """
            query CatalogCategories {
              GenreCollection
              MediaTagCollection { name description category isAdult }
            }
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
              progressVolumes
              repeat
              startedAt { year month day }
              completedAt { year month day }
              media {
                id
                type
                format
                countryOfOrigin
                title { romaji english native }
                coverImage { large }
                bannerImage
                description
                genres
                tags { name rank description category isMediaSpoiler }
                volumes
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

    private static final String USER_PERSON_FAVOURITES_QUERY = """
            query UserPersonFavourites($userName: String!, $characterPage: Int!, $staffPage: Int!, $perPage: Int!) {
              User(name: $userName) {
                favourites {
                  characters(page: $characterPage, perPage: $perPage) {
                    pageInfo { hasNextPage }
                    edges { node { id name { full native } image { large } } }
                  }
                  staff(page: $staffPage, perPage: $perPage) {
                    pageInfo { hasNextPage }
                    edges { node { id name { full native } image { large } } }
                  }
                }
              }
            }
            """;

    private static final String CHARACTER_DETAIL_QUERY = """
            query CharacterDetail($id: Int!) {
              Character(id: $id) {
                id name { full native } image { large } description gender age bloodType
                dateOfBirth { year month day } favourites
                media(page: 1, perPage: 30, sort: [POPULARITY_DESC]) {
                  edges {
                    characterRole
                    node { id type title { english romaji native } coverImage { large } }
                    voiceActors(language: JAPANESE, sort: [RELEVANCE, ID]) {
                      id name { full native } image { large } languageV2
                    }
                  }
                }
              }
            }
            """;

    private static final String STAFF_DETAIL_QUERY = """
            query StaffDetail($id: Int!) {
              Staff(id: $id) {
                id name { full native } image { large } description languageV2
                primaryOccupations gender age dateOfBirth { year month day }
                homeTown favourites
                staffMedia(page: 1, perPage: 50, sort: [POPULARITY_DESC]) {
                  edges {
                    staffRole
                    node { id type title { english romaji native } coverImage { large } }
                  }
                }
                characters(page: 1, perPage: 30, sort: [FAVOURITES_DESC]) {
                  edges {
                    role
                    node { id name { full } image { large } }
                    media { id type title { english romaji native } coverImage { large } }
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
                  coverImage { large }
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

    @Cacheable("mediaDetails")
    public AniListMediaResponse getMedia(Integer anilistId) {
        Map<String, Object> data = execute(DETAIL_QUERY, Map.of("id", anilistId));
        Object mediaData = data.get("Media");
        if (mediaData == null) {
            throw new AniListClientException("AniList no encontró la obra solicitada");
        }
        Map<String, Object> media = new LinkedHashMap<>(asMap(mediaData));
        List<Object> characterEdges = new ArrayList<>(asList(asMap(media.get("mainCharacters")).get("edges")));
        characterEdges.addAll(asList(asMap(media.get("supportingCharacters")).get("edges")));
        media.put("characters", Map.of("edges", characterEdges));
        return mapMedia(media, true, true);
    }

    @Cacheable(value = "mediaDetails", key = "'library:' + #anilistId")
    public AniListMediaResponse getMediaForLibrary(Integer anilistId) {
        Object media = execute(LIBRARY_MEDIA_QUERY, Map.of("id", anilistId)).get("Media");
        if (media == null) throw new AniListClientException("AniList no encontró la obra solicitada");
        return mapMedia(asMap(media), true, false);
    }

    public CharacterDetailResponse getCharacter(Integer anilistId) {
        Map<String, Object> character = asMap(execute(CHARACTER_DETAIL_QUERY, Map.of("id", anilistId)).get("Character"));
        if (character.isEmpty()) throw new AniListClientException("AniList no encontró el personaje solicitado");
        List<Map<String, Object>> edges = asList(asMap(character.get("media")).get("edges")).stream().map(this::asMap).toList();
        List<VoiceActorResponse> voiceActors = edges.stream()
                .flatMap(edge -> asList(edge.get("voiceActors")).stream())
                .map(this::asMap).map(this::mapVoiceActor)
                .filter(actor -> actor.anilistId() != null)
                .collect(java.util.stream.Collectors.toMap(VoiceActorResponse::anilistId, actor -> actor, (first, ignored) -> first, LinkedHashMap::new))
                .values().stream().toList();
        List<PersonMediaResponse> media = edges.stream().map(edge -> mapPersonMedia(asMap(edge.get("node")), asString(edge.get("characterRole")))).toList();
        Map<String, Object> name = asMap(character.get("name"));
        return new CharacterDetailResponse(asInteger(character.get("id")), asString(name.get("full")), asString(name.get("native")),
                asString(asMap(character.get("image")).get("large")), asString(character.get("description")), asString(character.get("gender")),
                asString(character.get("age")), formatFuzzyDate(character.get("dateOfBirth")), asString(character.get("bloodType")),
                asInteger(character.get("favourites")), voiceActors, media);
    }

    @Cacheable("staffDetails")
    public StaffDetailResponse getStaff(Integer anilistId) {
        Map<String, Object> staff = asMap(execute(STAFF_DETAIL_QUERY, Map.of("id", anilistId)).get("Staff"));
        if (staff.isEmpty()) throw new AniListClientException("AniList no encontró al profesional solicitado");
        List<StaffCharacterResponse> characters = asList(asMap(staff.get("characters")).get("edges")).stream().map(this::asMap).map(edge -> {
            Map<String, Object> node = asMap(edge.get("node"));
            List<PersonMediaResponse> media = asList(edge.get("media")).stream().map(this::asMap).limit(6).map(item -> mapPersonMedia(item, asString(edge.get("role")))).toList();
            return new StaffCharacterResponse(asInteger(node.get("id")), asString(asMap(node.get("name")).get("full")),
                    asString(asMap(node.get("image")).get("large")), asString(edge.get("role")), media);
        }).toList();
        List<PersonMediaResponse> works = new ArrayList<>(asList(asMap(staff.get("staffMedia")).get("edges")).stream()
                .map(this::asMap)
                .filter(edge -> asInteger(asMap(edge.get("node")).get("id")) != null)
                .collect(java.util.stream.Collectors.toMap(
                        edge -> asInteger(asMap(edge.get("node")).get("id")),
                        edge -> mapPersonMedia(asMap(edge.get("node")), asString(edge.get("staffRole"))),
                        (first, ignored) -> first,
                        LinkedHashMap::new))
                .values()).stream().limit(30).toList();
        Map<String, Object> name = asMap(staff.get("name"));
        return new StaffDetailResponse(asInteger(staff.get("id")), asString(name.get("full")), asString(name.get("native")),
                asString(asMap(staff.get("image")).get("large")), asString(staff.get("description")), asString(staff.get("languageV2")),
                asList(staff.get("primaryOccupations")).stream().map(String::valueOf).toList(), asString(staff.get("gender")),
                asInteger(staff.get("age")), formatFuzzyDate(staff.get("dateOfBirth")), asString(staff.get("homeTown")),
                asInteger(staff.get("favourites")), characters, works);
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

    public List<MediaRelationsResponse> getMediaRelations(List<Integer> anilistIds) {
        if (anilistIds == null || anilistIds.isEmpty()) return List.of();

        List<MediaRelationsResponse> result = new ArrayList<>();
        for (int start = 0; start < anilistIds.size(); start += 50) {
            List<Integer> batch = anilistIds.subList(start, Math.min(start + 50, anilistIds.size()));
            Map<String, Object> data = execute(RELATIONS_QUERY, Map.of("ids", batch, "perPage", batch.size()));
            asList(asMap(data.get("Page")).get("media")).stream()
                    .map(this::asMap)
                    .map(media -> new MediaRelationsResponse(
                            asInteger(media.get("id")),
                            mapRelations(media.get("relations"))))
                    .forEach(result::add);
        }
        return result;
    }

    @Cacheable("catalog")
    public AniListSearchResponse browse(
            MediaType type, int page, int perPage, String search, Integer year,
            ReleaseSeason season, List<String> genres, List<String> tags, ReleaseStatus status, String country, String format, String sort
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
        if (tags != null && !tags.isEmpty()) {
            declarations.append(", $tags: [String]");
            arguments.append(", tag_in: $tags");
            variables.put("tags", tags);
        }
        if (status != null) {
            declarations.append(", $status: MediaStatus");
            arguments.append(", status: $status");
            variables.put("status", status.name());
        }
        if (country != null && !country.isBlank()) {
            declarations.append(", $country: CountryCode");
            arguments.append(", countryOfOrigin: $country");
            variables.put("country", country.trim().toUpperCase());
        }
        if (format != null && !format.isBlank()) {
            declarations.append(", $format: MediaFormat");
            arguments.append(", format: $format");
            variables.put("format", format.trim().toUpperCase());
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

    @Cacheable("catalog")
    public AniListSearchResponse browseForAssistant(
            MediaType type, int perPage, Integer yearFrom, Integer yearTo,
            ReleaseSeason season, List<String> genres, List<String> tags,
            ReleaseStatus status, String country, String format, String sort
    ) {
        Map<String, Object> variables = new LinkedHashMap<>();
        StringBuilder declarations = new StringBuilder("$page: Int!, $perPage: Int!, $sort: [MediaSort]");
        StringBuilder arguments = new StringBuilder("sort: $sort, isAdult: false");
        if (type != null) {
            declarations.append(", $type: MediaType!");
            arguments.insert(0, "type: $type, ");
            variables.put("type", type.name());
        }
        variables.put("page", 1);
        variables.put("perPage", Math.max(1, Math.min(50, perPage)));
        if (yearFrom != null) {
            declarations.append(", $startAfter: FuzzyDateInt");
            arguments.append(", startDate_greater: $startAfter");
            variables.put("startAfter", yearFrom * 10_000 + 100);
        }
        if (yearTo != null) {
            declarations.append(", $startBefore: FuzzyDateInt");
            arguments.append(", startDate_lesser: $startBefore");
            variables.put("startBefore", yearTo * 10_000 + 12_32);
        }
        if (season != null) { declarations.append(", $season: MediaSeason"); arguments.append(", season: $season"); variables.put("season", season.name()); }
        if (genres != null && !genres.isEmpty()) { declarations.append(", $genres: [String]"); arguments.append(", genre_in: $genres"); variables.put("genres", genres); }
        if (tags != null && !tags.isEmpty()) { declarations.append(", $tags: [String]"); arguments.append(", tag_in: $tags"); variables.put("tags", tags); }
        if (status != null) { declarations.append(", $status: MediaStatus"); arguments.append(", status: $status"); variables.put("status", status.name()); }
        if (country != null && !country.isBlank()) { declarations.append(", $country: CountryCode"); arguments.append(", countryOfOrigin: $country"); variables.put("country", country); }
        if (format != null && !format.isBlank()) { declarations.append(", $format: MediaFormat"); arguments.append(", format: $format"); variables.put("format", format); }
        variables.put("sort", switch (sort == null ? "POPULARITY" : sort) {
            case "SCORE" -> List.of("SCORE_DESC", "POPULARITY_DESC");
            case "TRENDING" -> List.of("TRENDING_DESC", "POPULARITY_DESC");
            case "NEWEST" -> List.of("START_DATE_DESC", "POPULARITY_DESC");
            default -> List.of("POPULARITY_DESC", "SCORE_DESC");
        });
        String query = type == null
                ? MIXED_ASSISTANT_QUERY_TEMPLATE.formatted(declarations, arguments, arguments)
                : CATALOG_QUERY_TEMPLATE.formatted(declarations, arguments);
        Map<String, Object> data = execute(query, variables);
        if (type == null) {
            Map<String, Object> anime = asMap(data.get("anime"));
            Map<String, Object> manga = asMap(data.get("manga"));
            List<AniListMediaResponse> results = new ArrayList<>();
            results.addAll(asList(anime.get("media")).stream().map(this::asMap).map(item -> mapMedia(item, true)).toList());
            results.addAll(asList(manga.get("media")).stream().map(this::asMap).map(item -> mapMedia(item, true)).toList());
            boolean hasNextPage = Boolean.TRUE.equals(asMap(anime.get("pageInfo")).get("hasNextPage"))
                    || Boolean.TRUE.equals(asMap(manga.get("pageInfo")).get("hasNextPage"));
            return new AniListSearchResponse(1, hasNextPage, List.copyOf(results));
        }
        Map<String, Object> pageData = asMap(data.get("Page"));
        Map<String, Object> pageInfo = asMap(pageData.get("pageInfo"));
        return new AniListSearchResponse(asInteger(pageInfo.get("currentPage")), Boolean.TRUE.equals(pageInfo.get("hasNextPage")),
                asList(pageData.get("media")).stream().map(this::asMap).map(item -> mapMedia(item, true)).toList());
    }

    @Cacheable("catalogCategories")
    public CatalogCategoriesResponse getCategories() {
        Map<String, Object> data = execute(CATEGORIES_QUERY, Map.of());
        List<String> genres = asList(data.get("GenreCollection")).stream()
                .map(String::valueOf)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        List<CatalogTagResponse> tags = asList(data.get("MediaTagCollection")).stream()
                .map(this::asMap)
                .filter(tag -> !Boolean.TRUE.equals(tag.get("isAdult")))
                .map(tag -> new CatalogTagResponse(
                        asString(tag.get("name")),
                        asString(tag.get("description")),
                        asString(tag.get("category"))))
                .filter(tag -> tag.name() != null && !tag.name().isBlank())
                .sorted(java.util.Comparator.comparing(CatalogTagResponse::category,
                                java.util.Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(CatalogTagResponse::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return new CatalogCategoriesResponse(genres, tags);
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

    public List<AniListPersonFavorite> getUserPersonFavorites(String username, String accessToken) {
        final int perPage = 25;
        int characterPage = 1;
        int staffPage = 1;
        boolean moreCharacters = true;
        boolean moreStaff = true;
        int characterOrder = 0;
        int staffOrder = 0;
        Map<String, AniListPersonFavorite> unique = new LinkedHashMap<>();

        while (moreCharacters || moreStaff) {
            Map<String, Object> data = execute(USER_PERSON_FAVOURITES_QUERY, Map.of(
                    "userName", username,
                    "characterPage", characterPage,
                    "staffPage", staffPage,
                    "perPage", perPage
            ), accessToken);
            Map<String, Object> user = asMap(data.get("User"));
            if (user.isEmpty()) {
                throw new AniListClientException("AniList no encontró el usuario al importar sus favoritos");
            }
            Map<String, Object> favourites = asMap(user.get("favourites"));
            Map<String, Object> characters = asMap(favourites.get("characters"));
            Map<String, Object> staff = asMap(favourites.get("staff"));

            if (moreCharacters) {
                characterOrder = collectPersonFavorites(
                        characters.get("edges"), PersonType.CHARACTER, characterOrder, unique);
                moreCharacters = Boolean.TRUE.equals(asMap(characters.get("pageInfo")).get("hasNextPage"));
                if (moreCharacters) characterPage++;
            }
            if (moreStaff) {
                staffOrder = collectPersonFavorites(
                        staff.get("edges"), PersonType.STAFF, staffOrder, unique);
                moreStaff = Boolean.TRUE.equals(asMap(staff.get("pageInfo")).get("hasNextPage"));
                if (moreStaff) staffPage++;
            }
        }
        return List.copyOf(unique.values());
    }

    private int collectPersonFavorites(Object nodesValue, PersonType type, int nextOrder,
            Map<String, AniListPersonFavorite> target) {
        for (Object value : asList(nodesValue)) {
            Map<String, Object> edge = asMap(value);
            Map<String, Object> person = asMap(edge.get("node"));
            Integer id = asInteger(person.get("id"));
            if (id == null) continue;
            String key = type.name() + ":" + id;
            if (target.containsKey(key)) continue;
            Map<String, Object> name = asMap(person.get("name"));
            target.put(key, new AniListPersonFavorite(
                    id,
                    type,
                    asString(name.get("full")),
                    asString(name.get("native")),
                    asString(asMap(person.get("image")).get("large")),
                    nextOrder++
            ));
        }
        return nextOrder;
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
                int progress = "NOVEL".equals(media.mediaFormat())
                        ? integerOrZero(entry.get("progressVolumes"))
                        : integerOrZero(entry.get("progress"));
                target.putIfAbsent(media.anilistId(), new AniListLibraryItem(
                        media,
                        progress,
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

    @Cacheable("seasons")
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
        for (int attempt = 0; attempt < 2; attempt++) {
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
                if (!(response.get("data") instanceof Map<?, ?> data) || data.isEmpty()) {
                    throw new AniListClientException("AniList devolvió datos incompletos");
                }
                return asMap(data);
            } catch (AniListClientException exception) {
                throw exception;
            } catch (HttpStatusCodeException exception) {
                int status = exception.getStatusCode().value();
                if (attempt == 0 && status >= 500) {
                    pauseBeforeRetry();
                    continue;
                }
                if (status == 429) {
                    throw new AniListClientException(
                            "AniList alcanzó temporalmente su límite de solicitudes. Intenta nuevamente en un minuto.",
                            exception
                    );
                }
                throw new AniListClientException("AniList respondió con el estado HTTP " + status, exception);
            } catch (ResourceAccessException exception) {
                if (attempt == 0) {
                    pauseBeforeRetry();
                    continue;
                }
                throw new AniListClientException(
                        "AniList tardó demasiado en responder o no se pudo establecer la conexión",
                        exception
                );
            } catch (RestClientException exception) {
                throw new AniListClientException("No se pudo conectar con AniList", exception);
            }
        }
        throw new AniListClientException("AniList no respondió después de reintentar");
    }

    private void pauseBeforeRetry() {
        try {
            Thread.sleep(350);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AniListClientException("Se interrumpió el reintento hacia AniList", exception);
        }
    }

    private AniListMediaResponse mapMedia(Map<String, Object> media) {
        return mapMedia(media, false, false);
    }

    private AniListMediaResponse mapMedia(Map<String, Object> media, boolean includeAllTags) {
        return mapMedia(media, includeAllTags, false);
    }

    private AniListMediaResponse mapMedia(Map<String, Object> media, boolean includeAllTags, boolean preferExtraLarge) {
        MediaType mediaType = enumValue(MediaType.class, asString(media.get("type")));
        Map<String, Object> coverImage = asMap(media.get("coverImage"));
        Map<String, Object> titles = asMap(media.get("title"));
        String cover = preferExtraLarge
                ? firstNonBlank(asString(coverImage.get("extraLarge")), asString(coverImage.get("large")))
                : firstNonBlank(asString(coverImage.get("large")), asString(coverImage.get("extraLarge")));
        String mediaFormat = asString(media.get("format"));
        Integer totalUnits = mediaType == MediaType.ANIME
                ? asInteger(media.get("episodes"))
                : "NOVEL".equals(mediaFormat)
                        ? asInteger(media.get("volumes"))
                        : asInteger(media.get("chapters"));

        return new AniListMediaResponse(
                asInteger(media.get("id")),
                mediaType,
                mediaFormat,
                asString(media.get("countryOfOrigin")),
                selectTitle(titles),
                asString(titles.get("romaji")),
                cover,
                asString(media.get("bannerImage")),
                resolveMusicVideoUrl(mediaFormat, media.get("externalLinks"), media.get("trailer")),
                mapStreamingLinks(mediaType, media.get("externalLinks")),
                asString(media.get("description")),
                totalUnits,
                mapDate(media.get("startDate")),
                mapDate(media.get("endDate")),
                enumValue(ReleaseStatus.class, asString(media.get("status"))),
                enumValue(ReleaseSeason.class, asString(media.get("season"))),
                mediaType == MediaType.MANGA ? asInteger(asMap(media.get("startDate")).get("year"))
                        : asInteger(media.get("seasonYear")),
                asInteger(media.get("favourites")),
                mapAiring(media.get("nextAiringEpisode")),
                mapCredits(mediaType, media.get("staff"), media.get("studios")),
                mapRelations(media.get("relations")),
                mapCharacters(media.get("characters")),
                asList(media.get("genres")).stream().map(String::valueOf).toList(),
                mapTags(media.get("tags"), includeAllTags ? Integer.MAX_VALUE : 5),
                asInteger(asMap(media.get("endDate")).get("year"))
        );
    }

    private String resolveMusicVideoUrl(String mediaFormat, Object externalLinksValue, Object trailerValue) {
        if (!"MUSIC".equals(mediaFormat)) return null;
        String streamingUrl = asList(externalLinksValue).stream()
                .map(this::asMap)
                .filter(link -> !Boolean.TRUE.equals(link.get("isDisabled")))
                .filter(link -> "STREAMING".equalsIgnoreCase(asString(link.get("type"))))
                .filter(link -> "YouTube".equalsIgnoreCase(asString(link.get("site"))))
                .map(link -> asString(link.get("url")))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
        if (streamingUrl != null) return streamingUrl;
        Map<String, Object> trailer = asMap(trailerValue);
        String trailerId = asString(trailer.get("id"));
        return trailerId != null && "youtube".equalsIgnoreCase(asString(trailer.get("site")))
                ? "https://www.youtube.com/watch?v=" + trailerId
                : null;
    }

    private List<MediaStreamingLinkResponse> mapStreamingLinks(MediaType mediaType, Object externalLinksValue) {
        if (mediaType != MediaType.ANIME) return List.of();
        return asList(externalLinksValue).stream()
                .map(this::asMap)
                .filter(link -> !Boolean.TRUE.equals(link.get("isDisabled")))
                .filter(link -> "STREAMING".equalsIgnoreCase(asString(link.get("type"))))
                .filter(link -> isHttpUrl(asString(link.get("url"))))
                .map(link -> new MediaStreamingLinkResponse(
                        asString(link.get("site")),
                        asString(link.get("url")),
                        asString(link.get("language")),
                        safeHexColor(asString(link.get("color"))),
                        isHttpUrl(asString(link.get("icon"))) ? asString(link.get("icon")) : null,
                        asString(link.get("notes"))
                ))
                .distinct()
                .toList();
    }

    private boolean isHttpUrl(String value) {
        return value != null && (value.startsWith("https://") || value.startsWith("http://"));
    }

    private String safeHexColor(String value) {
        return value != null && value.matches("#[0-9a-fA-F]{6}") ? value : null;
    }

    private List<MediaCreditResponse> mapCredits(MediaType mediaType, Object staffValue, Object studiosValue) {
        List<Map<String, Object>> edges = asList(asMap(staffValue).get("edges")).stream()
                .map(this::asMap)
                .filter(edge -> !asMap(edge.get("node")).isEmpty())
                .toList();
        Map<Integer, CreditBuilder> selected = new LinkedHashMap<>();

        if (mediaType == MediaType.ANIME) {
            addFirstCredit(selected, edges, "Obra original", "Original Creator", "Original Story");
            addFirstCredit(selected, edges, "Dirección", "Director", "Chief Director");
            addFirstCredit(selected, edges, "Composición de serie", "Series Composition", "Head Writer", "Script", "Screenplay");
            addFirstCredit(selected, edges, "Diseño de personajes", "Character Design");
        } else {
            boolean combined = addFirstCredit(selected, edges, "Historia y dibujo", "Story & Art");
            if (!combined) {
                boolean hasStory = addFirstCredit(selected, edges, "Historia", "Story", "Original Story");
                boolean hasArt = addFirstCredit(selected, edges, "Dibujo", "Art");
                if (!hasStory && !hasArt) addFirstCredit(selected, edges, "Historia y dibujo", "Original Creator");
            }
        }

        List<MediaCreditResponse> credits = selected.values().stream().map(CreditBuilder::toResponse).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (mediaType == MediaType.ANIME) {
            Map<String, Object> studioEdge = asList(asMap(studiosValue).get("edges")).stream()
                    .map(this::asMap)
                    .filter(edge -> Boolean.TRUE.equals(edge.get("isMain")))
                    .findFirst()
                    .orElseGet(() -> asList(asMap(studiosValue).get("edges")).stream()
                            .map(this::asMap)
                            .filter(edge -> Boolean.TRUE.equals(asMap(edge.get("node")).get("isAnimationStudio")))
                            .findFirst().orElse(Map.of()));
            Map<String, Object> studio = asMap(studioEdge.get("node"));
            if (!studio.isEmpty()) {
                credits.add(new MediaCreditResponse("STUDIO", asInteger(studio.get("id")),
                        asString(studio.get("name")), null, List.of("Estudio de animación")));
            }
        }
        return credits;
    }

    private boolean addFirstCredit(Map<Integer, CreditBuilder> selected, List<Map<String, Object>> edges,
                                   String label, String... preferredRoles) {
        for (String preferredRole : preferredRoles) {
            Map<String, Object> match = edges.stream()
                    .filter(edge -> preferredRole.equalsIgnoreCase(asString(edge.get("role"))))
                    .findFirst().orElse(null);
            if (match != null) {
                addCredit(selected, match, label);
                return true;
            }
        }
        return false;
    }

    private void addCredit(Map<Integer, CreditBuilder> selected, Map<String, Object> edge, String label) {
        Map<String, Object> person = asMap(edge.get("node"));
        Integer id = asInteger(person.get("id"));
        String name = asString(asMap(person.get("name")).get("full"));
        if (id == null || name == null || label == null) return;
        CreditBuilder credit = selected.computeIfAbsent(id, ignored -> new CreditBuilder(
                id, name, asString(asMap(person.get("image")).get("large"))));
        if (!credit.roles.contains(label)) credit.roles.add(label);
    }

    private static final class CreditBuilder {
        private final Integer id;
        private final String name;
        private final String image;
        private final List<String> roles = new ArrayList<>();

        private CreditBuilder(Integer id, String name, String image) {
            this.id = id;
            this.name = name;
            this.image = image;
        }

        private MediaCreditResponse toResponse() {
            return new MediaCreditResponse("PERSON", id, name, image, List.copyOf(roles));
        }
    }

    private List<MediaTagResponse> mapTags(Object tagsValue, int limit) {
        List<MediaTagResponse> tags = asList(tagsValue).stream()
                .map(this::asMap)
                .filter(tag -> !Boolean.TRUE.equals(tag.get("isMediaSpoiler")))
                .sorted((first, second) -> Integer.compare(
                        asInteger(second.get("rank")) == null ? 0 : asInteger(second.get("rank")),
                        asInteger(first.get("rank")) == null ? 0 : asInteger(first.get("rank"))))
                .map(tag -> new MediaTagResponse(
                        asString(tag.get("name")),
                        asInteger(tag.get("rank")),
                        asString(tag.get("description")),
                        asString(tag.get("category"))))
                .toList();
        if (limit >= tags.size()) return tags;
        List<MediaTagResponse> visible = new ArrayList<>(tags.stream().limit(limit).toList());
        tags.stream().filter(tag -> "Yuri".equalsIgnoreCase(tag.name())).findFirst().ifPresent(yuri -> {
            if (visible.stream().noneMatch(tag -> "Yuri".equalsIgnoreCase(tag.name()))) {
                if (visible.size() == limit) visible.remove(limit - 1);
                visible.add(yuri);
            }
        });
        return visible;
    }

    private List<MediaCharacterResponse> mapCharacters(Object charactersValue) {
        return asList(asMap(charactersValue).get("edges")).stream()
                .map(this::asMap)
                .map(edge -> {
                    Map<String, Object> node = asMap(edge.get("node"));
                    return new MediaCharacterResponse(
                            asInteger(node.get("id")),
                            asString(asMap(node.get("name")).get("full")),
                            asString(asMap(node.get("image")).get("large")),
                            asString(edge.get("role")),
                            asList(edge.get("voiceActors")).stream().map(this::asMap).map(this::mapVoiceActor).toList()
                    );
                })
                .toList();
    }

    private VoiceActorResponse mapVoiceActor(Map<String, Object> actor) {
        Map<String, Object> name = asMap(actor.get("name"));
        return new VoiceActorResponse(asInteger(actor.get("id")), asString(name.get("full")), asString(name.get("native")),
                asString(asMap(actor.get("image")).get("large")), asString(actor.get("languageV2")));
    }

    private PersonMediaResponse mapPersonMedia(Map<String, Object> media, String role) {
        return new PersonMediaResponse(asInteger(media.get("id")), enumValue(MediaType.class, asString(media.get("type"))),
                selectTitle(asMap(media.get("title"))), asString(asMap(media.get("coverImage")).get("large")), role);
    }

    private String formatFuzzyDate(Object value) {
        Map<String, Object> date = asMap(value);
        Integer year = asInteger(date.get("year"));
        Integer month = asInteger(date.get("month"));
        Integer day = asInteger(date.get("day"));
        if (year == null && month == null && day == null) return null;
        List<String> parts = new ArrayList<>();
        if (day != null) parts.add(String.valueOf(day));
        if (month != null) parts.add(String.valueOf(month));
        if (year != null) parts.add(String.valueOf(year));
        return String.join("/", parts);
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
