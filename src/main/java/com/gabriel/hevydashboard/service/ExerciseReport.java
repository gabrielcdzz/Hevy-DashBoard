package com.gabriel.hevydashboard.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Métricas por exercício: evolução de carga, 1RM estimado, recordes pessoais e alerta de estagnação.
 * Os exercícios são identificados pelo template do Hevy, não pelo nome.
 * Só entram séries de trabalho com carga e repetições ({@link SetRecord#countsForStrength()}).
 */
public final class ExerciseReport {

    private ExerciseReport() {
    }

    /** O melhor de um exercício em um dia de treino (várias séries do mesmo dia viram um ponto). */
    public record SessionPoint(LocalDate date, double maxWeightKg, double bestOneRepMax) {
    }

    /** Um recorde: o valor e o primeiro dia em que foi alcançado. */
    public record Mark(double value, LocalDate date) {
    }

    public record Records(Mark maxWeightKg, Mark maxReps, Mark bestOneRepMax) {
    }

    public enum Progress {
        /** O melhor 1RM das últimas semanas passou o das semanas anteriores. */
        PROGRESSING,
        /** Foi treinado nas últimas semanas, mas o melhor 1RM não passou o das semanas anteriores. */
        STAGNANT,
        /** Não foi treinado nas últimas semanas: não é estagnação, é que não está no treino. */
        INACTIVE,
        /** Foi treinado agora, mas não nas semanas anteriores: não há com o que comparar. */
        NO_BASELINE
    }

    /** @param recentBest   melhor 1RM das últimas N semanas; null se não treinou
     *  @param previousBest melhor 1RM das N semanas anteriores; null se não treinou */
    public record Trend(Progress progress, Double recentBest, Double previousBest) {
    }

    public record ExerciseSummary(
            String templateId,
            String name,
            String primaryMuscle,
            List<SessionPoint> sessions,
            Records records,
            Trend trend,
            LocalDate lastTrained) {
    }

    public enum PrType {
        WEIGHT,
        REPS,
        ONE_REP_MAX
    }

    /** Um recorde batido em uma data. */
    public record PrEvent(LocalDate date, String templateId, String exercise, PrType type, double value) {
    }

    public record Result(List<ExerciseSummary> exercises, List<PrEvent> prEvents) {
    }

    /**
     * @param today            a data de hoje, que define "as últimas N semanas" (a semana atual conta, mesmo incompleta)
     * @param stagnationWeeks  N, o tamanho de cada uma das duas janelas comparadas
     */
    public static Result build(Collection<SetRecord> sets, LocalDate today, int stagnationWeeks) {
        Map<String, List<SetRecord>> byExercise = new TreeMap<>();
        for (SetRecord set : sets) {
            if (set.countsForStrength()) {
                byExercise.computeIfAbsent(set.templateId(), id -> new ArrayList<>()).add(set);
            }
        }

        List<ExerciseSummary> summaries = new ArrayList<>();
        List<PrEvent> events = new ArrayList<>();
        byExercise.forEach((templateId, list) -> summaries.add(summarize(templateId, list, today, stagnationWeeks, events)));

        summaries.sort(Comparator.comparing(ExerciseSummary::name));
        events.sort(Comparator.comparing(PrEvent::date).thenComparing(PrEvent::exercise).thenComparing(PrEvent::type));
        return new Result(summaries, events);
    }

    private static ExerciseSummary summarize(String templateId, List<SetRecord> sets, LocalDate today,
                                             int stagnationWeeks, List<PrEvent> events) {
        // O nome e o músculo vêm da série mais recente, caso o exercício tenha sido renomeado.
        SetRecord latest = sets.stream().max(Comparator.comparing(SetRecord::date)).orElseThrow();

        TreeMap<LocalDate, List<SetRecord>> byDate = sets.stream()
                .collect(Collectors.groupingBy(SetRecord::date, TreeMap::new, Collectors.toList()));

        List<SessionPoint> points = new ArrayList<>();
        Mark bestWeight = null;
        Mark bestReps = null;
        Mark bestOneRepMax = null;
        for (Map.Entry<LocalDate, List<SetRecord>> day : byDate.entrySet()) {
            LocalDate date = day.getKey();
            double maxWeight = day.getValue().stream().mapToDouble(SetRecord::weightKg).max().orElseThrow();
            int maxReps = day.getValue().stream().mapToInt(SetRecord::reps).max().orElseThrow();
            double maxOneRepMax = day.getValue().stream()
                    .mapToDouble(s -> OneRepMax.estimate(s.weightKg(), s.reps()))
                    .max().orElseThrow();
            points.add(new SessionPoint(date, maxWeight, maxOneRepMax));

            // A primeira sessão só estabelece a base: bater recorde exige ter um recorde anterior.
            if (bestWeight != null) {
                if (maxWeight > bestWeight.value()) {
                    events.add(new PrEvent(date, templateId, latest.exercise(), PrType.WEIGHT, maxWeight));
                }
                if (maxReps > bestReps.value()) {
                    events.add(new PrEvent(date, templateId, latest.exercise(), PrType.REPS, maxReps));
                }
                if (maxOneRepMax > bestOneRepMax.value()) {
                    events.add(new PrEvent(date, templateId, latest.exercise(), PrType.ONE_REP_MAX, maxOneRepMax));
                }
            }
            bestWeight = better(bestWeight, maxWeight, date);
            bestReps = better(bestReps, maxReps, date);
            bestOneRepMax = better(bestOneRepMax, maxOneRepMax, date);
        }

        return new ExerciseSummary(
                templateId,
                latest.exercise(),
                latest.primaryMuscle(),
                points,
                new Records(bestWeight, bestReps, bestOneRepMax),
                trendOf(points, today, stagnationWeeks),
                points.get(points.size() - 1).date());
    }

    /** Só troca o recorde se o valor for estritamente maior, então vale a primeira data em que foi alcançado. */
    private static Mark better(Mark current, double value, LocalDate date) {
        return current == null || value > current.value() ? new Mark(value, date) : current;
    }

    private static Trend trendOf(List<SessionPoint> points, LocalDate today, int weeks) {
        LocalDate recentStart = Weeks.startOf(today).minusWeeks(weeks - 1L);
        LocalDate previousStart = recentStart.minusWeeks(weeks);

        Double recentBest = null;
        Double previousBest = null;
        for (SessionPoint point : points) {
            if (!point.date().isBefore(recentStart)) {
                recentBest = max(recentBest, point.bestOneRepMax());
            } else if (!point.date().isBefore(previousStart)) {
                previousBest = max(previousBest, point.bestOneRepMax());
            }
        }

        Progress progress;
        if (recentBest == null) {
            progress = Progress.INACTIVE;
        } else if (previousBest == null) {
            progress = Progress.NO_BASELINE;
        } else {
            progress = recentBest > previousBest ? Progress.PROGRESSING : Progress.STAGNANT;
        }
        return new Trend(progress, recentBest, previousBest);
    }

    private static Double max(Double current, double value) {
        return current == null || value > current ? value : current;
    }
}
