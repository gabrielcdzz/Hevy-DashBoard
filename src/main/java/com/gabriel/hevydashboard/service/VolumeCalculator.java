package com.gabriel.hevydashboard.service;

import com.gabriel.hevydashboard.model.Exercise;
import com.gabriel.hevydashboard.model.ExerciseTemplate;
import com.gabriel.hevydashboard.model.SetEntry;
import com.gabriel.hevydashboard.model.Workout;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Calcula as séries diretas e indiretas por músculo. Não faz I/O: recebe os dados já carregados.
 * <p>
 * Regras:
 * <ol>
 *   <li>Só contam séries de trabalho: tudo que não é aquecimento ("warmup").</li>
 *   <li>Os músculos do Hevy passam pelo agrupamento de {@link VolumeRules} (ex.: "lats" → "back").</li>
 *   <li>Cada série de trabalho vale 1 direta para o grupo do músculo primário.</li>
 *   <li>Cada série de trabalho vale 1 indireta para cada grupo secundário que está na lista de
 *       indiretas, uma vez só por grupo e nunca para o próprio grupo primário.</li>
 * </ol>
 * Exemplo: supino (peito; secundários tríceps e ombro) com 2 séries de trabalho
 * = 2 diretas de peito e 2 indiretas de tríceps. O ombro fica de fora da lista de indiretas.
 */
public class VolumeCalculator {

    private final Map<String, ExerciseTemplate> templatesById;
    private final VolumeRules rules;

    public VolumeCalculator(Collection<ExerciseTemplate> templates, VolumeRules rules) {
        this.templatesById = templates.stream()
                .collect(Collectors.toMap(ExerciseTemplate::id, Function.identity(), (a, b) -> a));
        this.rules = rules;
    }

    public static boolean isWorkingSet(SetEntry set) {
        return !"warmup".equals(set.type());
    }

    /** Volume por grupo muscular de uma lista de exercícios (de um treino, de uma rotina ou de vários). */
    public Map<String, MuscleVolume> volumeOf(Collection<Exercise> exercises) {
        Map<String, MuscleVolume> volume = new TreeMap<>();
        for (Exercise exercise : exercises) {
            int workingSets = (int) exercise.sets().stream().filter(VolumeCalculator::isWorkingSet).count();
            if (workingSets == 0) {
                continue;
            }
            ExerciseTemplate template = templateOf(exercise);

            rules.contribution(template.primaryMuscleGroup(), template.secondaryMuscleGroups(), workingSets)
                    .forEach((muscle, contribution) -> volume.merge(muscle, contribution, MuscleVolume::plus));
        }
        return volume;
    }

    /**
     * Volume por semana. A chave é a segunda-feira da semana (ver {@link Weeks#startOf}),
     * calculada pelo horário de início do treino no fuso {@code zone}.
     */
    public Map<LocalDate, Map<String, MuscleVolume>> weeklyVolume(Collection<Workout> workouts, ZoneId zone) {
        Map<LocalDate, List<Exercise>> exercisesByWeek = new TreeMap<>();
        for (Workout workout : workouts) {
            LocalDate week = Weeks.startOf(workout.startTime(), zone);
            exercisesByWeek.computeIfAbsent(week, w -> new ArrayList<>()).addAll(workout.exercises());
        }

        Map<LocalDate, Map<String, MuscleVolume>> weekly = new TreeMap<>();
        exercisesByWeek.forEach((week, exercises) -> weekly.put(week, volumeOf(exercises)));
        return weekly;
    }

    private ExerciseTemplate templateOf(Exercise exercise) {
        ExerciseTemplate template = templatesById.get(exercise.exerciseTemplateId());
        if (template == null) {
            throw new IllegalArgumentException("O exercício \"" + exercise.title() + "\" usa o template "
                    + exercise.exerciseTemplateId() + ", que não está na lista de templates.");
        }
        return template;
    }
}
