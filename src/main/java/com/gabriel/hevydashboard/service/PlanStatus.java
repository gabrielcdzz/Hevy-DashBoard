package com.gabriel.hevydashboard.service;

/** Como as séries diretas de um músculo se comparam com o que as rotinas planejam para ele na semana. */
public enum PlanStatus {
    /** Menos que o planejado, com a semana já encerrada. */
    BELOW,
    /** Exatamente o planejado. */
    WITHIN,
    /** Mais que o planejado. */
    ABOVE,
    /** Menos que o planejado, mas a semana ainda está em andamento: ainda dá tempo. */
    REMAINING;

    /**
     * Exemplo com 5 séries planejadas de peito: 4 na semana fechada é abaixo; 5 é dentro; 6 é acima.
     * Na semana em andamento, 4 de 5 ainda é "faltam 1", e não "abaixo", porque o treino não acabou.
     *
     * @param done            séries diretas feitas
     * @param planned         séries diretas planejadas nas rotinas (maior que zero)
     * @param weekInProgress  a semana de hoje, que ainda não terminou
     */
    public static PlanStatus of(int done, int planned, boolean weekInProgress) {
        if (done > planned) {
            return ABOVE;
        }
        if (done == planned) {
            return WITHIN;
        }
        return weekInProgress ? REMAINING : BELOW;
    }
}
