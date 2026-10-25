package com.gabriel.hevydashboard.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Servidor local que entrega a pasta do site, para ver o dashboard no navegador sem instalar nada além do Java.
 * <p>
 * Escuta <b>só em localhost</b>: outro computador da mesma rede não consegue abrir, porque o site pode
 * estar mostrando os treinos reais do usuário. Também não entrega nada fora da pasta do site.
 */
public final class SiteServer implements AutoCloseable {

    private static final Map<String, String> TYPES = Map.of(
            ".html", "text/html; charset=utf-8",
            ".js", "text/javascript; charset=utf-8",
            ".css", "text/css; charset=utf-8",
            ".json", "application/json; charset=utf-8",
            ".png", "image/png",
            ".svg", "image/svg+xml",
            ".md", "text/markdown; charset=utf-8");

    private final HttpServer server;
    private final Path root;

    private SiteServer(HttpServer server, Path root) {
        this.server = server;
        this.root = root;
    }

    /** @param port a porta; 0 escolhe uma livre (usado nos testes) */
    public static SiteServer start(Path siteDir, int port) throws IOException {
        Path root = siteDir.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IOException("A pasta do site não existe: " + root);
        }
        HttpServer http = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        SiteServer site = new SiteServer(http, root);
        http.createContext("/", site::handle);
        http.start();
        return site;
    }

    /** O endereço em que o servidor escuta. */
    public InetSocketAddress address() {
        return server.getAddress();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public String url() {
        return "http://localhost:" + port() + "/";
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!"GET".equals(exchange.getRequestMethod()) && !"HEAD".equals(exchange.getRequestMethod())) {
                reply(exchange, 405, "text/plain; charset=utf-8", "Método não permitido".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                return;
            }
            String requested = exchange.getRequestURI().getPath();
            if (requested.equals("/")) {
                requested = "/index.html";
            }
            Path file = root.resolve(requested.substring(1)).normalize();
            // Nada fora da pasta do site (por exemplo, "/../secrets/credencial.json").
            if (!file.startsWith(root) || !Files.isRegularFile(file)) {
                reply(exchange, 404, "text/plain; charset=utf-8", "Não encontrado".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                return;
            }
            reply(exchange, 200, contentType(file), Files.readAllBytes(file));
        }
    }

    private static void reply(HttpExchange exchange, int status, String type, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type);
        // O arquivo de dados muda a cada sincronização: o navegador não pode guardar uma cópia velha.
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        boolean head = "HEAD".equals(exchange.getRequestMethod());
        exchange.sendResponseHeaders(status, head ? -1 : body.length);
        if (!head) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
    }

    private static String contentType(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "application/octet-stream" : TYPES.getOrDefault(name.substring(dot), "application/octet-stream");
    }
}
