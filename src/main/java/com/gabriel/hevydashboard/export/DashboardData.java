package com.gabriel.hevydashboard.export;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * O conteúdo de dashboard.json, o único arquivo que o site lê. Os nomes dos campos aqui são o contrato
 * com o JavaScript do site. Valores numéricos já vêm arredondados e os campos sem valor são omitidos.
 *
 * @param sample se true, os dados são fictícios (o site mostra um aviso)
 * @param today  a data de "hoje" usada nos cálculos, para o site não depender do relógio do navegador
 */
public record DashboardData(
        String generatedAt,
        boolean sample,
        LocalDate today,
        Config config,
        Map<String, String> muscleLabels,
        Consistency consistency,
        Double averageDurationMinutes,
        List<Week> weeks,
        List<VolumeRow> volume,
        Map<String, Planned> plannedVolume,
        List<Day> calendar,
        List<ExerciseData> exercises,
        List<PrEvent> prEvents,
        List<WorkoutData> workouts,
        List<PhaseData> phases) {

    /** @param max null quando não há teto */
    public record Range(int min, Integer max) {
    }

    public record Level(String name, int min, Integer max) {
    }

    public record Config(int workoutsPerWeek, int stagnationWeeks, Map<String, Range> targets, List<Level> levels) {
    }

    public record Consistency(int workoutsPerWeek, int currentStreakWeeks, int bestStreakWeeks) {
    }

    public record Week(
            LocalDate week,
            boolean inProgress,
            boolean complete,
            int workouts,
            int sets,
            double tonnageKg,
            Double averageDurationMinutes,
            int repsLow,
            int repsMid,
            int repsHigh,
            int push,
            int pull,
            int legs,
            int core,
            Double changePercent) {
    }

    /** @param status "below", "within" ou "above" em relação à faixa-alvo; omitido se o músculo não tem faixa */
    public record VolumeRow(LocalDate week, String muscle, int direct, int indirect, int sessions,
                            String level, String status) {
    }

    public record Planned(int direct, int indirect) {
    }

    public record Day(LocalDate date, int workouts, int sets) {
    }

    public record Mark(double value, LocalDate date) {
    }

    public record Session(LocalDate date, double weightKg, double oneRepMax) {
    }

    /**
     * @param muscleGroup o grupo do músculo primário, já com dorsal e costas superiores juntos ("back")
     * @param inRoutine   definido pelas rotinas atuais; o site mostra só esses exercícios por padrão
     * @param progress    "progressing", "stagnant", "inactive" ou "no_baseline"
     */
    public record ExerciseData(
            String id,
            String name,
            String muscleGroup,
            boolean inRoutine,
            LocalDate lastTrained,
            String progress,
            Double recentBest,
            Double previousBest,
            Mark maxWeightKg,
            Mark maxReps,
            Mark bestOneRepMax,
            List<Session> sessions) {
    }

    /** @param type "weight", "reps" ou "one_rep_max" */
    public record PrEvent(LocalDate date, String exercise, String type, double value) {
    }

    public record WorkoutData(String id, LocalDate date, String title, Integer durationMinutes, int sets, double tonnageKg) {
    }

    public record PhaseData(String name, LocalDate start, LocalDate end) {
    }
}
