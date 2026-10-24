package com.gabriel.hevydashboard.client;

import com.gabriel.hevydashboard.model.Routine;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Testa o cliente contra um servidor local falso, sem chamar o Hevy de verdade. */
class HevyClientTest {

    private HttpServer server;
    private HevyClient client;
    private final List<String> chavesRecebidas = new ArrayList<>();

    @BeforeEach
    void iniciaServidor() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        server.createContext("/v1/routines", exchange -> {
            chavesRecebidas.add(exchange.getRequestHeaders().getFirst("api-key"));
            String query = exchange.getRequestURI().getQuery();
            String body = query.contains("page=2")
                    ? "{\"page\":2,\"page_count\":2,\"routines\":[{\"id\":\"r2\",\"title\":\"PULL\"}]}"
                    : "{\"page\":1,\"page_count\":2,\"routines\":[{\"id\":\"r1\",\"title\":\"PUSH\"}]}";
            responde(exchange, 200, body);
        });

        server.createContext("/v1/workouts/count", exchange ->
                responde(exchange, 401, "{\"error\":\"Invalid API key\"}"));

        server.start();

        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
        HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        client = new HevyClient("chave-de-teste", baseUrl, http);
    }

    @AfterEach
    void paraServidor() {
        server.stop(0);
    }

    @Test
    void percorreTodasAsPaginasEEnviaAChaveNoHeader() {
        List<Routine> routines = client.getAllRoutines();

        assertEquals(2, routines.size());
        assertEquals("PUSH", routines.get(0).title());
        assertEquals("PULL", routines.get(1).title());
        assertEquals(2, chavesRecebidas.size());
        assertTrue(chavesRecebidas.stream().allMatch("chave-de-teste"::equals));
    }

    @Test
    void chaveInvalidaLancaExcecaoComStatus401() {
        HevyApiException erro = assertThrows(HevyApiException.class, () -> client.getWorkoutCount());

        assertEquals(401, erro.status());
    }

    private static void responde(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
