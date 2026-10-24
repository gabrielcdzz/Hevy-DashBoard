package com.gabriel.hevydashboard.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tudo que o usuário ajusta na planilha e o cálculo precisa conhecer.
 *
 * @param rules            quais músculos recebem indireta e como os músculos são agrupados
 * @param targets          faixa-alvo de séries diretas por semana, por grupo muscular
 * @param levels           níveis de volume (baixo, moderado, alto)
 * @param stagnationWeeks  semanas sem progressão para o alerta de estagnação
 * @param workoutsPerWeek  meta de treinos por semana; uma semana com pelo menos isso é "completa"
 */
public record Settings(
        VolumeRules rules,
        Map<String, TargetRange> targets,
        VolumeLevels levels,
        int stagnationWeeks,
        int workoutsPerWeek) {

    public Settings {
        targets = Map.copyOf(targets);
    }

    /**
     * Os valores iniciais: os que uma planilha nova recebe e os que os dados de exemplo usam.
     * O peito fica em baixo volume de propósito; os demais músculos, em 10 a 20 séries diretas.
     */
    public static Settings defaults() {
        Map<String, TargetRange> targets = new HashMap<>();
        targets.put("chest", new TargetRange(1, 9));
        for (String muscle : List.of("back", "shoulders", "biceps", "triceps", "quadriceps",
                "hamstrings", "glutes", "calves", "abdominals", "forearms")) {
            targets.put(muscle, new TargetRange(10, 20));
        }
        VolumeLevels levels = new VolumeLevels(List.of(
                new VolumeLevel("baixo", new TargetRange(1, 9)),
                new VolumeLevel("moderado", new TargetRange(10, 20)),
                new VolumeLevel("alto", new TargetRange(21, TargetRange.NO_LIMIT))));
        return new Settings(VolumeRules.DEFAULT, targets, levels, 3, 5);
    }
}
