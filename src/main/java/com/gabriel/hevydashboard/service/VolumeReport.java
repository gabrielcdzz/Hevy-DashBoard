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

/** Volume semanal por músculo: séries diretas e indiretas, comparadas com o volume planejado das rotinas. */
public final class VolumeReport {

    private VolumeReport() {
    }

    /**
     * @param sessions            quantos dias da semana o músculo recebeu pelo menos uma série direta
     * @param planned             séries diretas planejadas nas rotinas; null se o músculo não está nas rotinas
     * @param status              situação em relação ao planejado; null se não há planejado
     * @param directChangePercent variação das séries diretas em relação à semana anterior; null se a anterior teve zero
     */
    public record MuscleWeek(
            LocalDate week,
            String muscle,
            int direct,
            int indirect,
            int sessions,
            Integer planned,
            PlanStatus status,
            Double directChangePercent) {

        /** No formato "Bíceps: 11 diretas (indiretas 10)". */
        public String text() {
            return MuscleNames.label(muscle) + ": " + direct + " diretas (indiretas " + indirect + ")";
        }
    }

    /**
     * Uma linha por semana e por músculo, sem buracos: todas as semanas entre a primeira com treino e a
     * semana de hoje aparecem (inclusive as sem treino nenhum, e a de hoje mesmo que ainda sem treino),
     * e todos os músculos planejados aparecem mesmo com zero séries. Só contam séries de trabalho.
     * <p>
     * O planejado é o das rotinas de hoje, aplicado a todas as semanas: serve de referência, mas as semanas
     * antigas foram feitas com outras rotinas.
     *
     * @param planned volume planejado das rotinas por grupo muscular (pode ser vazio)
     * @param today   define qual é a semana em andamento
     */
    public static List<MuscleWeek> weekly(Collection<SetRecord> sets, Settings settings,
                                          Map<String, MuscleVolume> planned, LocalDate today) {
        TreeMap<LocalDate, Map<String, MuscleVolume>> byWeek = new TreeMap<>();
        Map<LocalDate, Map<String, Set<LocalDate>>> daysByWeek = new HashMap<>();
        TreeSet<String> muscles = new TreeSet<>();
        planned.forEach((muscle, volume) -> {
            if (volume.direct() > 0) {
                muscles.add(muscle);
            }
        });

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

        LocalDate currentWeek = Weeks.startOf(today);
        if (byWeek.isEmpty()) {
            return List.of();
        }
        LocalDate first = byWeek.firstKey();
        LocalDate last = byWeek.lastKey().isAfter(currentWeek) ? byWeek.lastKey() : currentWeek;

        Map<String, Integer> previousDirect = new HashMap<>();
        List<MuscleWeek> rows = new ArrayList<>();
        for (LocalDate week = first; !week.isAfter(last); week = week.plusWeeks(1)) {
            Map<String, MuscleVolume> volumes = byWeek.getOrDefault(week, Map.of());
            boolean inProgress = week.equals(currentWeek);
            for (String muscle : muscles) {
                MuscleVolume volume = volumes.getOrDefault(muscle, MuscleVolume.ZERO);
                MuscleVolume plan = planned.get(muscle);
                Integer plannedDirect = plan != null && plan.direct() > 0 ? plan.direct() : null;
                Integer before = previousDirect.get(muscle);
                Double change = before == null || before == 0 ? null : (volume.direct() - before) * 100.0 / before;
                rows.add(new MuscleWeek(
                        week,
                        muscle,
                        volume.direct(),
                        volume.indirect(),
                        daysByWeek.getOrDefault(week, Map.of()).getOrDefault(muscle, Set.of()).size(),
                        plannedDirect,
                        plannedDirect == null ? null : PlanStatus.of(volume.direct(), plannedDirect, inProgress),
                        change));
                previousDirect.put(muscle, volume.direct());
            }
        }
        return rows;
    }
}
