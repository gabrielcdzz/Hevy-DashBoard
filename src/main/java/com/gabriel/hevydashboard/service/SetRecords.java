package com.gabriel.hevydashboard.service;

import com.gabriel.hevydashboard.model.Exercise;
import com.gabriel.hevydashboard.model.ExerciseTemplate;
import com.gabriel.hevydashboard.model.SetEntry;
import com.gabriel.hevydashboard.model.Workout;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Transforma um treino do Hevy em uma lista de {@link SetRecord}, uma por série. */
public final class SetRecords {

    private SetRecords() {
    }

    public static List<SetRecord> of(Workout workout, Map<String, ExerciseTemplate> templatesById, ZoneId zone) {
        LocalDate date = workout.startTime().atZoneSameInstant(zone).toLocalDate();
        LocalDate week = Weeks.startOf(workout.startTime(), zone);
        Integer minutes = workout.endTime() == null ? null
                : (int) Math.round(Duration.between(workout.startTime(), workout.endTime()).toSeconds() / 60.0);

        List<SetRecord> records = new ArrayList<>();
        for (Exercise exercise : workout.exercises()) {
            ExerciseTemplate template = templateOf(exercise, templatesById);
            for (SetEntry set : exercise.sets()) {
                records.add(new SetRecord(
                        workout.id(),
                        date,
                        week,
                        workout.title() == null ? "" : workout.title(),
                        minutes,
                        exercise.title(),
                        exercise.exerciseTemplateId(),
                        template.primaryMuscleGroup(),
                        template.secondaryMuscleGroups(),
                        set.type(),
                        VolumeCalculator.isWorkingSet(set),
                        set.weightKg(),
                        set.reps(),
                        set.rpe()));
            }
        }
        return records;
    }

    private static ExerciseTemplate templateOf(Exercise exercise, Map<String, ExerciseTemplate> templatesById) {
        ExerciseTemplate template = templatesById.get(exercise.exerciseTemplateId());
        if (template == null) {
            throw new IllegalArgumentException("O exercício \"" + exercise.title() + "\" usa o template "
                    + exercise.exerciseTemplateId() + ", que não está na lista de templates. "
                    + "Pode ser um exercício personalizado que foi apagado no Hevy.");
        }
        return template;
    }
}
