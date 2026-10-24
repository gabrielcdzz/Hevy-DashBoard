package com.gabriel.hevydashboard.sheets;

/** Erro ao falar com o Google Sheets. {@code status} é o código HTTP, ou -1 se não houve resposta. */
public class SheetsException extends RuntimeException {

    private final int status;

    public SheetsException(String message, int status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
