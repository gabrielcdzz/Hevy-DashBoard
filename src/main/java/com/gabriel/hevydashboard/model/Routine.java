package com.gabriel.hevydashboard.model;

import java.time.OffsetDateTime;
import java.util.List;

/** Uma rotina (o treino planejado) cadastrada no Hevy. */
public record Routine(
        String id,
        String title,
        Long folderId,
        OffsetDateTime updatedAt,
        OffsetDateTime createdAt,
        List<Exercise> exercises) {

    public Routine {
        exercises = exercises == null ? List.of() : List.copyOf(exercises);
    }
}
