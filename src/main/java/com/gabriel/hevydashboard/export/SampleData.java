package com.gabriel.hevydashboard.export;

import com.gabriel.hevydashboard.model.Exercise;
import com.gabriel.hevydashboard.model.ExerciseTemplate;
import com.gabriel.hevydashboard.model.Routine;
import com.gabriel.hevydashboard.model.SetEntry;
import com.gabriel.hevydashboard.model.Workout;
import com.gabriel.hevydashboard.service.MuscleVolume;
import com.gabriel.hevydashboard.service.Phase;
import com.gabriel.hevydashboard.service.SetRecord;
import com.gabriel.hevydashboard.service.SetRecords;
import com.gabriel.hevydashboard.service.Settings;
import com.gabriel.hevydashboard.service.VolumeCalculator;
import com.gabriel.hevydashboard.service.Weeks;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Dados de treino fictícios para demonstrar o dashboard sem expor os treinos de ninguém.
 * <p>
 * Um usuário imaginário treina 5 vezes por semana (PUSH, PULL, LEGS, PUSH 2, PULL 2) durante 60 semanas,
 * com progressão de carga, platôs em alguns exercícios, semanas de deload e uma semana parada.
 * O resultado é sempre o mesmo para a mesma data ({@code Random} com semente fixa), então o arquivo
 * gerado pode ficar no repositório sem gerar diferenças a cada execução.
 * <p>
 * Os treinos são objetos do Hevy e passam pelo mesmo caminho dos dados reais ({@link SetRecords} e
 * {@link DashboardExporter}), então o exemplo sempre tem o formato verdadeiro.
 */
public final class SampleData {

    private static final int WEEKS = 60;
    private static final int NO_PLATEAU = 999;
    private static final int ILLNESS_WEEK = 31;
    private static final ZoneOffset BRASILIA = ZoneOffset.ofHours(-3);

    private SampleData() {
    }

    public record Sample(List<ExerciseTemplate> templates, List<Routine> routines, List<Workout> workouts) {
    }

    /** Um exercício do catálogo fictício. */
    private record Move(String id, String title, String primary, List<String> secondary,
                        double baseKg, double weeklyGain, int plateauWeek, int sets, int repsLow, int repsHigh) {
    }

    private static final List<Move> CATALOG = List.of(
            new Move("S01", "Supino Reto (Barra)", "chest", List.of("triceps", "shoulders"), 60, 0.006, NO_PLATEAU, 3, 6, 10),
            new Move("S02", "Supino Inclinado (Halter)", "chest", List.of("triceps", "shoulders"), 22, 0.005, 40, 3, 8, 12),
            new Move("S03", "Desenvolvimento (Halter)", "shoulders", List.of("triceps"), 18, 0.005, NO_PLATEAU, 3, 8, 12),
            new Move("S04", "Elevação Lateral (Cabo)", "shoulders", List.of(), 7, 0.004, 25, 4, 12, 18),
            new Move("S05", "Tríceps na Polia", "triceps", List.of(), 28, 0.004, 30, 3, 10, 15),
            new Move("S06", "Tríceps Testa (Barra W)", "triceps", List.of(), 24, 0.004, NO_PLATEAU, 3, 8, 12),
            new Move("S07", "Puxada Alta (Máquina)", "lats", List.of("upper_back", "biceps", "forearms"), 55, 0.006, NO_PLATEAU, 3, 8, 12),
            new Move("S08", "Remada Curvada (Barra)", "upper_back", List.of("lats", "biceps", "forearms"), 50, 0.006, 45, 3, 8, 10),
            new Move("S09", "Remada Unilateral (Cabo)", "upper_back", List.of("lats", "biceps"), 30, 0.005, NO_PLATEAU, 3, 10, 12),
            new Move("S10", "Crucifixo Invertido (Máquina)", "shoulders", List.of("upper_back"), 28, 0.004, 30, 3, 12, 15),
            new Move("S11", "Rosca Direta (Barra W)", "biceps", List.of("forearms"), 24, 0.004, NO_PLATEAU, 3, 8, 12),
            new Move("S12", "Rosca Martelo (Halter)", "biceps", List.of("forearms"), 13, 0.004, 20, 3, 10, 12),
            new Move("S13", "Agachamento (Smith)", "quadriceps", List.of("glutes", "hamstrings"), 70, 0.007, NO_PLATEAU, 4, 6, 10),
            new Move("S14", "Cadeira Extensora (Máquina)", "quadriceps", List.of(), 55, 0.005, NO_PLATEAU, 3, 10, 15),
            new Move("S15", "Mesa Flexora (Máquina)", "hamstrings", List.of("calves"), 40, 0.005, 35, 3, 10, 12),
            new Move("S16", "Stiff (Barra)", "hamstrings", List.of("glutes", "lower_back"), 60, 0.006, NO_PLATEAU, 3, 8, 10),
            new Move("S17", "Panturrilha em Pé (Máquina)", "calves", List.of(), 80, 0.004, NO_PLATEAU, 3, 12, 15),
            new Move("S18", "Abdominal (Máquina)", "abdominals", List.of(), 35, 0.005, 30, 3, 12, 15));

