package com.gabriel.hevydashboard.model;

import java.time.OffsetDateTime;
import java.util.List;

/** Um treino executado e salvo no Hevy. */
public record Workout(
        String id,
        String title,
        String description,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        OffsetDateTime updatedAt,
        OffsetDateTime createdAt,
        List<Exercise> exercises) {

    public Workout {
        exercises = exercises == null ? List.of() : List.copyOf(exercises);
    }
}
