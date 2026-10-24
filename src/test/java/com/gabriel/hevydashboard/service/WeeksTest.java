package com.gabriel.hevydashboard.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WeeksTest {

    // Semana de 19/10/2026 (segunda) a 25/10/2026 (domingo). Brasília = UTC-3.
    private static final LocalDate SEMANA_19_OUT = LocalDate.of(2026, 10, 19);
    private static final LocalDate SEMANA_26_OUT = LocalDate.of(2026, 10, 26);

    @Test
    void treinoNoDomingoANoiteFicaNaSemanaQueTermina() {
        // Domingo 25/10 às 22h em Brasília = segunda 26/10 à 01h em UTC.
        OffsetDateTime treino = OffsetDateTime.parse("2026-10-26T01:00:00Z");

        assertEquals(SEMANA_19_OUT, Weeks.startOf(treino, Weeks.SAO_PAULO));
    }

    @Test
    void viradaDaSemanaAconteceAMeiaNoiteDeBrasilia() {
        OffsetDateTime domingo2359 = OffsetDateTime.parse("2026-10-26T02:59:59Z");
        OffsetDateTime segunda0000 = OffsetDateTime.parse("2026-10-26T03:00:00Z");

        assertEquals(SEMANA_19_OUT, Weeks.startOf(domingo2359, Weeks.SAO_PAULO));
        assertEquals(SEMANA_26_OUT, Weeks.startOf(segunda0000, Weeks.SAO_PAULO));
    }

    @Test
    void segundaFeiraEOInicioDaPropriaSemana() {
        OffsetDateTime segunda = OffsetDateTime.parse("2026-10-19T10:00:00-03:00");

        assertEquals(SEMANA_19_OUT, Weeks.startOf(segunda, Weeks.SAO_PAULO));
    }
}
