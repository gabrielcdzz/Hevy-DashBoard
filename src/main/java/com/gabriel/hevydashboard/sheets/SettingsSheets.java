package com.gabriel.hevydashboard.sheets;

import com.gabriel.hevydashboard.service.Settings;
import com.gabriel.hevydashboard.service.VolumeRules;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * As abas que o usuário edita à mão: ajustes_manuais e config.
 * <p>
 * Uma aba só é preenchida com os valores iniciais quando está <b>totalmente vazia</b>. Depois disso o
 * conteúdo é do usuário e o programa apenas lê, nunca sobrescreve. Apagar as linhas e deixar só o
 * cabeçalho é uma escolha válida e é respeitada.
 * <p>
 * Abas de versões anteriores que deixaram de existir (faixa_alvo, niveis_volume e fases) não são lidas nem
 * apagadas: se estiverem na planilha, ficam lá, sem uso, até o usuário removê-las.
 */
public class SettingsSheets {

    public static final String ADJUSTMENTS = "ajustes_manuais";
    public static final String CONFIG = "config";

    private static final String INDIRECT = "indireta";
    private static final String GROUPING = "agrupamento";
    /** Os valores iniciais ficam em {@link Settings#defaults()}, usados também pelos dados de exemplo. */
    private static final Settings DEFAULTS = Settings.defaults();

    private static final String STAGNATION_KEY = "semanas_estagnacao";
    private static final int DEFAULT_STAGNATION_WEEKS = DEFAULTS.stagnationWeeks();
    private static final String WORKOUTS_KEY = "treinos_por_semana";
    private static final int DEFAULT_WORKOUTS_PER_WEEK = DEFAULTS.workoutsPerWeek();

    private final SheetsGateway gateway;

    public SettingsSheets(SheetsGateway gateway) {
        this.gateway = gateway;
    }

    /** Cria e preenche as abas que estiverem vazias e lê todas as configurações. */
    public Settings load() {
        gateway.ensureSheets(List.of(ADJUSTMENTS, CONFIG));
        seedIfEmpty(ADJUSTMENTS, adjustmentsSeed());
        seedIfEmpty(CONFIG, configSeed());
        appendMissingConfigKeys();

        return new Settings(
                readRules(),
                readConfigNumber(STAGNATION_KEY, DEFAULT_STAGNATION_WEEKS),
                readConfigNumber(WORKOUTS_KEY, DEFAULT_WORKOUTS_PER_WEEK));
    }

    // ------------------------------------------------------------------
    // Valores iniciais
    // ------------------------------------------------------------------

    private static List<List<Object>> adjustmentsSeed() {
        VolumeRules defaults = VolumeRules.DEFAULT;
        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of("tipo", "musculo", "grupo"));
        defaults.indirectMuscles().stream().sorted()
                .forEach(muscle -> rows.add(List.of(INDIRECT, muscle, "")));
        defaults.muscleGroups().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> rows.add(List.of(GROUPING, e.getKey(), e.getValue())));
        return rows;
    }

    private static List<List<Object>> configSeed() {
        return List.of(
                List.of("chave", "valor"),
                List.of(STAGNATION_KEY, DEFAULT_STAGNATION_WEEKS),
                List.of(WORKOUTS_KEY, DEFAULT_WORKOUTS_PER_WEEK));
    }

    /**
     * Quando uma versão nova do programa passa a precisar de um parâmetro, a aba config que o usuário já
     * tem não o possui. Em vez de sobrescrever a aba, a linha que falta é acrescentada no fim.
     */
    private void appendMissingConfigKeys() {
        Set<String> present = new HashSet<>();
        List<List<String>> rows = gateway.readRows(CONFIG);
        for (int i = 1; i < rows.size(); i++) {
            present.add(cell(rows.get(i), 0));
        }
        List<List<Object>> missing = new ArrayList<>();
        if (!present.contains(STAGNATION_KEY)) {
            missing.add(List.of(STAGNATION_KEY, DEFAULT_STAGNATION_WEEKS));
        }
        if (!present.contains(WORKOUTS_KEY)) {
            missing.add(List.of(WORKOUTS_KEY, DEFAULT_WORKOUTS_PER_WEEK));
        }
        if (!missing.isEmpty()) {
            gateway.appendRows(CONFIG, missing, SheetsGateway.InputMode.TYPED);
        }
    }

    private void seedIfEmpty(String sheet, List<List<Object>> rows) {
        if (gateway.readRows(sheet).isEmpty()) {
            gateway.replaceRows(sheet, rows, SheetsGateway.InputMode.TYPED);
        }
    }

    // ------------------------------------------------------------------
    // Leitura
    // ------------------------------------------------------------------

    private VolumeRules readRules() {
        Set<String> indirect = new HashSet<>();
        Map<String, String> groups = new LinkedHashMap<>();
        List<List<String>> rows = gateway.readRows(ADJUSTMENTS);
        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (isBlank(row)) {
                continue;
            }
            String type = lower(cell(row, 0));
            String muscle = lower(cell(row, 1));
            switch (type) {
                case INDIRECT -> indirect.add(requireText(ADJUSTMENTS, i, "musculo", muscle));
                case GROUPING -> groups.put(
                        requireText(ADJUSTMENTS, i, "musculo", muscle),
                        requireText(ADJUSTMENTS, i, "grupo", lower(cell(row, 2))));
                default -> throw invalid(ADJUSTMENTS, i,
                        "o tipo \"" + cell(row, 0) + "\" não existe. Use \"" + INDIRECT + "\" ou \"" + GROUPING + "\"");
            }
        }
        return new VolumeRules(indirect, groups);
    }

    private int readConfigNumber(String key, int defaultValue) {
        List<List<String>> rows = gateway.readRows(CONFIG);
        for (int i = 1; i < rows.size(); i++) {
            if (key.equals(cell(rows.get(i), 0))) {
                return parseNumber(CONFIG, i, "valor", cell(rows.get(i), 1));
            }
        }
        return defaultValue;
    }

    private static int parseNumber(String sheet, int rowIndex, String column, String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw invalid(sheet, rowIndex, "a coluna \"" + column + "\" precisa ser um número inteiro, mas está \"" + text + "\"");
        }
    }

    private static String requireText(String sheet, int rowIndex, String column, String text) {
        if (text.isEmpty()) {
            throw invalid(sheet, rowIndex, "a coluna \"" + column + "\" está vazia");
        }
        return text;
    }

    /** Erro que diz a aba e a linha (como aparece na planilha, contando o cabeçalho como linha 1). */
    private static IllegalStateException invalid(String sheet, int rowIndex, String problem) {
        return new IllegalStateException("Aba " + sheet + ", linha " + (rowIndex + 1) + ": " + problem + ".");
    }

    private static String cell(List<String> row, int column) {
        return column < row.size() ? row.get(column).trim() : "";
    }

    private static String lower(String text) {
        return text.toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(List<String> row) {
        return row.stream().allMatch(c -> c.isBlank());
    }
}
