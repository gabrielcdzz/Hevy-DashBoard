package com.gabriel.hevydashboard.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExerciseReportTest {

    /** Sábado 24/10/2026: a semana atual começa em 19/10 e, com N = 3, a janela recente começa em 05/10. */
    private static final LocalDate HOJE = LocalDate.of(2026, 10, 24);

    // ------------------------------------------------------------------
    // 1RM
    // ------------------------------------------------------------------

    @Test
    void epleyUsaCargaVezesUmMaisRepeticoesSobreTrinta() {
        assertEquals(76.0, OneRepMax.estimate(60, 8), 1e-9);
        assertEquals(100.0 * (1 + 5 / 30.0), OneRepMax.estimate(100, 5), 1e-9);
    }

    // ------------------------------------------------------------------
    // Quais séries entram
    // ------------------------------------------------------------------

    @Test
    void soSeriesDeTrabalhoComCargaERepeticoesEntram() {
        List<SetRecord> series = List.of(
                serie("supino", "Supino", "2026-10-01", 20, 12, false),  // aquecimento
                serie("supino", "Supino", "2026-10-01", 0, 10, true),    // exercício ainda não feito (carga 0)
                serie("supino", "Supino", "2026-10-01", null, 10, true), // peso corporal
                serie("supino", "Supino", "2026-10-01", 60, null, true), // sem repetições
                serie("supino", "Supino", "2026-10-01", 60, 8, true));

        ExerciseReport.Result resultado = ExerciseReport.build(series, HOJE, 3);

        assertEquals(1, resultado.exercises().size());
        ExerciseReport.SessionPoint sessao = resultado.exercises().get(0).sessions().get(0);
        assertEquals(60.0, sessao.maxWeightKg());
        assertEquals(76.0, sessao.bestOneRepMax(), 1e-9);
    }

    @Test
    void repeticoesAbsurdasSaoTratadasComoErroDeDigitacao() {
        List<SetRecord> series = List.of(
                serie("rosca", "Rosca Scott", "2026-09-01", 55, 79, true),  // era 9
                serie("rosca", "Rosca Scott", "2026-09-08", 55, 9, true),
                serie("rosca", "Rosca Scott", "2026-09-15", 55, 34, true)); // série longa de verdade

        ExerciseReport.Records recordes = ExerciseReport.build(series, HOJE, 3).exercises().get(0).records();

        assertEquals(34.0, recordes.maxReps().value());
        assertEquals(55 * (1 + 34 / 30.0), recordes.bestOneRepMax().value(), 1e-9);
        // A série de 79 repetições continua sendo uma série de trabalho para o volume.
        assertTrue(series.get(0).valid());
    }

    @Test
    void variasSeriesDoMesmoDiaViramUmPontoComOMelhorDeCada() {
        List<SetRecord> series = List.of(
                serie("supino", "Supino", "2026-10-01", 60, 8, true),   // 1RM 76
                serie("supino", "Supino", "2026-10-01", 70, 3, true),   // 1RM 77, carga maior
                serie("supino", "Supino", "2026-10-01", 50, 15, true)); // 1RM 75

        ExerciseReport.SessionPoint ponto = ExerciseReport.build(series, HOJE, 3)
                .exercises().get(0).sessions().get(0);

        assertEquals(70.0, ponto.maxWeightKg());
        assertEquals(77.0, ponto.bestOneRepMax(), 1e-9);
    }

    @Test
    void exerciciosSaoSeparadosPeloTemplateENomeVemDaSerieMaisRecente() {
        List<SetRecord> series = List.of(
                serie("remada", "Remada Curvada", "2026-09-01", 40, 10, true),
                serie("remada", "Remada Iso-Lateral", "2026-10-01", 45, 10, true),
                serie("supino", "Supino", "2026-10-01", 60, 8, true));

        List<ExerciseReport.ExerciseSummary> exercicios = ExerciseReport.build(series, HOJE, 3).exercises();

        assertEquals(List.of("Remada Iso-Lateral", "Supino"), exercicios.stream().map(ExerciseReport.ExerciseSummary::name).toList());
        assertEquals(2, exercicios.get(0).sessions().size());
    }

    // ------------------------------------------------------------------
    // Recordes
    // ------------------------------------------------------------------

    @Test
    void recordesGuardamOValorEAPrimeiraDataEmQueForamAlcancados() {
        List<SetRecord> series = List.of(
                serie("supino", "Supino", "2026-09-01", 60, 8, true),
                serie("supino", "Supino", "2026-09-15", 70, 5, true),
                serie("supino", "Supino", "2026-10-01", 70, 5, true),   // repetiu a carga: não muda a data
                serie("supino", "Supino", "2026-10-10", 50, 15, true)); // mais repetições, com carga menor

        ExerciseReport.Records recordes = ExerciseReport.build(series, HOJE, 3).exercises().get(0).records();

        assertEquals(new ExerciseReport.Mark(70.0, LocalDate.of(2026, 9, 15)), recordes.maxWeightKg());
        assertEquals(new ExerciseReport.Mark(15.0, LocalDate.of(2026, 10, 10)), recordes.maxReps());
        assertEquals(LocalDate.of(2026, 9, 15), recordes.bestOneRepMax().date());
        assertEquals(70 * (1 + 5 / 30.0), recordes.bestOneRepMax().value(), 1e-9);
    }

    @Test
    void primeiraSessaoSoEstabeleceABaseEnaoContaComoRecorde() {
        List<SetRecord> series = List.of(serie("supino", "Supino", "2026-09-01", 60, 8, true));

        assertTrue(ExerciseReport.build(series, HOJE, 3).prEvents().isEmpty());
    }

    @Test
    void registraCadaRecordeBatidoComSeuTipoEData() {
        List<SetRecord> series = List.of(
                serie("supino", "Supino", "2026-09-01", 60, 8, true),
                serie("supino", "Supino", "2026-09-08", 65, 8, true),  // carga e 1RM sobem, repetições não
                serie("supino", "Supino", "2026-09-15", 65, 8, true),  // igual: nenhum recorde
                serie("supino", "Supino", "2026-09-22", 60, 12, true)); // repetições sobem; 1RM 84 > 82,3

        List<ExerciseReport.PrEvent> eventos = ExerciseReport.build(series, HOJE, 3).prEvents();

        // Na mesma data, a ordem segue a do enum: carga, repetições, 1RM.
        assertEquals(List.of(
                        evento("2026-09-08", ExerciseReport.PrType.WEIGHT),
                        evento("2026-09-08", ExerciseReport.PrType.ONE_REP_MAX),
                        evento("2026-09-22", ExerciseReport.PrType.REPS),
                        evento("2026-09-22", ExerciseReport.PrType.ONE_REP_MAX)),
                eventos.stream().map(e -> evento(e.date().toString(), e.type())).toList());
    }

    // ------------------------------------------------------------------
    // Estagnação (hoje = sábado 24/10; recente = 05/10 a 24/10; anterior = 14/09 a 04/10)
    // ------------------------------------------------------------------

    @Test
    void melhoraDoUmRMNasUltimasSemanasEProgresso() {
        List<SetRecord> series = List.of(
                serie("supino", "Supino", "2026-09-20", 60, 8, true),
                serie("supino", "Supino", "2026-10-15", 65, 8, true));

        ExerciseReport.Trend tendencia = tendenciaDe(series);

        assertEquals(ExerciseReport.Progress.PROGRESSING, tendencia.progress());
        assertEquals(76.0, tendencia.previousBest(), 1e-9);
    }

    @Test
    void mesmoUmRMOuMenorEEstagnacao() {
        List<SetRecord> igual = List.of(
                serie("supino", "Supino", "2026-09-20", 60, 8, true),
                serie("supino", "Supino", "2026-10-15", 60, 8, true));
        List<SetRecord> menor = List.of(
                serie("supino", "Supino", "2026-09-20", 60, 8, true),
                serie("supino", "Supino", "2026-10-15", 55, 8, true));

        assertEquals(ExerciseReport.Progress.STAGNANT, tendenciaDe(igual).progress());
        assertEquals(ExerciseReport.Progress.STAGNANT, tendenciaDe(menor).progress());
    }

    @Test
    void exercicioNaoTreinadoNasUltimasSemanasEInativoENaoEstagnado() {
        List<SetRecord> series = List.of(
                serie("supino", "Supino", "2026-09-10", 60, 8, true),
                serie("supino", "Supino", "2026-09-20", 60, 8, true));

        ExerciseReport.Trend tendencia = tendenciaDe(series);

        assertEquals(ExerciseReport.Progress.INACTIVE, tendencia.progress());
        assertNull(tendencia.recentBest());
    }

    @Test
    void exercicioNovoSemSemanasAnterioresNaoTemComparacao() {
        List<SetRecord> series = List.of(serie("supino", "Supino", "2026-10-15", 60, 8, true));

        assertEquals(ExerciseReport.Progress.NO_BASELINE, tendenciaDe(series).progress());
    }

    @Test
    void asJanelasTemOTamanhoConfigurado() {
        List<SetRecord> series = List.of(
                serie("supino", "Supino", "2026-09-20", 60, 8, true),
                serie("supino", "Supino", "2026-10-15", 65, 8, true));

        // Com 5 semanas, a janela recente já cobre 21/09 a 24/10 e a anterior cobre 17/08 a 20/09.
        ExerciseReport.Trend cinco = ExerciseReport.build(series, HOJE, 5).exercises().get(0).trend();
        // Com 1 semana, só vale a semana atual (a partir de 19/10), onde não há treino.
        ExerciseReport.Trend uma = ExerciseReport.build(series, HOJE, 1).exercises().get(0).trend();

        assertEquals(ExerciseReport.Progress.PROGRESSING, cinco.progress());
        assertEquals(ExerciseReport.Progress.INACTIVE, uma.progress());
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------

    private static ExerciseReport.Trend tendenciaDe(List<SetRecord> series) {
        return ExerciseReport.build(series, HOJE, 3).exercises().get(0).trend();
    }

    private static List<Object> evento(String data, ExerciseReport.PrType tipo) {
        return List.of(data, tipo);
    }

    private static SetRecord serie(String templateId, String nome, String data, Integer carga, Integer reps, boolean valida) {
        LocalDate date = LocalDate.parse(data);
        return new SetRecord("w-" + data, date, Weeks.startOf(date), "PUSH", 60, nome, templateId, "chest",
                new ArrayList<>(), valida ? "normal" : "warmup", valida,
                carga == null ? null : carga.doubleValue(), reps, null);
    }
}
