package com.gabriel.hevydashboard.service;

import com.gabriel.hevydashboard.model.ExerciseTemplate;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SetRecordsTest {

    private static SetRecord serie(String templateId, String nome, String primario, List<String> secundarios) {
        LocalDate data = LocalDate.of(2026, 10, 20);
        return new SetRecord("w1", data, Weeks.startOf(data), "PUSH", 60, nome, templateId, primario, secundarios,
                "normal", true, 60.0, 8, null);
    }

    @Test
    void exercicioPadraoMantemONomeGravadoMasAtualizaOsMusculos() {
        // A API devolve o título em inglês; o treino guarda o nome no idioma do app. O gravado é o que vale.
        SetRecord antiga = serie("t2", "Puxada Alta (Máquina)", "lats", List.of("biceps"));
        Map<String, ExerciseTemplate> templates = Map.of("t2",
                new ExerciseTemplate("t2", "Lat Pulldown (Machine)", "weight_reps", "lats", List.of("biceps", "forearms"), false));

        SetRecord atual = SetRecords.withCurrentTemplates(List.of(antiga), templates).get(0);

        assertEquals("Puxada Alta (Máquina)", atual.exercise());
        assertEquals(List.of("biceps", "forearms"), atual.secondaryMuscles());
    }

    @Test
    void atualizaONomeEOsMusculosComOTemplateDeHoje() {
        SetRecord antiga = serie("t1", "Elevação Lateral (Halter) 1", "shoulders", List.of("traps"));
        Map<String, ExerciseTemplate> templates = Map.of("t1",
                new ExerciseTemplate("t1", "Elevação Lateral (Halter)", "weight_reps", "shoulders", List.of(), true));

        SetRecord atual = SetRecords.withCurrentTemplates(List.of(antiga), templates).get(0);

        assertEquals("Elevação Lateral (Halter)", atual.exercise());
        assertEquals(List.of(), atual.secondaryMuscles());
        // O resto da série não muda.
        assertEquals(antiga.weightKg(), atual.weightKg());
        assertEquals(antiga.date(), atual.date());
        assertEquals("t1", atual.templateId());
    }

    @Test
    void templateQueNaoExisteMaisMantemOQueEstavaGravado() {
        SetRecord antiga = serie("apagado", "Exercício antigo", "chest", List.of("triceps"));

        SetRecord atual = SetRecords.withCurrentTemplates(List.of(antiga), Map.of()).get(0);

        assertEquals(antiga, atual);
    }
}
