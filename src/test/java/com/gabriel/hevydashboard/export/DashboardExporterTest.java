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

    private static String texto;
    private static JsonNode json;
    private static DashboardData dados;

    @BeforeAll
    static void geraOExemplo() throws Exception {
        dados = SampleData.dashboard(HOJE, GERADO_EM);
        texto = DashboardExporter.toJson(dados);
        json = new ObjectMapper().readTree(texto);
    }

    @Test
    void marcaOArquivoComoExemploEGuardaAsDatas() {
        assertTrue(json.get("sample").asBoolean());
        assertEquals("2026-10-24", json.get("today").asText());
        assertEquals("2026-10-24T12:00:00Z", json.get("generatedAt").asText());
    }

    @Test
    void trazAsSecoesQueOSiteLeESoElas() {
        for (String secao : new String[]{"config", "muscleLabels", "consistency", "averageDurationMinutes", "weeks",
                "volume", "plannedVolume", "exercises", "prEvents"}) {
            assertTrue(json.has(secao), "falta a seção " + secao);
        }
        // O que saiu do programa não pode voltar para o arquivo.
        for (String removida : new String[]{"phases", "calendar", "workouts"}) {
            assertFalse(json.has(removida), "a seção " + removida + " não existe mais");
        }
    }

    @Test
    void naoTemMaisOneRmNosExerciciosNemFasesNemFaixasFixas() {
        // O 1RM existe só como tipo de recorde batido (prEvents), para a contagem do mês.
        for (String palavra : new String[]{"oneRepMax", "bestOneRepMax", "cutting", "bulking", "targets", "levels", "tonnage"}) {
            assertFalse(texto.contains(palavra), "o arquivo não deveria mais conter \"" + palavra + "\"");
        }
        for (JsonNode exercicio : json.get("exercises")) {
            assertFalse(exercicio.toString().contains("one_rep_max"), "o 1RM não aparece nos exercícios");
        }
    }

    @Test
    void configuracaoTemSoAsMetasDeTreinoEDeEstagnacao() {
        JsonNode config = json.get("config");

        assertEquals(5, config.get("workoutsPerWeek").asInt());
        assertEquals(3, config.get("stagnationWeeks").asInt());
        assertEquals(2, config.size());
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
            if (semana.has("averageDurationMinutes")) {
                double minutos = semana.get("averageDurationMinutes").asDouble();
                assertEquals(Math.round(minutos * 10) / 10.0, minutos, 1e-9);
            }
        }
        assertTrue(semanas.get(semanas.size() - 1).get("inProgress").asBoolean());
    }

    @Test
    void volumeTrazOPlanejadoEASituacaoDeCadaMusculo() {
        int comPlanejado = 0;
        for (JsonNode linha : json.get("volume")) {
            if (linha.has("planned")) {
                comPlanejado++;
                assertTrue(linha.get("status").asText().matches("below|within|above|remaining"), linha.toString());
                assertTrue(linha.get("planned").asInt() > 0);
            } else {
                assertFalse(linha.has("status"), "sem planejado não há situação: " + linha);
            }
        }
        assertTrue(comPlanejado > 60 * 5, "os músculos das rotinas têm planejado em todas as semanas");
    }

    @Test
    void faltamSoExisteNaSemanaEmAndamentoEAbaixoSoNasEncerradas() {
        String semanaDeHoje = json.get("weeks").get(json.get("weeks").size() - 1).get("week").asText();

        for (JsonNode linha : json.get("volume")) {
            if (!linha.has("status")) {
                continue;
            }
            String situacao = linha.get("status").asText();
            if (linha.get("week").asText().equals(semanaDeHoje)) {
                assertFalse(situacao.equals("below"), "na semana em andamento falta, não é abaixo: " + linha);
            } else {
                assertFalse(situacao.equals("remaining"), "semana encerrada não tem 'remaining': " + linha);
            }
        }
    }

    @Test
    void volumePlanejadoDasRotinasBateComAContaFeitaAMao() {
        JsonNode planejado = json.get("plannedVolume");

        // Mesmos números da conta feita à mão em VolumeCalculatorTest.
        assertEquals(9, planejado.get("chest").get("direct").asInt());
        assertEquals(20, planejado.get("shoulders").get("direct").asInt());
        assertEquals(6, planejado.get("triceps").get("direct").asInt());
        assertEquals(15, planejado.get("triceps").get("indirect").asInt());
        assertEquals(18, planejado.get("back").get("direct").asInt());
        assertEquals(6, planejado.get("biceps").get("direct").asInt());
        assertEquals(18, planejado.get("biceps").get("indirect").asInt());
        assertFalse(planejado.has("lats"), "dorsal e costas superiores contam juntos como back");
    }

    @Test
    void exerciciosTrazemRecordesTreinosETendenciaSemOneRm() {
        JsonNode supino = null;
        for (JsonNode exercicio : json.get("exercises")) {
            if (exercicio.get("name").asText().equals("Supino Reto (Barra)")) {
                supino = exercicio;
            }
        }
        assertNotNull(supino);
        assertTrue(supino.get("inRoutine").asBoolean());
        assertTrue(supino.get("trend").get("progress").asText().matches("progressing|stagnant|inactive|no_baseline"));
        assertTrue(supino.get("sessions").size() > 20);
        JsonNode ultimoTreino = supino.get("sessions").get(supino.get("sessions").size() - 1);
        assertTrue(ultimoTreino.get("weightKg").asDouble() >= 60.0);
        assertTrue(ultimoTreino.get("reps").asInt() > 0);
        assertTrue(supino.get("maxWeightKg").get("value").asDouble() >= 60.0);
        assertNotNull(supino.get("maxReps").get("date"));
        assertTrue(supino.get("lastChange").get("type").asText().matches("load_up|load_down|reps_up|reps_down|same"));
        assertNotNull(supino.get("lastChange").get("previous").get("weightKg"));
        assertNotNull(supino.get("lastChange").get("current").get("reps"));
    }

    @Test
    void recordesSaoDeCargaDeRepeticoesEDeOneRmParaContarOMes() {
        assertTrue(json.get("prEvents").size() > 20);
        boolean achouOneRm = false;
        for (JsonNode evento : json.get("prEvents")) {
            assertTrue(evento.get("type").asText().matches("weight|reps|one_rep_max"), evento.toString());
            achouOneRm |= evento.get("type").asText().equals("one_rep_max");
        }
        assertTrue(achouOneRm, "os recordes de 1RM contam nos recordes do mês");
    }

    @Test
    void rotulosDosMusculosEstaoEmPortugues() {
        assertEquals("Peito", json.get("muscleLabels").get("chest").asText());
        assertEquals("Costas", json.get("muscleLabels").get("back").asText());
    }

    @Test
    void mesmaDataGeraExatamenteOMesmoArquivo() {
        assertEquals(texto, DashboardExporter.toJson(SampleData.dashboard(HOJE, GERADO_EM)));
    }

    @Test
    void osPadroesDeSettingsSaoOsUsadosNoExemplo() {
        assertEquals(Settings.defaults().workoutsPerWeek(), dados.config().workoutsPerWeek());
        assertEquals(Settings.defaults().stagnationWeeks(), dados.config().stagnationWeeks());
    }
}
