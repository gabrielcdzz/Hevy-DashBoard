package com.gabriel.hevydashboard.service;

/**
 * Ajuste manual: não contar as séries de um tipo para um músculo.
 * Exemplo: {@code new IgnoreRule("forearms", CountType.INDIRECT)}, porque o Hevy marca antebraço
 * como secundário em quase toda puxada e rosca e isso inflava o número.
 */
public record IgnoreRule(String muscle, CountType type) {
}