    /** Segunda a sexta: o título do treino e os exercícios (ids do catálogo). */
    private static final List<Map.Entry<String, List<String>>> WEEK_PLAN = List.of(
            Map.entry("PUSH", List.of("S01", "S02", "S03", "S04", "S05")),
            Map.entry("PULL", List.of("S07", "S08", "S09", "S10", "S11")),
            Map.entry("LEGS", List.of("S13", "S14", "S15", "S16", "S17", "S18")),
            Map.entry("PUSH 2", List.of("S02", "S04", "S06", "S03")),
            Map.entry("PULL 2", List.of("S08", "S07", "S12", "S10", "S09")));

    /** O arquivo de dados completo do dashboard de exemplo. */
    public static DashboardData dashboard(LocalDate today, Instant generatedAt) {
        Sample sample = generate(today);
        Settings settings = Settings.defaults();
        Map<String, ExerciseTemplate> templatesById =
                sample.templates().stream().collect(Collectors.toMap(ExerciseTemplate::id, Function.identity()));

        List<SetRecord> sets = new ArrayList<>();
        sample.workouts().forEach(w -> sets.addAll(SetRecords.of(w, templatesById, Weeks.SAO_PAULO)));

        List<Exercise> routineExercises = sample.routines().stream().flatMap(r -> r.exercises().stream()).toList();
        Map<String, MuscleVolume> planned =
                new VolumeCalculator(sample.templates(), settings.rules()).volumeOf(routineExercises);
        Set<String> routineTemplateIds = routineExercises.stream().map(Exercise::exerciseTemplateId).collect(Collectors.toSet());
        Set<String> routineNames = routineExercises.stream()
                .map(e -> e.title().trim().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());

        LocalDate firstMonday = Weeks.startOf(today).minusWeeks(WEEKS);
        List<Phase> phases = List.of(
                new Phase("bulking", firstMonday, today.minusWeeks(16).minusDays(1)),
                new Phase("cutting", today.minusWeeks(16), null));

        return DashboardExporter.build(new DashboardExporter.Input(
                sets, settings, phases, planned, routineTemplateIds, routineNames, today, generatedAt, true));
    }

