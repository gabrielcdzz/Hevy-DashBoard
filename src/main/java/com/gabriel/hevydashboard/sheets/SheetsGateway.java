package com.gabriel.hevydashboard.sheets;

import java.util.Collection;
import java.util.List;

/**
 * O que o programa precisa da planilha. Existe como interface para o restante do código
 * (e os testes) não depender da rede: {@link SheetsClient} fala com o Google de verdade
 * e os testes usam uma versão em memória.
 */
public interface SheetsGateway {

    /** Como o Google Sheets interpreta o que é escrito. */
    enum InputMode {
        /** Grava o texto exatamente como veio. Usado para dados de controle. */
        RAW,
        /** Interpreta como se fosse digitado: "2026-10-24" vira data e "12,5" vira número. */
        TYPED
    }

    /** Cria as abas que ainda não existem. As que já existem não são tocadas. */
    void ensureSheets(Collection<String> titles);

    /** Todas as linhas da aba, como texto. Aba vazia devolve lista vazia. */
    List<List<String>> readRows(String sheet);

    /** Apaga o conteúdo da aba e grava as linhas a partir de A1. */
    void replaceRows(String sheet, List<List<Object>> rows, InputMode mode);
}
