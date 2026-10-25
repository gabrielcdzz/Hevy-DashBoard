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
    // Comparação de duas séries
    // ------------------------------------------------------------------

    @Test
    void aCargaTemPrioridadeSobreAsRepeticoes() {
        assertEquals(ExerciseReport.Change.LOAD_UP, ExerciseReport.compare(60, 10, 62.5, 6));   // carga sobe, repetições caem
        assertEquals(ExerciseReport.Change.LOAD_DOWN, ExerciseReport.compare(60, 10, 55, 15));  // carga cai, repetições sobem
    }

    @Test
    void comAMesmaCargaOQueMudaSaoAsRepeticoes() {
        assertEquals(ExerciseReport.Change.REPS_UP, ExerciseReport.compare(60, 8, 60, 10));
        assertEquals(ExerciseReport.Change.REPS_DOWN, ExerciseReport.compare(60, 10, 60, 8));
        assertEquals(ExerciseReport.Change.SAME, ExerciseReport.compare(60, 10, 60, 10));
    }

    @Test
    void diferencaMinimaDeCargaEhRuidoDeArredondamentoEnaoMudanca() {
        assertEquals(ExerciseReport.Change.SAME, ExerciseReport.compare(62.5, 10, 62.500001, 10));
    }

    // ------------------------------------------------------------------
    // Quais séries entram e a série mais pesada de cada treino
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
        assertEquals(new ExerciseReport.SessionPoint(LocalDate.of(2026, 10, 1), 60.0, 8),
                resultado.exercises().get(0).sessions().get(0));
    }

    @Test
    void cadaTreinoVirouASerieMaisPesadaEComAMesmaCargaAMaisRepetida() {
        List<SetRecord> series = List.of(
                serie("supino", "Supino", "2026-10-01", 60, 8, true),
                serie("supino", "Supino", "2026-10-01", 70, 3, true),
                serie("supino", "Supino", "2026-10-01", 70, 5, true),   // a mais pesada, com mais repetições
                serie("supino", "Supino", "2026-10-01", 50, 15, true)); // mais repetições, mas mais leve

        ExerciseReport.SessionPoint ponto = ExerciseReport.build(series, HOJE, 3).exercises().get(0).sessions().get(0);

        assertEquals(70.0, ponto.weightKg());
        assertEquals(5, ponto.reps());
    }

    @Test
    void repeticoesAbsurdasSaoTratadasComoErroDeDigitacao() {
        List<SetRecord> series = List.of(
                serie("rosca", "Rosca Scott", "2026-09-01", 55, 79, true),  // era 9
                serie("rosca", "Rosca Scott", "2026-09-08", 55, 9, true),
                serie("rosca", "Rosca Scott", "2026-09-15", 55, 34, true)); // série longa de verdade

        ExerciseReport.ExerciseSummary rosca = ExerciseReport.build(series, HOJE, 3).exercises().get(0);

        assertEquals(34.0, rosca.records().maxReps().value());
        assertEquals(2, rosca.sessions().size());
        // A série de 79 repetições continua sendo uma série de trabalho para o volume.
        assertTrue(series.get(0).valid());
    }

    // ------------------------------------------------------------------
    // Um exercício só: mesmo template ou mesmo nome
    // ------------------------------------------------------------------

    @Test
    void templatesDiferentesComOMesmoNomeSaoUmExercicioSo() {
        List<SetRecord> series = List.of(
                serie("remada-antiga", "Remada Iso-Lateral (Máquina)", "2026-08-01", 50, 10, true),
                serie("remada-nova", "  remada iso-lateral (máquina) ", "2026-10-10", 55, 10, true),
                serie("supino", "Supino", "2026-10-10", 60, 8, true));

        List<ExerciseReport.ExerciseSummary> exercicios = ExerciseReport.build(series, HOJE, 3).exercises();

        assertEquals(2, exercicios.size());
        ExerciseReport.ExerciseSummary remada = exercicios.stream()
                .filter(e -> e.name().toLowerCase().contains("remada")).findFirst().orElseThrow();
        assertEquals(2, remada.sessions().size());
        assertEquals("remada-nova", remada.templateId()); // o da sessão mais recente
        assertEquals(55.0, remada.records().maxWeightKg().value());
    }

    @Test
    void nomesDiferentesComTemplatesDiferentesContinuamSeparados() {
        List<SetRecord> series = List.of(
                serie("a", "Elevação Lateral (Halter)", "2026-08-01", 10, 12, true),
                serie("b", "Elevação Lateral (Halter) 1", "2026-10-01", 12, 12, true));

        assertEquals(2, ExerciseReport.build(series, HOJE, 3).exercises().size());
    }

    @Test
    void exercicioRenomeadoComOMesmoTemplateContinuaUmSo() {
        List<SetRecord> series = List.of(
                serie("remada", "Remada Curvada", "2026-09-01", 40, 10, true),
                serie("remada", "Remada Iso-Lateral", "2026-10-01", 45, 10, true));

        List<ExerciseReport.ExerciseSummary> exercicios = ExerciseReport.build(series, HOJE, 3).exercises();

        assertEquals(1, exercicios.size());
        assertEquals("Remada Iso-Lateral", exercicios.get(0).name()); // o nome da série mais recente
    }

    // ------------------------------------------------------------------
    // O que mudou do último treino para o anterior
    // ------------------------------------------------------------------

    @Test
    void ultimoTreinoContraOAnterior() {
        ExerciseReport.Comparison subiuACarga = ultimaMudanca(
                serie("a", "Supino", "2026-10-01", 60, 8, true), serie("a", "Supino", "2026-10-08", 62.5, 8, true));
        ExerciseReport.Comparison subiuReps = ultimaMudanca(
                serie("a", "Supino", "2026-10-01", 60, 8, true), serie("a", "Supino", "2026-10-08", 60, 10, true));
        ExerciseReport.Comparison caiuReps = ultimaMudanca(
                serie("a", "Supino", "2026-10-01", 60, 10, true), serie("a", "Supino", "2026-10-08", 60, 8, true));
        ExerciseReport.Comparison caiuACarga = ultimaMudanca(
                serie("a", "Supino", "2026-10-01", 60, 8, true), serie("a", "Supino", "2026-10-08", 55, 8, true));
        ExerciseReport.Comparison igual = ultimaMudanca(
                serie("a", "Supino", "2026-10-01", 60, 8, true), serie("a", "Supino", "2026-10-08", 60, 8, true));

        assertEquals(ExerciseReport.Change.LOAD_UP, subiuACarga.change());
        assertEquals(ExerciseReport.Change.REPS_UP, subiuReps.change());
        assertEquals(ExerciseReport.Change.REPS_DOWN, caiuReps.change());
        assertEquals(ExerciseReport.Change.LOAD_DOWN, caiuACarga.change());
        assertEquals(ExerciseReport.Change.SAME, igual.change());
    }

    @Test
    void aComparacaoMostraOsDoisTreinosComCargaERepeticoes() {
        ExerciseReport.Comparison comparacao = ultimaMudanca(
                serie("a", "Supino", "2026-10-01", 60, 8, true), serie("a", "Supino", "2026-10-08", 62.5, 6, true));

        assertEquals(new ExerciseReport.SessionPoint(LocalDate.of(2026, 10, 1), 60.0, 8), comparacao.previous());
        assertEquals(new ExerciseReport.SessionPoint(LocalDate.of(2026, 10, 8), 62.5, 6), comparacao.current());
        assertEquals(ExerciseReport.Change.LOAD_UP, comparacao.change()); // subiu a carga, mesmo com menos repetições
    }

    @Test
    void usaOsDoisUltimosTreinosEnaoOPrimeiro() {
        List<SetRecord> series = List.of(
                serie("a", "Supino", "2026-09-01", 40, 8, true),
                serie("a", "Supino", "2026-09-15", 60, 8, true),
                serie("a", "Supino", "2026-10-01", 60, 10, true));

        ExerciseReport.Comparison comparacao = ExerciseReport.build(series, HOJE, 3).exercises().get(0).lastChange();

        assertEquals(ExerciseReport.Change.REPS_UP, comparacao.change());
        assertEquals(LocalDate.of(2026, 9, 15), comparacao.previous().date());
    }

    @Test
    void exercicioComUmTreinoSoNaoTemComparacao() {
        List<SetRecord> series = List.of(serie("a", "Supino", "2026-10-01", 60, 8, true));

        assertNull(ExerciseReport.build(series, HOJE, 3).exercises().get(0).lastChange());
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
                serie("supino", "Supino", "2026-09-08", 65, 8, true),  // só a carga sobe
                serie("supino", "Supino", "2026-09-15", 65, 8, true),  // igual: nenhum recorde
                serie("supino", "Supino", "2026-09-22", 60, 12, true)); // só as repetições sobem

        List<ExerciseReport.PrEvent> eventos = ExerciseReport.build(series, HOJE, 3).prEvents();

        // Na mesma data, a ordem segue a do enum: carga, repetições, 1RM. A carga sobe em 08/09 (65 x 8 rende
        // 82,3 de 1RM contra 76) e as repetições sobem em 22/09 (60 x 12 rende 84).
        assertEquals(List.of(
                        List.of("2026-09-08", ExerciseReport.PrType.WEIGHT),
                        List.of("2026-09-08", ExerciseReport.PrType.ONE_REP_MAX),
                        List.of("2026-09-22", ExerciseReport.PrType.REPS),
                        List.of("2026-09-22", ExerciseReport.PrType.ONE_REP_MAX)),
                eventos.stream().map(e -> List.of(e.date().toString(), e.type())).toList());
    }

    @Test
    void recordeDeOneRmSoAconteceQuandoASerieRendeMaisQueQualquerAnterior() {
        List<SetRecord> series = List.of(
                serie("supino", "Supino", "2026-09-01", 60, 8, true),    // 1RM 76
                serie("supino", "Supino", "2026-09-08", 62.5, 5, true),  // carga sobe, mas rende 72,9: sem recorde de 1RM
                serie("supino", "Supino", "2026-09-15", 57.5, 12, true)); // mais repetições e rende 80,5: recorde de 1RM

        List<ExerciseReport.PrEvent> umRm = ExerciseReport.build(series, HOJE, 3).prEvents().stream()
                .filter(e -> e.type() == ExerciseReport.PrType.ONE_REP_MAX).toList();

        assertEquals(1, umRm.size());
        assertEquals(LocalDate.of(2026, 9, 15), umRm.get(0).date());
        assertEquals(57.5 * (1 + 12 / 30.0), umRm.get(0).value(), 1e-9);
    }

    @Test
    void epleyUsaCargaVezesUmMaisRepeticoesSobreTrinta() {
        assertEquals(76.0, OneRepMax.estimate(60, 8), 1e-9);
        assertEquals(100.0 * (1 + 5 / 30.0), OneRepMax.estimate(100, 5), 1e-9);
    }

    // ------------------------------------------------------------------
    // Estagnação (hoje = sábado 24/10; recente = 05/10 a 24/10; anterior = 14/09 a 04/10)
    // ------------------------------------------------------------------

    @Test
    void cargaMaiorNasUltimasSemanasEProgresso() {
        List<SetRecord> series = List.of(
                serie("supino", "Supino", "2026-09-20", 60, 8, true),
                serie("supino", "Supino", "2026-10-15", 65, 6, true));  // mais carga, mesmo com menos repetições

        ExerciseReport.Trend tendencia = tendenciaDe(series);

        assertEquals(ExerciseReport.Progress.PROGRESSING, tendencia.progress());
        assertEquals(60.0, tendencia.previous().weightKg());
        assertEquals(65.0, tendencia.recent().weightKg());
    }

    @Test
    void maisRepeticoesComAMesmaCargaTambemEProgresso() {
        List<SetRecord> series = List.of(
                serie("supino", "Supino", "2026-09-20", 60, 8, true),
                serie("supino", "Supino", "2026-10-15", 60, 10, true));

        assertEquals(ExerciseReport.Progress.PROGRESSING, tendenciaDe(series).progress());
    }

    @Test
    void igualOuPiorEEstagnacao() {
        List<SetRecord> igual = List.of(
                serie("supino", "Supino", "2026-09-20", 60, 8, true),
                serie("supino", "Supino", "2026-10-15", 60, 8, true));
        List<SetRecord> menosCarga = List.of(
                serie("supino", "Supino", "2026-09-20", 60, 8, true),
                serie("supino", "Supino", "2026-10-15", 55, 12, true));
        List<SetRecord> menosRepeticoes = List.of(
                serie("supino", "Supino", "2026-09-20", 60, 10, true),
                serie("supino", "Supino", "2026-10-15", 60, 8, true));

        assertEquals(ExerciseReport.Progress.STAGNANT, tendenciaDe(igual).progress());
        assertEquals(ExerciseReport.Progress.STAGNANT, tendenciaDe(menosCarga).progress());
        assertEquals(ExerciseReport.Progress.STAGNANT, tendenciaDe(menosRepeticoes).progress());
    }

    @Test
    void usaAMelhorSerieDeCadaJanelaEnaoAUltima() {
        List<SetRecord> series = List.of(
                serie("supino", "Supino", "2026-09-20", 60, 8, true),
                serie("supino", "Supino", "2026-10-06", 65, 8, true),  // melhor das últimas semanas
                serie("supino", "Supino", "2026-10-20", 55, 8, true)); // o último treino foi mais leve (deload)

        ExerciseReport.Trend tendencia = tendenciaDe(series);

        assertEquals(ExerciseReport.Progress.PROGRESSING, tendencia.progress());
        assertEquals(65.0, tendencia.recent().weightKg());
    }

    @Test
    void exercicioNaoTreinadoNasUltimasSemanasEInativoENaoEstagnado() {
        List<SetRecord> series = List.of(
                serie("supino", "Supino", "2026-09-10", 60, 8, true),
                serie("supino", "Supino", "2026-09-20", 60, 8, true));

        ExerciseReport.Trend tendencia = tendenciaDe(series);

        assertEquals(ExerciseReport.Progress.INACTIVE, tendencia.progress());
        assertNull(tendencia.recent());
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

        // Com 5 semanas, a janela recente cobre 21/09 a 24/10 e a anterior cobre 17/08 a 20/09.
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

    private static ExerciseReport.Comparison ultimaMudanca(SetRecord antes, SetRecord depois) {
        return ExerciseReport.build(List.of(antes, depois), HOJE, 3).exercises().get(0).lastChange();
    }

    private static SetRecord serie(String templateId, String nome, String data, Number carga, Integer reps, boolean valida) {
        LocalDate date = LocalDate.parse(data);
        return new SetRecord("w-" + data, date, Weeks.startOf(date), "PUSH", 60, nome, templateId, "chest",
                new ArrayList<>(), valida ? "normal" : "warmup", valida,
                carga == null ? null : carga.doubleValue(), reps, null);
    }
}
