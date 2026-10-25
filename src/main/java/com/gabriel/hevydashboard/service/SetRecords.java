package com.gabriel.hevydashboard.service;

import com.gabriel.hevydashboard.model.Exercise;
import com.gabriel.hevydashboard.model.ExerciseTemplate;
import com.gabriel.hevydashboard.model.SetEntry;
import com.gabriel.hevydashboard.model.Workout;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
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

    /**
     * Atualiza os músculos (e, nos exercícios personalizados, o nome) com o que o template tem <b>hoje</b>.
     * <p>
     * Renomear um exercício ou corrigir seus músculos no Hevy não altera os treinos antigos, então a
     * sincronização incremental não vê mudança e a planilha continua com o que valia na época do treino.
     * Sem esta atualização, um exercício renomeado ficaria dividido em dois no dashboard.
     * <p>
     * O nome só é trocado nos exercícios <b>personalizados</b>. Nos padrão, a API devolve o título em inglês
     * ("Lat Pulldown (Machine)") e o treino guarda o que o app mostra no idioma do usuário
     * ("Puxada Alta (Máquina)"), então o nome gravado é o certo. Séries de templates que não existem mais
     * (exercício personalizado apagado) ficam como estão.
     */
    public static List<SetRecord> withCurrentTemplates(Collection<SetRecord> sets, Map<String, ExerciseTemplate> templatesById) {
        return sets.stream().map(set -> {
            ExerciseTemplate template = templatesById.get(set.templateId());
            if (template == null) {
                return set;
            }
            String name = Boolean.TRUE.equals(template.isCustom()) ? template.title() : set.exercise();
            return set.withTemplate(name, template.primaryMuscleGroup(), template.secondaryMuscleGroups());
        }).toList();
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
