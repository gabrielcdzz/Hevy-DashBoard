package com.gabriel.hevydashboard.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Volume semanal por músculo: séries diretas e indiretas, nível e posição na faixa-alvo. */
public final class VolumeReport {

    private VolumeReport() {
    }

    /**
     * @param sessions quantos dias da semana o músculo recebeu pelo menos uma série direta (a frequência)
     * @param level    "baixo", "moderado", "alto" ou "nenhum", pelas séries diretas
     * @param status   posição das séries diretas na faixa-alvo do músculo; null se o músculo não tem faixa-alvo
     */
    public record MuscleWeek(
            LocalDate week,
            String muscle,
            int direct,
            int indirect,
            int sessions,
            String level,
            TargetRange.Status status) {

        /** No formato "Bíceps: 11 diretas (indiretas 10)". */
        public String text() {
            return MuscleNames.label(muscle) + ": " + direct + " diretas (indiretas " + indirect + ")";
        }
    }

    /**
     * Uma linha por semana e por músculo, sem buracos: todas as semanas entre a primeira e a última com treino
     * aparecem (inclusive as sem treino nenhum), e todos os músculos com faixa-alvo aparecem mesmo com zero séries.
     * Só contam séries de trabalho.
     */
    public static List<MuscleWeek> weekly(Collection<SetRecord> sets, Settings settings) {
        TreeMap<LocalDate, Map<String, MuscleVolume>> byWeek = new TreeMap<>();
        Map<LocalDate, Map<String, Set<LocalDate>>> daysByWeek = new HashMap<>();
        TreeSet<String> muscles = new TreeSet<>(settings.targets().keySet());

        for (SetRecord set : sets) {
            if (!set.valid()) {
                continue;
            }
            Map<String, MuscleVolume> week = byWeek.computeIfAbsent(set.week(), w -> new TreeMap<>());
            Map<String, Set<LocalDate>> weekDays = daysByWeek.computeIfAbsent(set.week(), w -> new HashMap<>());
            settings.rules().contribution(set.primaryMuscle(), set.secondaryMuscles(), 1).forEach((muscle, volume) -> {
                week.merge(muscle, volume, MuscleVolume::plus);
                muscles.add(muscle);
                if (volume.direct() > 0) {
                    weekDays.computeIfAbsent(muscle, m -> new HashSet<>()).add(set.date());
                }
            });
        }
        if (byWeek.isEmpty()) {
            return List.of();
        }

        LocalDate first = byWeek.firstKey();
        LocalDate last = byWeek.lastKey();
        List<MuscleWeek> rows = new ArrayList<>();
        for (LocalDate week = first; !week.isAfter(last); week = week.plusWeeks(1)) {
            Map<String, MuscleVolume> volumes = byWeek.getOrDefault(week, Map.of());
            for (String muscle : muscles) {
                MuscleVolume volume = volumes.getOrDefault(muscle, MuscleVolume.ZERO);
                TargetRange target = settings.targets().get(muscle);
                rows.add(new MuscleWeek(
                        week,
                        muscle,
                        volume.direct(),
                        volume.indirect(),
                        daysByWeek.getOrDefault(week, Map.of()).getOrDefault(muscle, Set.of()).size(),
                        settings.levels().classify(volume.direct()),
                        target == null ? null : target.statusOf(volume.direct())));
            }
        }
        return rows;
    }
}
