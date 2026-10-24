# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Visão geral

Aplicação Java 21 (Maven, sem framework) que lê os treinos do Hevy, calcula as séries diretas e indiretas por grupo muscular na semana, grava tudo no Google Sheets e alimenta um dashboard web próprio (decisão de 24/10/2026; o plano original usava Looker Studio). Vai rodar agendada no GitHub Actions. O projeto também é portfólio do usuário, então testes, README (em português) e organização do código fazem parte do escopo.

Documentação do projeto:

- `docs/contexto.md`: decisões tomadas, regras de cálculo, **valores esperados para testes**, estrutura da planilha, lógica de sincronização e próximas etapas. Leia antes de começar uma etapa nova.
- `docs/plano.md`: especificação original (abas e métricas do dashboard). Em caso de conflito, vale o `contexto.md`.

Hoje o `Main` só faz um teste de conexão. O cálculo de volume (`service`) está pronto; a próxima etapa é configurar o Google Cloud e escrever o pacote `sheets`.

## Como trabalhar com o usuário

- Desde 24/10/2026 o usuário liberou o projeto inteiro, inclusive o front end e os commits. Commitar em etapas pequenas, com mensagem em português. **Perguntar antes de** `git push`, de apagar dados na planilha, de criar serviços externos e de publicar dados reais de treino (o repositório é público).
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
java -jar target/hevy-dashboard-0.1.0-SNAPSHOT.jar     # precisa de HEVY_API_KEY, GOOGLE_APPLICATION_CREDENTIALS e SPREADSHEET_ID
```

## Convenções de código

- Nomes de classes, métodos e variáveis em inglês. Comentários, Javadoc, mensagens de erro e nomes de testes em português (ex.: `leEventoDeTreinoApagado`).
- Modelos são `record` imutáveis. Os construtores compactos trocam listas nulas por `List.of()` e fazem cópia com `List.copyOf`.
- Poucas dependências: HTTP com `java.net.http.HttpClient`, sem biblioteca extra.
- Testes sem rede: JSON de exemplo em text blocks ou em `src/test/resources/`, ou servidor local com `com.sun.net.httpserver.HttpServer`.

## Arquitetura

Pacotes: `client` (API do Hevy), `model`, `service` (cálculo de volume, **sem I/O**, para ser testável isoladamente) e `sheets` (Google Sheets, via conta de serviço). Em `sheets`, já existem a conexão, a leitura/escrita de abas, o `SyncState` e a aba `series_treinos` (`WorkoutSetsTable`). Faltam `series_rotinas` e as abas de volume.

- `sheets/SettingsSheets.load()`: cria e lê as abas que o usuário edita (`ajustes_manuais`, `faixa_alvo`, `niveis_volume`, `fases`, `config`) e devolve um `service.Settings`. **Só preenche uma aba com os valores iniciais se ela estiver totalmente vazia**; depois o conteúdo é do usuário e nunca é sobrescrito (cabeçalho sozinho conta como escolha do usuário). Erros de preenchimento dizem a aba e a linha. A aba `fases` só é lida pelo dashboard.
- Níveis de volume semanal (séries diretas): baixo 1–9, moderado 10–20, alto 21+, e "nenhum" para 0 (`VolumeLevels.classify`). `TargetRange.statusOf` diz se um valor está abaixo, dentro ou acima da faixa-alvo.

- `sheets/SheetsGateway`: a interface do que o código precisa da planilha (`ensureSheets`, `readRows`, `readColumn`, `replaceRows`, `appendRows`, `deleteRows`). O resto do código depende dela, nunca do `SheetsClient`, para os testes usarem `InMemorySheets` (em `src/test`) e não chamarem o Google. A escrita tem dois modos: `RAW` (texto exato, para dados de controle) e `TYPED` (o Sheets interpreta datas e números, para as abas que o Looker Studio vai ler).
- **Nunca ler de volta e regravar uma aba `TYPED`.** O Sheets devolve datas formatadas conforme o idioma da planilha ("24/10/2026"), e regravar isso pode trocar dia e mês. Por isso as atualizações são incrementais: `readColumn` só do `workout_id`, `deleteRows` nas linhas afetadas e `appendRows` com as novas. As datas ficam como datas de verdade (confirmado: o Sheets guarda o número de série).
- `sheets/WorkoutSetsTable`: aba `series_treinos`, uma linha por série, com os dados brutos (todos os secundários do Hevy, aquecimento marcado em `valida`). `applyEvents` recebe os eventos de `/workouts/events` e, para cada treino, vale o evento mais recente (`updatedAt` ou `deletedAt`), seja qual for a ordem em que a API os devolve. É idempotente: aplicar os mesmos eventos duas vezes não duplica linhas. Template desconhecido lança erro, em vez de gravar a linha sem músculo. A aba tem 14 colunas (a última é `duracao_min`, a duração do treino repetida em cada série); se o cabeçalho for de uma versão antiga, `applyEvents` e `readAll` param com um erro que explica como recomeçar (esvaziar a aba e o valor de `estado_sync`).
- **A planilha é o armazenamento, e as métricas leem dela.** A sincronização incremental só traz o que mudou, então o histórico completo vem de `WorkoutSetsTable.readAll()`, que usa `SheetsGateway.readTypedRows` (valores sem formatação: data = número de dias desde 30/12/1899, tratado em `fromRow`). Nunca interprete texto formatado de data.
- `sheets/SyncState`: guarda a data da última sincronização na aba `estado_sync`. Aba vazia = nunca sincronizou = importar o histórico inteiro. Ao testar contra a planilha real, **deixe a aba só com o cabeçalho** (`chave | valor`), senão a primeira sincronização pula o histórico.

- `sheets/SheetsClient`: autentica com `ServiceAccountCredentials` (do `google-auth-library`, que já vem com o `google-api-client`). Lê o caminho do JSON em `GOOGLE_APPLICATION_CREDENTIALS` e o ID em `SPREADSHEET_ID`. Toda chamada passa por `execute`, que transforma 403 e 404 em `SheetsException` com uma mensagem que diz o que corrigir. No GitHub Actions, o secret com o JSON será gravado num arquivo temporário apontado pela mesma variável.

- `service/VolumeCalculator`: recebe templates e `VolumeRules` (músculos com indireta e agrupamento) no construtor. `volumeOf(exercícios)` devolve `Map<músculo, MuscleVolume>`, e `weeklyVolume(treinos, zona)` agrupa por segunda-feira (`Weeks.startOf`). Template desconhecido lança `IllegalArgumentException` em vez de contar errado.
- `service/SetRecord`: uma série já com data local, semana e músculos. É a unidade de dados das métricas, venha de `SetRecords.of(Workout, ...)` ou da planilha. As métricas novas devem receber `Collection<SetRecord>` e `Settings`, sem saber de onde vêm.
- `service/VolumeRules.contribution`: a única implementação da regra "direta no primário, indireta só nos permitidos". Tanto `VolumeCalculator` (rotinas) quanto `VolumeReport` (semanas) a usam; não duplique a regra.
- `service/VolumeReport.weekly`: uma linha por semana e músculo, sem buracos (semanas sem treino aparecem com zero, e músculos com faixa-alvo aparecem com zero). Traz o nível e a posição na faixa. `MuscleNames.label` traduz os nomes.
- `service/ExerciseReport.build(séries, hoje, semanasDeEstagnação)`: por exercício (identificado pelo template, nome da série mais recente), os pontos por dia de treino (maior carga e melhor 1RM de Epley), os recordes (carga, repetições e 1RM, com a **primeira** data em que foram alcançados), a lista de recordes batidos (`PrEvent`, base para "PRs do mês"; a primeira sessão só estabelece a base) e a tendência. Tendência: melhor 1RM das últimas N semanas contra o das N anteriores, com `PROGRESSING`, `STAGNANT` (treinou e não melhorou), `INACTIVE` (não treinou, não é estagnação) e `NO_BASELINE`. A semana atual conta, mesmo incompleta. O relatório cobre todos os exercícios; filtrar pelos das rotinas é papel de quem exporta.
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
- Séries com carga 0, sem carga, sem repetições ou com mais de 50 repetições (`SetRecord.MAX_PLAUSIBLE_REPS`, erro de digitação) contam no volume, mas são ignoradas na tonelagem, no 1RM e nos PRs. Use sempre `SetRecord.countsForStrength()`.
- 1RM estimado pela fórmula de Epley: `carga × (1 + reps / 30)`.
- A soma das diretas é sempre igual ao total de séries válidas. Os valores esperados das 5 rotinas estão em `docs/contexto.md` e devem virar teste.
- O volume baixo de peito é proposital. Não tratar como alerta nem sugerir mudança.

## Segredos

- `HEVY_API_KEY`, a credencial da conta de serviço do Google e o ID da planilha vêm de variáveis de ambiente (secrets no GitHub Actions). Nunca vão para código, log, README ou commit.
- O `.gitignore` já exclui `.env`, `secrets/`, `service-account*.json` e `credentials*.json`. Não ignorar `*.json` inteiro, porque as fixtures de teste são `.json`.
