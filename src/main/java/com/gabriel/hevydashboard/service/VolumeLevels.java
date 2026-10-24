package com.gabriel.hevydashboard.service;

import java.util.List;

/** Os níveis de volume semanal (baixo, moderado, alto) e a classificação de um número de séries. */
public record VolumeLevels(List<VolumeLevel> levels) {

    /** Nome usado quando o músculo não teve nenhuma série (ou o número não cai em nenhum nível). */
    public static final String NONE = "nenhum";

    public VolumeLevels {
        levels = List.copyOf(levels);
    }

    public String classify(int sets) {
        return levels.stream()
                .filter(level -> level.range().contains(sets))
                .map(VolumeLevel::name)
                .findFirst()
                .orElse(NONE);
    }
}
