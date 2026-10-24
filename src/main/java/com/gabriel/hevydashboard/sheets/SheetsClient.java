package com.gabriel.hevydashboard.sheets;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.AddSheetRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.ClearValuesRequest;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.ServiceAccountCredentials;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cliente da planilha do Google Sheets, autenticado com a conta de serviço.
 * A conta de serviço só enxerga planilhas compartilhadas com o e-mail dela.
 */
public class SheetsClient implements SheetsGateway {

    private static final String APPLICATION_NAME = "hevy-dashboard";

    /** Linhas por chamada de escrita, para o pedido não ficar grande demais com o histórico inteiro. */
    private static final int ROWS_PER_WRITE = 5000;

    private final Sheets sheets;
    private final String spreadsheetId;
    private final String serviceAccountEmail;

    public SheetsClient(Sheets sheets, String spreadsheetId, String serviceAccountEmail) {
        this.sheets = sheets;
        this.spreadsheetId = spreadsheetId;
        this.serviceAccountEmail = serviceAccountEmail;
    }

    /**
     * Cria o cliente a partir de duas variáveis de ambiente:
     * GOOGLE_APPLICATION_CREDENTIALS (caminho do JSON da conta de serviço) e SPREADSHEET_ID.
     */
    public static SheetsClient fromEnvironment() {
        String credentialsPath = requireEnv("GOOGLE_APPLICATION_CREDENTIALS");
        String spreadsheetId = requireEnv("SPREADSHEET_ID");

        try (InputStream in = Files.newInputStream(Path.of(credentialsPath))) {
            ServiceAccountCredentials credentials = ServiceAccountCredentials.fromStream(in);
            Sheets sheets = new Sheets.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(credentials.createScoped(SheetsScopes.SPREADSHEETS)))
                    .setApplicationName(APPLICATION_NAME)
                    .build();
            return new SheetsClient(sheets, spreadsheetId, credentials.getClientEmail());
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException(
                    "Não foi possível ler a credencial do Google em " + credentialsPath, e);
        }
    }

    public String spreadsheetTitle() {
        Spreadsheet spreadsheet = execute(() -> sheets.spreadsheets().get(spreadsheetId)
                .setFields("properties.title")
                .execute());
        return spreadsheet.getProperties().getTitle();
    }

    public List<String> sheetTitles() {
        Spreadsheet spreadsheet = execute(() -> sheets.spreadsheets().get(spreadsheetId)
                .setFields("sheets.properties.title")
                .execute());
        return spreadsheet.getSheets().stream()
                .map(Sheet::getProperties)
                .map(SheetProperties::getTitle)
                .toList();
    }

    @Override
    public void ensureSheets(Collection<String> titles) {
        Set<String> existing = Set.copyOf(sheetTitles());
        List<Request> requests = titles.stream()
                .filter(title -> !existing.contains(title))
                .map(title -> new Request().setAddSheet(
                        new AddSheetRequest().setProperties(new SheetProperties().setTitle(title))))
                .collect(Collectors.toList());
        if (requests.isEmpty()) {
            return;
        }
        execute(() -> sheets.spreadsheets()
                .batchUpdate(spreadsheetId, new BatchUpdateSpreadsheetRequest().setRequests(requests))
                .execute());
    }

    @Override
    public List<List<String>> readRows(String sheet) {
        ValueRange range = execute(() -> sheets.spreadsheets().values()
                .get(spreadsheetId, quoted(sheet))
                .execute());
        if (range.getValues() == null) {
            return List.of();
        }
        return range.getValues().stream()
                .map(row -> row.stream().map(String::valueOf).toList())
                .toList();
    }

    @Override
    public void replaceRows(String sheet, List<List<Object>> rows, InputMode mode) {
        execute(() -> sheets.spreadsheets().values()
                .clear(spreadsheetId, quoted(sheet), new ClearValuesRequest())
                .execute());

        String valueInputOption = mode == InputMode.RAW ? "RAW" : "USER_ENTERED";
        for (int start = 0; start < rows.size(); start += ROWS_PER_WRITE) {
            List<List<Object>> chunk = rows.subList(start, Math.min(start + ROWS_PER_WRITE, rows.size()));
            String firstCell = quoted(sheet) + "!A" + (start + 1);
            execute(() -> sheets.spreadsheets().values()
                    .update(spreadsheetId, firstCell, new ValueRange().setValues(chunk))
                    .setValueInputOption(valueInputOption)
                    .execute());
        }
    }

    // ------------------------------------------------------------------
    // Internos
    // ------------------------------------------------------------------

    private interface Call<T> {
        T run() throws IOException;
    }

    /** Executa a chamada e troca os erros mais comuns por mensagens que dizem o que fazer. */
    private <T> T execute(Call<T> call) {
        try {
            return call.run();
        } catch (GoogleJsonResponseException e) {
            int status = e.getStatusCode();
            String message = switch (status) {
                case 403 -> "A conta de serviço não tem acesso à planilha. Compartilhe a planilha, como Editor, com "
                        + serviceAccountEmail + ". Se já compartilhou, confira se a Google Sheets API está ativada.";
                case 404 -> "Planilha não encontrada. Confira o SPREADSHEET_ID: deve ser só o trecho entre /d/ e /edit da URL.";
                default -> "O Google Sheets respondeu " + status + ": " + e.getStatusMessage();
            };
            throw new SheetsException(message, status, e);
        } catch (IOException e) {
            throw new SheetsException("Falha de rede ao chamar o Google Sheets", -1, e);
        }
    }

    /** Nome da aba entre aspas simples, como o Sheets exige quando há espaço ou acento. */
    private static String quoted(String sheet) {
        return "'" + sheet.replace("'", "''") + "'";
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Variável de ambiente " + name + " não encontrada. "
                    + "Defina a variável e reinicie o terminal ou o VS Code.");
        }
        return value;
    }
}
