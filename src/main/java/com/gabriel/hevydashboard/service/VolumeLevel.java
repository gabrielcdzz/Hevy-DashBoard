package com.gabriel.hevydashboard.service;

/** Um nível de volume semanal, como "baixo" (1 a 9 séries). */
public record VolumeLevel(String name, TargetRange range) {
}
