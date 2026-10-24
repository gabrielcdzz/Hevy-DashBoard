package com.gabriel.hevydashboard.client;

import com.gabriel.hevydashboard.model.Exercise;
import com.gabriel.hevydashboard.model.ExerciseTemplate;
import com.gabriel.hevydashboard.model.Pages;
import com.gabriel.hevydashboard.model.SetEntry;
import com.gabriel.hevydashboard.model.WorkoutEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HevyJsonTest {

    private static final String EVENTS_JSON = """
            {
              "page": 1,
              "page_count": 1,
              "events": [
                {
                  "type": "updated",
                  "workout": {
                    "id": "w1",
                    "title": "PUSH",
                    "start_time": "2026-10-20T13:00:00+00:00",
                    "end_time": "2026-10-20T14:15:00+00:00",
                    "campo_novo_que_nao_existe_na_classe": 123,
                    "exercises": [
                      {
                        "index": 0,
                        "title": "Shoulder Press (Dumbbell)",
                        "exercise_template_id": "878CD1D0",
                        "sets": [
                          { "index": 0, "type": "warmup", "weight_kg": 10.0, "reps": 12 },
                          { "index": 1, "type": "normal", "weight_kg": 20.0, "reps": 10, "rpe": 8.5 }
                        ]
                      }
                    ]
                  }
                },
                { "type": "deleted", "id": "w2", "deleted_at": "2026-10-21T10:00:00Z" }
              ]
            }
            """;

    private static final String TEMPLATE_JSON = """
            {
              "id": "3edf9228",
              "title": "Supino Inclinado Halter",
              "type": "weight_reps",
              "primary_muscle_group": "chest",
              "secondary_muscle_groups": ["triceps", "shoulders"],
              "is_custom": true
            }
            """;

    @Test
    void leEventoDeTreinoAtualizado() throws Exception {
        Pages.WorkoutEventsPage page = HevyJson.mapper().readValue(EVENTS_JSON, Pages.WorkoutEventsPage.class);

        assertEquals(1, page.pageCount());
        assertEquals(2, page.events().size());

        WorkoutEvent updated = page.events().get(0);
        assertTrue(updated.isUpdated());
        assertEquals("PUSH", updated.workout().title());
        assertEquals(Instant.parse("2026-10-20T13:00:00Z"), updated.workout().startTime().toInstant());

        Exercise exercise = updated.workout().exercises().get(0);
        assertEquals("878CD1D0", exercise.exerciseTemplateId());
        assertEquals(2, exercise.sets().size());

        SetEntry work = exercise.sets().get(1);
        assertEquals("normal", work.type());
        assertEquals(20.0, work.weightKg().doubleValue());
        assertEquals(10, work.reps());
        assertEquals(8.5, work.rpe().doubleValue());
    }

    @Test
    void leEventoDeTreinoApagado() throws Exception {
        Pages.WorkoutEventsPage page = HevyJson.mapper().readValue(EVENTS_JSON, Pages.WorkoutEventsPage.class);

        WorkoutEvent deleted = page.events().get(1);
        assertTrue(deleted.isDeleted());
        assertFalse(deleted.isUpdated());
        assertEquals("w2", deleted.id());
        assertNull(deleted.workout());
        assertEquals(Instant.parse("2026-10-21T10:00:00Z"), deleted.deletedAt().toInstant());
    }

    @Test
    void leMusculosPrimarioESecundarios() throws Exception {
        ExerciseTemplate template = HevyJson.mapper().readValue(TEMPLATE_JSON, ExerciseTemplate.class);

        assertEquals("chest", template.primaryMuscleGroup());
        assertEquals(2, template.secondaryMuscleGroups().size());
        assertTrue(template.secondaryMuscleGroups().contains("triceps"));
        assertTrue(template.isCustom());
    }
}