    public static Sample generate(LocalDate today) {
        Random random = new Random(42);
        LocalDate firstMonday = Weeks.startOf(today).minusWeeks(WEEKS);
        OffsetDateTime created = firstMonday.atTime(LocalTime.NOON).atOffset(BRASILIA);

        List<ExerciseTemplate> templates = CATALOG.stream()
                .map(m -> new ExerciseTemplate(m.id(), m.title(), "weight_reps", m.primary(), m.secondary(), false))
                .toList();

        List<Routine> routines = new ArrayList<>();
        for (Map.Entry<String, List<String>> plan : WEEK_PLAN) {
            List<Exercise> exercises = new ArrayList<>();
            for (String id : plan.getValue()) {
                Move move = move(id);
                exercises.add(new Exercise(exercises.size(), move.title(), null, move.id(),
                        setsOf(move, exercises.isEmpty() || exercises.size() == 1, move.baseKg(), move.sets(), random, false)));
            }
            routines.add(new Routine("R-" + plan.getKey(), plan.getKey(), null, created, created, exercises));
        }

        List<Workout> workouts = new ArrayList<>();
        for (int week = 0; week <= WEEKS; week++) {
            LocalDate monday = firstMonday.plusWeeks(week);
            boolean deload = week % 8 == 7;
            for (int day = 0; day < WEEK_PLAN.size(); day++) {
                LocalDate date = monday.plusDays(day);
                if (date.isAfter(today)) {
                    break;
                }
                boolean skipped = week == ILLNESS_WEEK || random.nextDouble() < 0.12;
                if (skipped) {
                    continue;
                }
                workouts.add(workout(workouts.size() + 1, date, week, deload, WEEK_PLAN.get(day), random));
            }
        }
        return new Sample(templates, routines, workouts);
    }

    private static Workout workout(int number, LocalDate date, int week, boolean deload,
                                   Map.Entry<String, List<String>> plan, Random random) {
        OffsetDateTime start = date.atTime(LocalTime.of(18, 0)).atOffset(BRASILIA);
        OffsetDateTime end = start.plusMinutes(55 + random.nextInt(21));

        List<Exercise> exercises = new ArrayList<>();
        for (String id : plan.getValue()) {
            Move move = move(id);
            double weight = weightAt(move, week, deload);
            // Às vezes falta energia ou tempo e uma série fica de fora; no deload sempre há uma a menos.
            int sets = deload || random.nextDouble() < 0.12 ? Math.max(2, move.sets() - 1) : move.sets();
            exercises.add(new Exercise(exercises.size(), move.title(), null, move.id(),
                    setsOf(move, exercises.size() < 2, weight, sets, random, true)));
        }
        return new Workout(String.format(Locale.ROOT, "sample-%04d", number), plan.getKey(), null, start, end, end, start, exercises);
    }

    /** Aquecimento (50% e 70% da carga) nos dois primeiros exercícios, seguido das séries de trabalho. */
    private static List<SetEntry> setsOf(Move move, boolean warmup, double weight, int workSets, Random random, boolean varyReps) {
        List<SetEntry> sets = new ArrayList<>();
        if (warmup) {
            sets.add(new SetEntry(sets.size(), "warmup", round(move, weight * 0.5), 12, null, null, null, null));
            sets.add(new SetEntry(sets.size(), "warmup", round(move, weight * 0.7), 6, null, null, null, null));
        }
        for (int i = 0; i < workSets; i++) {
            int reps = varyReps
                    ? Math.max(move.repsLow(), move.repsHigh() - i - random.nextInt(2))
                    : move.repsHigh();
            String type = "normal";
            if (varyReps && i == workSets - 1) { // a última série às vezes vai até a falha ou vira dropset
                double roll = random.nextDouble();
                type = roll < 0.06 ? "failure" : roll < 0.09 ? "dropset" : "normal";
            }
            sets.add(new SetEntry(sets.size(), type, round(move, weight), reps, null, null, null, null));
        }
        return sets;
    }

    /** A carga sobe uma fração por semana até o platô e cai 15% na semana de deload. */
    private static double weightAt(Move move, int week, boolean deload) {
        double weight = move.baseKg() * (1 + move.weeklyGain() * Math.min(week, move.plateauWeek()));
        return round(move, deload ? weight * 0.85 : weight);
    }

    /** Cargas pesadas sobem de 2,5 em 2,5 kg; as leves, de 1 em 1 kg ou de meio em meio. */
    private static double round(Move move, double weight) {
        double step = move.baseKg() >= 40 ? 2.5 : move.baseKg() >= 15 ? 1.0 : 0.5;
        return Math.round(weight / step) * step;
    }

    private static Move move(String id) {
        return CATALOG.stream().filter(m -> m.id().equals(id)).findFirst().orElseThrow();
    }
}
