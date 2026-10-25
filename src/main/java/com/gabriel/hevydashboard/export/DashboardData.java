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
        List<ExerciseData> exercises,
        List<PrEvent> prEvents) {

    public record Config(int workoutsPerWeek, int stagnationWeeks) {
    }

    public record Consistency(int workoutsPerWeek, int currentStreakWeeks, int bestStreakWeeks) {
    }

    public record Week(
            LocalDate week,
            boolean inProgress,
            boolean complete,
            int workouts,
            int sets,
            Double averageDurationMinutes,
            Double changePercent) {
    }

    /**
     * @param planned       séries diretas planejadas nas rotinas; omitido se o músculo não está nas rotinas
     * @param status        "below", "within", "above" ou "remaining" (semana em andamento, ainda falta); omitido sem planejado
     * @param changePercent variação das séries diretas em relação à semana anterior; omitido se a anterior teve zero
     */
    public record VolumeRow(LocalDate week, String muscle, int direct, int indirect, int sessions,
                            Integer planned, String status, Double changePercent) {
    }

    /** O volume planejado das rotinas atuais para um grupo muscular. */
    public record Planned(int direct, int indirect) {
    }

    /** A série mais pesada de um dia de treino. */
    public record Point(LocalDate date, double weightKg, int reps) {
    }

    public record Mark(double value, LocalDate date) {
    }

    /** @param type "load_up", "load_down", "reps_up", "reps_down" ou "same" */
    public record Change(String type, Point previous, Point current) {
    }

    /**
     * @param progress "progressing", "stagnant", "inactive" ou "no_baseline"
     * @param recent   a melhor série das últimas semanas; omitido se não treinou
     * @param previous a melhor série das semanas anteriores; omitido se não treinou
     */
    public record Trend(String progress, Point recent, Point previous) {
    }

    /**
     * @param muscleGroup o grupo do músculo primário, já com dorsal e costas superiores juntos ("back")
     * @param inRoutine   definido pelas rotinas atuais; o site mostra só esses exercícios por padrão
     * @param lastChange  o último treino contra o anterior; omitido se só teve um treino
     */
    public record ExerciseData(
            String id,
            String name,
            String muscleGroup,
            boolean inRoutine,
            LocalDate lastTrained,
            Mark maxWeightKg,
            Mark maxReps,
            Change lastChange,
            Trend trend,
            List<Point> sessions) {
    }

    /** @param type "weight" ou "reps" */
    public record PrEvent(LocalDate date, String exercise, String type, double value) {
    }
}
