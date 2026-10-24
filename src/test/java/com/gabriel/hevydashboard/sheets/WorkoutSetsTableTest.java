package com.gabriel.hevydashboard.sheets;

import com.gabriel.hevydashboard.model.Exercise;
import com.gabriel.hevydashboard.model.ExerciseTemplate;
import com.gabriel.hevydashboard.model.SetEntry;
import com.gabriel.hevydashboard.model.Workout;
import com.gabriel.hevydashboard.model.WorkoutEvent;
import com.gabriel.hevydashboard.service.SetRecord;
import com.gabriel.hevydashboard.service.SetRecords;
import com.gabriel.hevydashboard.service.Weeks;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkoutSetsTableTest {

    private static final ExerciseTemplate SUPINO = new ExerciseTemplate(
            "supino", "Supino Reto", "weight_reps", "chest", List.of("triceps", "shoulders"), false);
    private static final ExerciseTemplate FLEXAO = new ExerciseTemplate(
            "flexao", "Flexão", "reps_only", "chest", List.of(), false);

    private final InMemorySheets planilha = new InMemorySheets();
    private final WorkoutSetsTable tabela = new WorkoutSetsTable(planilha, List.of(SUPINO, FLEXAO), Weeks.SAO_PAULO);

    // ------------------------------------------------------------------
    // Transformação treino → linhas
    // ------------------------------------------------------------------

    @Test
    void cadaSerieViraUmaLinhaComTodosOsCampos() {
        // Domingo 25/10 às 22h em Brasília = segunda 26/10 à 01h em UTC.
        Workout treino = treino("w1", "PUSH", "2026-10-26T01:00:00Z", "2026-10-26T01:00:00Z",
                exercicio("supino", "Supino Reto", serie("warmup", 20.0, 12), serie("normal", 60.0, 8)));

        List<List<Object>> linhas = tabela.rowsOf(treino);

        assertEquals(2, linhas.size());
        // A última coluna é a duração do treino em minutos (o helper treino() usa 1 hora).
        assertEquals(List.of("w1", "2026-10-25", "2026-10-19", "PUSH", "Supino Reto", "supino",
                "chest", "triceps, shoulders", "warmup", false, 20.0, 12, "", 60), linhas.get(0));
        assertEquals(List.of("w1", "2026-10-25", "2026-10-19", "PUSH", "Supino Reto", "supino",
                "chest", "triceps, shoulders", "normal", true, 60.0, 8, "", 60), linhas.get(1));
    }

    @Test
    void serieSemCargaOuRepeticoesFicaComCelulasVazias() {
        Workout treino = treino("w1", "PUSH", "2026-10-21T15:00:00Z", "2026-10-21T15:00:00Z",
                exercicio("flexao", "Flexão", serie("normal", null, null)));

        List<Object> linha = tabela.rowsOf(treino).get(0);

        assertEquals("", linha.get(10));
        assertEquals("", linha.get(11));
        assertEquals("", linha.get(7)); // sem músculos secundários
    }

    @Test
    void templateDesconhecidoLancaExcecaoQueExplicaOMotivo() {
        Workout treino = treino("w1", "PUSH", "2026-10-21T15:00:00Z", "2026-10-21T15:00:00Z",
                exercicio("apagado", "Exercício antigo", serie("normal", 10.0, 10)));

        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class, () -> tabela.rowsOf(treino));

        assertTrue(erro.getMessage().contains("Exercício antigo"));
    }

    // ------------------------------------------------------------------
    // Atualização incremental
    // ------------------------------------------------------------------

    @Test
    void primeiraImportacaoCriaOCabecalhoEGravaAsSeries() {
        WorkoutSetsTable.Result resultado = tabela.applyEvents(List.of(
                atualizado(treino("w1", "PUSH", "2026-10-21T15:00:00Z", "2026-10-21T15:00:00Z",
                        exercicio("supino", "Supino Reto", serie("normal", 60.0, 8), serie("normal", 60.0, 7))))));

        List<List<String>> aba = planilha.sheets.get(WorkoutSetsTable.SHEET);
        assertEquals(3, aba.size());
        assertEquals(WorkoutSetsTable.HEADER.stream().map(String::valueOf).toList(), aba.get(0));
        assertEquals(new WorkoutSetsTable.Result(1, 0, 0, 2), resultado);
    }

    @Test
    void treinoEditadoTrocaSoAsLinhasDele() {
        tabela.applyEvents(List.of(
                atualizado(treino("w1", "PUSH", "2026-10-21T15:00:00Z", "2026-10-21T15:00:00Z",
                        exercicio("supino", "Supino Reto", serie("normal", 60.0, 8), serie("normal", 60.0, 7)))),
                atualizado(treino("w2", "PULL", "2026-10-22T15:00:00Z", "2026-10-22T15:00:00Z",
                        exercicio("supino", "Supino Reto", serie("normal", 50.0, 10))))));

        // O usuário removeu uma série e mudou a carga do treino w1.
        WorkoutSetsTable.Result resultado = tabela.applyEvents(List.of(
                atualizado(treino("w1", "PUSH", "2026-10-21T15:00:00Z", "2026-10-23T09:00:00Z",
                        exercicio("supino", "Supino Reto", serie("normal", 65.0, 8))))));

        assertEquals(new WorkoutSetsTable.Result(1, 0, 2, 1), resultado);
        List<List<String>> aba = planilha.sheets.get(WorkoutSetsTable.SHEET);
        assertEquals(3, aba.size()); // cabeçalho + 1 linha do w2 + 1 linha do w1
        assertEquals(List.of("w2", "w1"), colunaDe(aba, 0));
        assertEquals("65.0", aba.get(2).get(10));
    }

    @Test
    void treinoApagadoSaiDaAba() {
        tabela.applyEvents(List.of(
                atualizado(treino("w1", "PUSH", "2026-10-21T15:00:00Z", "2026-10-21T15:00:00Z",
                        exercicio("supino", "Supino Reto", serie("normal", 60.0, 8)))),
                atualizado(treino("w2", "PULL", "2026-10-22T15:00:00Z", "2026-10-22T15:00:00Z",
                        exercicio("supino", "Supino Reto", serie("normal", 50.0, 10))))));

        WorkoutSetsTable.Result resultado = tabela.applyEvents(List.of(
                apagado("w1", "2026-10-24T10:00:00Z")));

        assertEquals(new WorkoutSetsTable.Result(0, 1, 1, 0), resultado);
        assertEquals(List.of("w2"), colunaDe(planilha.sheets.get(WorkoutSetsTable.SHEET), 0));
    }

    @Test
    void valeOEventoMaisRecenteIndependenteDaOrdem() {
        Workout versaoAntiga = treino("w1", "PUSH", "2026-10-21T15:00:00Z", "2026-10-21T16:00:00Z",
                exercicio("supino", "Supino Reto", serie("normal", 60.0, 8)));
        WorkoutEvent atualizacao = atualizado(versaoAntiga);
        WorkoutEvent exclusao = apagado("w1", "2026-10-22T10:00:00Z");

        // Treino apagado depois de atualizado: não pode sobrar nada, em qualquer ordem dos eventos.
        for (List<WorkoutEvent> ordem : List.of(List.of(atualizacao, exclusao), List.of(exclusao, atualizacao))) {
            InMemorySheets outra = new InMemorySheets();
            WorkoutSetsTable t = new WorkoutSetsTable(outra, List.of(SUPINO), Weeks.SAO_PAULO);
            t.applyEvents(ordem);
            assertEquals(1, outra.sheets.get(WorkoutSetsTable.SHEET).size(), "só o cabeçalho");
        }
    }

    @Test
    void aplicarOsMesmosEventosDuasVezesNaoDuplicaLinhas() {
        List<WorkoutEvent> eventos = List.of(
                atualizado(treino("w1", "PUSH", "2026-10-21T15:00:00Z", "2026-10-21T15:00:00Z",
                        exercicio("supino", "Supino Reto", serie("normal", 60.0, 8), serie("normal", 60.0, 7)))));

        tabela.applyEvents(eventos);
        tabela.applyEvents(eventos);

        assertEquals(3, planilha.sheets.get(WorkoutSetsTable.SHEET).size());
    }

    // ------------------------------------------------------------------
    // Leitura de volta
    // ------------------------------------------------------------------

    @Test
    void lerDeVoltaDevolveAsMesmasSeriesQueForamGravadas() {
        Workout treino = treino("w1", "PUSH", "2026-10-26T01:00:00Z", "2026-10-26T01:00:00Z",
                exercicio("supino", "Supino Reto", serie("warmup", 20.0, 12), serie("normal", 60.0, 8)),
                exercicio("flexao", "Flexão", serie("normal", null, null)));
        tabela.applyEvents(List.of(atualizado(treino)));

        List<SetRecord> lidas = tabela.readAll();

        assertEquals(SetRecords.of(treino, Map.of("supino", SUPINO, "flexao", FLEXAO), Weeks.SAO_PAULO), lidas);
    }

    @Test
    void abaVaziaOuSoComCabecalhoNaoTemSeries() {
        assertTrue(tabela.readAll().isEmpty());
    }

    @Test
    void fromRowEntendeADataComoNumeroDeSerieDoSheets() {
        // É assim que o Google devolve a leitura sem formatação: 46319 = 24/10/2026 e 46314 = 19/10/2026.
        List<Object> linha = List.of("w1", new BigDecimal("46319"), new BigDecimal("46314"), "PULL 2",
                "Puxada Alta", "473CF5B8", "lats", "upper_back, biceps", "normal", true,
                new BigDecimal("40"), new BigDecimal("12"), "", new BigDecimal("96"));

        SetRecord serie = WorkoutSetsTable.fromRow(linha);

        assertEquals(LocalDate.of(2026, 10, 24), serie.date());
        assertEquals(LocalDate.of(2026, 10, 19), serie.week());
        assertEquals(List.of("upper_back", "biceps"), serie.secondaryMuscles());
        assertEquals(40.0, serie.weightKg());
        assertEquals(12, serie.reps());
        assertEquals(96, serie.durationMinutes());
        assertTrue(serie.valid());
        assertNull(serie.rpe());
    }

    @Test
    void cabecalhoAntigoDaErroQueExplicaComoRecomecar() {
        planilha.ensureSheets(List.of(WorkoutSetsTable.SHEET));
        planilha.replaceRows(WorkoutSetsTable.SHEET, List.of(List.of("workout_id", "data")),
                SheetsGateway.InputMode.RAW);

        IllegalStateException naLeitura = assertThrows(IllegalStateException.class, tabela::readAll);
        IllegalStateException naAtualizacao = assertThrows(IllegalStateException.class, () -> tabela.applyEvents(List.of(
                atualizado(treino("w1", "PUSH", "2026-10-21T15:00:00Z", "2026-10-21T15:00:00Z",
                        exercicio("supino", "Supino Reto", serie("normal", 60.0, 8)))))));

        assertTrue(naLeitura.getMessage().contains("estado_sync"));
        assertTrue(naAtualizacao.getMessage().contains("estado_sync"));
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------

    private static List<String> colunaDe(List<List<String>> aba, int coluna) {
        List<String> valores = new ArrayList<>();
        for (int i = 1; i < aba.size(); i++) { // pula o cabeçalho
            String valor = aba.get(i).get(coluna);
            if (valores.isEmpty() || !valores.get(valores.size() - 1).equals(valor)) {
                valores.add(valor);
            }
        }
        return valores;
    }

    private static SetEntry serie(String tipo, Double carga, Integer reps) {
        return new SetEntry(0, tipo, carga, reps, null, null, null, null);
    }

    private static Exercise exercicio(String templateId, String titulo, SetEntry... series) {
        return new Exercise(0, titulo, null, templateId, List.of(series));
    }

    private static Workout treino(String id, String titulo, String inicio, String atualizadoEm, Exercise... exercicios) {
        OffsetDateTime start = OffsetDateTime.parse(inicio);
        return new Workout(id, titulo, null, start, start.plusHours(1),
                OffsetDateTime.parse(atualizadoEm), start, List.of(exercicios));
    }

    private static WorkoutEvent atualizado(Workout treino) {
        return new WorkoutEvent("updated", treino, null, null);
    }

    private static WorkoutEvent apagado(String id, String quando) {
        return new WorkoutEvent("deleted", null, id, OffsetDateTime.parse(quando));
    }
}
