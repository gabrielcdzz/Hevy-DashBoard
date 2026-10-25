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

    /** Hoje é sexta da semana 3: as semanas 1 e 2 estão encerradas e a 3 está em andamento. */
    private static final LocalDate HOJE = LocalDate.of(2026, 10, 30);

    /** O planejado das rotinas: 5 séries diretas de peito e 10 de bíceps por semana. */
    private static final Map<String, MuscleVolume> PLANEJADO = Map.of(
            "chest", new MuscleVolume(5, 0),
            "biceps", new MuscleVolume(10, 18));

    private static final Settings SETTINGS = Settings.defaults();

    // ------------------------------------------------------------------
    // Contagem
    // ------------------------------------------------------------------

    @Test
    void contaDiretasEIndiretasDeSeriesDeTrabalhoPorMusculoESemana() {
        List<SetRecord> series = supino(SEMANA_1, "warmup", "normal", "normal", "failure");

        List<VolumeReport.MuscleWeek> relatorio = VolumeReport.weekly(series, SETTINGS, Map.of(), HOJE);

        assertEquals(3, resultado(relatorio, SEMANA_1, "chest").direct());
        assertEquals(0, resultado(relatorio, SEMANA_1, "chest").indirect());
        // Tríceps entra como indireta; ombro não está na lista de indiretas.
        assertEquals(0, resultado(relatorio, SEMANA_1, "triceps").direct());
        assertEquals(3, resultado(relatorio, SEMANA_1, "triceps").indirect());
        assertTrue(relatorio.stream().noneMatch(r -> r.muscle().equals("shoulders")));
    }

    @Test
    void semSeriesDeTrabalhoNaoTemRelatorio() {
        assertTrue(VolumeReport.weekly(supino(SEMANA_1, "warmup", "warmup"), SETTINGS, PLANEJADO, HOJE).isEmpty());
        assertTrue(VolumeReport.weekly(List.of(), SETTINGS, PLANEJADO, HOJE).isEmpty());
    }

    @Test
    void naoTemBuracosAteASemanaDeHojeEOsMusculosPlanejadosAparecemComZero() {
        List<SetRecord> series = new ArrayList<>();
        series.addAll(supino(SEMANA_1, "normal"));

        List<VolumeReport.MuscleWeek> relatorio = VolumeReport.weekly(series, SETTINGS, PLANEJADO, HOJE);

        // 3 semanas (a 2 sem treino e a 3, de hoje, sem treino ainda) x 3 músculos (peito, bíceps e tríceps indireto).
        assertEquals(9, relatorio.size());
        assertEquals(0, resultado(relatorio, SEMANA_2, "chest").direct());
        assertEquals(0, resultado(relatorio, SEMANA_3, "biceps").direct());
    }

    // ------------------------------------------------------------------
    // Planejado das rotinas: abaixo, dentro, acima, e "faltam" na semana em andamento
    // ------------------------------------------------------------------

    @Test
    void semanaEncerradaComparaComOPlanejadoEmAbaixoDentroEAcima() {
        List<SetRecord> series = new ArrayList<>();
        series.addAll(supino(SEMANA_1, "normal", "normal", "normal", "normal"));                       // 4 de 5
        series.addAll(supino(SEMANA_2, "normal", "normal", "normal", "normal", "normal"));             // 5 de 5
        series.addAll(supino(SEMANA_3, "normal", "normal", "normal", "normal", "normal", "normal"));               // 6 de 5

        List<VolumeReport.MuscleWeek> relatorio = VolumeReport.weekly(series, SETTINGS, PLANEJADO, SEMANA_3.plusWeeks(1));

        assertEquals(PlanStatus.BELOW, resultado(relatorio, SEMANA_1, "chest").status());
        assertEquals(PlanStatus.WITHIN, resultado(relatorio, SEMANA_2, "chest").status());
        assertEquals(PlanStatus.ABOVE, resultado(relatorio, SEMANA_3, "chest").status());
        assertEquals(5, resultado(relatorio, SEMANA_1, "chest").planned());
    }

    @Test
    void naSemanaEmAndamentoMenosQueOPlanejadoEhFaltamEPassarDoPlanejadoEhAcima() {
        List<SetRecord> faltando = supino(SEMANA_3, "normal", "normal", "normal"); // 3 de 5, ainda é sexta
        List<SetRecord> completo = supino(SEMANA_3, "normal", "normal", "normal", "normal", "normal");
        List<SetRecord> passou = supino(SEMANA_3, "normal", "normal", "normal", "normal", "normal", "normal");

        assertEquals(PlanStatus.REMAINING, resultado(VolumeReport.weekly(faltando, SETTINGS, PLANEJADO, HOJE), SEMANA_3, "chest").status());
        assertEquals(PlanStatus.WITHIN, resultado(VolumeReport.weekly(completo, SETTINGS, PLANEJADO, HOJE), SEMANA_3, "chest").status());
        assertEquals(PlanStatus.ABOVE, resultado(VolumeReport.weekly(passou, SETTINGS, PLANEJADO, HOJE), SEMANA_3, "chest").status());
    }

    @Test
    void musculoPlanejadoQueAindaNaoTeveSerieNaSemanaEmAndamentoFalta() {
        List<SetRecord> series = supino(SEMANA_2, "normal"); // treinou antes, mas nada ainda na semana de hoje

        List<VolumeReport.MuscleWeek> relatorio = VolumeReport.weekly(series, SETTINGS, PLANEJADO, HOJE);

        VolumeReport.MuscleWeek biceps = resultado(relatorio, SEMANA_3, "biceps");
        assertEquals(0, biceps.direct());
        assertEquals(PlanStatus.REMAINING, biceps.status());
        assertEquals(PlanStatus.BELOW, resultado(relatorio, SEMANA_2, "biceps").status());
    }

    @Test
    void musculoForaDasRotinasNaoTemPlanejadoNemSituacao() {
        List<SetRecord> series = supino(SEMANA_1, "normal", "normal");

        VolumeReport.MuscleWeek triceps = resultado(VolumeReport.weekly(series, SETTINGS, PLANEJADO, HOJE), SEMANA_1, "triceps");

        assertNull(triceps.planned());
        assertNull(triceps.status());
    }

    @Test
    void planejadoZeroEquivaleASemPlanejado() {
        List<SetRecord> series = supino(SEMANA_1, "normal");
        Map<String, MuscleVolume> planejado = Map.of("chest", new MuscleVolume(0, 0));

        VolumeReport.MuscleWeek peito = resultado(VolumeReport.weekly(series, SETTINGS, planejado, HOJE), SEMANA_1, "chest");

        assertNull(peito.planned());
        assertNull(peito.status());
    }

    // ------------------------------------------------------------------
    // Variação por músculo e frequência
    // ------------------------------------------------------------------

    @Test
    void variacaoDasSeriesDiretasDeCadaMusculoContraASemanaAnterior() {
        List<SetRecord> series = new ArrayList<>();
        series.addAll(supino(SEMANA_1, "normal", "normal", "normal", "normal"));               // 4
        series.addAll(supino(SEMANA_2, "normal", "normal", "normal", "normal", "normal", "normal")); // 6: +50%

        List<VolumeReport.MuscleWeek> relatorio = VolumeReport.weekly(series, SETTINGS, PLANEJADO, HOJE);

        assertNull(resultado(relatorio, SEMANA_1, "chest").directChangePercent());              // primeira semana
        assertEquals(50.0, resultado(relatorio, SEMANA_2, "chest").directChangePercent(), 1e-9);
        assertEquals(-100.0, resultado(relatorio, SEMANA_3, "chest").directChangePercent(), 1e-9);
        // Bíceps começou em zero: não dá para calcular percentual sobre zero.
        assertNull(resultado(relatorio, SEMANA_2, "biceps").directChangePercent());
    }

    @Test
    void frequenciaContaOsDiasDistintosComSerieDireta() {
        List<SetRecord> series = new ArrayList<>();
        series.addAll(supino(SEMANA_1, "normal", "normal", "normal"));           // 1 dia, 3 séries
        series.add(new SetRecord("w-2", SEMANA_1.plusDays(2), SEMANA_1, "PUSH", 60, "Supino", "supino", "chest",
                List.of("triceps", "shoulders"), "normal", true, 60.0, 8, null));  // 2º dia
        series.add(new SetRecord("w-3", SEMANA_1.plusDays(4), SEMANA_1, "PULL", 60, "Rosca", "rosca", "biceps",
                List.of(), "normal", true, 20.0, 10, null));                       // bíceps direto

        List<VolumeReport.MuscleWeek> relatorio = VolumeReport.weekly(series, SETTINGS, PLANEJADO, HOJE);

        assertEquals(2, resultado(relatorio, SEMANA_1, "chest").sessions());
        assertEquals(1, resultado(relatorio, SEMANA_1, "biceps").sessions());
        // O tríceps só recebeu indireta (supino), e indireta não conta como dia de treino do músculo.
        assertEquals(0, resultado(relatorio, SEMANA_1, "triceps").sessions());
    }

    // ------------------------------------------------------------------
    // Texto e nomes
    // ------------------------------------------------------------------

    @Test
    void textoNoFormatoDeExibicao() {
        VolumeReport.MuscleWeek semana = new VolumeReport.MuscleWeek(SEMANA_1, "biceps", 11, 10, 3, 10, PlanStatus.ABOVE, null);

        assertEquals("Bíceps: 11 diretas (indiretas 10)", semana.text());
    }

    @Test
    void nomesEmPortuguesComReservaParaMusculoDesconhecido() {
        assertEquals("Posterior de coxa", MuscleNames.label("hamstrings"));
        assertEquals("Costas", MuscleNames.label("back"));
        assertEquals("Cardio", MuscleNames.label("cardio"));
        assertEquals("Corpo inteiro", MuscleNames.label("full_body"));
        assertEquals("rotator cuff", MuscleNames.label("rotator_cuff")); // desconhecido: usa o nome do Hevy
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
