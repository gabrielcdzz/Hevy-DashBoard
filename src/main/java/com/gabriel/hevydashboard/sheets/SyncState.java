package com.gabriel.hevydashboard.sheets;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

/**
 * Guarda a data da última sincronização na aba "estado_sync" da própria planilha.
 * O GitHub Actions começa do zero a cada execução e não lembra de nada, então essa data
 * precisa ficar em algum lugar. Aba vazia significa "nunca sincronizou": importa o histórico inteiro.
 * <p>
 * Formato da aba: cabeçalho {@code chave | valor} e uma linha {@code ultima_sync | 2026-10-24T22:00:00Z}.
 */
public class SyncState {

    public static final String SHEET = "estado_sync";
    private static final String KEY = "ultima_sync";

    private final SheetsGateway gateway;

    public SyncState(SheetsGateway gateway) {
        this.gateway = gateway;
    }

    public Optional<Instant> lastSync() {
        gateway.ensureSheets(List.of(SHEET));
        for (List<String> row : gateway.readRows(SHEET)) {
            if (row.size() >= 2 && KEY.equals(row.get(0)) && !row.get(1).isBlank()) {
                return Optional.of(parse(row.get(1)));
            }
        }
        return Optional.empty();
    }

    public void saveLastSync(Instant moment) {
        gateway.ensureSheets(List.of(SHEET));
        gateway.replaceRows(SHEET, List.of(
                List.of("chave", "valor"),
                List.of(KEY, moment.toString())),
                SheetsGateway.InputMode.RAW);
    }

    private static Instant parse(String text) {
        try {
            return Instant.parse(text.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalStateException("A data na aba " + SHEET + " não é válida: \"" + text
                    + "\". Apague o valor para importar o histórico de novo, ou use o formato 2026-10-24T22:00:00Z.", e);
        }
    }
}
