package com.animelist.backend.service;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AniListClientTest {

    @Test
    void categoriesAreSortedAndExcludeAdultTags() throws Exception {
        String response = """
                {"data":{
                  "GenreCollection":["Romance","Action"],
                  "MediaTagCollection":[
                    {"name":"Time Loop","description":"Loops in time","category":"Theme-Sci-Fi","isAdult":false},
                    {"name":"Assassins","description":"Professional killers","category":"Cast-Traits","isAdult":false},
                    {"name":"Adult only","description":"Hidden","category":"Sexual Content","isAdult":true}
                  ]
                }}
                """;

        withResponse(response, client -> {
            var categories = client.getCategories();
            assertEquals(java.util.List.of("Action", "Romance"), categories.genres());
            assertEquals(java.util.List.of("Assassins", "Time Loop"),
                    categories.tags().stream().map(tag -> tag.name()).toList());
        });
    }

    @Test
    void mediaDetailKeepsMoreThanTheFiveCatalogCardTags() throws Exception {
        String response = """
                {"data":{"Media":{
                  "id":20,"type":"ANIME","format":"TV","countryOfOrigin":"JP",
                  "title":{"romaji":"Example","english":"Example","native":"Example"},
                  "coverImage":{"extraLarge":"cover.jpg","large":"cover.jpg"},
                  "bannerImage":null,"description":"Synopsis","episodes":12,
                  "startDate":{"year":2020,"month":1,"day":1},
                  "endDate":{"year":2020,"month":3,"day":1},
                  "status":"FINISHED","season":"WINTER","seasonYear":2020,"favourites":1,
                  "nextAiringEpisode":null,"genres":["Action"],
                  "tags":[
                    {"name":"Tag 1","rank":99,"isMediaSpoiler":false,"isGeneralSpoiler":false,"description":"1","category":"A"},
                    {"name":"Tag 2","rank":98,"isMediaSpoiler":false,"isGeneralSpoiler":false,"description":"2","category":"A"},
                    {"name":"Tag 3","rank":97,"isMediaSpoiler":false,"isGeneralSpoiler":false,"description":"3","category":"A"},
                    {"name":"Tag 4","rank":96,"isMediaSpoiler":false,"isGeneralSpoiler":false,"description":"4","category":"A"},
                    {"name":"Tag 5","rank":95,"isMediaSpoiler":false,"isGeneralSpoiler":false,"description":"5","category":"A"},
                    {"name":"Tag 6","rank":94,"isMediaSpoiler":false,"isGeneralSpoiler":false,"description":"6","category":"A"},
                    {"name":"Tag 7","rank":93,"isMediaSpoiler":false,"isGeneralSpoiler":false,"description":"7","category":"A"},
                    {"name":"Hidden reveal","rank":92,"isMediaSpoiler":true,"isGeneralSpoiler":false,"description":"Spoiler","category":"A"}
                  ],
                  "staff":{"edges":[]},"studios":{"nodes":[]},"relations":{"edges":[]},
                  "mainCharacters":{"edges":[]},"supportingCharacters":{"edges":[]},
                  "streamingEpisodes":[]
                }}}
                """;

        withResponse(response, client -> {
            var tags = client.getMedia(20).tags();
            assertEquals(8, tags.size());
            assertTrue(tags.stream().filter(tag -> "Hidden reveal".equals(tag.name())).findFirst().orElseThrow().spoiler());
            assertEquals(7, client.getMediaForLibrary(20).tags().size());
        });
    }

    @Test
    void libraryMetadataUsesTheSmallerCardCover() throws Exception {
        String response = """
                {"data":{"Media":{
                  "id":20,"type":"ANIME","format":"TV","countryOfOrigin":"JP",
                  "title":{"romaji":"Example","english":"Example","native":"Example"},
                  "coverImage":{"extraLarge":"extra.jpg","large":"large.jpg"},
                  "startDate":{"year":2020,"month":1,"day":1},"endDate":{},
                  "status":"FINISHED","genres":[],"tags":[]
                }}}
                """;

        withResponse(response, client -> assertEquals("large.jpg", client.getMediaForLibrary(20).coverImage()));
    }

    @Test
    void mediaDetailMapsUsefulInsightsAndAnOfficialTrailer() throws Exception {
        String response = """
                {"data":{"Media":{
                  "id":20,"type":"ANIME","format":"TV","countryOfOrigin":"JP",
                  "title":{"romaji":"Example"},"coverImage":{"extraLarge":"cover.jpg"},
                  "trailer":{"id":"abc_123-XYZ","site":"youtube"},"duration":24,
                  "averageScore":84,"meanScore":81,"popularity":123456,
                  "rankings":[{"rank":4,"type":"RATED","allTime":false,"context":"highest rated","year":2026,"season":"FALL"}],
                  "startDate":{},"endDate":{},"genres":[],"tags":[],"externalLinks":[],
                  "staff":{"edges":[]},"studios":{"edges":[]},"relations":{"edges":[]},
                  "mainCharacters":{"edges":[]},"supportingCharacters":{"edges":[]}
                }}}
                """;
        AtomicReference<String> request = new AtomicReference<>();

        withResponse(response, request, client -> {
            var insights = client.getMedia(20).insights();
            assertEquals("https://www.youtube.com/watch?v=abc_123-XYZ", insights.trailerUrl());
            assertEquals(24, insights.durationMinutes());
            assertEquals(84, insights.averageScore());
            assertEquals(81, insights.meanScore());
            assertEquals(123456, insights.popularity());
            assertEquals(4, insights.rankings().get(0).rank());
            assertTrue(request.get().contains("rankings { rank type allTime context year season }"));
        });
    }

    @Test
    void mangaDetailKeepsOnlyEnabledLegalReadingLinks() throws Exception {
        String response = """
                {"data":{"Media":{
                  "id":30,"type":"MANGA","format":"MANGA","countryOfOrigin":"JP",
                  "title":{"romaji":"Example manga"},"coverImage":{},
                  "externalLinks":[
                    {"site":"MANGA Plus","url":"https://mangaplus.shueisha.co.jp/example","type":"STREAMING","color":"#ff5500","isDisabled":false},
                    {"site":"Old reader","url":"https://example.com/old","type":"STREAMING","isDisabled":true},
                    {"site":"Official site","url":"https://example.com","type":"INFO","isDisabled":false}
                  ],
                  "startDate":{},"endDate":{},"genres":[],"tags":[],
                  "staff":{"edges":[]},"studios":{"edges":[]},"relations":{"edges":[]},
                  "mainCharacters":{"edges":[]},"supportingCharacters":{"edges":[]}
                }}}
                """;

        withResponse(response, client -> {
            var links = client.getMedia(30).streamingLinks();
            assertEquals(1, links.size());
            assertEquals("MANGA Plus", links.get(0).site());
            assertEquals("#ff5500", links.get(0).color());
        });
    }

    @Test
    void assistantCanBrowseAnimeAndMangaInOneRequestWhenTypeIsUnspecified() throws Exception {
        String response = """
                {"data":{
                  "anime":{"pageInfo":{"currentPage":1,"hasNextPage":false},"media":[
                    {"id":1,"type":"ANIME","format":"TV","title":{"romaji":"Anime"},"coverImage":{},"startDate":{},"endDate":{},"genres":[],"tags":[]}
                  ]},
                  "manga":{"pageInfo":{"currentPage":1,"hasNextPage":false},"media":[
                    {"id":2,"type":"MANGA","format":"MANGA","title":{"romaji":"Manga"},"coverImage":{},"startDate":{},"endDate":{},"genres":[],"tags":[],
                     "relations":{"edges":[{"relationType":"ADAPTATION","node":{"id":9,"type":"ANIME","format":"TV","title":{"romaji":"Anime adaptation"},"coverImage":{}}}]}}
                  ]}
                }}
                """;

        withResponse(response, client -> {
            var results = client.browseForAssistant(null, 20, null, null, null, List.of(), List.of(),
                    null, null, null, "POPULARITY").results();
            assertEquals(List.of("Anime", "Manga"), results.stream().map(item -> item.title()).toList());
            assertEquals(9, results.get(1).relations().get(0).anilistId());
        });
    }

    @Test
    void regularCatalogDoesNotRequestRelationsButAssistantDoes() throws Exception {
        String response = """
                {"data":{"Page":{"pageInfo":{"currentPage":1,"hasNextPage":false},"media":[]}}}
                """;
        AtomicReference<String> request = new AtomicReference<>();
        withResponse(response, request, client -> client.browse(
                com.animelist.backend.enums.MediaType.ANIME, 1, 20, null, null, null,
                List.of(), List.of(), null, null, null, "POPULARITY"));
        assertFalse(request.get().contains("relations"));

        withResponse(response, request, client -> client.browseForAssistant(
                com.animelist.backend.enums.MediaType.ANIME, 20, null, null, null,
                List.of(), List.of(), null, null, null, "POPULARITY"));
        assertTrue(request.get().contains("relations"));
    }

    @Test
    void assistantBatchLoadsRelationsAndTagRelevanceForLibraryVerification() throws Exception {
        String response = """
                {"data":{"Page":{"media":[{
                  "id":20,"type":"ANIME","format":"TV","countryOfOrigin":"JP",
                  "title":{"romaji":"Example"},"coverImage":{"large":"cover.jpg"},
                  "startDate":{"year":2024},"genres":["Romance"],"episodes":12,"status":"FINISHED",
                  "tags":[{"name":"Love Triangle","rank":87,"category":"Theme-Romance","isMediaSpoiler":false}],
                  "relations":{"edges":[{"relationType":"SEQUEL","node":{"id":21,"type":"ANIME","format":"TV","title":{"romaji":"Season 2"},"coverImage":{}}}]}
                }]}}}
                """;

        withResponse(response, client -> {
            var media = client.getMediaForAssistant(List.of(20)).get(0);
            assertEquals(87, media.tags().get(0).rank());
            assertEquals("SEQUEL", media.relations().get(0).relationType());
        });
    }

    @Test
    void globalSearchMapsMediaCharactersAndStaffInOneRequest() throws Exception {
        String response = """
                {"data":{
                  "mediaPage":{"media":[{"id":1,"type":"ANIME","format":"TV","title":{"romaji":"Frieren"},"coverImage":{"large":"media.jpg"}}]},
                  "characterPage":{"characters":[{"id":2,"name":{"full":"Frieren","native":"フリーレン"},"image":{"large":"character.jpg"}}]},
                  "staffPage":{"staff":[{"id":3,"name":{"full":"Atsumi Tanezaki"},"image":{"large":"staff.jpg"},"primaryOccupations":["Voice Actor"],"languageV2":"Japanese"}]}
                }}
                """;
        AtomicReference<String> request = new AtomicReference<>();
        withResponse(response, request, client -> {
            var result = client.globalSearch("Frieren", 5);
            assertEquals("Frieren", result.media().get(0).title());
            assertEquals("CHARACTER", result.characters().get(0).kind());
            assertEquals("Voice Actor", result.staff().get(0).subtitle());
            assertTrue(request.get().contains("mediaPage"));
            assertTrue(request.get().contains("characterPage"));
            assertTrue(request.get().contains("staffPage"));
        });
    }

    @Test
    void similarMediaExcludesFranchiseRelationsAndAdultResults() throws Exception {
        String response = """
                {"data":{"Media":{
                  "id":1,"type":"ANIME","title":{"romaji":"Baki"},"genres":["Action","Sports"],
                  "tags":[{"name":"Martial Arts","rank":95,"isMediaSpoiler":false}],
                  "relations":{"edges":[{"node":{"id":2}}]},
                  "recommendations":{"nodes":[
                    {"rating":120,"mediaRecommendation":{"id":2,"type":"ANIME","format":"TV","isAdult":false,"title":{"romaji":"Baki Season 2"},"coverImage":{},"genres":["Action"],"tags":[],"relations":{"edges":[]}}},
                    {"rating":100,"mediaRecommendation":{"id":3,"type":"ANIME","format":"TV","isAdult":true,"title":{"romaji":"Adult fighter"},"coverImage":{},"genres":["Action"],"tags":[],"relations":{"edges":[]}}},
                    {"rating":80,"mediaRecommendation":{"id":4,"type":"ANIME","format":"TV","isAdult":false,"title":{"romaji":"Kengan Ashura","english":"Kengan Ashura","native":"ケンガンアシュラ"},"coverImage":{"large":"kengan.jpg"},"genres":["Action","Sports"],"startDate":{"year":2019},"episodes":12,"status":"FINISHED","tags":[{"name":"Martial Arts","rank":90,"description":"Fighting","category":"Theme","isMediaSpoiler":false}],"relations":{"edges":[]}}}
                  ]}
                }}}
                """;

        withResponse(response, client -> {
            var results = client.getSimilarMedia(1, false);
            assertEquals(1, results.size());
            assertEquals("Kengan Ashura", results.get(0).media().title());
            assertTrue(results.get(0).reason().contains("Martial Arts"));
        });
    }

    private static void withResponse(String responseBody, ThrowingConsumer<AniListClient> assertion) throws Exception {
        withResponse(responseBody, new AtomicReference<>(), assertion);
    }

    private static void withResponse(String responseBody, AtomicReference<String> requestBody,
            ThrowingConsumer<AniListClient> assertion) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var client = new AniListClient(
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    Duration.ofSeconds(1),
                    Duration.ofSeconds(1));
            assertion.accept(client);
        } finally {
            server.stop(0);
        }
    }

    @FunctionalInterface
    private interface ThrowingConsumer<T> {
        void accept(T value) throws Exception;
    }
}
