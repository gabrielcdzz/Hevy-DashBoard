package com.gabriel.hevydashboard.sheets;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyncStateTest {

    private final InMemorySheets planilha = new InMemorySheets();
    private final SyncState estado = new SyncState(planilha);

    @Test
    void primeiraExecucaoNaoTemDataECriaAAba() {
        Optional<Instant> ultima = estado.lastSync();

        assertTrue(ultima.isEmpty());
        assertTrue(planilha.sheets.containsKey(SyncState.SHEET));
    }

    @Test
    void guardaELeADataDaUltimaSincronizacao() {
        Instant momento = Instant.parse("2026-10-24T22:00:00Z");

        estado.saveLastSync(momento);

        assertEquals(Optional.of(momento), estado.lastSync());
        assertEquals(List.of(List.of("chave", "valor"), List.of("ultima_sync", "2026-10-24T22:00:00Z")),
                planilha.sheets.get(SyncState.SHEET));
    }

    @Test
    void gravarDeNovoSubstituiADataAnterior() {
        estado.saveLastSync(Instant.parse("2026-10-24T22:00:00Z"));
        estado.saveLastSync(Instant.parse("2026-10-25T01:00:00Z"));

        assertEquals(Optional.of(Instant.parse("2026-10-25T01:00:00Z")), estado.lastSync());
        assertEquals(2, planilha.sheets.get(SyncState.SHEET).size());
    }

    @Test
    void dataInvalidaDaErroQueExplicaComoCorrigir() {
        planilha.ensureSheets(List.of(SyncState.SHEET));
        planilha.replaceRows(SyncState.SHEET,
                List.of(List.of("chave", "valor"), List.of("ultima_sync", "ontem")),
                SheetsGateway.InputMode.RAW);

        IllegalStateException erro = assertThrows(IllegalStateException.class, estado::lastSync);

        assertTrue(erro.getMessage().contains("ontem"));
    }
}
