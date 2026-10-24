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
}
