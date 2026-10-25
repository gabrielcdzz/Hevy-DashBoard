package com.gabriel.hevydashboard.service;

import java.time.LocalDate;
import java.util.List;

/**
 * Uma série de um treino executado, já com a data local, a semana e os músculos do exercício.
 * É a unidade de dados das métricas: vem dos treinos do Hevy ({@link SetRecords}) ou da aba
 * series_treinos da planilha, e as métricas não sabem de onde.
 *
 * @param week             a segunda-feira da semana, no fuso do usuário
 * @param durationMinutes  duração do treino inteiro (repetida em todas as séries dele); pode ser null
 * @param valid            true para série de trabalho, false para aquecimento
 */
public record SetRecord(
        String workoutId,
        LocalDate date,
        LocalDate week,
        String workoutTitle,
        Integer durationMinutes,
        String exercise,
        String templateId,
        String primaryMuscle,
        List<String> secondaryMuscles,
        String type,
        boolean valid,
        Double weightKg,
        Integer reps,
        Double rpe) {

    public SetRecord {
        secondaryMuscles = List.copyOf(secondaryMuscles);
    }

    /**
     * Acima disto, a série é tratada como erro de digitação (ex.: 101 repetições em vez de 10).
     * No histórico real há séries de até 34 repetições e depois dessas só três erros, de 79 a 101.
     */
    public static final int MAX_PLAUSIBLE_REPS = 50;

    /**
     * Série de trabalho com carga e repetições. É a única que entra em 1RM, recordes e tonelagem:
     * aquecimento não conta, e séries de exercícios ainda não feitos (carga 0), de peso corporal
     * (sem carga) ou com repetições absurdas distorceriam os números. O volume, ao contrário,
     * conta todas as séries de trabalho.
     */
    public boolean countsForStrength() {
        return valid && weightKg != null && weightKg > 0 && reps != null && reps > 0 && reps <= MAX_PLAUSIBLE_REPS;
    }

    /** A mesma série com outro nome de exercício e outros músculos (ver {@link SetRecords#withCurrentTemplates}). */
    public SetRecord withTemplate(String newExercise, String newPrimaryMuscle, List<String> newSecondaryMuscles) {
        return new SetRecord(workoutId, date, week, workoutTitle, durationMinutes, newExercise, templateId,
                newPrimaryMuscle, newSecondaryMuscles, type, valid, weightKg, reps, rpe);
    }
}
