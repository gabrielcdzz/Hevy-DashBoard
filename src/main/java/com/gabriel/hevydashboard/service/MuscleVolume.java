package com.gabriel.hevydashboard.service;

/** Séries de um músculo: diretas (músculo primário) e indiretas (músculo secundário). */
public record MuscleVolume(int direct, int indirect) {

    public static final MuscleVolume ZERO = new MuscleVolume(0, 0);

    public MuscleVolume plus(MuscleVolume other) {
        return new MuscleVolume(direct + other.direct, indirect + other.indirect);
    }
}
