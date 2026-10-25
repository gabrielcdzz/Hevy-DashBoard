package com.gabriel.hevydashboard.client;

import com.gabriel.hevydashboard.model.ExerciseTemplate;
import com.gabriel.hevydashboard.model.Routine;
import com.gabriel.hevydashboard.model.WorkoutEvent;

import java.time.Instant;
import java.util.List;

/**
 * O que a sincronização precisa do Hevy. Existe como interface para o {@code SyncService} (e os testes)
 * não dependerem da rede: {@link HevyClient} fala com a API de verdade e os testes usam uma versão falsa.
 */
public interface HevySource {

    /** Treinos atualizados ou apagados desde {@code since}. Com {@link Instant#EPOCH}, o histórico inteiro. */
    List<WorkoutEvent> getWorkoutEventsSince(Instant since);

    List<ExerciseTemplate> getAllExerciseTemplates();

    List<Routine> getAllRoutines();
}
