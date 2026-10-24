package com.gabriel.hevydashboard.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VolumeReportTest {

    private static final LocalDate SEMANA_1 = LocalDate.of(2026, 10, 12);
    private static final LocalDate SEMANA_2 = LocalDate.of(2026, 10, 19);
    private static final LocalDate SEMANA_3 = LocalDate.of(2026, 10, 26);

    private static final Settings SETTINGS = new Settings(
            VolumeRules.DEFAULT,
            Map.of("chest", new TargetRange(1, 9), "biceps", new TargetRange(10, 20)),
            new VolumeLevels(List.of(
                    new VolumeLevel("baixo", new TargetRange(1, 9)),
                    new VolumeLevel("moderado", new TargetRange(10, 20)),
                    new VolumeLevel("alto", new TargetRange(21, TargetRange.NO_LIMIT)))),
            3,
            5);

    @Test
    void contaDiretasEIndiretasDeSeriesDeTrabalhoPorMusculoESemana() {
        List<SetRecord> series = new ArrayList<>();
        series.addAll(supino(SEMANA_1, "warmup", "normal", "normal", "failure"));

        List<VolumeReport.MuscleWeek> relatorio = VolumeReport.weekly(series, SETTINGS);

        assertEquals(3, resultado(relatorio, SEMANA_1, "chest").direct());
        assertEquals(0, resultado(relatorio, SEMANA_1, "chest").indirect());
        // Tríceps entra como indireta; ombro não está na lista de indiretas.
        assertEquals(0, resultado(relatorio, SEMANA_1, "triceps").direct());
        assertEquals(3, resultado(relatorio, SEMANA_1, "triceps").indirect());
        assertTrue(relatorio.stream().noneMatch(r -> r.muscle().equals("shoulders")));
    }

    @Test
    void naoTemBuracosEntreSemanasENemMusculosComFaixaSemSerie() {
        List<SetRecord> series = new ArrayList<>();
        series.addAll(supino(SEMANA_1, "normal"));
        series.addAll(supino(SEMANA_3, "normal"));

        List<VolumeReport.MuscleWeek> relatorio = VolumeReport.weekly(series, SETTINGS);

        // 3 semanas (a do meio sem treino) x 3 músculos (chest, biceps e triceps, que aparece como indireta).
        assertEquals(9, relatorio.size());
        assertEquals(0, resultado(relatorio, SEMANA_2, "chest").direct());
        assertEquals(0, resultado(relatorio, SEMANA_1, "biceps").direct());
    }

    @Test
    void classificaONivelEAPosicaoNaFaixaAlvo() {
        List<SetRecord> series = supino(SEMANA_1, "normal", "normal", "normal", "normal", "normal");

        List<VolumeReport.MuscleWeek> relatorio = VolumeReport.weekly(series, SETTINGS);

        VolumeReport.MuscleWeek peito = resultado(relatorio, SEMANA_1, "chest");
        assertEquals("baixo", peito.level());
        assertEquals(TargetRange.Status.WITHIN, peito.status()); // 5 séries, faixa 1-9

        VolumeReport.MuscleWeek biceps = resultado(relatorio, SEMANA_1, "biceps");
        assertEquals(VolumeLevels.NONE, biceps.level());
        assertEquals(TargetRange.Status.BELOW, biceps.status()); // 0 séries, faixa 10-20

        // Tríceps não tem faixa-alvo, então não tem posição.
        assertNull(resultado(relatorio, SEMANA_1, "triceps").status());
    }

    @Test
    void textoNoFormatoDeExibicao() {
        VolumeReport.MuscleWeek semana = new VolumeReport.MuscleWeek(SEMANA_1, "biceps", 11, 10, 3, "moderado", null);

        assertEquals("Bíceps: 11 diretas (indiretas 10)", semana.text());
    }

    @Test
    void frequenciaContaOsDiasDistintosComSerieDireta() {
        List<SetRecord> series = new ArrayList<>();
        series.addAll(supino(SEMANA_1, "normal", "normal", "normal"));           // 1 dia, 3 séries
        series.add(new SetRecord("w-2", SEMANA_1.plusDays(2), SEMANA_1, "PUSH", 60, "Supino", "supino", "chest",
                List.of("triceps", "shoulders"), "normal", true, 60.0, 8, null));  // 2º dia
        series.add(new SetRecord("w-3", SEMANA_1.plusDays(4), SEMANA_1, "PULL", 60, "Rosca", "rosca", "biceps",
                List.of(), "normal", true, 20.0, 10, null));                       // bíceps direto

        List<VolumeReport.MuscleWeek> relatorio = VolumeReport.weekly(series, SETTINGS);

        assertEquals(2, resultado(relatorio, SEMANA_1, "chest").sessions());
        assertEquals(1, resultado(relatorio, SEMANA_1, "biceps").sessions());
        // O tríceps só recebeu indireta (supino), e indireta não conta como dia de treino do músculo.
        assertEquals(0, resultado(relatorio, SEMANA_1, "triceps").sessions());
    }

    @Test
    void semSeriesDeTrabalhoNaoTemRelatorio() {
        assertTrue(VolumeReport.weekly(supino(SEMANA_1, "warmup", "warmup"), SETTINGS).isEmpty());
        assertTrue(VolumeReport.weekly(List.of(), SETTINGS).isEmpty());
    }

    @Test
    void nomesEmPortuguesComReservaParaMusculoDesconhecido() {
        assertEquals("Posterior de coxa", MuscleNames.label("hamstrings"));
        assertEquals("Costas", MuscleNames.label("back"));
        assertEquals("full body", MuscleNames.label("full_body"));
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------

    private static VolumeReport.MuscleWeek resultado(List<VolumeReport.MuscleWeek> relatorio, LocalDate semana, String musculo) {
        return relatorio.stream()
                .filter(r -> r.week().equals(semana) && r.muscle().equals(musculo))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Sem linha de " + musculo + " na semana " + semana));
    }

    /** Um supino (peito; secundários tríceps e ombro) com uma série para cada tipo informado. */
    private static List<SetRecord> supino(LocalDate semana, String... tipos) {
        List<SetRecord> series = new ArrayList<>();
        for (String tipo : tipos) {
            series.add(new SetRecord("w-" + semana, semana, semana, "PUSH", 60, "Supino", "supino", "chest",
                    List.of("triceps", "shoulders"), tipo, !tipo.equals("warmup"), 60.0, 8, null));
        }
        return series;
    }
}
