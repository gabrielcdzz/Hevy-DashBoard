package com.gabriel.hevydashboard;

import com.gabriel.hevydashboard.client.HevyApiException;
import com.gabriel.hevydashboard.client.HevyClient;
import com.gabriel.hevydashboard.model.Exercise;
import com.gabriel.hevydashboard.model.ExerciseTemplate;
import com.gabriel.hevydashboard.export.DashboardExporter;
import com.gabriel.hevydashboard.export.SampleData;
import com.gabriel.hevydashboard.model.Routine;
import com.gabriel.hevydashboard.sheets.SheetsClient;
import com.gabriel.hevydashboard.sheets.SheetsException;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Ponto de entrada.
 * <ul>
 *   <li>{@code sample [arquivo]}: gera os dados de exemplo do dashboard (padrão: site/data/sample.json).</li>
 *   <li>sem argumentos: por enquanto, só um teste de conexão com o Hevy e a planilha.
 *       A sincronização completa vem nas próximas etapas.</li>
 * </ul>
 */
public class Main {

    /** Data fixa dos dados de exemplo, para o arquivo gerado ser sempre o mesmo. */
    private static final LocalDate SAMPLE_TODAY = LocalDate.of(2026, 10, 24);
    private static final Instant SAMPLE_GENERATED_AT = Instant.parse("2026-10-24T12:00:00Z");

    public static void main(String[] args) {
        if (args.length > 0 && "sample".equals(args[0])) {
            Path file = Path.of(args.length > 1 ? args[1] : "site/data/sample.json");
            DashboardExporter.write(SampleData.dashboard(SAMPLE_TODAY, SAMPLE_GENERATED_AT), file);
            System.out.println("Dados de exemplo gravados em " + file.toAbsolutePath());
            return;
        }
        try {
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

        } catch (HevyApiException | SheetsException | IllegalStateException e) {
            System.err.println("Erro: " + e.getMessage());
            System.exit(1);
        }
    }
}
