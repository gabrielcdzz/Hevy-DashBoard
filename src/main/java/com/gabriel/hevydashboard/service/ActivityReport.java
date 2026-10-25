package com.gabriel.hevydashboard.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Consistência do treino: treinos e séries por semana, duração média e sequência de semanas completas. */
public final class ActivityReport {

    private ActivityReport() {
    }

    /**
     * @param inProgress            é a semana de hoje, ainda incompleta: os totais podem subir até domingo
     * @param complete              teve pelo menos a meta de treinos por semana
     * @param averageDurationMinutes média da duração dos treinos da semana; null se nenhum tem duração
     * @param setsChangePercent     variação percentual das séries em relação à semana anterior; null se a anterior teve zero
     */
    public record WeekSummary(
            LocalDate week,
            boolean inProgress,
            boolean complete,
            int workouts,
            int validSets,
            Double averageDurationMinutes,
            Double setsChangePercent) {
    }

    /** @param currentStreakWeeks semanas completas seguidas até agora (a semana atual só entra se já estiver completa) */
    public record Consistency(int workoutsPerWeek, int currentStreakWeeks, int bestStreakWeeks) {
    }

    public record Result(List<WeekSummary> weeks, Consistency consistency, Double averageDurationMinutes) {
    }

    /** @param today define qual é a semana em andamento e até onde a lista de semanas vai */
    public static Result build(Collection<SetRecord> sets, Settings settings, LocalDate today) {
        int target = settings.workoutsPerWeek();
        if (sets.isEmpty()) {
            return new Result(List.of(), new Consistency(target, 0, 0), null);
        }

        Map<String, List<SetRecord>> byWorkout = new LinkedHashMap<>();
        for (SetRecord set : sets) {
            byWorkout.computeIfAbsent(set.workoutId(), id -> new ArrayList<>()).add(set);
        }

        TreeMap<LocalDate, WeekTotals> totalsByWeek = new TreeMap<>();
        long durationSum = 0;
        long durationCount = 0;
        for (List<SetRecord> workoutSets : byWorkout.values()) {
            SetRecord first = workoutSets.get(0);
            int validSets = (int) workoutSets.stream().filter(SetRecord::valid).count();

            WeekTotals totals = totalsByWeek.computeIfAbsent(first.week(), w -> new WeekTotals());
            totals.workouts++;
            totals.validSets += validSets;
            if (first.durationMinutes() != null) {
                totals.durationSum += first.durationMinutes();
                totals.durationCount++;
                durationSum += first.durationMinutes();
                durationCount++;
            }
        }

        List<WeekSummary> weeks = weeksOf(totalsByWeek, target, today);
        return new Result(weeks, consistencyOf(weeks, target), durationCount == 0 ? null : (double) durationSum / durationCount);
    }

    /** Uma semana por segunda-feira, da primeira com treino até a de hoje, sem buracos. */
    private static List<WeekSummary> weeksOf(TreeMap<LocalDate, WeekTotals> totalsByWeek, int target, LocalDate today) {
        LocalDate currentWeek = Weeks.startOf(today);
        LocalDate last = totalsByWeek.lastKey().isAfter(currentWeek) ? totalsByWeek.lastKey() : currentWeek;

        List<WeekSummary> weeks = new ArrayList<>();
        WeekSummary previous = null;
        for (LocalDate week = totalsByWeek.firstKey(); !week.isAfter(last); week = week.plusWeeks(1)) {
            WeekTotals t = totalsByWeek.getOrDefault(week, new WeekTotals());
            Double change = previous == null || previous.validSets() == 0 ? null
                    : (t.validSets - previous.validSets()) * 100.0 / previous.validSets();
            WeekSummary summary = new WeekSummary(
                    week,
                    week.equals(currentWeek),
                    t.workouts >= target,
                    t.workouts,
                    t.validSets,
                    t.durationCount == 0 ? null : (double) t.durationSum / t.durationCount,
                    change);
            weeks.add(summary);
            previous = summary;
        }
        return weeks;
    }

    /**
     * Sequência de semanas completas. A semana em andamento que ainda não chegou na meta não quebra a
     * sequência (ainda dá tempo); se já chegou, ela entra na conta.
     */
    private static Consistency consistencyOf(List<WeekSummary> weeks, int target) {
        int best = 0;
        int run = 0;
        for (WeekSummary week : weeks) {
            run = week.complete() ? run + 1 : 0;
            best = Math.max(best, run);
        }

        int index = weeks.size() - 1;
        if (!weeks.get(index).complete() && weeks.get(index).inProgress()) {
            index--;
        }
        int current = 0;
        while (index >= 0 && weeks.get(index).complete()) {
            current++;
            index--;
        }
        return new Consistency(target, current, best);
    }

    /** Acumuladores de uma semana. */
    private static final class WeekTotals {
        int workouts;
        int validSets;
        long durationSum;
        int durationCount;
    }
}
