package com.gabriel.hevydashboard.export;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gabriel.hevydashboard.service.ActivityReport;
import com.gabriel.hevydashboard.service.ExerciseReport;
import com.gabriel.hevydashboard.service.MuscleNames;
import com.gabriel.hevydashboard.service.MuscleVolume;
import com.gabriel.hevydashboard.service.Phase;
import com.gabriel.hevydashboard.service.SetRecord;
import com.gabriel.hevydashboard.service.Settings;
import com.gabriel.hevydashboard.service.TargetRange;
import com.gabriel.hevydashboard.service.VolumeReport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Junta todas as métricas em um {@link DashboardData} e o grava como JSON para o site. */
public final class DashboardExporter {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .build();

    private DashboardExporter() {
    }

    /**
     * Tudo que o dashboard precisa.
     *
     * @param plannedVolume       volume planejado das rotinas atuais, por grupo muscular (pode ser vazio)
     * @param routineTemplateIds  templates dos exercícios das rotinas atuais
     * @param routineExerciseNames nomes (em minúsculas) dos exercícios das rotinas atuais
     */
    public record Input(
            Collection<SetRecord> sets,
            Settings settings,
            List<Phase> phases,
            Map<String, MuscleVolume> plannedVolume,
            Set<String> routineTemplateIds,
            Set<String> routineExerciseNames,
            LocalDate today,
            Instant generatedAt,
            boolean sample) {
    }

    public static DashboardData build(Input in) {
        Settings settings = in.settings();
        ActivityReport.Result activity = ActivityReport.build(in.sets(), settings, in.today());
        List<VolumeReport.MuscleWeek> volume = VolumeReport.weekly(in.sets(), settings);
        ExerciseReport.Result exercises = ExerciseReport.build(in.sets(), in.today(), settings.stagnationWeeks());

        Set<String> muscles = new TreeSet<>(settings.targets().keySet());
        volume.forEach(row -> muscles.add(row.muscle()));
        in.plannedVolume().keySet().forEach(muscles::add);
        Map<String, String> labels = new TreeMap<>();
        muscles.forEach(muscle -> labels.put(muscle, MuscleNames.label(muscle)));

        Map<String, DashboardData.Planned> planned = new TreeMap<>();
        in.plannedVolume().forEach((muscle, v) -> planned.put(muscle, new DashboardData.Planned(v.direct(), v.indirect())));

        return new DashboardData(
                in.generatedAt().toString(),
                in.sample(),
                in.today(),
                configOf(settings),
                labels,
                new DashboardData.Consistency(
                        activity.consistency().workoutsPerWeek(),
                        activity.consistency().currentStreakWeeks(),
                        activity.consistency().bestStreakWeeks()),
                round1(activity.averageDurationMinutes()),
                activity.weeks().stream().map(DashboardExporter::weekOf).toList(),
                volume.stream().map(DashboardExporter::volumeRowOf).toList(),
                planned,
                activity.calendar().stream()
                        .map(d -> new DashboardData.Day(d.date(), d.workouts(), d.validSets())).toList(),
                exercises.exercises().stream().map(e -> exerciseOf(e, in)).toList(),
                exercises.prEvents().stream()
                        .map(e -> new DashboardData.PrEvent(e.date(), e.exercise(),
                                e.type().name().toLowerCase(Locale.ROOT), round1(e.value())))
                        .toList(),
                activity.workouts().stream()
                        .map(w -> new DashboardData.WorkoutData(w.workoutId(), w.date(), w.title(),
                                w.durationMinutes(), w.validSets(), round1(w.tonnageKg())))
                        .toList(),
                in.phases().stream()
                        .sorted(Comparator.comparing(Phase::start))
                        .map(p -> new DashboardData.PhaseData(p.name(), p.start(), p.end()))
                        .toList());
    }

    public static String toJson(DashboardData data) {
        try {
            return MAPPER.writeValueAsString(data);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível gerar o JSON do dashboard", e);
        }
    }

    public static void write(DashboardData data, Path file) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, toJson(data), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível gravar " + file, e);
        }
    }

    // ------------------------------------------------------------------
    // Conversões
    // ------------------------------------------------------------------

    private static DashboardData.Config configOf(Settings settings) {
        Map<String, DashboardData.Range> targets = new TreeMap<>();
        settings.targets().forEach((muscle, range) -> targets.put(muscle, new DashboardData.Range(range.min(), limitOf(range))));
        List<DashboardData.Level> levels = settings.levels().levels().stream()
                .map(l -> new DashboardData.Level(l.name(), l.range().min(), limitOf(l.range())))
                .toList();
        return new DashboardData.Config(settings.workoutsPerWeek(), settings.stagnationWeeks(), targets, levels);
    }

    private static Integer limitOf(TargetRange range) {
        return range.max() == TargetRange.NO_LIMIT ? null : range.max();
    }

    private static DashboardData.Week weekOf(ActivityReport.WeekSummary w) {
        return new DashboardData.Week(w.week(), w.inProgress(), w.complete(), w.workouts(), w.validSets(),
                round1(w.tonnageKg()), round1(w.averageDurationMinutes()), w.repsLow(), w.repsMid(), w.repsHigh(),
                w.pushSets(), w.pullSets(), w.legsSets(), w.coreSets(), round1(w.setsChangePercent()));
    }

    private static DashboardData.VolumeRow volumeRowOf(VolumeReport.MuscleWeek row) {
        return new DashboardData.VolumeRow(row.week(), row.muscle(), row.direct(), row.indirect(), row.sessions(),
                row.level(), row.status() == null ? null : row.status().name().toLowerCase(Locale.ROOT));
    }

    private static DashboardData.ExerciseData exerciseOf(ExerciseReport.ExerciseSummary e, Input in) {
        boolean inRoutine = in.routineTemplateIds().contains(e.templateId())
                || in.routineExerciseNames().contains(e.name().trim().toLowerCase(Locale.ROOT));
        return new DashboardData.ExerciseData(
                e.templateId(),
                e.name(),
                in.settings().rules().groupOf(e.primaryMuscle()),
                inRoutine,
                e.lastTrained(),
                e.trend().progress().name().toLowerCase(Locale.ROOT),
                round1(e.trend().recentBest()),
                round1(e.trend().previousBest()),
                markOf(e.records().maxWeightKg()),
                markOf(e.records().maxReps()),
                markOf(e.records().bestOneRepMax()),
                e.sessions().stream()
                        .map(s -> new DashboardData.Session(s.date(), round1(s.maxWeightKg()), round1(s.bestOneRepMax())))
                        .toList());
    }

    private static DashboardData.Mark markOf(ExerciseReport.Mark mark) {
        return new DashboardData.Mark(round1(mark.value()), mark.date());
    }

    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private static Double round1(Double value) {
        return value == null ? null : round1(value.doubleValue());
    }
}
