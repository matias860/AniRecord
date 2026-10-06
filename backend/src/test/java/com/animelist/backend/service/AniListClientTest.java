package com.animelist.backend.service;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
                    {"name":"Tag 7","rank":93,"isMediaSpoiler":false,"isGeneralSpoiler":false,"description":"7","category":"A"}
                  ],
                  "staff":{"edges":[]},"studios":{"nodes":[]},"relations":{"edges":[]},
                  "mainCharacters":{"edges":[]},"supportingCharacters":{"edges":[]},
                  "streamingEpisodes":[]
                }}}
                """;

        withResponse(response, client -> assertEquals(7, client.getMedia(20).tags().size()));
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

    private static void withResponse(String responseBody, ThrowingConsumer<AniListClient> assertion) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
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
