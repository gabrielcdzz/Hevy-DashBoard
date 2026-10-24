package com.gabriel.hevydashboard.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.OffsetDateTime;

/**
 * Evento de treino devolvido por /workouts/events.
 * <ul>
 *   <li>"updated": traz o treino completo em {@code workout}.</li>
 *   <li>"deleted": traz só o {@code id} do treino e a data da exclusão.</li>
 * </ul>
 */
public record WorkoutEvent(
        String type,
        Workout workout,
        String id,
        OffsetDateTime deletedAt) {

    @JsonIgnore
    public boolean isUpdated() {
        return "updated".equals(type);
    }

    @JsonIgnore
    public boolean isDeleted() {
        return "deleted".equals(type);
    }
}
