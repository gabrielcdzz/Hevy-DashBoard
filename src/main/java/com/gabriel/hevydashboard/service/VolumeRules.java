package com.gabriel.hevydashboard.service;

import java.util.Map;
import java.util.Set;

/**
 * Regras ajustáveis da contagem de volume.
 *
 * @param indirectMuscles músculos (já agrupados) que recebem séries indiretas. Os demais
 *                        secundários do Hevy são ignorados, porque trabalham pouco no exercício.
 * @param muscleGroups    junta músculos do Hevy num grupo só. Exemplo: "lats" e "upper_back"
 *                        viram "back", porque trabalham juntos em quase todo exercício de costas.
 */
public record VolumeRules(Set<String> indirectMuscles, Map<String, String> muscleGroups) {

    /** Bíceps (nas puxadas) e tríceps (nos empurrões) são os únicos secundários que contam. */
    public static final VolumeRules DEFAULT = new VolumeRules(
            Set.of("biceps", "triceps"),
            Map.of("lats", "back", "upper_back", "back"));

    public VolumeRules {
        indirectMuscles = Set.copyOf(indirectMuscles);
        muscleGroups = Map.copyOf(muscleGroups);
    }

    /** O grupo em que o músculo do Hevy é contado. Sem agrupamento, é o próprio músculo. */
    public String groupOf(String muscle) {
        return muscleGroups.getOrDefault(muscle, muscle);
    }

    public boolean countsIndirect(String group) {
        return indirectMuscles.contains(group);
    }
}
