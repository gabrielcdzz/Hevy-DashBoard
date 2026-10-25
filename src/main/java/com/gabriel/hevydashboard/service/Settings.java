package com.gabriel.hevydashboard.service;

/**
 * Tudo que o usuário ajusta na planilha e o cálculo precisa conhecer.
 * <p>
 * A meta de séries de cada músculo não está aqui: ela vem das rotinas do Hevy (o volume planejado),
 * então muda sozinha quando o usuário muda a rotina.
 *
 * @param rules            quais músculos recebem indireta e como os músculos são agrupados
 * @param stagnationWeeks  semanas sem progressão para o alerta de estagnação
 * @param workoutsPerWeek  meta de treinos por semana; uma semana com pelo menos isso é "completa"
 */
public record Settings(VolumeRules rules, int stagnationWeeks, int workoutsPerWeek) {

    /** Os valores iniciais: os que uma planilha nova recebe e os que os dados de exemplo usam. */
    public static Settings defaults() {
        return new Settings(VolumeRules.DEFAULT, 3, 5);
    }
}
