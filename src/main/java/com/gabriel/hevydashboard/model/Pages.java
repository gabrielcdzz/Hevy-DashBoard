package com.gabriel.hevydashboard.model;

import java.util.List;

/** Respostas paginadas da API do Hevy. */
public final class Pages {

    private Pages() {
    }

    public record WorkoutsPage(int page, int pageCount, List<Workout> workouts) {
    }

    public record WorkoutEventsPage(int page, int pageCount, List<WorkoutEvent> events) {
    }

    public record RoutinesPage(int page, int pageCount, List<Routine> routines) {
    }

    public record ExerciseTemplatesPage(int page, int pageCount, List<ExerciseTemplate> exerciseTemplates) {
    }
}
