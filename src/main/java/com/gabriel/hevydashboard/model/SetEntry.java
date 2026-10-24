package com.gabriel.hevydashboard.model;

/**
 * Uma série de um exercício (tanto em treinos executados quanto em rotinas).
 * O campo {@code type} vem do Hevy: "warmup", "normal", "failure" ou "dropset".
 */
public record SetEntry(
        Integer index,
        String type,
        Double weightKg,
        Integer reps,
        Integer distanceMeters,
        Integer durationSeconds,
        Double rpe,
        Double customMetric) {
}
