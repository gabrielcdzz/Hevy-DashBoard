package com.gabriel.hevydashboard.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gabriel.hevydashboard.service.Settings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardExporterTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 24);
    private static final Instant GERADO_EM = Instant.parse("2026-10-24T12:00:00Z");

    private static JsonNode json;
    private static DashboardData dados;

    @BeforeAll
    static void geraOExemplo() throws Exception {
        dados = SampleData.dashboard(HOJE, GERADO_EM);
        json = new ObjectMapper().readTree(DashboardExporter.toJson(dados));
    }

    @Test
    void marcaOArquivoComoExemploEGuardaAsDatas() {
        assertTrue(json.get("sample").asBoolean());
        assertEquals("2026-10-24", json.get("today").asText());
        assertEquals("2026-10-24T12:00:00Z", json.get("generatedAt").asText());
    }

    @Test
    void trazTodasAsSecoesQueOSiteLe() {
        for (String secao : new String[]{"config", "muscleLabels", "consistency", "averageDurationMinutes", "weeks",
                "volume", "plannedVolume", "calendar", "exercises", "prEvents", "workouts", "phases"}) {
            assertTrue(json.has(secao), "falta a seção " + secao);
        }
    }

    @Test
    void configuracaoLevaFaixasENiveisComTetoOmitidoQuandoNaoHaLimite() {
        JsonNode config = json.get("config");

        assertEquals(5, config.get("workoutsPerWeek").asInt());
        assertEquals(1, config.get("targets").get("chest").get("min").asInt());
        assertEquals(9, config.get("targets").get("chest").get("max").asInt());
        JsonNode alto = config.get("levels").get(2);
        assertEquals("alto", alto.get("name").asText());
        assertEquals(21, alto.get("min").asInt());
        assertFalse(alto.has("max"), "nível sem teto não deve ter o campo max");
    }

    @Test
    void semanasVoltamEmOrdemSemBuracosENumerosArredondados() {
        JsonNode semanas = json.get("weeks");

        assertTrue(semanas.size() >= 60);
        LocalDate anterior = null;
        for (JsonNode semana : semanas) {
            LocalDate atual = LocalDate.parse(semana.get("week").asText());
            if (anterior != null) {
                assertEquals(anterior.plusWeeks(1), atual);
            }
            anterior = atual;
            double tonelagem = semana.get("tonnageKg").asDouble();
            assertEquals(Math.round(tonelagem * 10) / 10.0, tonelagem, 1e-9);
        }
        assertTrue(semanas.get(semanas.size() - 1).get("inProgress").asBoolean());
    }

    @Test
    void volumeTemStatusEmMinusculasENivelEmPortugues() {
        assertTrue(json.get("volume").size() > 60 * 5);
        for (JsonNode linha : json.get("volume")) {
            // Com as faixas padrão, todo músculo do exemplo tem faixa-alvo.
            assertTrue(linha.get("status").asText().matches("below|within|above"), linha.toString());
            assertTrue(linha.get("level").asText().matches("nenhum|baixo|moderado|alto"), linha.toString());
        }
    }

    @Test
    void exerciciosTrazemRecordesSessoesETendenciaEMarcamOsDasRotinas() {
        JsonNode supino = null;
        for (JsonNode exercicio : json.get("exercises")) {
            if (exercicio.get("name").asText().equals("Supino Reto (Barra)")) {
                supino = exercicio;
            }
        }
        assertNotNull(supino);
        assertTrue(supino.get("inRoutine").asBoolean());
        assertTrue(supino.get("progress").asText().matches("progressing|stagnant|inactive|no_baseline"));
        assertTrue(supino.get("sessions").size() > 20);
        assertTrue(supino.get("maxWeightKg").get("value").asDouble() >= 60.0);
        assertNotNull(supino.get("maxWeightKg").get("date"));
    }

    @Test
    void volumePlanejadoDasRotinasVemPorGrupoMuscular() {
        JsonNode planejado = json.get("plannedVolume");

        assertTrue(planejado.has("chest"));
        assertTrue(planejado.get("chest").get("direct").asInt() > 0);
        // Tríceps recebe indireta dos empurrões; costas só aparece como grupo (lats e upper_back juntos).
        assertTrue(planejado.get("triceps").get("indirect").asInt() > 0);
        assertTrue(planejado.has("back"));
        assertFalse(planejado.has("lats"));
    }

    @Test
    void fasesVoltamOrdenadasEAUltimaEstaEmAndamento() {
        JsonNode fases = json.get("phases");

        assertEquals("bulking", fases.get(0).get("name").asText());
        assertEquals("cutting", fases.get(1).get("name").asText());
        assertFalse(fases.get(1).has("end"));
    }

    @Test
    void rotulosDosMusculosEstaoEmPortugues() {
        assertEquals("Peito", json.get("muscleLabels").get("chest").asText());
        assertEquals("Costas", json.get("muscleLabels").get("back").asText());
    }

    @Test
    void mesmaDataGeraExatamenteOMesmoArquivo() {
        String primeiro = DashboardExporter.toJson(SampleData.dashboard(HOJE, GERADO_EM));
        String segundo = DashboardExporter.toJson(SampleData.dashboard(HOJE, GERADO_EM));

        assertEquals(primeiro, segundo);
    }

    @Test
    void osDefaultsDeSettingsSaoOsMesmosQueUmaPlanilhaNovaRecebe() {
        // A planilha nova é preenchida a partir de Settings.defaults(); o exemplo usa os mesmos valores.
        assertEquals(Settings.defaults().workoutsPerWeek(), dados.config().workoutsPerWeek());
        assertEquals(Settings.defaults().targets().size(), dados.config().targets().size());
    }
}
