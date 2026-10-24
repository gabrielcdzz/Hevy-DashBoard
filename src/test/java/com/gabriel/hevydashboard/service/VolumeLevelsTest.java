package com.gabriel.hevydashboard.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VolumeLevelsTest {

    private static final VolumeLevels NIVEIS = new VolumeLevels(List.of(
            new VolumeLevel("baixo", new TargetRange(1, 9)),
            new VolumeLevel("moderado", new TargetRange(10, 20)),
            new VolumeLevel("alto", new TargetRange(21, TargetRange.NO_LIMIT))));

    @Test
    void classificaOsLimitesDeCadaNivel() {
        assertEquals("baixo", NIVEIS.classify(1));
        assertEquals("baixo", NIVEIS.classify(9));
        assertEquals("moderado", NIVEIS.classify(10));
        assertEquals("moderado", NIVEIS.classify(20));
        assertEquals("alto", NIVEIS.classify(21));
        assertEquals("alto", NIVEIS.classify(80));
    }

    @Test
    void semSeriesNaoTemNivel() {
        assertEquals(VolumeLevels.NONE, NIVEIS.classify(0));
    }

    @Test
    void faixaAlvoDizSeEstaAbaixoDentroOuAcima() {
        TargetRange alvo = new TargetRange(10, 20);

        assertEquals(TargetRange.Status.BELOW, alvo.statusOf(9));
        assertEquals(TargetRange.Status.WITHIN, alvo.statusOf(10));
        assertEquals(TargetRange.Status.WITHIN, alvo.statusOf(20));
        assertEquals(TargetRange.Status.ABOVE, alvo.statusOf(21));
    }
}
