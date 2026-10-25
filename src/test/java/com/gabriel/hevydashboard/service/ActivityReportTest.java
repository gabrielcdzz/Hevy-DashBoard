package com.gabriel.hevydashboard.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActivityReportTest {

    // Segundas-feiras: 05/10, 12/10, 19/10, 26/10 e 02/11/2026.
    private static final LocalDate S1 = LocalDate.of(2026, 10, 5);
    private static final LocalDate S2 = LocalDate.of(2026, 10, 12);
    private static final LocalDate S3 = LocalDate.of(2026, 10, 19);
    private static final LocalDate S4 = LocalDate.of(2026, 10, 26);
    private static final LocalDate S5 = LocalDate.of(2026, 11, 2);

    private static Settings settings(int treinosPorSemana) {
        return new Settings(VolumeRules.DEFAULT, 3, treinosPorSemana);
    }

    // ------------------------------------------------------------------
    // Treinos, séries e duração
    // ------------------------------------------------------------------

    @Test
    void somaTreinosESeriesDeTrabalhoDeCadaSemana() {
        List<SetRecord> series = new ArrayList<>();
        series.add(serie("w1", S1, "warmup", 90));    // aquecimento não conta como série
        series.add(serie("w1", S1, "normal", 90));
        series.add(serie("w1", S1, "normal", 90));
        series.add(serie("w2", S1.plusDays(2), "normal", 60));

        ActivityReport.WeekSummary semana = ActivityReport.build(series, settings(2), S1.plusDays(5)).weeks().get(0);

        assertEquals(2, semana.workouts());
        assertEquals(3, semana.validSets());
        assertTrue(semana.complete());
    }

    @Test
    void duracaoMediaConsideraCadaTreinoUmaVezSoEIgnoraOsSemDuracao() {
        List<SetRecord> series = List.of(
                serie("w1", S1, "normal", 90),
                serie("w1", S1, "normal", 90),   // várias séries do mesmo treino: 1 treino
                serie("w2", S1.plusDays(1), "normal", 60),
                serie("w3", S1.plusDays(2), "normal", null));

        ActivityReport.Result resultado = ActivityReport.build(series, settings(5), S1.plusDays(5));

        assertEquals(75.0, resultado.averageDurationMinutes(), 1e-9);
        assertEquals(75.0, resultado.weeks().get(0).averageDurationMinutes(), 1e-9);
    }

    // ------------------------------------------------------------------
    // Semanas, variação e consistência
    // ------------------------------------------------------------------

    @Test
    void semanasSemBuracosEVariacaoPercentualDasSeries() {
        List<SetRecord> series = new ArrayList<>();
        series.addAll(treinoComSeries("w1", S1, 10));
        series.addAll(treinoComSeries("w2", S3, 15));   // a semana S2 ficou sem treino

        List<ActivityReport.WeekSummary> semanas = ActivityReport.build(series, settings(5), S3.plusDays(2)).weeks();

        assertEquals(List.of(S1, S2, S3), semanas.stream().map(ActivityReport.WeekSummary::week).toList());
        assertNull(semanas.get(0).setsChangePercent());      // primeira semana: sem anterior
        assertEquals(-100.0, semanas.get(1).setsChangePercent(), 1e-9);
        assertNull(semanas.get(2).setsChangePercent());      // a anterior teve zero: não dá para calcular
    }

    @Test
    void variacaoComparaComASemanaAnterior() {
        List<SetRecord> series = new ArrayList<>();
        series.addAll(treinoComSeries("w1", S1, 10));
        series.addAll(treinoComSeries("w2", S2, 15));

        List<ActivityReport.WeekSummary> semanas = ActivityReport.build(series, settings(5), S2.plusDays(3)).weeks();

        assertEquals(50.0, semanas.get(1).setsChangePercent(), 1e-9);
    }

    @Test
    void listaDeSemanasVaiAteHojeEMarcaASemanaEmAndamento() {
        List<SetRecord> series = treinoComSeries("w1", S1, 10);

        List<ActivityReport.WeekSummary> semanas = ActivityReport.build(series, settings(5), S3.plusDays(4)).weeks();

        assertEquals(3, semanas.size()); // S1, S2 e a semana de hoje (S3), mesmo sem treino
        assertFalse(semanas.get(1).inProgress());
        assertTrue(semanas.get(2).inProgress());
        assertEquals(0, semanas.get(2).workouts());
    }

    @Test
    void sequenciaDeSemanasCompletasNaoQuebraComASemanaAtualIncompleta() {
        // Meta de 2 treinos: S1 completa, S2 completa, S3 incompleta (1), S4 completa, S5 (hoje) com 1 treino.
        List<SetRecord> series = new ArrayList<>();
        series.addAll(treinosNaSemana("a", S1, 2));
        series.addAll(treinosNaSemana("b", S2, 2));
        series.addAll(treinosNaSemana("c", S3, 1));
        series.addAll(treinosNaSemana("d", S4, 2));
        series.addAll(treinosNaSemana("e", S5, 1));

        ActivityReport.Consistency consistencia = ActivityReport.build(series, settings(2), S5.plusDays(2)).consistency();

        assertEquals(2, consistencia.bestStreakWeeks());     // S1 e S2
        assertEquals(1, consistencia.currentStreakWeeks());  // só S4: a S5 ainda pode fechar, a S3 quebrou
    }

    @Test
    void semanaAtualJaCompletaEntraNaSequencia() {
        List<SetRecord> series = new ArrayList<>();
        series.addAll(treinosNaSemana("d", S4, 2));
        series.addAll(treinosNaSemana("e", S5, 2));

        ActivityReport.Consistency consistencia = ActivityReport.build(series, settings(2), S5.plusDays(2)).consistency();

        assertEquals(2, consistencia.currentStreakWeeks());
        assertEquals(2, consistencia.bestStreakWeeks());
    }

    @Test
    void semanaPassadaIncompletaQuebraASequenciaAtual() {
        List<SetRecord> series = new ArrayList<>();
        series.addAll(treinosNaSemana("a", S1, 2));
        series.addAll(treinosNaSemana("b", S2, 1));   // fechou incompleta

        ActivityReport.Consistency consistencia = ActivityReport.build(series, settings(2), S3.plusDays(1)).consistency();

        assertEquals(0, consistencia.currentStreakWeeks());
        assertEquals(1, consistencia.bestStreakWeeks());
    }

    @Test
    void semDadosNaoQuebra() {
        ActivityReport.Result resultado = ActivityReport.build(List.of(), settings(5), S1);

        assertTrue(resultado.weeks().isEmpty());
        assertEquals(0, resultado.consistency().currentStreakWeeks());
        assertNull(resultado.averageDurationMinutes());
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------

    private static SetRecord serie(String treino, LocalDate data, String tipo, Integer duracao) {
        return new SetRecord(treino, data, Weeks.startOf(data), "TREINO", duracao, "Exercício", "ex-chest",
                "chest", List.of(), tipo, !tipo.equals("warmup"), 60.0, 8, null);
    }

    /** Um treino com {@code series} séries de trabalho. */
    private static List<SetRecord> treinoComSeries(String treino, LocalDate data, int series) {
        List<SetRecord> lista = new ArrayList<>();
        for (int i = 0; i < series; i++) {
            lista.add(serie(treino, data, "normal", 60));
        }
        return lista;
    }

    /** {@code quantos} treinos de uma série cada, em dias seguidos a partir da segunda-feira da semana. */
    private static List<SetRecord> treinosNaSemana(String prefixo, LocalDate semana, int quantos) {
        List<SetRecord> lista = new ArrayList<>();
        for (int i = 0; i < quantos; i++) {
            lista.add(serie(prefixo + i, semana.plusDays(i), "normal", 60));
        }
        return lista;
    }
}
