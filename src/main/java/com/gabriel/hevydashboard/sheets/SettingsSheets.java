package com.gabriel.hevydashboard.sheets;

import com.gabriel.hevydashboard.service.Phase;
import com.gabriel.hevydashboard.service.Settings;
import com.gabriel.hevydashboard.service.TargetRange;
import com.gabriel.hevydashboard.service.VolumeLevel;
import com.gabriel.hevydashboard.service.VolumeLevels;
import com.gabriel.hevydashboard.service.VolumeRules;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * As abas que o usuário edita à mão: ajustes_manuais, faixa_alvo, niveis_volume, fases e config.
 * <p>
 * Uma aba só é preenchida com os valores iniciais quando está <b>totalmente vazia</b>. Depois disso o
 * conteúdo é do usuário e o programa apenas lê, nunca sobrescreve. Apagar as linhas e deixar só o
 * cabeçalho é uma escolha válida e é respeitada.
 * <p>
 * A aba "fases" é lida só pelo dashboard (marcador nos gráficos), então o Java apenas a cria.
 */
public class SettingsSheets {

    public static final String ADJUSTMENTS = "ajustes_manuais";
    public static final String TARGETS = "faixa_alvo";
    public static final String LEVELS = "niveis_volume";
    public static final String PHASES = "fases";
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
        gateway.ensureSheets(List.of(ADJUSTMENTS, TARGETS, LEVELS, PHASES, CONFIG));
        seedIfEmpty(ADJUSTMENTS, adjustmentsSeed());
        seedIfEmpty(TARGETS, targetsSeed());
        seedIfEmpty(LEVELS, levelsSeed());
        seedIfEmpty(PHASES, phasesSeed());
        seedIfEmpty(CONFIG, configSeed());
        appendMissingConfigKeys();

        return new Settings(
                readRules(),
                readTargets(),
                readLevels(),
                readConfigNumber(STAGNATION_KEY, DEFAULT_STAGNATION_WEEKS),
                readConfigNumber(WORKOUTS_KEY, DEFAULT_WORKOUTS_PER_WEEK));
    }

    /**
     * As fases (aba "fases"). Só o dashboard usa, para marcar nos gráficos. Uma fase sem início é erro;
     * sem fim, é a fase em andamento.
     */
    public List<Phase> loadPhases() {
        gateway.ensureSheets(List.of(PHASES));
        seedIfEmpty(PHASES, phasesSeed());
        List<List<Object>> rows = gateway.readTypedRows(PHASES);
        List<Phase> phases = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            List<Object> row = rows.get(i);
            if (row.stream().allMatch(c -> String.valueOf(c).isBlank())) {
                continue;
            }
            String name = requireText(PHASES, i, "fase", Cells.text(row, 0));
            LocalDate start;
            try {
                start = Cells.dateOrNull(row, 1);
            } catch (DateTimeParseException e) {
                throw invalid(PHASES, i, "a data de início \"" + Cells.text(row, 1) + "\" não é válida. Use o formato 2026-09-14");
            }
            if (start == null) {
                throw invalid(PHASES, i, "a coluna \"inicio\" está vazia");
            }
            LocalDate end;
            try {
                end = Cells.dateOrNull(row, 2);
            } catch (DateTimeParseException e) {
                throw invalid(PHASES, i, "a data de fim \"" + Cells.text(row, 2) + "\" não é válida. Use o formato 2026-09-14");
            }
            phases.add(new Phase(name, start, end));
        }
        return phases;
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

    private static List<List<Object>> targetsSeed() {
        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of("musculo", "min", "max"));
        DEFAULTS.targets().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> rows.add(List.of(e.getKey(), e.getValue().min(), limitCell(e.getValue()))));
        return rows;
    }

    private static List<List<Object>> levelsSeed() {
        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of("nivel", "min", "max"));
        DEFAULTS.levels().levels()
                .forEach(level -> rows.add(List.of(level.name(), level.range().min(), limitCell(level.range()))));
        return rows;
    }

    /** O teto como vai para a planilha: vazio quando não há limite. */
    private static Object limitCell(TargetRange range) {
        return range.max() == TargetRange.NO_LIMIT ? "" : range.max();
    }

    /** Uma planilha nova não tem fase nenhuma: só o cabeçalho. O usuário preenche (ex.: cutting, 2026-01-05, vazio). */
    private static List<List<Object>> phasesSeed() {
        return List.of(List.of("fase", "inicio", "fim"));
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

    private Map<String, TargetRange> readTargets() {
        Map<String, TargetRange> targets = new LinkedHashMap<>();
        List<List<String>> rows = gateway.readRows(TARGETS);
        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (isBlank(row)) {
                continue;
            }
            targets.put(requireText(TARGETS, i, "musculo", lower(cell(row, 0))), readRange(TARGETS, i, row));
        }
        return targets;
    }

    private VolumeLevels readLevels() {
        List<VolumeLevel> levels = new ArrayList<>();
        List<List<String>> rows = gateway.readRows(LEVELS);
        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (isBlank(row)) {
                continue;
            }
            levels.add(new VolumeLevel(requireText(LEVELS, i, "nivel", cell(row, 0)), readRange(LEVELS, i, row)));
        }
        return new VolumeLevels(levels);
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

    /** Lê as colunas min e max (B e C). Max vazio significa "sem teto". */
    private TargetRange readRange(String sheet, int rowIndex, List<String> row) {
        int min = parseNumber(sheet, rowIndex, "min", cell(row, 1));
        String maxText = cell(row, 2);
        int max = maxText.isEmpty() ? TargetRange.NO_LIMIT : parseNumber(sheet, rowIndex, "max", maxText);
        if (max < min) {
            throw invalid(sheet, rowIndex, "o max (" + max + ") é menor que o min (" + min + ")");
        }
        return new TargetRange(min, max);
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
