package com.gabriel.hevydashboard.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Evolução por exercício, sempre em carga e repetições (sem 1RM estimado): o que mudou de um treino para o
 * outro, os recordes pessoais e o alerta de estagnação.
 * <p>
 * Um exercício é identificado pelo template do Hevy ou pelo nome (ver {@code groupByExercise}).
 * Só entram séries de trabalho com carga e repetições ({@link SetRecord#countsForStrength()}).
 * <p>
 * Cada treino de um exercício é resumido pela sua <b>série mais pesada</b> (a maior carga e, nela, as
 * repetições mais altas). É essa série que se compara de um treino para o outro.
 */
public final class ExerciseReport {

    /** Diferença de carga menor que isso (em kg) é tratada como a mesma carga: evita ruído de arredondamento. */
    private static final double SAME_WEIGHT_EPSILON = 0.01;

    private ExerciseReport() {
    }

    /** A série mais pesada de um dia de treino. */
    public record SessionPoint(LocalDate date, double weightKg, int reps) {
    }

    /** O que mudou entre dois treinos, com a carga tendo prioridade sobre as repetições. */
    public enum Change {
        /** A carga subiu (as repetições podem ter subido ou caído). */
        LOAD_UP,
        /** A carga caiu. */
        LOAD_DOWN,
        /** A mesma carga, mais repetições. */
        REPS_UP,
        /** A mesma carga, menos repetições. */
        REPS_DOWN,
        /** A mesma carga e as mesmas repetições. */
        SAME
    }

    /** A comparação do último treino com o anterior do mesmo exercício. */
    public record Comparison(Change change, SessionPoint previous, SessionPoint current) {
    }

    /** Um recorde: o valor e o primeiro dia em que foi alcançado. */
    public record Mark(double value, LocalDate date) {
    }

    public record Records(Mark maxWeightKg, Mark maxReps) {
    }

    public enum Progress {
        /** Nas últimas semanas a carga subiu, ou as repetições subiram com a mesma carga. */
        PROGRESSING,
        /** Foi treinado nas últimas semanas, mas não subiu carga nem repetições em relação às semanas anteriores. */
        STAGNANT,
        /** Não foi treinado nas últimas semanas: não é estagnação, é que não está no treino. */
        INACTIVE,
        /** Foi treinado agora, mas não nas semanas anteriores: não há com o que comparar. */
        NO_BASELINE
    }

    /**
     * A melhor série (a mais pesada) das últimas N semanas e das N anteriores.
     *
     * @param recent   null se não treinou nas últimas semanas
     * @param previous null se não treinou nas semanas anteriores
     */
    public record Trend(Progress progress, SessionPoint recent, SessionPoint previous) {
    }

    /**
     * @param lastChange o último treino contra o anterior; null se o exercício só teve um treino
     */
    public record ExerciseSummary(
            String templateId,
            String name,
            String primaryMuscle,
            List<SessionPoint> sessions,
            Records records,
            Comparison lastChange,
            Trend trend,
            LocalDate lastTrained) {
    }

    public enum PrType {
        WEIGHT,
        REPS
    }

    /** Um recorde batido em uma data. */
    public record PrEvent(LocalDate date, String templateId, String exercise, PrType type, double value) {
    }

    public record Result(List<ExerciseSummary> exercises, List<PrEvent> prEvents) {
    }

    /** Compara dois treinos: primeiro pela carga e, se ela é a mesma, pelas repetições. */
    public static Change compare(double previousWeight, int previousReps, double currentWeight, int currentReps) {
        if (Math.abs(currentWeight - previousWeight) >= SAME_WEIGHT_EPSILON) {
            return currentWeight > previousWeight ? Change.LOAD_UP : Change.LOAD_DOWN;
        }
        if (currentReps == previousReps) {
            return Change.SAME;
        }
        return currentReps > previousReps ? Change.REPS_UP : Change.REPS_DOWN;
    }

    /**
     * @param today            a data de hoje, que define "as últimas N semanas" (a semana atual conta, mesmo incompleta)
     * @param stagnationWeeks  N, o tamanho de cada uma das duas janelas comparadas no alerta de estagnação
     */
    public static Result build(Collection<SetRecord> sets, LocalDate today, int stagnationWeeks) {
        List<SetRecord> strengthSets = sets.stream().filter(SetRecord::countsForStrength).toList();

        List<ExerciseSummary> summaries = new ArrayList<>();
        List<PrEvent> events = new ArrayList<>();
        groupByExercise(strengthSets).forEach(list -> summaries.add(summarize(list, today, stagnationWeeks, events)));

        summaries.sort(Comparator.comparing(ExerciseSummary::name).thenComparing(ExerciseSummary::templateId));
        events.sort(Comparator.comparing(PrEvent::date).thenComparing(PrEvent::exercise).thenComparing(PrEvent::type));
        return new Result(summaries, events);
    }

    /**
     * Agrupa as séries por exercício. Duas séries são do mesmo exercício se têm o mesmo template
     * (mesmo que o exercício tenha sido renomeado) ou o mesmo nome (mesmo que o usuário tenha criado um
     * template novo para ele). Sem isso o histórico de um exercício ficaria cortado em dois.
     */
    private static Collection<List<SetRecord>> groupByExercise(List<SetRecord> sets) {
        Map<String, String> parent = new HashMap<>();
        for (SetRecord set : sets) {
            union(parent, "template:" + set.templateId(), "nome:" + normalized(set.exercise()));
        }
        Map<String, List<SetRecord>> groups = new TreeMap<>();
        for (SetRecord set : sets) {
            groups.computeIfAbsent(find(parent, "template:" + set.templateId()), root -> new ArrayList<>()).add(set);
        }
        return groups.values();
    }

    private static String normalized(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private static String find(Map<String, String> parent, String key) {
        parent.putIfAbsent(key, key);
        String root = key;
        while (!parent.get(root).equals(root)) {
            root = parent.get(root);
        }
        parent.put(key, root); // atalho para as próximas buscas
        return root;
    }

    private static void union(Map<String, String> parent, String a, String b) {
        String rootA = find(parent, a);
        String rootB = find(parent, b);
        if (!rootA.equals(rootB)) {
            parent.put(rootB, rootA);
        }
    }

    private static ExerciseSummary summarize(List<SetRecord> sets, LocalDate today,
                                             int stagnationWeeks, List<PrEvent> events) {
        // O nome, o template e o músculo vêm da série mais recente, caso o exercício tenha sido renomeado.
        SetRecord latest = sets.stream().max(Comparator.comparing(SetRecord::date)).orElseThrow();
        String templateId = latest.templateId();

        TreeMap<LocalDate, List<SetRecord>> byDate = sets.stream()
                .collect(Collectors.groupingBy(SetRecord::date, TreeMap::new, Collectors.toList()));

        List<SessionPoint> points = new ArrayList<>();
        Mark bestWeight = null;
        Mark bestReps = null;
        for (Map.Entry<LocalDate, List<SetRecord>> day : byDate.entrySet()) {
            LocalDate date = day.getKey();
            SessionPoint top = topSet(date, day.getValue());
            points.add(top);

            // O recorde de repetições vale para qualquer série do dia, não só para a mais pesada.
            int maxReps = day.getValue().stream().mapToInt(SetRecord::reps).max().orElseThrow();

            // A primeira sessão só estabelece a base: bater recorde exige ter um recorde anterior.
            if (bestWeight != null) {
                if (top.weightKg() > bestWeight.value()) {
                    events.add(new PrEvent(date, templateId, latest.exercise(), PrType.WEIGHT, top.weightKg()));
                }
                if (maxReps > bestReps.value()) {
                    events.add(new PrEvent(date, templateId, latest.exercise(), PrType.REPS, maxReps));
                }
            }
            bestWeight = better(bestWeight, top.weightKg(), date);
            bestReps = better(bestReps, maxReps, date);
        }

        Comparison lastChange = null;
        if (points.size() >= 2) {
            SessionPoint previous = points.get(points.size() - 2);
            SessionPoint current = points.get(points.size() - 1);
            lastChange = new Comparison(compare(previous.weightKg(), previous.reps(), current.weightKg(), current.reps()),
                    previous, current);
        }

        return new ExerciseSummary(
                templateId,
                latest.exercise(),
                latest.primaryMuscle(),
                points,
                new Records(bestWeight, bestReps),
                lastChange,
                trendOf(points, today, stagnationWeeks),
                points.get(points.size() - 1).date());
    }

    /** A série mais pesada do dia; com a mesma carga, a de mais repetições. */
    private static SessionPoint topSet(LocalDate date, List<SetRecord> daySets) {
        double maxWeight = daySets.stream().mapToDouble(SetRecord::weightKg).max().orElseThrow();
        int reps = daySets.stream()
                .filter(s -> Math.abs(s.weightKg() - maxWeight) < SAME_WEIGHT_EPSILON)
                .mapToInt(SetRecord::reps)
                .max().orElseThrow();
        return new SessionPoint(date, maxWeight, reps);
    }

    /** Só troca o recorde se o valor for estritamente maior, então vale a primeira data em que foi alcançado. */
    private static Mark better(Mark current, double value, LocalDate date) {
        return current == null || value > current.value() ? new Mark(value, date) : current;
    }

    private static Trend trendOf(List<SessionPoint> points, LocalDate today, int weeks) {
        LocalDate recentStart = Weeks.startOf(today).minusWeeks(weeks - 1L);
        LocalDate previousStart = recentStart.minusWeeks(weeks);

        SessionPoint recent = null;
        SessionPoint previous = null;
        for (SessionPoint point : points) {
            if (!point.date().isBefore(recentStart)) {
                recent = heavier(recent, point);
            } else if (!point.date().isBefore(previousStart)) {
                previous = heavier(previous, point);
            }
        }

        Progress progress;
        if (recent == null) {
            progress = Progress.INACTIVE;
        } else if (previous == null) {
            progress = Progress.NO_BASELINE;
        } else {
            Change change = compare(previous.weightKg(), previous.reps(), recent.weightKg(), recent.reps());
            progress = change == Change.LOAD_UP || change == Change.REPS_UP ? Progress.PROGRESSING : Progress.STAGNANT;
        }
        return new Trend(progress, recent, previous);
    }

    /** A melhor das duas séries: a mais pesada e, na mesma carga, a de mais repetições. */
    private static SessionPoint heavier(SessionPoint current, SessionPoint candidate) {
        if (current == null) {
            return candidate;
        }
        Change change = compare(current.weightKg(), current.reps(), candidate.weightKg(), candidate.reps());
        return change == Change.LOAD_UP || change == Change.REPS_UP ? candidate : current;
    }
}
