package com.gabriel.hevydashboard.sync;

import com.gabriel.hevydashboard.client.HevySource;
import com.gabriel.hevydashboard.export.DashboardData;
import com.gabriel.hevydashboard.export.DashboardExporter;
import com.gabriel.hevydashboard.model.Exercise;
import com.gabriel.hevydashboard.model.ExerciseTemplate;
import com.gabriel.hevydashboard.model.Routine;
import com.gabriel.hevydashboard.model.WorkoutEvent;
import com.gabriel.hevydashboard.service.MuscleVolume;
import com.gabriel.hevydashboard.service.Phase;
import com.gabriel.hevydashboard.service.SetRecord;
import com.gabriel.hevydashboard.service.SetRecords;
import com.gabriel.hevydashboard.service.Settings;
import com.gabriel.hevydashboard.service.VolumeCalculator;
import com.gabriel.hevydashboard.sheets.SettingsSheets;
import com.gabriel.hevydashboard.sheets.SheetsGateway;
import com.gabriel.hevydashboard.sheets.SyncState;
import com.gabriel.hevydashboard.sheets.WorkoutSetsTable;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Uma execução completa: traz do Hevy o que mudou desde a última vez, atualiza a planilha, calcula as
 * métricas a partir da planilha e grava o arquivo de dados do dashboard.
 * <p>
 * A data da última sincronização fica na planilha (aba estado_sync), porque o GitHub Actions não guarda
 * nada entre execuções. Ela só é atualizada depois que a planilha foi atualizada com sucesso, então uma
 * execução que falha no meio é refeita por inteiro na próxima, e como aplicar os mesmos eventos duas vezes
 * não duplica nada, a planilha se corrige sozinha.
 */
public class SyncService {

    private final HevySource hevy;
    private final SheetsGateway sheets;
    private final Clock clock;
    private final ZoneId zone;

    public SyncService(HevySource hevy, SheetsGateway sheets, Clock clock, ZoneId zone) {
        this.hevy = hevy;
        this.sheets = sheets;
        this.clock = clock;
        this.zone = zone;
    }

    /**
     * @param firstRun true se a planilha nunca tinha sido sincronizada (importou o histórico inteiro)
     * @param weeks    semanas no arquivo gerado
     */
    public record Result(boolean firstRun, Instant since, Instant syncedAt, WorkoutSetsTable.Result sheetUpdate,
                         int sets, int weeks, Path output) {
    }

    public Result run(Path output) {
        // O instante é anotado antes de buscar: um treino salvo durante a execução entra de novo na próxima
        // (sem prejuízo, porque a aplicação é idempotente) em vez de ser esquecido.
        Instant startedAt = clock.instant();

        SyncState state = new SyncState(sheets);
        Optional<Instant> last = state.lastSync();
        Instant since = last.orElse(Instant.EPOCH);

        List<ExerciseTemplate> templates = hevy.getAllExerciseTemplates();
        List<WorkoutEvent> events = hevy.getWorkoutEventsSince(since);

        WorkoutSetsTable table = new WorkoutSetsTable(sheets, templates, zone);
        WorkoutSetsTable.Result update = table.applyEvents(events);
        state.saveLastSync(startedAt);

        // Nome e músculos vêm do template de hoje, não do que ficou gravado na linha (ver SetRecords).
        Map<String, ExerciseTemplate> templatesById = templates.stream()
                .collect(Collectors.toMap(ExerciseTemplate::id, Function.identity(), (a, b) -> a));
        List<SetRecord> sets = SetRecords.withCurrentTemplates(table.readAll(), templatesById);
        SettingsSheets settingsSheets = new SettingsSheets(sheets);
        Settings settings = settingsSheets.load();
        List<Phase> phases = settingsSheets.loadPhases();

        // As rotinas não têm eventos na API, então são lidas inteiras a cada execução (são poucas).
        List<Routine> routines = hevy.getAllRoutines();
        List<Exercise> routineExercises = routines.stream().flatMap(r -> r.exercises().stream()).toList();
        Map<String, MuscleVolume> planned = new VolumeCalculator(templates, settings.rules()).volumeOf(routineExercises);
        Set<String> routineTemplateIds = routineExercises.stream().map(Exercise::exerciseTemplateId).collect(Collectors.toSet());
        Set<String> routineNames = routineExercises.stream()
                .map(e -> e.title().trim().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());

        DashboardData data = DashboardExporter.build(new DashboardExporter.Input(
                sets, settings, phases, planned, routineTemplateIds, routineNames,
                LocalDate.now(clock.withZone(zone)), startedAt, false));
        DashboardExporter.write(data, output);

        return new Result(last.isEmpty(), since, startedAt, update, sets.size(), data.weeks().size(), output);
    }
}
