package com.gabriel.hevydashboard.sheets;

import java.time.LocalDate;
import java.util.List;

/**
 * Leitura de células de uma linha devolvida por {@link SheetsGateway#readTypedRows}, onde o Sheets
 * entrega números e datas como número, texto como texto e verdadeiro/falso como booleano.
 * Também aceita texto, para a planilha falsa dos testes e para células digitadas como texto.
 */
final class Cells {

    /** O Sheets conta as datas em dias desde 30/12/1899. */
    private static final LocalDate SHEETS_EPOCH = LocalDate.of(1899, 12, 30);

    private Cells() {
    }

    static String text(List<Object> row, int column) {
        Object value = column < row.size() ? row.get(column) : null;
        return value == null ? "" : String.valueOf(value).trim();
    }

    /** Data obrigatória: número de série do Sheets ou texto no formato 2026-10-24. */
    static LocalDate date(List<Object> row, int column) {
        LocalDate date = dateOrNull(row, column);
        if (date == null) {
            throw new IllegalStateException("Esperava uma data na coluna " + (column + 1) + ", mas a célula está vazia.");
        }
        return date;
    }

    static LocalDate dateOrNull(List<Object> row, int column) {
        Object value = column < row.size() ? row.get(column) : null;
        if (value instanceof Number number) {
            return SHEETS_EPOCH.plusDays(number.longValue());
        }
        String text = text(row, column);
        return text.isEmpty() ? null : LocalDate.parse(text);
    }

    static boolean bool(List<Object> row, int column) {
        Object value = column < row.size() ? row.get(column) : null;
        return value instanceof Boolean b ? b : "true".equalsIgnoreCase(text(row, column));
    }

    static Double doubleOrNull(List<Object> row, int column) {
        Object value = column < row.size() ? row.get(column) : null;
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        String text = text(row, column);
        return text.isEmpty() ? null : Double.valueOf(text);
    }

    static Integer integerOrNull(List<Object> row, int column) {
        Double value = doubleOrNull(row, column);
        return value == null ? null : (int) Math.round(value);
    }
}
