package com.gabriel.hevydashboard.client;

/** Erro ao falar com a API do Hevy. {@code status} é o código HTTP, ou -1 se foi falha de rede. */
public class HevyApiException extends RuntimeException {

    private final int status;

    public HevyApiException(String message, int status) {
        super(message);
        this.status = status;
    }

    public HevyApiException(String message, int status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
