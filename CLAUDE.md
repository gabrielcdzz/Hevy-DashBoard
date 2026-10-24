# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Visão geral

Aplicação Java 21 (Maven, sem framework) que lê os treinos do Hevy, calcula as séries diretas e indiretas por grupo muscular na semana, grava tudo no Google Sheets e alimenta um dashboard no Looker Studio. Vai rodar agendada no GitHub Actions. O projeto também é portfólio do usuário, então testes, README (em português) e organização do código fazem parte do escopo.

Documentação do projeto:

- `docs/contexto.md`: decisões tomadas, regras de cálculo, **valores esperados para testes**, estrutura da planilha, lógica de sincronização e próximas etapas. Leia antes de começar uma etapa nova.
- `docs/plano.md`: especificação original (abas e métricas do dashboard). Em caso de conflito, vale o `contexto.md`.

Hoje o `Main` só faz um teste de conexão. O cálculo de volume (`service`) está pronto; a próxima etapa é configurar o Google Cloud e escrever o pacote `sheets`.

## Como trabalhar com o usuário

- O Claude escreve o código e o usuário revisa, executa e commita pelo **GitHub Desktop**, para o histórico ficar no nome dele. **Não fazer commit nem push** sem pedido explícito.
- Explicar a lógica de cada parte de forma objetiva, passo a passo, com números e exemplos. O usuário precisa saber defender o projeto numa entrevista.
- Trabalhar em etapas pequenas, uma camada por vez, cada uma terminando com `mvn test` passando.
- O ambiente é Windows/PowerShell. Comandos entregues ao usuário devem funcionar no PowerShell.

## Comandos

Não há Maven Wrapper, então use o `mvn` instalado.

```bash
mvn test                                               # todos os testes
mvn test -Dtest=HevyClientTest                         # uma classe
mvn test -Dtest=HevyJsonTest#leEventoDeTreinoApagado   # um método
mvn package                                            # gera target/hevy-dashboard-0.1.0-SNAPSHOT.jar (fat jar via shade)
java -jar target/hevy-dashboard-0.1.0-SNAPSHOT.jar     # precisa de HEVY_API_KEY no ambiente
```

## Convenções de código

- Nomes de classes, métodos e variáveis em inglês. Comentários, Javadoc, mensagens de erro e nomes de testes em português (ex.: `leEventoDeTreinoApagado`).
- Modelos são `record` imutáveis. Os construtores compactos trocam listas nulas por `List.of()` e fazem cópia com `List.copyOf`.
- Poucas dependências: HTTP com `java.net.http.HttpClient`, sem biblioteca extra.
- Testes sem rede: JSON de exemplo em text blocks ou em `src/test/resources/`, ou servidor local com `com.sun.net.httpserver.HttpServer`.

## Arquitetura

Pacotes: `client` (API do Hevy), `model`, `service` (cálculo de volume, **sem I/O**, para ser testável isoladamente) e `sheets` (Google Sheets, via conta de serviço). Hoje só `client`, `model` e `service` existem.

- `service/VolumeCalculator`: recebe templates e `VolumeRules` (músculos com indireta e agrupamento) no construtor. `volumeOf(exercícios)` devolve `Map<músculo, MuscleVolume>`, e `weeklyVolume(treinos, zona)` agrupa por segunda-feira (`Weeks.startOf`). Template desconhecido lança `IllegalArgumentException` em vez de contar errado.
- `src/test/resources/fixtures/`: as 5 rotinas reais e os 22 templates delas, salvos da API em 24/10/2026. São a base do teste que confere os números de `docs/contexto.md`. Se as rotinas mudarem no Hevy, a fixture e os números esperados mudam juntos.

- `client/HevyClient`: cliente HTTP somente leitura para `https://api.hevyapp.com/v1`.
  - A chave vai no header `api-key` e nunca deve aparecer em log nem em mensagem de exceção.
  - Toda listagem passa por `fetchAll`, que percorre as páginas até `page_count`. Os tamanhos de página (`*_PAGE_SIZE`) seguem os limites da API: 10 na maioria, 100 em templates.
  - `get` tenta até 3 vezes, com espera crescente, em falha de rede, 429 ou 5xx. Os demais erros viram `HevyApiException` com o status HTTP (`-1` para falha de rede).
  - O construtor de 3 argumentos (`apiKey`, `baseUrl`, `HttpClient`) existe para os testes apontarem para um servidor local.
- `client/HevyJson`: o único `ObjectMapper`. Converte o snake_case do Hevy para os nomes camelCase dos records, suporta `java.time` e ignora campos desconhecidos. Reutilize esse mapper.
- Sincronização incremental: `/workouts/events?since=` devolve eventos `updated` (com o `Workout` completo) e `deleted` (só `id` e `deleted_at`). Use `Instant.EPOCH` para importar o histórico inteiro. O endpoint não cobre rotinas, então as rotinas são relidas inteiras a cada execução.

## Regras de cálculo (decididas, não mudar sem o usuário pedir)

- Série válida é toda série que não seja `warmup`. Séries `failure` e `dropset` contam.
- Série direta conta para o `primaryMuscleGroup` do template. Série indireta conta, em campo separado, **só para bíceps e tríceps**. Os outros secundários do Hevy são ignorados.
- `lats` e `upper_back` são contados juntos como `back`. Essas regras ficam em `VolumeRules.DEFAULT` e depois vão ser lidas da aba `ajustes_manuais`.
- A semana vai de segunda a domingo, no fuso **America/Sao_Paulo**. O Hevy grava em UTC, e um treino às 22h de domingo não pode cair na semana seguinte.
- Séries com carga 0 ou sem repetições contam no volume, mas são ignoradas na tonelagem, no 1RM e nos PRs.
- 1RM estimado pela fórmula de Epley: `carga × (1 + reps / 30)`.
- A soma das diretas é sempre igual ao total de séries válidas. Os valores esperados das 5 rotinas estão em `docs/contexto.md` e devem virar teste.
- O volume baixo de peito é proposital. Não tratar como alerta nem sugerir mudança.

## Segredos

- `HEVY_API_KEY`, a credencial da conta de serviço do Google e o ID da planilha vêm de variáveis de ambiente (secrets no GitHub Actions). Nunca vão para código, log, README ou commit.
- O `.gitignore` já exclui `.env`, `secrets/`, `service-account*.json` e `credentials*.json`. Não ignorar `*.json` inteiro, porque as fixtures de teste são `.json`.
