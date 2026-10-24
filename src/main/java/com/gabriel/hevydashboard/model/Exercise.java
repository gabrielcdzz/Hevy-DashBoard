package com.gabriel.hevydashboard.model;

import java.util.List;

/** Um exercício dentro de um treino ou de uma rotina, com suas séries. */
public record Exercise(
        Integer index,
        String title,
        String notes,
        String exerciseTemplateId,
        List<SetEntry> sets) {

    public Exercise {
        sets = sets == null ? List.of() : List.copyOf(sets);
    }
}
