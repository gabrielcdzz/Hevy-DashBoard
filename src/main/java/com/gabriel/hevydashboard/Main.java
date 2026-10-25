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
import com.gabriel.hevydashboard.web.SiteServer;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Ponto de entrada. Comandos:
 * <ul>
 *   <li>{@code sync [arquivo]}: sincroniza o Hevy com a planilha e gera o arquivo de dados do dashboard
 *       (padrão: site/data/dashboard.json). Precisa de HEVY_API_KEY, GOOGLE_APPLICATION_CREDENTIALS e SPREADSHEET_ID.</li>
 *   <li>{@code sample [arquivo]}: gera os dados de exemplo do dashboard (padrão: site/data/sample.json).</li>
 *   <li>{@code serve [porta]}: abre o dashboard no navegador, com um servidor que só escuta em localhost.
 *       Mostra os seus dados se site/data/dashboard.json existir; senão, os dados de exemplo.</li>
 *   <li>{@code dashboard [porta]}: o comando do dia a dia. Faz o {@code sync} e depois o {@code serve}.</li>
 *   <li>{@code check}: só testa a conexão com o Hevy e com a planilha.</li>
 * </ul>
 */
public class Main {

    /** Data fixa dos dados de exemplo, para o arquivo gerado ser sempre o mesmo. */
    private static final LocalDate SAMPLE_TODAY = LocalDate.of(2026, 10, 24);
    private static final Instant SAMPLE_GENERATED_AT = Instant.parse("2026-10-24T12:00:00Z");
    private static final int DEFAULT_PORT = 8080;

    public static void main(String[] args) {
        String command = args.length > 0 ? args[0] : "";
        try {
            switch (command) {
                case "sync" -> sync(args.length > 1 ? args[1] : "site/data/dashboard.json");
                case "sample" -> sample(args.length > 1 ? args[1] : "site/data/sample.json");
                case "serve" -> serve(args.length > 1 ? args[1] : null);
                case "dashboard" -> {
                    sync("site/data/dashboard.json");
                    serve(args.length > 1 ? args[1] : null);
                }
                case "check" -> check();
                default -> {
                    System.out.println("Uso: java -jar hevy-dashboard.jar <comando> [arquivo]");
                    System.out.println("  dashboard  sincroniza com o Hevy e abre o dashboard com os seus dados (o do dia a dia)");
                    System.out.println("  sync       só sincroniza e gera site/data/dashboard.json");
                    System.out.println("  serve      só abre o dashboard (os seus dados, se já sincronizou; senão o exemplo)");
                    System.out.println("  sample     gera os dados de exemplo em site/data/sample.json");
                    System.out.println("  check      testa a conexão com o Hevy e com a planilha");
                    System.exit(command.isEmpty() ? 0 : 2);
                }
            }
        } catch (IOException e) {
            System.err.println("Erro: não consegui iniciar o servidor (" + e.getMessage() + "). Se a porta estiver em uso, tente outra: serve 8081.");
            System.exit(1);
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

    private static void serve(String portArg) throws IOException {
        int port = DEFAULT_PORT;
        if (portArg != null) {
            try {
                port = Integer.parseInt(portArg);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("A porta precisa ser um número, mas veio \"" + portArg + "\".");
            }
        }
        Path site = Path.of("site");
        if (!Files.exists(site.resolve("index.html"))) {
            throw new IllegalStateException("Não encontrei a pasta site/. Rode o comando dentro da pasta do projeto.");
        }
        boolean real = Files.exists(site.resolve("data/dashboard.json"));

        try (SiteServer server = SiteServer.start(site, port)) {
            System.out.println(real
                    ? "Mostrando os SEUS dados (site/data/dashboard.json)."
                    : "Mostrando os dados de EXEMPLO. Para ver os seus, use o comando dashboard.");
            System.out.println("Dashboard no ar em " + server.url() + "  (Ctrl+C para parar)");
            openBrowser(server.url());
            new CountDownLatch(1).await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Abre o navegador padrão; se não der (sem ambiente gráfico), o endereço já foi escrito na tela. */
    private static void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            // sem navegador automático: o usuário abre o endereço que está na tela
        }
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
