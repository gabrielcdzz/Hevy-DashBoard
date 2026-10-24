package com.gabriel.hevydashboard.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Configuração única do Jackson para ler o JSON do Hevy.
 * O Hevy usa snake_case (weight_kg); nas classes usamos camelCase (weightKg).
 * Campos que o Hevy adicionar no futuro são ignorados em vez de quebrar o programa.
 */
public final class HevyJson {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private HevyJson() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }
}
