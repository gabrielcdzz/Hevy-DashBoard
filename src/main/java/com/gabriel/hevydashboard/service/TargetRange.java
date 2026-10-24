package com.gabriel.hevydashboard.service;

/**
 * Uma faixa de séries, com os dois limites incluídos. {@code max = NO_LIMIT} significa "sem teto"
 * (por exemplo, "21 ou mais").
 */
public record TargetRange(int min, int max) {

    public static final int NO_LIMIT = Integer.MAX_VALUE;

    /** Onde um número de séries fica em relação à faixa. */
    public enum Status {
        BELOW,
        WITHIN,
        ABOVE
    }

    public boolean contains(int sets) {
        return sets >= min && sets <= max;
    }

    public Status statusOf(int sets) {
        if (sets < min) {
            return Status.BELOW;
        }
        return sets > max ? Status.ABOVE : Status.WITHIN;
    }
}
