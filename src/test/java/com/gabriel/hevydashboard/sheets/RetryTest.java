package com.gabriel.hevydashboard.sheets;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponseException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetryTest {

    private final List<Long> esperas = new ArrayList<>();
    private static final long[] ESPERAS = {10, 20, 30};

    @Test
    void repeteAteDarCertoEEsperaMaisACadaTentativa() throws IOException {
        AtomicInteger chamadas = new AtomicInteger();

        String resultado = Retry.run(() -> {
            if (chamadas.incrementAndGet() < 3) {
                throw new IOException("429");
            }
            return "ok";
        }, e -> true, ESPERAS, esperas::add);

        assertEquals("ok", resultado);
        assertEquals(3, chamadas.get());
        assertEquals(List.of(10L, 20L), esperas);
    }

    @Test
    void desisteDepoisDaUltimaTentativaELancaOUltimoErro() {
        AtomicInteger chamadas = new AtomicInteger();

        IOException erro = assertThrows(IOException.class, () -> Retry.run(() -> {
            throw new IOException("tentativa " + chamadas.incrementAndGet());
        }, e -> true, ESPERAS, esperas::add));

        assertEquals(4, chamadas.get()); // a primeira + 3 repetições
        assertEquals("tentativa 4", erro.getMessage());
        assertEquals(List.of(10L, 20L, 30L), esperas);
    }

    @Test
    void erroDefinitivoNaoEhRepetido() {
        AtomicInteger chamadas = new AtomicInteger();

        assertThrows(IOException.class, () -> Retry.run(() -> {
            chamadas.incrementAndGet();
            throw new IOException("403");
        }, e -> false, ESPERAS, esperas::add));

        assertEquals(1, chamadas.get());
        assertTrue(esperas.isEmpty());
    }

    @Test
    void quaisErrosDoGoogleSaoPassageiros() {
        assertTrue(SheetsClient.isTransient(erroDoGoogle(429)));
        assertTrue(SheetsClient.isTransient(erroDoGoogle(500)));
        assertTrue(SheetsClient.isTransient(erroDoGoogle(503)));
        assertTrue(SheetsClient.isTransient(new IOException("rede caiu")));
        assertFalse(SheetsClient.isTransient(erroDoGoogle(403)));
        assertFalse(SheetsClient.isTransient(erroDoGoogle(404)));
        assertFalse(SheetsClient.isTransient(erroDoGoogle(400)));
    }

    private static GoogleJsonResponseException erroDoGoogle(int status) {
        HttpResponseException.Builder builder = new HttpResponseException.Builder(status, "erro", new HttpHeaders());
        return new GoogleJsonResponseException(builder, null);
    }
}
