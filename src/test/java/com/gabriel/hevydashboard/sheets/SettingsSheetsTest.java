package com.gabriel.hevydashboard.sheets;

import com.gabriel.hevydashboard.service.Settings;
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
    void primeiraLeituraCriaSoAsAbasQueExistemHojeComOsValoresIniciais() {
        Settings settings = configuracao.load();

        assertEquals(Set.of("ajustes_manuais", "config"), planilha.sheets.keySet());
        assertEquals(VolumeRules.DEFAULT, settings.rules());
        assertEquals(3, settings.stagnationWeeks());
        assertEquals(5, settings.workoutsPerWeek());
    }

    @Test
    void umaPlanilhaNovaRecebeExatamenteOsValoresPadrao() {
        assertEquals(Settings.defaults(), configuracao.load());
    }

    @Test
    void abasDeVersoesAnterioresFicamIntactasESemUso() {
        // A planilha do usuário ainda tem as abas antigas, com conteúdo. O programa não as lê nem as apaga.
        planilha.ensureSheets(List.of("faixa_alvo", "niveis_volume", "fases"));
        planilha.replaceRows("faixa_alvo", List.of(List.of("musculo", "min", "max"), List.of("chest", 1, 9)),
                SheetsGateway.InputMode.TYPED);
        planilha.replaceRows("fases", List.of(List.of("fase", "inicio", "fim"), List.of("cutting", "2026-09-14", "")),
                SheetsGateway.InputMode.TYPED);
        // Mesmo com conteúdo inválido, elas não podem derrubar a leitura.
        planilha.replaceRows("niveis_volume", List.of(List.of("nivel", "min", "max"), List.of("baixo", "dez", "")),
                SheetsGateway.InputMode.TYPED);

        Settings settings = configuracao.load();

        assertEquals(Settings.defaults(), settings);
        assertEquals(2, planilha.sheets.get("faixa_alvo").size());
        assertEquals(2, planilha.sheets.get("fases").size());
        assertEquals("dez", planilha.sheets.get("niveis_volume").get(1).get(1));
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
    void naoSobrescreveAbaQueOUsuarioJaEditou() {
        planilha.ensureSheets(List.of("ajustes_manuais"));
        planilha.replaceRows("ajustes_manuais", List.of(
                List.of("tipo", "musculo", "grupo"),
                List.of("indireta", "biceps", "")), SheetsGateway.InputMode.TYPED);

        Settings settings = configuracao.load();

        assertEquals(Set.of("biceps"), settings.rules().indirectMuscles()); // sem o tríceps que é do padrão
        assertEquals(2, planilha.sheets.get("ajustes_manuais").size());
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
    void numeroInvalidoDaErroComAbaELinha() {
        planilha.ensureSheets(List.of("config"));
        planilha.replaceRows("config", List.of(
                List.of("chave", "valor"),
                List.of("semanas_estagnacao", 3),
                List.of("treinos_por_semana", "cinco")), SheetsGateway.InputMode.TYPED);

        IllegalStateException erro = assertThrows(IllegalStateException.class, configuracao::load);

        assertTrue(erro.getMessage().contains("config"));
        assertTrue(erro.getMessage().contains("linha 3"));
        assertTrue(erro.getMessage().contains("cinco"));
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
