package com.gabriel.hevydashboard.sync;

import com.gabriel.hevydashboard.client.HevyApiException;
import com.gabriel.hevydashboard.client.HevySource;
import com.gabriel.hevydashboard.model.ExerciseTemplate;
import com.gabriel.hevydashboard.model.Routine;
import com.gabriel.hevydashboard.model.WorkoutEvent;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/** Hevy falso: guarda os eventos e responde como a API, só com os posteriores ao instante pedido. */
class FakeHevy implements HevySource {

    final List<WorkoutEvent> events = new ArrayList<>();
    final List<ExerciseTemplate> templates = new ArrayList<>();
    final List<Routine> routines = new ArrayList<>();
    final List<Instant> requestedSince = new ArrayList<>();
    boolean offline;

    @Override
    public List<WorkoutEvent> getWorkoutEventsSince(Instant since) {
        if (offline) {
            throw new HevyApiException("O Hevy está fora do ar", 503);
        }
        requestedSince.add(since);
        return events.stream().filter(e -> momentOf(e).toInstant().isAfter(since)).toList();
    }

    @Override
    public List<ExerciseTemplate> getAllExerciseTemplates() {
        return List.copyOf(templates);
    }

    @Override
    public List<Routine> getAllRoutines() {
        return List.copyOf(routines);
    }

    private static OffsetDateTime momentOf(WorkoutEvent event) {
        return event.isUpdated() ? event.workout().updatedAt() : event.deletedAt();
    }
}
