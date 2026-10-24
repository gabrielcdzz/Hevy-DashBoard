package com.gabriel.hevydashboard.sheets;

import com.gabriel.hevydashboard.model.ExerciseTemplate;
import com.gabriel.hevydashboard.model.Workout;
import com.gabriel.hevydashboard.model.WorkoutEvent;
import com.gabriel.hevydashboard.service.SetRecord;
import com.gabriel.hevydashboard.service.SetRecords;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.gabriel.hevydashboard.sheets.Cells.bool;
import static com.gabriel.hevydashboard.sheets.Cells.date;
import static com.gabriel.hevydashboard.sheets.Cells.doubleOrNull;
import static com.gabriel.hevydashboard.sheets.Cells.integerOrNull;
import static com.gabriel.hevydashboard.sheets.Cells.text;

/**
 * A aba "series_treinos": uma linha por série de cada treino executado.
 * <p>
 * Guarda os dados brutos, sem aplicar as regras de volume (todos os músculos secundários que o Hevy
 * informa, aquecimento incluído e marcado em {@code valida}). Assim as regras podem mudar depois
 * e o volume é recalculado a partir daqui, sem perder histórico.
 * <p>
 * A atualização é incremental: apaga só as linhas dos treinos que mudaram e acrescenta as novas.
 * As demais linhas nunca são relidas nem regravadas. Para calcular as métricas, {@link #readAll()}
 * lê a aba inteira sem formatação.
 */
public class WorkoutSetsTable {

    public static final String SHEET = "series_treinos";

    public static final List<Object> HEADER = List.of(
            "workout_id", "data", "semana_inicio", "rotina", "exercicio", "template_id",
            "musculo_primario", "musculos_secundarios", "tipo_serie", "valida", "carga_kg", "reps", "rpe",
            "duracao_min");

    private static final int WORKOUT_ID = 0;
    private static final int DATE = 1;
    private static final int WEEK = 2;
    private static final int TITLE = 3;
    private static final int EXERCISE = 4;
    private static final int TEMPLATE_ID = 5;
    private static final int PRIMARY = 6;
    private static final int SECONDARY = 7;
    private static final int TYPE = 8;
    private static final int VALID = 9;
    private static final int WEIGHT = 10;
    private static final int REPS = 11;
    private static final int RPE = 12;
    private static final int DURATION = 13;

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
        List<String> workoutIds = gateway.readColumn(SHEET, WORKOUT_ID);
        if (workoutIds.isEmpty()) {
            gateway.replaceRows(SHEET, List.of(HEADER), SheetsGateway.InputMode.RAW);
            workoutIds = List.of("workout_id");
        } else {
            requireCurrentHeader(gateway.readFirstRow(SHEET));
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

    /** Todas as séries da aba. Aba vazia (ou só com o cabeçalho) devolve lista vazia. */
    public List<SetRecord> readAll() {
        gateway.ensureSheets(List.of(SHEET));
        List<List<Object>> rows = gateway.readTypedRows(SHEET);
        if (rows.isEmpty()) {
            return List.of();
        }
        requireCurrentHeader(rows.get(0).stream().map(String::valueOf).toList());
        return rows.stream().skip(1)
                .filter(row -> !row.isEmpty() && !text(row, WORKOUT_ID).isEmpty())
                .map(WorkoutSetsTable::fromRow)
                .toList();
    }

    /** As linhas de um treino: uma por série, na ordem do treino. */
    public List<List<Object>> rowsOf(Workout workout) {
        return SetRecords.of(workout, templatesById, zone).stream()
                .map(WorkoutSetsTable::toRow)
                .toList();
    }

    static List<Object> toRow(SetRecord set) {
        return Arrays.asList(
                set.workoutId(),
                set.date().toString(),
                set.week().toString(),
                set.workoutTitle(),
                set.exercise(),
                set.templateId(),
                set.primaryMuscle(),
                String.join(", ", set.secondaryMuscles()),
                set.type(),
                set.valid(),
                emptyIfNull(set.weightKg()),
                emptyIfNull(set.reps()),
                emptyIfNull(set.rpe()),
                emptyIfNull(set.durationMinutes()));
    }

    /**
     * Lê uma linha como o Sheets a devolve sem formatação: data como número de dias, mas também aceita
     * texto no formato 2026-10-24, e número ou texto nas colunas numéricas.
     */
    static SetRecord fromRow(List<Object> row) {
        String secondary = text(row, SECONDARY);
        return new SetRecord(
                text(row, WORKOUT_ID),
                date(row, DATE),
                date(row, WEEK),
                text(row, TITLE),
                integerOrNull(row, DURATION),
                text(row, EXERCISE),
                text(row, TEMPLATE_ID),
                text(row, PRIMARY),
                secondary.isEmpty() ? List.of() : Arrays.stream(secondary.split(",")).map(String::trim).toList(),
                text(row, TYPE),
                bool(row, VALID),
                doubleOrNull(row, WEIGHT),
                integerOrNull(row, REPS),
                doubleOrNull(row, RPE));
    }

    /** Resultado de uma atualização, para o log. */
    public record Result(int workoutsWritten, int workoutsRemoved, int rowsDeleted, int rowsAppended) {
    }

    private static void requireCurrentHeader(List<String> header) {
        if (!header.equals(HEADER.stream().map(String::valueOf).toList())) {
            throw new IllegalStateException("A aba " + SHEET + " tem colunas diferentes das esperadas (versão antiga?). "
                    + "Apague todo o conteúdo dela e o valor de ultima_sync na aba estado_sync, "
                    + "e rode de novo para importar o histórico.");
        }
    }

    private static OffsetDateTime momentOf(WorkoutEvent event) {
        return event.isUpdated() ? event.workout().updatedAt() : event.deletedAt();
    }

    /** O Sheets recebe "" nas células vazias; null viraria um valor estranho no JSON. */
    private static Object emptyIfNull(Object value) {
        return value == null ? "" : value;
    }
}
