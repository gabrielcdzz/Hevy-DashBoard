package com.gabriel.hevydashboard.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiteServerTest {

    @TempDir
    Path pasta;

    private SiteServer servidor;
    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void iniciaServidor() throws IOException {
        Path site = Files.createDirectories(pasta.resolve("site/data"));
        Files.writeString(pasta.resolve("site/index.html"), "<h1>Olá</h1>", StandardCharsets.UTF_8);
        Files.writeString(site.resolve("sample.json"), "{\"sample\":true}", StandardCharsets.UTF_8);
        // Fora da pasta do site: não pode ser entregue de jeito nenhum.
        Files.writeString(pasta.resolve("segredo.txt"), "senha", StandardCharsets.UTF_8);
        servidor = SiteServer.start(pasta.resolve("site"), 0);
    }

    @AfterEach
    void paraServidor() {
        servidor.close();
    }

    private HttpResponse<String> get(String caminho) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + servidor.port() + caminho)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    @Test
    void entregaAPaginaInicialEOsArquivosComOTipoCerto() throws Exception {
        HttpResponse<String> inicio = get("/");
        HttpResponse<String> dados = get("/data/sample.json");

        assertEquals(200, inicio.statusCode());
        assertEquals("<h1>Olá</h1>", inicio.body());
        assertTrue(inicio.headers().firstValue("Content-Type").orElse("").startsWith("text/html"));
        assertTrue(dados.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
        assertEquals("no-cache", dados.headers().firstValue("Cache-Control").orElse(""));
    }

    @Test
    void arquivoQueNaoExisteDa404() throws Exception {
        assertEquals(404, get("/data/dashboard.json").statusCode());
    }

    @Test
    void naoEntregaNadaForaDaPastaDoSite() throws Exception {
        // O cliente HTTP normaliza "..", então o pedido é montado na mão com o caminho codificado.
        assertEquals(404, get("/%2e%2e/segredo.txt").statusCode());
        assertEquals(404, get("/..%2fsegredo.txt").statusCode());
    }

    @Test
    void soEscutaEmLocalhostParaOsDadosNaoSairemDaMaquina() {
        // O endereço de escuta é o de loopback (127.0.0.1), não "qualquer interface" (0.0.0.0).
        assertTrue(servidor.address().getAddress().isLoopbackAddress(), servidor.address().toString());
        assertTrue(servidor.url().startsWith("http://localhost:"));
    }

    @Test
    void pastaInexistenteDaErroQueDizOCaminho() {
        IOException erro = assertThrows(IOException.class, () -> SiteServer.start(pasta.resolve("nao-existe"), 0));

        assertTrue(erro.getMessage().contains("nao-existe"));
    }
}
