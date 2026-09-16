package com.animelist.backend.service;

import com.animelist.backend.exception.BusinessException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.animelist.backend.service.AniListExportClient.State;

class AniListExportClientTest {
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

