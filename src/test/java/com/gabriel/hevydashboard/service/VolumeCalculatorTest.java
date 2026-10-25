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
import java.util.Set;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VolumeCalculatorTest {

    /**
     * As 5 rotinas de um usuário fictício (PUSH, PUSH 2, PULL, PULL 2 e LEGS) e os templates delas, no mesmo
     * formato que a API do Hevy devolve. São dados inventados: nenhum treino de ninguém entra no repositório.
     */
    private static List<Routine> rotinas;
    private static List<ExerciseTemplate> templates;

    @BeforeAll
    static void carregaFixtures() throws IOException {
        rotinas = le("/fixtures/rotinas.json", new TypeReference<Pages.RoutinesPage>() { }).routines();
        templates = le("/fixtures/templates.json", new TypeReference<List<ExerciseTemplate>>() { });
    }

    // ------------------------------------------------------------------
    // As 5 rotinas, com a conta feita à mão
    // ------------------------------------------------------------------

    /**
     * Conta feita à mão, exercício por exercício (séries de trabalho entre parênteses; só direta no primário,
     * e indireta só para bíceps e tríceps; dorsal e costas superiores juntos como "back"):
     * <pre>
     * PUSH:   supino reto (3), supino inclinado (3), desenvolvimento (3), elevação lateral (4), tríceps na polia (3)
     * PUSH 2: supino inclinado (3), elevação lateral (4), tríceps testa (3), desenvolvimento (3)
     * PULL:   puxada alta (3), remada curvada (3), remada unilateral (3), crucifixo invertido (3), rosca direta (3)
     * PULL 2: remada curvada (3), puxada alta (3), rosca martelo (3), crucifixo invertido (3), remada unilateral (3)
     * LEGS:   agachamento (4), extensora (3), mesa flexora (3), stiff (3), panturrilha (3), abdominal (3)
     *
     * peito       diretas: 3 + 3 + 3                                  = 9
     * ombros      diretas: 3 + 4 (PUSH) + 4 + 3 (PUSH 2) + 3 + 3 (crucifixo invertido) = 20
     * tríceps     diretas: 3 (polia) + 3 (testa) = 6;  indiretas: 3 + 3 + 3 (PUSH) + 3 + 3 (PUSH 2) = 15
     * costas      diretas: 3 puxadas + 3 remadas curvadas + 3 remadas unilaterais, nas duas rotinas = 18
     * bíceps      diretas: 3 (rosca direta) + 3 (rosca martelo) = 6;  indiretas: 3 puxadas/remadas x 6 = 18
     * quadríceps  diretas: 4 + 3 = 7
     * posterior   diretas: 3 + 3 = 6
     * panturrilha 3, abdômen 3
     * </pre>
     * Glúteos (agachamento e stiff), antebraço, ombro nos supinos e lombar são secundários que não contam.
     */
    @Test
    void volumePlanejadoDasCincoRotinasConfereComAContaFeitaAMao() {
        VolumeCalculator calculator = new VolumeCalculator(templates, VolumeRules.DEFAULT);

        Map<String, MuscleVolume> volume = calculator.volumeOf(todosOsExercicios(rotinas));

        Map<String, MuscleVolume> esperado = Map.ofEntries(
                entry("chest", new MuscleVolume(9, 0)),
                entry("shoulders", new MuscleVolume(20, 0)),
                entry("triceps", new MuscleVolume(6, 15)),
                entry("back", new MuscleVolume(18, 0)),
                entry("biceps", new MuscleVolume(6, 18)),
                entry("quadriceps", new MuscleVolume(7, 0)),
                entry("hamstrings", new MuscleVolume(6, 0)),
                entry("calves", new MuscleVolume(3, 0)),
                entry("abdominals", new MuscleVolume(3, 0)));
        assertEquals(esperado, volume);
    }

    @Test
    void seriesDeTrabalhoDeCadaRotina() {
        Map<String, Long> esperado = Map.of("PUSH", 16L, "PUSH 2", 13L, "PULL", 15L, "PULL 2", 15L, "LEGS", 19L);

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
        VolumeCalculator calculator = new VolumeCalculator(templates, VolumeRules.DEFAULT);

        int diretas = calculator.volumeOf(todosOsExercicios(rotinas)).values().stream()
                .mapToInt(MuscleVolume::direct)
                .sum();

        assertEquals(78, diretas); // 16 + 13 + 15 + 15 + 19
    }

    // ------------------------------------------------------------------
    // Regras isoladas, com dados pequenos montados no teste
    // ------------------------------------------------------------------

    private static final ExerciseTemplate SUPINO = template("supino", "chest", "triceps", "shoulders");
    private static final ExerciseTemplate ROSCA_MARTELO = template("rosca", "biceps", "forearms");
    private static final ExerciseTemplate REMADA = template("remada", "upper_back", "lats", "biceps", "forearms");
    private static final ExerciseTemplate PULLDOWN = template("pulldown", "lats", "upper_back", "biceps");

    @Test
    void aquecimentoNaoConta() {
        VolumeCalculator calculator = new VolumeCalculator(List.of(SUPINO), VolumeRules.DEFAULT);

        Map<String, MuscleVolume> volume = calculator.volumeOf(List.of(
                exercicio("supino", "warmup", "warmup", "normal", "normal")));

        assertEquals(new MuscleVolume(2, 0), volume.get("chest"));
        assertEquals(new MuscleVolume(0, 2), volume.get("triceps"));
    }

    @Test
    void falhaEDropsetContam() {
        VolumeCalculator calculator = new VolumeCalculator(List.of(SUPINO), VolumeRules.DEFAULT);

        Map<String, MuscleVolume> volume = calculator.volumeOf(List.of(
                exercicio("supino", "normal", "failure", "dropset")));

        assertEquals(new MuscleVolume(3, 0), volume.get("chest"));
    }

    @Test
    void exercicioSoComAquecimentoNaoApareceNoVolume() {
        VolumeCalculator calculator = new VolumeCalculator(List.of(SUPINO), VolumeRules.DEFAULT);

        Map<String, MuscleVolume> volume = calculator.volumeOf(List.of(exercicio("supino", "warmup")));

        assertTrue(volume.isEmpty());
    }

    @Test
    void secundarioForaDaListaDeIndiretasNaoConta() {
        VolumeCalculator calculator = new VolumeCalculator(List.of(SUPINO, ROSCA_MARTELO), VolumeRules.DEFAULT);

        Map<String, MuscleVolume> volume = calculator.volumeOf(List.of(
                exercicio("supino", "normal", "normal"),
                exercicio("rosca", "normal", "normal")));

        // Supino: tríceps entra, ombro não. Rosca martelo: antebraço não entra.
        assertEquals(Map.of(
                "chest", new MuscleVolume(2, 0),
                "triceps", new MuscleVolume(0, 2),
                "biceps", new MuscleVolume(2, 0)), volume);
    }

    @Test
    void dorsalECostasSuperioresSaoContadosComoCostas() {
        VolumeCalculator calculator = new VolumeCalculator(List.of(REMADA, PULLDOWN), VolumeRules.DEFAULT);

        Map<String, MuscleVolume> volume = calculator.volumeOf(List.of(
                exercicio("remada", "normal", "normal", "normal"),
                exercicio("pulldown", "normal", "normal")));

        assertEquals(Map.of(
                "back", new MuscleVolume(5, 0),
                "biceps", new MuscleVolume(0, 5)), volume);
    }

    @Test
    void grupoPrimarioNaoRecebeIndiretaDoProprioExercicio() {
        // Mesmo com "back" na lista de indiretas, a remada (upper_back; secundário lats)
        // não pode contar 1 direta e 1 indireta de costas pela mesma série.
        VolumeRules costasComIndireta = new VolumeRules(Set.of("biceps", "back"), VolumeRules.DEFAULT.muscleGroups());
        VolumeCalculator calculator = new VolumeCalculator(List.of(REMADA), costasComIndireta);

        Map<String, MuscleVolume> volume = calculator.volumeOf(List.of(exercicio("remada", "normal")));

        assertEquals(new MuscleVolume(1, 0), volume.get("back"));
    }

    @Test
    void templateDesconhecidoLancaExcecao() {
        VolumeCalculator calculator = new VolumeCalculator(List.of(SUPINO), VolumeRules.DEFAULT);

        assertThrows(IllegalArgumentException.class,
                () -> calculator.volumeOf(List.of(exercicio("nao-existe", "normal"))));
    }

    @Test
    void volumeSemanalSeparaOsTreinosPelaSegundaFeira() {
        VolumeCalculator calculator = new VolumeCalculator(List.of(SUPINO, ROSCA_MARTELO), VolumeRules.DEFAULT);

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
