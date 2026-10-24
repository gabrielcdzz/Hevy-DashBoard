package com.gabriel.hevydashboard.model;

import java.util.List;

/**
 * Cadastro de um exercício no Hevy, com o músculo primário e os secundários.
 * É daqui que saem as séries diretas e indiretas.
 */
public record ExerciseTemplate(
        String id,
        String title,
        String type,
        String primaryMuscleGroup,
        List<String> secondaryMuscleGroups,
        Boolean isCustom) {

    public ExerciseTemplate {
        secondaryMuscleGroups = secondaryMuscleGroups == null ? List.of() : List.copyOf(secondaryMuscleGroups);
    }
}
