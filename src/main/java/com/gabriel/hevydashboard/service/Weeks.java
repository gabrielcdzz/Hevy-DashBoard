package com.gabriel.hevydashboard.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

/** Semanas de segunda a domingo, contadas no fuso do usuário. */
public final class Weeks {

    public static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    private Weeks() {
    }

    /**
     * A segunda-feira da semana em que {@code time} cai, no fuso {@code zone}.
     * O Hevy grava em UTC: domingo 22h em Brasília é segunda 01h em UTC, e por isso
     * o horário é convertido para o fuso local antes de olhar o dia da semana.
     */
    public static LocalDate startOf(OffsetDateTime time, ZoneId zone) {
        LocalDate localDate = time.atZoneSameInstant(zone).toLocalDate();
        return localDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
