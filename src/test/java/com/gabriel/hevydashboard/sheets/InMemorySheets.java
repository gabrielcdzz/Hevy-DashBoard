package com.gabriel.hevydashboard.sheets;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Planilha falsa, em memória, para testar sem chamar o Google. */
class InMemorySheets implements SheetsGateway {

    final Map<String, List<List<String>>> sheets = new LinkedHashMap<>();

    @Override
    public void ensureSheets(Collection<String> titles) {
        titles.forEach(title -> sheets.putIfAbsent(title, List.of()));
    }

    @Override
    public List<List<String>> readRows(String sheet) {
        if (!sheets.containsKey(sheet)) {
            throw new IllegalStateException("Aba inexistente: " + sheet);
        }
        return sheets.get(sheet);
    }

    @Override
    public void replaceRows(String sheet, List<List<Object>> rows, InputMode mode) {
        if (!sheets.containsKey(sheet)) {
            throw new IllegalStateException("Aba inexistente: " + sheet);
        }
        sheets.put(sheet, rows.stream()
                .map(row -> row.stream().map(String::valueOf).toList())
                .toList());
    }
}
