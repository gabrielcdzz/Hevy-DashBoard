package com.gabriel.hevydashboard.sheets;

import com.gabriel.hevydashboard.service.Settings;
import com.gabriel.hevydashboard.service.TargetRange;
import com.gabriel.hevydashboard.service.VolumeRules;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsSheetsTest {

    private final InMemorySheets planilha = new InMemorySheets();
    private final SettingsSheets configuracao = new SettingsSheets(planilha);

    @Test
    void primeiraLeituraCriaAsAbasComOsValoresIniciais() {
        Settings settings = configuracao.load();

        assertTrue(planilha.sheets.keySet().containsAll(List.of(
                "ajustes_manuais", "faixa_alvo", "niveis_volume", "fases", "config")));
        assertEquals(VolumeRules.DEFAULT, settings.rules());
        assertEquals(3, settings.stagnationWeeks());
        assertEquals(new TargetRange(1, 9), settings.targets().get("chest"));
        assertEquals(new TargetRange(10, 20), settings.targets().get("biceps"));
    }

    @Test
    void acrescentaParametroNovoNaConfigQueOUsuarioJaTinhaSemSobrescrever() {
        planilha.ensureSheets(List.of("config"));
        planilha.replaceRows("config", List.of(
                List.of("chave", "valor"),
                List.of("semanas_estagnacao", 4)), SheetsGateway.InputMode.TYPED);

        Settings settings = configuracao.load();

        assertEquals(4, settings.stagnationWeeks());     // o valor do usuário foi mantido
        assertEquals(5, settings.workoutsPerWeek());     // o parâmetro novo entrou com o padrão
        assertEquals(List.of("treinos_por_semana", "5"), planilha.sheets.get("config").get(2));
    }

    @Test
    void metaDeTreinosPorSemanaEditadaPeloUsuarioEMantida() {
        planilha.ensureSheets(List.of("config"));
        planilha.replaceRows("config", List.of(
                List.of("chave", "valor"),
                List.of("semanas_estagnacao", 3),
                List.of("treinos_por_semana", 4)), SheetsGateway.InputMode.TYPED);

        assertEquals(4, configuracao.load().workoutsPerWeek());
        assertEquals(3, planilha.sheets.get("config").size()); // nada foi acrescentado
    }

    @Test
    void niveisIniciaisSaoBaixoModeradoEAlto() {
        Settings settings = configuracao.load();

        assertEquals("baixo", settings.levels().classify(9));
        assertEquals("moderado", settings.levels().classify(10));
        assertEquals("moderado", settings.levels().classify(20));
        assertEquals("alto", settings.levels().classify(21));
    }

    @Test
    void faseAtualVemPreenchida() {
        configuracao.load();

        assertEquals(List.of("fase", "inicio", "fim"), planilha.sheets.get("fases").get(0));
        assertEquals(List.of("cutting", "2026-09-14", ""), planilha.sheets.get("fases").get(1));
    }

    @Test
    void naoSobrescreveAbaQueOUsuarioJaEditou() {
        planilha.ensureSheets(List.of("faixa_alvo"));
        planilha.replaceRows("faixa_alvo", List.of(
                List.of("musculo", "min", "max"),
                List.of("chest", 12, 16)), SheetsGateway.InputMode.TYPED);

        Settings settings = configuracao.load();

        assertEquals(new TargetRange(12, 16), settings.targets().get("chest"));
        assertEquals(1, settings.targets().size());
    }

    @Test
    void abaComSoOCabecalhoEUmaEscolhaDoUsuarioEERespeitada() {
        planilha.ensureSheets(List.of("ajustes_manuais"));
        planilha.replaceRows("ajustes_manuais", List.of(List.of("tipo", "musculo", "grupo")),
                SheetsGateway.InputMode.TYPED);

        Settings settings = configuracao.load();

        assertEquals(Set.of(), settings.rules().indirectMuscles());
    }

    @Test
    void leOsAjustesIgnorandoMaiusculasEEspacos() {
        planilha.ensureSheets(List.of("ajustes_manuais"));
        planilha.replaceRows("ajustes_manuais", List.of(
                List.of("tipo", "musculo", "grupo"),
                List.of("Indireta", " Biceps ", ""),
                List.of("agrupamento", "Lats", "Back")), SheetsGateway.InputMode.TYPED);

        Settings settings = configuracao.load();

        assertEquals(Set.of("biceps"), settings.rules().indirectMuscles());
        assertEquals("back", settings.rules().groupOf("lats"));
    }

    @Test
    void nivelAltoSemTetoLeComoSemLimite() {
        Settings settings = configuracao.load();

        assertEquals("alto", settings.levels().classify(500));
    }

    @Test
    void numeroInvalidoDaErroComAbaELinha() {
        planilha.ensureSheets(List.of("faixa_alvo"));
        planilha.replaceRows("faixa_alvo", List.of(
                List.of("musculo", "min", "max"),
                List.of("chest", 1, 9),
                List.of("biceps", "dez", 20)), SheetsGateway.InputMode.TYPED);

        IllegalStateException erro = assertThrows(IllegalStateException.class, configuracao::load);

        assertTrue(erro.getMessage().contains("faixa_alvo"));
        assertTrue(erro.getMessage().contains("linha 3"));
        assertTrue(erro.getMessage().contains("dez"));
    }

    @Test
    void maxMenorQueMinDaErro() {
        planilha.ensureSheets(List.of("niveis_volume"));
        planilha.replaceRows("niveis_volume", List.of(
                List.of("nivel", "min", "max"),
                List.of("baixo", 9, 1)), SheetsGateway.InputMode.TYPED);

        IllegalStateException erro = assertThrows(IllegalStateException.class, configuracao::load);

        assertTrue(erro.getMessage().contains("niveis_volume"));
    }

    @Test
    void tipoDeAjusteDesconhecidoDaErro() {
        planilha.ensureSheets(List.of("ajustes_manuais"));
        planilha.replaceRows("ajustes_manuais", List.of(
                List.of("tipo", "musculo", "grupo"),
                List.of("ignorar", "forearms", "")), SheetsGateway.InputMode.TYPED);

        IllegalStateException erro = assertThrows(IllegalStateException.class, configuracao::load);

        assertTrue(erro.getMessage().contains("ignorar"));
    }
}
