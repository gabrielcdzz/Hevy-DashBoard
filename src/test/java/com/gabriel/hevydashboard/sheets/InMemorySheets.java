package com.gabriel.hevydashboard.sheets;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Planilha falsa, em memória, para testar sem chamar o Google. */
public class InMemorySheets implements SheetsGateway {

    public final Map<String, List<List<String>>> sheets = new LinkedHashMap<>();

    @Override
    public void ensureSheets(Collection<String> titles) {
        titles.forEach(title -> sheets.putIfAbsent(title, new ArrayList<>()));
    }

    @Override
    public List<List<String>> readRows(String sheet) {
        return List.copyOf(rowsOf(sheet));
    }

    /** A planilha falsa guarda texto; o Google devolveria números e datas, e o código aceita os dois. */
    @Override
    public List<List<Object>> readTypedRows(String sheet) {
        return rowsOf(sheet).stream().map(row -> (List<Object>) List.<Object>copyOf(row)).toList();
    }

    @Override
    public List<String> readFirstRow(String sheet) {
        List<List<String>> rows = rowsOf(sheet);
        return rows.isEmpty() ? List.of() : rows.get(0);
    }

    @Override
    public List<String> readColumn(String sheet, int column) {
        return rowsOf(sheet).stream()
                .map(row -> column < row.size() ? row.get(column) : "")
                .toList();
    }

    @Override
    public void replaceRows(String sheet, List<List<Object>> rows, InputMode mode) {
        List<List<String>> target = rowsOf(sheet);
        target.clear();
        appendRows(sheet, rows, mode);
    }

    @Override
    public void appendRows(String sheet, List<List<Object>> rows, InputMode mode) {
        rowsOf(sheet).addAll(rows.stream()
                .map(row -> row.stream().map(String::valueOf).toList())
                .toList());
    }

    @Override
    public void deleteRows(String sheet, Collection<Integer> rowIndexes) {
        List<List<String>> target = rowsOf(sheet);
        rowIndexes.stream().sorted(Comparator.reverseOrder()).forEach(i -> target.remove((int) i));
    }

    private List<List<String>> rowsOf(String sheet) {
        List<List<String>> rows = sheets.get(sheet);
        if (rows == null) {
            throw new IllegalStateException("Aba inexistente: " + sheet);
        }
        return rows;
    }
}
