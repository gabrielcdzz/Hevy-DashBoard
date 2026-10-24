package com.gabriel.hevydashboard.service;

import java.util.Map;

/**
 * Tudo que o usuário ajusta na planilha e o cálculo precisa conhecer.
 *
 * @param rules            quais músculos recebem indireta e como os músculos são agrupados
 * @param targets          faixa-alvo de séries diretas por semana, por grupo muscular
 * @param levels           níveis de volume (baixo, moderado, alto)
 * @param stagnationWeeks  semanas sem progressão para o alerta de estagnação
 */
public record Settings(
        VolumeRules rules,
        Map<String, TargetRange> targets,
        VolumeLevels levels,
        int stagnationWeeks) {

    public Settings {
        targets = Map.copyOf(targets);
    }
}
