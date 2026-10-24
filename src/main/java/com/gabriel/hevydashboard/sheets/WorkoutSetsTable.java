package com.gabriel.hevydashboard.sheets;

import com.gabriel.hevydashboard.model.Exercise;
import com.gabriel.hevydashboard.model.ExerciseTemplate;
import com.gabriel.hevydashboard.model.SetEntry;
import com.gabriel.hevydashboard.model.Workout;
import com.gabriel.hevydashboard.model.WorkoutEvent;
import com.gabriel.hevydashboard.service.VolumeCalculator;
import com.gabriel.hevydashboard.service.Weeks;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A aba "series_treinos": uma linha por série de cada treino executado.
 * <p>
 * Guarda os dados brutos, sem aplicar as regras de volume (todos os músculos secundários que o Hevy
 * informa, aquecimento incluído e marcado em {@code valida}). Assim as regras podem mudar depois
 * e o volume é recalculado a partir daqui, sem perder histórico.
 * <p>
 * A atualização é incremental: apaga só as linhas dos treinos que mudaram e acrescenta as novas.
 * As demais linhas nunca são relidas nem regravadas.
 */
public class WorkoutSetsTable {

    public static final String SHEET = "series_treinos";

    public static final List<Object> HEADER = List.of(
            "workout_id", "data", "semana_inicio", "rotina", "exercicio", "template_id",
            "musculo_primario", "musculos_secundarios", "tipo_serie", "valida", "carga_kg", "reps", "rpe");

    private static final int WORKOUT_ID_COLUMN = 0;

    private final SheetsGateway gateway;
    private final Map<String, ExerciseTemplate> templatesById;
    private final ZoneId zone;

    public WorkoutSetsTable(SheetsGateway gateway, Collection<ExerciseTemplate> templates, ZoneId zone) {
        this.gateway = gateway;
        this.templatesById = templates.stream()
                .collect(Collectors.toMap(ExerciseTemplate::id, Function.identity(), (a, b) -> a));
        this.zone = zone;
    }

    /**
     * Aplica os eventos de /workouts/events à aba. Para cada treino vale o evento mais recente:
     * se foi atualizado por último, as linhas são trocadas pelas novas; se foi apagado, são removidas.
     * A ordem em que a API devolve os eventos não importa.
     *
     * @return quantos treinos foram gravados e quantos foram removidos
     */
    public Result applyEvents(Collection<WorkoutEvent> events) {
        Map<String, WorkoutEvent> latestById = new LinkedHashMap<>();
        for (WorkoutEvent event : events) {
            String id = event.isUpdated() ? event.workout().id() : event.id();
            latestById.merge(id, event, (a, b) -> momentOf(b).isAfter(momentOf(a)) ? b : a);
        }

        List<Workout> toWrite = new ArrayList<>();
        int removed = 0;
        for (WorkoutEvent event : latestById.values()) {
            if (event.isUpdated()) {
                toWrite.add(event.workout());
            } else {
                removed++;
            }
        }

        gateway.ensureSheets(List.of(SHEET));
        List<String> workoutIds = gateway.readColumn(SHEET, WORKOUT_ID_COLUMN);
        if (workoutIds.isEmpty()) {
            gateway.replaceRows(SHEET, List.of(HEADER), SheetsGateway.InputMode.RAW);
            workoutIds = List.of("workout_id");
        }

        Set<String> affected = new HashSet<>(latestById.keySet());
        List<Integer> rowsToDelete = new ArrayList<>();
        for (int i = 1; i < workoutIds.size(); i++) { // a linha 0 é o cabeçalho
            if (affected.contains(workoutIds.get(i))) {
                rowsToDelete.add(i);
            }
        }
        gateway.deleteRows(SHEET, rowsToDelete);

        List<List<Object>> newRows = new ArrayList<>();
        toWrite.forEach(workout -> newRows.addAll(rowsOf(workout)));
        if (!newRows.isEmpty()) {
            gateway.appendRows(SHEET, newRows, SheetsGateway.InputMode.TYPED);
        }
        return new Result(toWrite.size(), removed, rowsToDelete.size(), newRows.size());
    }

    /** As linhas de um treino: uma por série, na ordem do treino. */
    public List<List<Object>> rowsOf(Workout workout) {
        LocalDate date = workout.startTime().atZoneSameInstant(zone).toLocalDate();
        LocalDate week = Weeks.startOf(workout.startTime(), zone);

        List<List<Object>> rows = new ArrayList<>();
        for (Exercise exercise : workout.exercises()) {
            ExerciseTemplate template = templateOf(exercise);
            String secondary = String.join(", ", template.secondaryMuscleGroups());
            for (SetEntry set : exercise.sets()) {
                rows.add(List.of(
                        workout.id(),
                        date.toString(),
                        week.toString(),
                        nullToEmpty(workout.title()),
                        exercise.title(),
                        exercise.exerciseTemplateId(),
                        template.primaryMuscleGroup(),
                        secondary,
                        set.type(),
                        VolumeCalculator.isWorkingSet(set),
                        nullToEmpty(set.weightKg()),
                        nullToEmpty(set.reps()),
                        nullToEmpty(set.rpe())));
            }
        }
        return rows;
    }

    /** Resultado de uma atualização, para o log. */
    public record Result(int workoutsWritten, int workoutsRemoved, int rowsDeleted, int rowsAppended) {
    }

    private ExerciseTemplate templateOf(Exercise exercise) {
        ExerciseTemplate template = templatesById.get(exercise.exerciseTemplateId());
        if (template == null) {
            throw new IllegalArgumentException("O exercício \"" + exercise.title() + "\" usa o template "
                    + exercise.exerciseTemplateId() + ", que não está na lista de templates. "
                    + "Pode ser um exercício personalizado que foi apagado no Hevy.");
        }
        return template;
    }

    private static OffsetDateTime momentOf(WorkoutEvent event) {
        return event.isUpdated() ? event.workout().updatedAt() : event.deletedAt();
    }

    /** O Sheets recebe "" nas células vazias; null viraria um valor estranho no JSON. */
    private static Object nullToEmpty(Object value) {
        return value == null ? "" : value;
    }
}
