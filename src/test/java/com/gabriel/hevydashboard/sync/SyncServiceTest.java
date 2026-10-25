package com.gabriel.hevydashboard.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gabriel.hevydashboard.client.HevyApiException;
import com.gabriel.hevydashboard.model.Exercise;
import com.gabriel.hevydashboard.model.ExerciseTemplate;
import com.gabriel.hevydashboard.model.Routine;
import com.gabriel.hevydashboard.model.SetEntry;
import com.gabriel.hevydashboard.model.Workout;
import com.gabriel.hevydashboard.model.WorkoutEvent;
import com.gabriel.hevydashboard.service.Weeks;
import com.gabriel.hevydashboard.sheets.InMemorySheets;
import com.gabriel.hevydashboard.sheets.SyncState;
import com.gabriel.hevydashboard.sheets.WorkoutSetsTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyncServiceTest {

    private static final Instant PRIMEIRA_EXECUCAO = Instant.parse("2026-10-24T15:00:00Z");
    private static final Instant SEGUNDA_EXECUCAO = Instant.parse("2026-10-24T20:00:00Z");

    private final FakeHevy hevy = new FakeHevy();
    private final InMemorySheets planilha = new InMemorySheets();

    @TempDir
    Path pasta;

    @BeforeEach
    void preparaOHevy() {
        hevy.templates.add(new ExerciseTemplate("supino", "Supino Reto", "weight_reps", "chest", List.of("triceps", "shoulders"), false));
        hevy.templates.add(new ExerciseTemplate("rosca", "Rosca Direta", "weight_reps", "biceps", List.of("forearms"), false));
        OffsetDateTime criada = OffsetDateTime.parse("2026-10-01T10:00:00Z");
        hevy.routines.add(new Routine("r1", "PUSH", null, criada, criada,
                List.of(exercicio("supino", "Supino Reto", 3))));
    }

    private SyncService servico(Instant agora) {
        return new SyncService(hevy, planilha, Clock.fixed(agora, ZoneOffset.UTC), Weeks.SAO_PAULO);
    }

    // ------------------------------------------------------------------
    // Primeira execução
    // ------------------------------------------------------------------

    @Test
    void primeiraExecucaoImportaTudoGravaOEstadoEGeraOArquivo() throws IOException {
        hevy.events.add(atualizado(treino("w1", "2026-10-20T15:00:00Z", "2026-10-20T16:30:00Z", exercicio("supino", "Supino Reto", 3))));
        hevy.events.add(atualizado(treino("w2", "2026-10-21T15:00:00Z", "2026-10-21T16:30:00Z", exercicio("rosca", "Rosca Direta", 2))));
        Path arquivo = pasta.resolve("dashboard.json");

        SyncService.Result resultado = servico(PRIMEIRA_EXECUCAO).run(arquivo);

        assertTrue(resultado.firstRun());
        assertEquals(Instant.EPOCH, hevy.requestedSince.get(0));
        // w1: 1 aquecimento + 3 de trabalho; w2: 1 aquecimento + 2 de trabalho. A planilha guarda também o aquecimento.
        assertEquals(new WorkoutSetsTable.Result(2, 0, 0, 7), resultado.sheetUpdate());
        assertEquals(Optional.of(PRIMEIRA_EXECUCAO), new SyncState(planilha).lastSync());

        JsonNode json = new ObjectMapper().readTree(Files.readString(arquivo));
        assertFalse(json.get("sample").asBoolean(), "dados reais não podem sair marcados como exemplo");
        assertEquals(2, totalDeTreinos(json));
        assertEquals(3, json.get("plannedVolume").get("chest").get("direct").asInt());
        assertTrue(json.get("exercises").get(0).get("inRoutine").asBoolean() || json.get("exercises").get(1).get("inRoutine").asBoolean());
    }

    // ------------------------------------------------------------------
    // Execuções seguintes
    // ------------------------------------------------------------------

    @Test
    void segundaExecucaoPedeSoOQueMudouDesdeAUltimaVez() throws IOException {
        hevy.events.add(atualizado(treino("w1", "2026-10-20T15:00:00Z", "2026-10-20T16:30:00Z", exercicio("supino", "Supino Reto", 3))));
        servico(PRIMEIRA_EXECUCAO).run(pasta.resolve("a.json"));

        // Depois da primeira execução: o usuário editou o w1 (uma série a menos) e fez um treino novo.
        hevy.events.add(atualizado(treino("w1", "2026-10-20T15:00:00Z", "2026-10-24T18:00:00Z", exercicio("supino", "Supino Reto", 2))));
        hevy.events.add(atualizado(treino("w3", "2026-10-24T14:00:00Z", "2026-10-24T19:00:00Z", exercicio("rosca", "Rosca Direta", 4))));
        SyncService.Result resultado = servico(SEGUNDA_EXECUCAO).run(pasta.resolve("b.json"));

        assertFalse(resultado.firstRun());
        assertEquals(PRIMEIRA_EXECUCAO, hevy.requestedSince.get(1));
        assertEquals(new WorkoutSetsTable.Result(2, 0, 4, 8), resultado.sheetUpdate());
        assertEquals(Optional.of(SEGUNDA_EXECUCAO), new SyncState(planilha).lastSync());

        // Planilha: cabeçalho + 3 linhas do w1 editado (1 aquecimento + 2) + 5 do w3 (1 + 4). As 4 do w1 antigo saíram.
        assertEquals(1 + 3 + 5, planilha.sheets.get(WorkoutSetsTable.SHEET).size());
        JsonNode json = new ObjectMapper().readTree(Files.readString(pasta.resolve("b.json")));
        assertEquals(2, totalDeTreinos(json));
    }

    @Test
    void treinoApagadoNoHevySaiDaPlanilhaEDoDashboard() throws IOException {
        hevy.events.add(atualizado(treino("w1", "2026-10-20T15:00:00Z", "2026-10-20T16:30:00Z", exercicio("supino", "Supino Reto", 3))));
        hevy.events.add(atualizado(treino("w2", "2026-10-21T15:00:00Z", "2026-10-21T16:30:00Z", exercicio("rosca", "Rosca Direta", 2))));
        servico(PRIMEIRA_EXECUCAO).run(pasta.resolve("a.json"));

        hevy.events.add(new WorkoutEvent("deleted", null, "w1", OffsetDateTime.parse("2026-10-24T18:00:00Z")));
        SyncService.Result resultado = servico(SEGUNDA_EXECUCAO).run(pasta.resolve("b.json"));

        assertEquals(1, resultado.sheetUpdate().workoutsRemoved());
        JsonNode json = new ObjectMapper().readTree(Files.readString(pasta.resolve("b.json")));
        assertEquals(1, totalDeTreinos(json));
        // Na planilha só sobraram as linhas do w2.
        assertEquals(List.of("w2"), planilha.sheets.get(WorkoutSetsTable.SHEET).stream().skip(1).map(linha -> linha.get(0)).distinct().toList());
    }

    @Test
    void rodarDeNovoSemNadaNovoNaoMudaAPlanilha() {
        hevy.events.add(atualizado(treino("w1", "2026-10-20T15:00:00Z", "2026-10-20T16:30:00Z", exercicio("supino", "Supino Reto", 3))));
        servico(PRIMEIRA_EXECUCAO).run(pasta.resolve("a.json"));
        List<List<String>> antes = new ArrayList<>(planilha.sheets.get(WorkoutSetsTable.SHEET));

        SyncService.Result resultado = servico(SEGUNDA_EXECUCAO).run(pasta.resolve("b.json"));

        assertEquals(new WorkoutSetsTable.Result(0, 0, 0, 0), resultado.sheetUpdate());
        assertEquals(antes, planilha.sheets.get(WorkoutSetsTable.SHEET));
    }

    // ------------------------------------------------------------------
    // Falhas e fuso
    // ------------------------------------------------------------------

    @Test
    void seOHevyFalharAUltimaSincronizacaoNaoMudaENenhumArquivoEGerado() {
        hevy.events.add(atualizado(treino("w1", "2026-10-20T15:00:00Z", "2026-10-20T16:30:00Z", exercicio("supino", "Supino Reto", 3))));
        servico(PRIMEIRA_EXECUCAO).run(pasta.resolve("a.json"));

        hevy.offline = true;
        Path arquivo = pasta.resolve("b.json");
        assertThrows(HevyApiException.class, () -> servico(SEGUNDA_EXECUCAO).run(arquivo));

        assertEquals(Optional.of(PRIMEIRA_EXECUCAO), new SyncState(planilha).lastSync());
        assertFalse(Files.exists(arquivo));
    }

    @Test
    void exercicioRenomeadoNoHevyAparecePeloNomeNovoSemTreinoNovo() throws IOException {
        hevy.events.add(atualizado(treino("w1", "2026-10-20T15:00:00Z", "2026-10-20T16:30:00Z", exercicio("supino", "Supino Reto", 3))));
        servico(PRIMEIRA_EXECUCAO).run(pasta.resolve("a.json"));

        // O usuário renomeia o exercício personalizado e muda o músculo principal. Nenhum treino é editado: não há eventos.
        hevy.templates.set(0, new ExerciseTemplate("supino", "Supino Reto (Barra)", "weight_reps", "shoulders", List.of(), true));
        servico(SEGUNDA_EXECUCAO).run(pasta.resolve("b.json"));

        JsonNode exercicios = new ObjectMapper().readTree(Files.readString(pasta.resolve("b.json"))).get("exercises");
        assertEquals(1, exercicios.size());
        assertEquals("Supino Reto (Barra)", exercicios.get(0).get("name").asText());
        assertEquals("shoulders", exercicios.get(0).get("muscleGroup").asText());
    }

    @Test
    void hojeVemDoFusoDeBrasiliaEnaoDoUtc() throws IOException {
        hevy.events.add(atualizado(treino("w1", "2026-10-20T15:00:00Z", "2026-10-20T16:30:00Z", exercicio("supino", "Supino Reto", 3))));
        Path arquivo = pasta.resolve("dashboard.json");

        // 01h de domingo 25/10 em UTC ainda é sábado 24/10, 22h, em Brasília.
        servico(Instant.parse("2026-10-25T01:00:00Z")).run(arquivo);

        JsonNode json = new ObjectMapper().readTree(Files.readString(arquivo));
        assertEquals("2026-10-24", json.get("today").asText());
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------

    /** Soma dos treinos de todas as semanas do arquivo de dados. */
    private static int totalDeTreinos(JsonNode json) {
        int total = 0;
        for (JsonNode semana : json.get("weeks")) {
            total += semana.get("workouts").asInt();
        }
        return total;
    }

    private static Exercise exercicio(String templateId, String titulo, int seriesDeTrabalho) {
        List<SetEntry> series = new ArrayList<>();
        series.add(new SetEntry(0, "warmup", 20.0, 12, null, null, null, null));
        for (int i = 0; i < seriesDeTrabalho; i++) {
            series.add(new SetEntry(i + 1, "normal", 60.0, 8, null, null, null, null));
        }
        return new Exercise(0, titulo, null, templateId, series);
    }

    private static Workout treino(String id, String inicio, String atualizadoEm, Exercise... exercicios) {
        OffsetDateTime start = OffsetDateTime.parse(inicio);
        return new Workout(id, "PUSH", null, start, start.plusMinutes(70), OffsetDateTime.parse(atualizadoEm), start, List.of(exercicios));
    }

    private static WorkoutEvent atualizado(Workout treino) {
        return new WorkoutEvent("updated", treino, null, null);
    }
}
