package com.gabriel.hevydashboard.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Métricas de consistência e de análise do treino: treinos e séries por semana, duração, tonelagem,
 * equilíbrio push/pull e superior/inferior, séries por faixa de repetição e calendário de dias treinados.
 */
public final class ActivityReport {

    private static final int LOW_REPS_MAX = 5;
    private static final int MID_REPS_MAX = 12;

    private ActivityReport() {
    }

    /**
     * @param inProgress           é a semana de hoje, ainda incompleta: os totais podem subir até domingo
     * @param complete             teve pelo menos a meta de treinos por semana
     * @param tonnageKg            soma de carga x repetições das séries de trabalho com carga
     * @param averageDurationMinutes média da duração dos treinos da semana; null se nenhum tem duração
     * @param repsLow              séries de trabalho de 1 a 5 repetições
     * @param repsMid              de 6 a 12
     * @param repsHigh             de 13 em diante
     * @param pushSets             séries diretas de peito, ombros e tríceps (ver {@link Movement})
     * @param setsChangePercent    variação percentual das séries em relação à semana anterior; null se a anterior teve zero
     */
    public record WeekSummary(
            LocalDate week,
            boolean inProgress,
            boolean complete,
            int workouts,
            int validSets,
            double tonnageKg,
            Double averageDurationMinutes,
            int repsLow,
            int repsMid,
            int repsHigh,
            int pushSets,
            int pullSets,
            int legsSets,
            int coreSets,
            Double setsChangePercent) {

        public int upperSets() {
            return pushSets + pullSets;
        }

        public int lowerSets() {
            return legsSets;
        }
    }

    public record WorkoutSummary(String workoutId, LocalDate date, String title, Integer durationMinutes,
                                 int validSets, double tonnageKg) {
    }

    /** Um dia do calendário de calor. */
    public record DayActivity(LocalDate date, int workouts, int validSets) {
    }

    /** @param currentStreakWeeks semanas completas seguidas até agora (a semana atual só entra se já estiver completa) */
    public record Consistency(int workoutsPerWeek, int currentStreakWeeks, int bestStreakWeeks) {
    }

    public record Result(
            List<WeekSummary> weeks,
            List<WorkoutSummary> workouts,
            List<DayActivity> calendar,
            Consistency consistency,
            Double averageDurationMinutes) {
    }

    /** @param today define qual é a semana em andamento e até onde a lista de semanas vai */
    public static Result build(Collection<SetRecord> sets, Settings settings, LocalDate today) {
        int target = settings.workoutsPerWeek();
        if (sets.isEmpty()) {
            return new Result(List.of(), List.of(), List.of(), new Consistency(target, 0, 0), null);
        }

        Map<String, List<SetRecord>> byWorkout = new LinkedHashMap<>();
        for (SetRecord set : sets) {
            byWorkout.computeIfAbsent(set.workoutId(), id -> new ArrayList<>()).add(set);
        }

        List<WorkoutSummary> workouts = new ArrayList<>();
        TreeMap<LocalDate, WeekTotals> totalsByWeek = new TreeMap<>();
        TreeMap<LocalDate, int[]> days = new TreeMap<>(); // por dia: {treinos, séries}
        for (List<SetRecord> workoutSets : byWorkout.values()) {
            SetRecord first = workoutSets.get(0);
            int validSets = (int) workoutSets.stream().filter(SetRecord::valid).count();
            double tonnage = workoutSets.stream()
                    .filter(SetRecord::countsForStrength)
                    .mapToDouble(s -> s.weightKg() * s.reps())
                    .sum();
            workouts.add(new WorkoutSummary(first.workoutId(), first.date(), first.workoutTitle(),
                    first.durationMinutes(), validSets, tonnage));

            WeekTotals totals = totalsByWeek.computeIfAbsent(first.week(), w -> new WeekTotals());
            totals.workouts++;
            totals.validSets += validSets;
            totals.tonnage += tonnage;
            if (first.durationMinutes() != null) {
                totals.durationSum += first.durationMinutes();
                totals.durationCount++;
            }
            days.merge(first.date(), new int[]{1, validSets}, (a, b) -> new int[]{a[0] + b[0], a[1] + b[1]});

            for (SetRecord set : workoutSets) {
                if (set.valid()) {
                    totals.add(set, settings.rules());
                }
            }
        }
        workouts.sort(Comparator.comparing(WorkoutSummary::date).thenComparing(WorkoutSummary::workoutId));

        List<WeekSummary> weeks = weeksOf(totalsByWeek, target, today);
        List<DayActivity> calendar = days.entrySet().stream()
                .map(e -> new DayActivity(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .toList();
        double durationSum = workouts.stream().filter(w -> w.durationMinutes() != null).mapToInt(WorkoutSummary::durationMinutes).sum();
        long durationCount = workouts.stream().filter(w -> w.durationMinutes() != null).count();

        return new Result(
                weeks,
                workouts,
                calendar,
                consistencyOf(weeks, target),
                durationCount == 0 ? null : durationSum / durationCount);
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
                    t.tonnage,
                    t.durationCount == 0 ? null : (double) t.durationSum / t.durationCount,
                    t.repsLow, t.repsMid, t.repsHigh,
                    t.push, t.pull, t.legs, t.core,
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
        double tonnage;
        long durationSum;
        int durationCount;
        int repsLow;
        int repsMid;
        int repsHigh;
        int push;
        int pull;
        int legs;
        int core;

        void add(SetRecord set, VolumeRules rules) {
            Integer reps = set.reps();
            if (reps != null && reps >= 1 && reps <= SetRecord.MAX_PLAUSIBLE_REPS) {
                if (reps <= LOW_REPS_MAX) {
                    repsLow++;
                } else if (reps <= MID_REPS_MAX) {
                    repsMid++;
                } else {
                    repsHigh++;
                }
            }
            switch (Movement.of(rules.groupOf(set.primaryMuscle()))) {
                case PUSH -> push++;
                case PULL -> pull++;
                case LEGS -> legs++;
                case CORE -> core++;
                case OTHER -> { }
            }
        }
    }
}
