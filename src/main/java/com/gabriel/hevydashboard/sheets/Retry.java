package com.gabriel.hevydashboard.sheets;

import java.io.IOException;
import java.util.function.LongConsumer;
import java.util.function.Predicate;

/**
 * Repete uma chamada que falhou por um motivo passageiro (limite de requisições por minuto, servidor
 * ocupado, rede instável), esperando mais a cada tentativa. Separada do {@link SheetsClient} para poder
 * ser testada sem o Google e sem esperar de verdade.
 */
final class Retry {

    @FunctionalInterface
    interface Call<T> {
        T run() throws IOException;
    }

    private Retry() {
    }

    /**
     * @param delaysMillis quanto esperar antes de cada nova tentativa; o tamanho dá o número de repetições
     * @param sleeper      como esperar (nos testes, só anota o tempo em vez de dormir)
     */
    static <T> T run(Call<T> call, Predicate<IOException> retryable, long[] delaysMillis, LongConsumer sleeper)
            throws IOException {
        for (int attempt = 0; ; attempt++) {
            try {
                return call.run();
            } catch (IOException e) {
                if (attempt >= delaysMillis.length || !retryable.test(e)) {
                    throw e;
                }
                sleeper.accept(delaysMillis[attempt]);
            }
        }
    }

    /** Espera de verdade; se a thread for interrompida, desiste da repetição. */
    static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Espera interrompida", e);
        }
    }
}
