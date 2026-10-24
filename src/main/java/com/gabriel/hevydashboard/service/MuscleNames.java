package com.gabriel.hevydashboard.service;

import java.util.Map;

/** Nomes dos grupos musculares em português, para exibição. */
public final class MuscleNames {

    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("chest", "Peito"),
            Map.entry("back", "Costas"),
            Map.entry("lats", "Dorsal"),
            Map.entry("upper_back", "Costas superiores"),
            Map.entry("lower_back", "Lombar"),
            Map.entry("traps", "Trapézio"),
            Map.entry("shoulders", "Ombros"),
            Map.entry("biceps", "Bíceps"),
            Map.entry("triceps", "Tríceps"),
            Map.entry("forearms", "Antebraço"),
            Map.entry("quadriceps", "Quadríceps"),
            Map.entry("hamstrings", "Posterior de coxa"),
            Map.entry("glutes", "Glúteos"),
            Map.entry("calves", "Panturrilha"),
            Map.entry("abdominals", "Abdômen"),
            Map.entry("adductors", "Adutores"),
            Map.entry("abductors", "Abdutores"));

    private MuscleNames() {
    }

    /** O nome em português; para um músculo desconhecido, o nome do Hevy com espaço no lugar do "_". */
    public static String label(String muscle) {
        return LABELS.getOrDefault(muscle, muscle.replace('_', ' '));
    }
}
