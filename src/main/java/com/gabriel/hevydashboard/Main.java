package com.gabriel.hevydashboard;

import com.gabriel.hevydashboard.client.HevyApiException;
import com.gabriel.hevydashboard.client.HevyClient;
import com.gabriel.hevydashboard.export.DashboardExporter;
import com.gabriel.hevydashboard.export.SampleData;
import com.gabriel.hevydashboard.model.Exercise;
import com.gabriel.hevydashboard.model.ExerciseTemplate;
import com.gabriel.hevydashboard.model.Routine;
import com.gabriel.hevydashboard.service.Weeks;
import com.gabriel.hevydashboard.sheets.SheetsClient;
import com.gabriel.hevydashboard.sheets.SheetsException;
import com.gabriel.hevydashboard.sync.SyncService;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Ponto de entrada. Comandos:
 * <ul>
 *   <li>{@code sync [arquivo]}: sincroniza o Hevy com a planilha e gera o arquivo de dados do dashboard
 *       (padrão: site/data/dashboard.json). Precisa de HEVY_API_KEY, GOOGLE_APPLICATION_CREDENTIALS e SPREADSHEET_ID.</li>
 *   <li>{@code sample [arquivo]}: gera os dados de exemplo do dashboard (padrão: site/data/sample.json).</li>
 *   <li>{@code check}: só testa a conexão com o Hevy e com a planilha.</li>
 * </ul>
 */
public class Main {

    /** Data fixa dos dados de exemplo, para o arquivo gerado ser sempre o mesmo. */
    private static final LocalDate SAMPLE_TODAY = LocalDate.of(2026, 10, 24);
    private static final Instant SAMPLE_GENERATED_AT = Instant.parse("2026-10-24T12:00:00Z");

    public static void main(String[] args) {
        String command = args.length > 0 ? args[0] : "";
        try {
            switch (command) {
                case "sync" -> sync(args.length > 1 ? args[1] : "site/data/dashboard.json");
                case "sample" -> sample(args.length > 1 ? args[1] : "site/data/sample.json");
                case "check" -> check();
                default -> {
                    System.out.println("Uso: java -jar hevy-dashboard.jar <comando> [arquivo]");
                    System.out.println("  sync    sincroniza o Hevy com a planilha e gera site/data/dashboard.json");
                    System.out.println("  sample  gera os dados de exemplo em site/data/sample.json");
                    System.out.println("  check   testa a conexão com o Hevy e com a planilha");
                    System.exit(command.isEmpty() ? 0 : 2);
                }
            }
        } catch (HevyApiException | SheetsException | IllegalStateException | IllegalArgumentException e) {
            System.err.println("Erro: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void sync(String output) {
        SyncService.Result result = new SyncService(
                HevyClient.fromEnvironment(), SheetsClient.fromEnvironment(), Clock.systemUTC(), Weeks.SAO_PAULO)
                .run(Path.of(output));

        System.out.println(result.firstRun()
                ? "Primeira sincronização: histórico inteiro importado."
                : "Sincronização incremental, desde " + result.since() + ".");
        System.out.printf("Treinos gravados: %d, removidos: %d (linhas apagadas: %d, acrescentadas: %d)%n",
                result.sheetUpdate().workoutsWritten(), result.sheetUpdate().workoutsRemoved(),
                result.sheetUpdate().rowsDeleted(), result.sheetUpdate().rowsAppended());
        System.out.printf("Planilha com %d séries; dashboard com %d semanas.%n", result.sets(), result.weeks());
        System.out.println("Arquivo de dados: " + result.output().toAbsolutePath());
    }

    private static void sample(String output) {
        Path file = Path.of(output);
        DashboardExporter.write(SampleData.dashboard(SAMPLE_TODAY, SAMPLE_GENERATED_AT), file);
        System.out.println("Dados de exemplo gravados em " + file.toAbsolutePath());
    }

    private static void check() {
        HevyClient client = HevyClient.fromEnvironment();

        System.out.println("Treinos salvos no Hevy: " + client.getWorkoutCount());

        List<Routine> routines = client.getAllRoutines();
        System.out.println("Rotinas encontradas: " + routines.size());
        for (Routine routine : routines) {
            int sets = routine.exercises().stream().mapToInt(e -> e.sets().size()).sum();
            System.out.printf("  - %s: %d exercícios, %d séries (com aquecimento)%n",
                    routine.title(), routine.exercises().size(), sets);
        }

        List<ExerciseTemplate> templates = client.getAllExerciseTemplates();
        System.out.println("Exercícios cadastrados (templates): " + templates.size());

        routines.stream()
                .flatMap(r -> r.exercises().stream())
                .map(Exercise::title)
                .distinct()
                .limit(3)
                .forEach(t -> System.out.println("  exemplo de exercício em rotina: " + t));

        SheetsClient sheets = SheetsClient.fromEnvironment();
        System.out.println("Planilha: " + sheets.spreadsheetTitle());
        System.out.println("Abas: " + sheets.sheetTitles());
    }
}
