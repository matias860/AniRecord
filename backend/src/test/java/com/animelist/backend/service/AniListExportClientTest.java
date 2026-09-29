package com.animelist.backend.service;

import com.animelist.backend.exception.BusinessException;
import com.animelist.backend.enums.MediaType;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.animelist.backend.service.AniListExportClient.State;
import static com.animelist.backend.service.AniListExportClient.ProgressUnit;

class AniListExportClientTest {
    @Test void readIncludesTheAccountsScoreFormat() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] response = "{\"data\":{\"User\":{\"mediaListOptions\":{\"scoreFormat\":\"POINT_10\"}},\"Page\":{\"pageInfo\":{\"hasNextPage\":false},\"mediaList\":[{\"id\":100,\"mediaId\":10,\"status\":\"COMPLETED\",\"progress\":12,\"score\":90}]}}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try {
            var snapshot = new AniListExportClient("http://127.0.0.1:" + server.getAddress().getPort()).read("test-token", 42);
            assertEquals("POINT_10", snapshot.scoreFormat());
            assertEquals(new State("COMPLETED", 12, 90), snapshot.entries().get(10).value());
        } finally { server.stop(0); }
    }

    @Test void mangaReadUsesVolumeProgressForNovels() throws Exception {
        var requestBody = new java.util.concurrent.atomic.AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"data\":{\"User\":{\"mediaListOptions\":{\"scoreFormat\":\"POINT_100\"}},\"Page\":{\"pageInfo\":{\"hasNextPage\":false},\"mediaList\":[{\"id\":100,\"mediaId\":10,\"status\":\"CURRENT\",\"progress\":12,\"progressVolumes\":3,\"score\":80,\"media\":{\"format\":\"NOVEL\"}}]}}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try {
            var snapshot = new AniListExportClient("http://127.0.0.1:" + server.getAddress().getPort()).read("test-token", 42, MediaType.MANGA);
            assertEquals(new State("CURRENT", 3, 80), snapshot.entries().get(10).value());
            assertTrue(requestBody.get().contains("\"type\":\"MANGA\""));
            assertTrue(requestBody.get().contains("progressVolumes"));
        } finally { server.stop(0); }
    }

    @Test void novelMutationWritesVolumesInsteadOfChapters() throws Exception {
        var requestBody = new java.util.concurrent.atomic.AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"data\":{\"SaveMediaListEntry\":{\"mediaId\":10,\"status\":\"CURRENT\",\"progress\":12,\"progressVolumes\":3,\"score\":80}}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try {
            new AniListExportClient("http://127.0.0.1:" + server.getAddress().getPort())
                    .save("test-token", 10, 100, new State("CURRENT", 3, 80), ProgressUnit.VOLUMES);
            assertTrue(requestBody.get().contains("progressVolumes: $progress"));
            assertFalse(requestBody.get().contains("status: $status, progress: $progress"));
        } finally { server.stop(0); }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {200, 400})
    void nestedValidationIdentifiesRejectedField(int httpStatus) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] response = "{\"errors\":[{\"message\":\"validation\",\"status\":400,\"validation\":{\"id\":[\"The selected id is invalid.\"],\"score\":[\"Invalid score test-token\"]}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(httpStatus, response.length);
            exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try {
            var client = new AniListExportClient("http://127.0.0.1:" + server.getAddress().getPort());
            var error = assertThrows(BusinessException.class, () -> client.save("test-token", 10, 100, new State("PLANNING", 0, 0)));
            assertTrue(error.getMessage().contains("id: The selected id is invalid."));
            assertTrue(error.getMessage().contains("score: Invalid score"));
            assertFalse(error.getMessage().contains("test-token"));
        } finally { server.stop(0); }
    }

    @Test void newEntryOmitsUpdateIdEntirely() throws Exception {
        var requestBody = new java.util.concurrent.atomic.AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"data\":{\"SaveMediaListEntry\":{\"mediaId\":143103,\"status\":\"PLANNING\",\"progress\":0,\"score\":0}}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try {
            new AniListExportClient("http://127.0.0.1:" + server.getAddress().getPort()).save("test-token", 143103, null, new State("PLANNING", 0, 0));
            assertFalse(requestBody.get().contains("$id"));
            assertFalse(requestBody.get().contains("\"id\""));
            assertTrue(requestBody.get().contains("mediaId: $media"));
        } finally { server.stop(0); }
    }

    @Test void validationResponsePreservesReasonButRedactsToken() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] response = "{\"errors\":[{\"message\":\"Invalid progress test-token\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(400, response.length);
            exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try {
            var client = new AniListExportClient("http://127.0.0.1:" + server.getAddress().getPort());
            var error = assertThrows(BusinessException.class, () -> client.save("test-token", 10, 100, new State("PLANNING", 0, 0)));
            assertTrue(error.getMessage().contains("Invalid progress"));
            assertFalse(error.getMessage().contains("test-token"));
        } finally { server.stop(0); }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"429,75 segundos", "401,autorización", "403,denegó", "503,problema temporal", "400,rechazó la solicitud"})
    void httpFailuresExplainTheCauseWithoutRetryingWrites(int status, String expected) throws Exception {
        var calls = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet(); exchange.getRequestBody().readAllBytes();
            byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Retry-After", "75");
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try {
            var client = new AniListExportClient("http://127.0.0.1:" + server.getAddress().getPort());
            var error = assertThrows(BusinessException.class, () -> client.save("test-token", 10, 100, new State("COMPLETED", 12, 85)));
            assertTrue(error.getMessage().contains(expected), error.getMessage());
            assertEquals(1, calls.get());
        } finally { server.stop(0); }
    }

    @Test void authenticatedMutationUsesRawScoreAndOnlyTheThreeApprovedFields() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals("Bearer test-token", exchange.getRequestHeaders().getFirst("Authorization"));
            assertTrue(request.contains("SaveMediaListEntry")); assertTrue(request.contains("scoreRaw"));
            assertFalse(request.contains("DeleteMediaListEntry")); assertFalse(request.contains("startedAt"));
            byte[] response = "{\"data\":{\"SaveMediaListEntry\":{\"mediaId\":10,\"status\":\"COMPLETED\",\"progress\":12,\"score\":85}}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try { new AniListExportClient("http://127.0.0.1:" + server.getAddress().getPort()).save("test-token", 10, 100, new State("COMPLETED", 12, 85)); }
        finally { server.stop(0); }
    }

    @Test void graphqlErrorsDoNotRetryMutations() throws Exception {
        var calls = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet(); exchange.getRequestBody().readAllBytes();
            byte[] response = "{\"errors\":[{\"message\":\"Invalid token\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try {
            var client = new AniListExportClient("http://127.0.0.1:" + server.getAddress().getPort());
            assertThrows(BusinessException.class, () -> client.save("test-token", 10, 100, new State("COMPLETED", 12, 85)));
            assertEquals(1, calls.get());
        } finally { server.stop(0); }
    }
}

