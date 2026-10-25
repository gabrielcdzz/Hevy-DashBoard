package com.gabriel.hevydashboard.service;

/**
 * Estimativa de 1RM (a maior carga para uma repetição) a partir de uma série qualquer.
 * <p>
 * O 1RM só existe aqui para contar os recordes do mês (um recorde de 1RM estimado é quando uma série
 * rende mais que qualquer anterior). Ele não é mostrado em nenhuma tela e não entra na evolução dos exercícios.
 */
public final class OneRepMax {

    private OneRepMax() {
    }

    /**
     * Fórmula de Epley: {@code carga × (1 + repetições / 30)}.
     * Exemplo: 60 kg x 8 repetições = 60 × (1 + 8/30) = 76 kg.
     */
    public static double estimate(double weightKg, int reps) {
        return weightKg * (1 + reps / 30.0);
    }
}
