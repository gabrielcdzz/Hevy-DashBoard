package com.gabriel.hevydashboard.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.gabriel.hevydashboard.client.HevyJson;
import com.gabriel.hevydashboard.model.Exercise;
import com.gabriel.hevydashboard.model.ExerciseTemplate;
import com.gabriel.hevydashboard.model.Pages;
import com.gabriel.hevydashboard.model.Routine;
import com.gabriel.hevydashboard.model.SetEntry;
import com.gabriel.hevydashboard.model.Workout;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VolumeCalculatorTest {

    private static final List<IgnoreRule> AJUSTES = List.of(new IgnoreRule("forearms", CountType.INDIRECT));

    /** As 5 rotinas reais e os templates dos exercícios delas, salvos da API em 24/10/2026. */
    private static List<Routine> rotinas;
    private static List<ExerciseTemplate> templates;

    @BeforeAll
    static void carregaFixtures() throws IOException {
        rotinas = le("/fixtures/rotinas.json", new TypeReference<Pages.RoutinesPage>() { }).routines();
        templates = le("/fixtures/templates.json", new TypeReference<List<ExerciseTemplate>>() { });
    }

    // ------------------------------------------------------------------
    // Rotinas reais: os números conferidos à mão com o Hevy
    // ------------------------------------------------------------------

    @Test
    void volumePlanejadoDasCincoRotinasBateComOConferidoNoHevy() {
        VolumeCalculator calculator = new VolumeCalculator(templates, AJUSTES);

        Map<String, MuscleVolume> volume = calculator.volumeOf(todosOsExercicios(rotinas));

        Map<String, MuscleVolume> esperado = Map.ofEntries(
                entry("shoulders", new MuscleVolume(19, 2)),
                entry("biceps", new MuscleVolume(11, 10)),
                entry("triceps", new MuscleVolume(8, 6)),
                entry("upper_back", new MuscleVolume(6, 10)),
                entry("abdominals", new MuscleVolume(6, 0)),
                entry("chest", new MuscleVolume(5, 0)),
                entry("lats", new MuscleVolume(4, 9)),
                entry("forearms", new MuscleVolume(4, 0)),
                entry("quadriceps", new MuscleVolume(4, 3)),
                entry("glutes", new MuscleVolume(3, 0)),
                entry("hamstrings", new MuscleVolume(3, 3)),
                entry("calves", new MuscleVolume(3, 3)),
                entry("lower_back", new MuscleVolume(0, 3)),
                entry("traps", new MuscleVolume(0, 3)));
        assertEquals(esperado, volume);
    }

    @Test
    void seriesDeTrabalhoDeCadaRotina() {
        Map<String, Long> esperado = Map.of("PUSH", 14L, "PUSH 2", 15L, "PULL", 15L, "PULL 2", 16L, "LEGS", 16L);

        for (Routine rotina : rotinas) {
            long trabalho = rotina.exercises().stream()
                    .flatMap(e -> e.sets().stream())
                    .filter(VolumeCalculator::isWorkingSet)
                    .count();
            assertEquals(esperado.get(rotina.title()), trabalho, rotina.title());
        }
    }

    @Test
    void somaDasDiretasEIgualAoTotalDeSeriesDeTrabalho() {
        VolumeCalculator calculator = new VolumeCalculator(templates, AJUSTES);

        int diretas = calculator.volumeOf(todosOsExercicios(rotinas)).values().stream()
                .mapToInt(MuscleVolume::direct)
                .sum();

        assertEquals(76, diretas);
    }

    @Test
    void semOAjusteAntebracoTeria16Indiretas() {
        VolumeCalculator semAjustes = new VolumeCalculator(templates, List.of());

        MuscleVolume antebraco = semAjustes.volumeOf(todosOsExercicios(rotinas)).get("forearms");

        assertEquals(new MuscleVolume(4, 16), antebraco);
    }

    // ------------------------------------------------------------------
    // Regras isoladas, com dados pequenos montados no teste
    // ------------------------------------------------------------------

    private static final ExerciseTemplate SUPINO = template("supino", "chest", "triceps", "shoulders");
    private static final ExerciseTemplate ROSCA_MARTELO = template("rosca", "biceps", "forearms");

    @Test
    void aquecimentoNaoConta() {
        VolumeCalculator calculator = new VolumeCalculator(List.of(SUPINO), List.of());

        Map<String, MuscleVolume> volume = calculator.volumeOf(List.of(
                exercicio("supino", "warmup", "warmup", "normal", "normal")));

        assertEquals(new MuscleVolume(2, 0), volume.get("chest"));
        assertEquals(new MuscleVolume(0, 2), volume.get("triceps"));
        assertEquals(new MuscleVolume(0, 2), volume.get("shoulders"));
    }

    @Test
    void falhaEDropsetContam() {
        VolumeCalculator calculator = new VolumeCalculator(List.of(SUPINO), List.of());

        Map<String, MuscleVolume> volume = calculator.volumeOf(List.of(
                exercicio("supino", "normal", "failure", "dropset")));

        assertEquals(new MuscleVolume(3, 0), volume.get("chest"));
    }

    @Test
    void exercicioSoComAquecimentoNaoApareceNoVolume() {
        VolumeCalculator calculator = new VolumeCalculator(List.of(SUPINO), List.of());

        Map<String, MuscleVolume> volume = calculator.volumeOf(List.of(exercicio("supino", "warmup")));

        assertTrue(volume.isEmpty());
    }

    @Test
    void antebracoIndiretoIgnoradoMasBicepsContaNormal() {
        VolumeCalculator calculator = new VolumeCalculator(List.of(ROSCA_MARTELO), AJUSTES);

        Map<String, MuscleVolume> volume = calculator.volumeOf(List.of(exercicio("rosca", "normal", "normal")));

        assertEquals(new MuscleVolume(2, 0), volume.get("biceps"));
        assertFalse(volume.containsKey("forearms"));
    }

    @Test
    void templateDesconhecidoLancaExcecao() {
        VolumeCalculator calculator = new VolumeCalculator(List.of(SUPINO), List.of());

        assertThrows(IllegalArgumentException.class,
                () -> calculator.volumeOf(List.of(exercicio("nao-existe", "normal"))));
    }

    @Test
    void volumeSemanalSeparaOsTreinosPelaSegundaFeira() {
        VolumeCalculator calculator = new VolumeCalculator(List.of(SUPINO, ROSCA_MARTELO), AJUSTES);

        // Domingo 25/10 às 22h em Brasília (segunda 01h em UTC) ainda é a semana de 19/10.
        Workout domingoANoite = treino("2026-10-26T01:00:00Z", exercicio("supino", "normal", "normal"));
        Workout quartaMesmaSemana = treino("2026-10-21T18:00:00-03:00", exercicio("supino", "normal"));
        Workout segundaSeguinte = treino("2026-10-26T18:00:00-03:00", exercicio("rosca", "normal"));

        Map<LocalDate, Map<String, MuscleVolume>> semanal = calculator.weeklyVolume(
                List.of(domingoANoite, quartaMesmaSemana, segundaSeguinte), Weeks.SAO_PAULO);

        assertEquals(List.of(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 26)),
                List.copyOf(semanal.keySet()));
        assertEquals(new MuscleVolume(3, 0), semanal.get(LocalDate.of(2026, 10, 19)).get("chest"));
        assertEquals(Map.of("biceps", new MuscleVolume(1, 0)), semanal.get(LocalDate.of(2026, 10, 26)));
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------

    private static <T> T le(String caminho, TypeReference<T> tipo) throws IOException {
        try (InputStream in = VolumeCalculatorTest.class.getResourceAsStream(caminho)) {
            if (in == null) {
                throw new IllegalStateException("Fixture não encontrada: " + caminho);
            }
            return HevyJson.mapper().readValue(in, tipo);
        }
    }

    private static List<Exercise> todosOsExercicios(List<Routine> rotinas) {
        List<Exercise> todos = new ArrayList<>();
        rotinas.forEach(r -> todos.addAll(r.exercises()));
        return todos;
    }

    private static ExerciseTemplate template(String id, String primario, String... secundarios) {
        return new ExerciseTemplate(id, id, "weight_reps", primario, List.of(secundarios), false);
    }

    private static Exercise exercicio(String templateId, String... tiposDeSerie) {
        List<SetEntry> series = new ArrayList<>();
        for (int i = 0; i < tiposDeSerie.length; i++) {
            series.add(new SetEntry(i, tiposDeSerie[i], 20.0, 10, null, null, null, null));
        }
        return new Exercise(0, templateId, null, templateId, series);
    }

    private static Workout treino(String inicio, Exercise... exercicios) {
        OffsetDateTime start = OffsetDateTime.parse(inicio);
        return new Workout("w-" + inicio, "treino", null, start, start.plusHours(1), start, start, List.of(exercicios));
    }
}
