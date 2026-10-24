package com.gabriel.hevydashboard.service;

import java.time.LocalDate;

/**
 * Uma fase do treino (cutting, bulking...), mostrada como marcador nos gráficos de evolução.
 *
 * @param end o último dia da fase; null se ainda está em andamento
 */
public record Phase(String name, LocalDate start, LocalDate end) {
}
