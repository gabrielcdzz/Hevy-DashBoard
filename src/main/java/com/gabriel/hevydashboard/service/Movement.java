package com.gabriel.hevydashboard.service;

import java.util.Set;

/**
 * Tipo de movimento de um grupo muscular, para medir o equilíbrio do treino (push contra pull,
 * superior contra inferior). É uma aproximação pelo músculo primário: o ombro, por exemplo, conta
 * como push mesmo em elevação lateral e crucifixo invertido.
 */
public enum Movement {
    PUSH,
    PULL,
    LEGS,
    CORE,
    OTHER;

    private static final Set<String> PUSH_MUSCLES = Set.of("chest", "shoulders", "triceps");
    private static final Set<String> PULL_MUSCLES = Set.of("back", "lats", "upper_back", "lower_back", "traps", "biceps", "forearms");
    private static final Set<String> LEGS_MUSCLES = Set.of("quadriceps", "hamstrings", "glutes", "calves", "adductors", "abductors");
    private static final Set<String> CORE_MUSCLES = Set.of("abdominals");

    /** O movimento de um grupo muscular (já passado por {@link VolumeRules#groupOf}). */
    public static Movement of(String muscleGroup) {
        if (PUSH_MUSCLES.contains(muscleGroup)) {
            return PUSH;
        }
        if (PULL_MUSCLES.contains(muscleGroup)) {
            return PULL;
        }
        if (LEGS_MUSCLES.contains(muscleGroup)) {
            return LEGS;
        }
        return CORE_MUSCLES.contains(muscleGroup) ? CORE : OTHER;
    }
}
