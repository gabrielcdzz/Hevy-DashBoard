# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Visão geral

Aplicação Java 21 (Maven, sem framework) que lê os treinos do Hevy, grava as séries no Google Sheets, calcula o volume semanal por músculo contra o que as rotinas planejam, a evolução de cada exercício e a consistência, e mostra tudo em um dashboard web próprio (HTML, CSS e JavaScript puros). É também portfólio do usuário, então testes, README (em português) e organização do código fazem parte do escopo.

Documentação:

- `docs/contexto.md`: decisões, regras de cálculo, valores esperados para testes, estrutura da planilha e histórico das mudanças. Leia antes de começar uma etapa nova.
- `docs/plano.md`: a especificação original. Em caso de conflito, vale o `contexto.md` (várias coisas do plano foram removidas por decisão do usuário).

**Estado:** tudo funciona de ponta a ponta com os dados reais do usuário. O CI (`.github/workflows/ci.yml`) roda os testes do Java e do navegador e publica no GitHub Pages **só o site com os dados de exemplo**. Decisão do usuário: os dados reais ficam só na máquina dele, então **não existe sincronização agendada no GitHub** e o repositório não guarda nenhum segredo. Falta apenas dar `push` e ligar o GitHub Pages (Settings → Pages → Source: GitHub Actions).

## Como trabalhar com o usuário

- O usuário liberou o projeto inteiro, inclusive o front end e os commits. Commitar em etapas, com mensagem em português. **Perguntar antes de** `git push`, de apagar dados na planilha, de criar serviços externos e de publicar dados reais de treino (o repositório é público).
- **Quando o usuário pedir várias mudanças, não execute de cara:** diga o que entendeu, aponte dúvidas e dê sugestões, e só execute depois que ele concordar. Ele pediu isso explicitamente.
- Explicar a lógica de cada parte de forma objetiva, passo a passo, com números e exemplos. O usuário precisa saber defender o projeto numa entrevista.
- O ambiente é Windows/PowerShell. Comandos entregues ao usuário devem funcionar no PowerShell.
- **Nada de dados reais do usuário em arquivo do repositório** (código, teste, documento, captura de tela): ele é público. Os números do histórico real ficam na memória local do Claude.

## Comandos

Não há Maven Wrapper, então use o `mvn` instalado.

```bash
mvn test                                               # todos os testes do Java
mvn test -Dtest=HevyClientTest                         # uma classe
mvn test -Dtest=HevyJsonTest#leEventoDeTreinoApagado   # um método
mvn package                                            # gera target/hevy-dashboard-0.1.0-SNAPSHOT.jar (fat jar via shade)
java -jar target/hevy-dashboard-0.1.0-SNAPSHOT.jar dashboard # o do dia a dia: sync + abre o site com os dados reais (no Windows: clique duplo em abrir-dashboard.bat)
java -jar target/hevy-dashboard-0.1.0-SNAPSHOT.jar serve     # só abre o site (dados reais se site/data/dashboard.json existir, senão o exemplo)
java -jar target/hevy-dashboard-0.1.0-SNAPSHOT.jar sync      # Hevy -> planilha -> site/data/dashboard.json (precisa de HEVY_API_KEY, GOOGLE_APPLICATION_CREDENTIALS e SPREADSHEET_ID)
java -jar target/hevy-dashboard-0.1.0-SNAPSHOT.jar sample    # regenera site/data/sample.json (dados fictícios, vão para o Git)
java -jar target/hevy-dashboard-0.1.0-SNAPSHOT.jar check     # só testa a conexão com o Hevy e a planilha
cd site/tests && npm ci && npm test                    # testes de navegador (precisa do Google Chrome; CHROME_PATH se não estiver no lugar padrão)
```

## Convenções de código

- Nomes de classes, métodos e variáveis em inglês. Comentários, Javadoc, mensagens de erro e nomes de testes em português (ex.: `leEventoDeTreinoApagado`).
- Modelos são `record` imutáveis. Os construtores compactos trocam listas nulas por `List.of()` e fazem cópia com `List.copyOf`.
- Poucas dependências: HTTP com `java.net.http.HttpClient`, sem biblioteca extra.
- Testes sem rede: JSON de exemplo em text blocks ou em `src/test/resources/`, servidor local com `com.sun.net.httpserver.HttpServer`, ou as versões falsas (`InMemorySheets`, `FakeHevy`).
- Acesso externo atrás de interface (`HevySource`, `SheetsGateway`), para o resto do código e os testes não dependerem da rede.

## Arquitetura (`src/main/java/com/gabriel/hevydashboard/`)

**`client/`** (API do Hevy)
- `HevyClient`: cliente somente leitura de `https://api.hevyapp.com/v1`. A chave vai no header `api-key` e nunca aparece em log nem em mensagem de erro. Toda listagem passa por `fetchAll` (paginação até `page_count`); `get` tenta até 3 vezes em falha de rede, 429 ou 5xx; o resto vira `HevyApiException` com o status HTTP (`-1` para rede). Implementa `HevySource`.
- `HevyJson`: o único `ObjectMapper` (snake_case do Hevy para camelCase dos records, `java.time`, ignora campos novos).
- Sincronização incremental: `/workouts/events?since=` devolve `updated` (com o treino completo) e `deleted` (só o id). `Instant.EPOCH` importa tudo. O endpoint não cobre rotinas, que são relidas inteiras.

**`service/`** (cálculo puro, **sem I/O**)
- `SetRecord`: uma série já com data local, semana e músculos; é a unidade de dados das métricas, venha de `SetRecords.of(Workout, ...)` ou da planilha. `countsForStrength()` = série de trabalho com carga e 1 a 50 repetições (acima disso é erro de digitação, `MAX_PLAUSIBLE_REPS`): é a única que entra em recordes e na evolução; o volume conta todas as séries de trabalho.
- `SetRecords.withCurrentTemplates`: atualiza os músculos com o template de hoje e o **nome só nos exercícios personalizados** (`isCustom`): a API devolve os padrão em inglês ("Lat Pulldown (Machine)") e os treinos guardam o nome no idioma do app, que é o certo. Renomear um template não gera evento de treino, então sem isso o histórico antigo ficaria com o nome velho.
- `VolumeRules.contribution`: a única implementação da regra "direta no primário, indireta só nos permitidos". `VolumeCalculator` (rotinas) e `VolumeReport` (semanas) a usam; não duplique.
- `VolumeCalculator`: volume de uma lista de exercícios (usado nas rotinas, para o **planejado**). Template desconhecido lança erro em vez de contar errado.
- `VolumeReport.weekly(séries, settings, planejado, hoje)`: uma linha por semana e músculo, sem buracos (até a semana de hoje, mesmo sem treino; músculos planejados aparecem com zero). Cada linha traz `planned`, `status` (`PlanStatus`) e a variação das diretas contra a semana anterior.
- `PlanStatus.of(feitas, planejadas, semanaEmAndamento)`: acima se passou; dentro se igual; abaixo se menos; **na semana em andamento, menos que o planejado é `REMAINING` ("faltam N"), não abaixo**.
- `ActivityReport.build`: por semana, treinos, séries, duração média e variação; mais a `Consistency` (semana completa = treinos ≥ `Settings.workoutsPerWeek`; a semana em andamento ainda incompleta não quebra a sequência).
- `ExerciseReport.build(séries, hoje, semanasDeEstagnação)`: **tudo em carga e repetições**. O 1RM estimado (Epley, `OneRepMax`) existe só como um tipo de recorde batido (`PrType.ONE_REP_MAX`): conta no cartão "Recordes no mês" e aparece na lista "Recordes mais recentes". Não aparece em mais nenhum lugar: nem na evolução, nem nos recordes de cada exercício. Séries do mesmo template **ou** do mesmo nome são um exercício só. Cada treino vira a **série mais pesada** (maior carga e, nela, mais repetições). Traz: os treinos, os recordes (maior carga e mais repetições, com a **primeira** data), os recordes batidos (`PrEvent`; a primeira sessão só estabelece a base), a comparação do **último treino contra o anterior** (`Change`: `LOAD_UP`, `LOAD_DOWN`, `REPS_UP`, `REPS_DOWN`, `SAME`; a carga tem prioridade) e a tendência por **janela de semanas** (série mais pesada das últimas N semanas contra a das N anteriores: `PROGRESSING`, `STAGNANT`, `INACTIVE`, `NO_BASELINE`).
- `Settings`: `VolumeRules`, semanas de estagnação e meta de treinos por semana. **A meta de séries de cada músculo não está aqui: é o volume planejado das rotinas.**

**`sheets/`**
- `SheetsGateway` (interface) e `SheetsClient`: `ensureSheets`, `readRows`, `readTypedRows`, `readFirstRow`, `readColumn`, `replaceRows`, `appendRows`, `deleteRows`. A escrita tem `RAW` (texto exato) e `TYPED` (o Sheets interpreta datas e números). `SheetsClient` repete chamadas com falha passageira (429, 5xx, rede) com esperas de 2, 5, 15 e 40 s (`Retry`): o Google limita as requisições por minuto e uma execução faz mais de 30 chamadas.
- **Nunca ler de volta e regravar uma aba `TYPED`**: o Sheets devolve datas formatadas conforme o idioma da planilha, e regravar pode trocar dia e mês. As atualizações são incrementais (`readColumn` do `workout_id`, `deleteRows`, `appendRows`). Para interpretar, use `readTypedRows` (data = dias desde 30/12/1899; `Cells`).
- `WorkoutSetsTable`: aba `series_treinos`, uma linha por série (14 colunas; a última é `duracao_min`). `applyEvents` vale o evento mais recente de cada treino, qualquer que seja a ordem, e é idempotente. `readAll()` relê tudo. Cabeçalho antigo para com um erro que explica como recomeçar.
- `SyncState`: a data da última sincronização na aba `estado_sync`. Vazia = importar tudo.
- `SettingsSheets.load()`: cria e lê `ajustes_manuais` (músculos com indireta e agrupamento) e `config` (`semanas_estagnacao`, `treinos_por_semana`). **Só preenche uma aba totalmente vazia**; depois o conteúdo é do usuário. Chaves novas de `config` são acrescentadas no fim. As abas antigas `faixa_alvo`, `niveis_volume` e `fases` não são lidas nem apagadas.

**`sync/`**: `SyncService.run(arquivo)` lê a data da última sincronização, pede ao Hevy os eventos desde então, aplica em `series_treinos`, **só então** grava a nova data, relê a aba, carrega as configurações, calcula o planejado das rotinas e grava o arquivo do dashboard. Se algo falhar antes de gravar a data, a próxima execução refaz tudo sem duplicar nada. O instante é anotado **antes** de buscar, para não esquecer um treino salvo durante a execução.

**`export/`**: `DashboardData` é **o contrato** com o site (`weeks`, `volume`, `plannedVolume`, `exercises`, `prEvents`, `consistency`, `config`, `muscleLabels`). Mudou um campo, mude o JavaScript junto. `DashboardExporter` monta e grava o JSON (1 casa decimal, enums em minúsculas, nulos omitidos). `SampleData` gera um usuário **fictício** e determinístico (60 semanas) pelo mesmo caminho dos dados reais; depois de mudar `DashboardData` ou `SampleData`, rode `sample` e commite `site/data/sample.json`.

**`web/`**: `SiteServer` serve a pasta `site/` **só em localhost** (o site pode mostrar treinos reais) e não entrega nada fora dela.

## Regras de cálculo (decididas pelo usuário, não mudar sem ele pedir)

- Série válida é toda série que não seja `warmup`. `failure` e `dropset` contam.
- Direta conta para o `primaryMuscleGroup` do template. Indireta, em campo separado, **só para bíceps e tríceps**. Dorsal e costas superiores contam juntos como `back`.
- A semana vai de segunda a domingo, no fuso **America/Sao_Paulo**.
- **A meta semanal de cada músculo é o volume planejado das rotinas atuais** (séries diretas somadas em todas as rotinas, cada uma uma vez): abaixo, dentro (igual) ou acima. Músculos fora das rotinas não têm meta. O planejado vale para todas as semanas do histórico, mas as antigas foram feitas com outras rotinas, então é só referência.
- **Evolução de um exercício = série mais pesada de cada treino.** Subiu a carga: "aumentou a carga" (mesmo com menos repetições). Mesma carga e repetições diferentes: "mudou as repetições". Estagnação: nas últimas N semanas não subiu carga nem repetições (com a mesma carga) em relação às N anteriores; exercício não treinado nas últimas N semanas é "inativo", não estagnado.
- Séries com carga 0, sem carga, sem repetições ou com mais de 50 repetições entram no volume, mas **não** em recordes nem na evolução.
- A soma das diretas é sempre igual ao total de séries válidas.
- **O 1RM estimado só é usado nos recordes** (cartão "Recordes no mês" e lista "Recordes mais recentes"; decisão do usuário: "o único caso que iremos usar"). Não coloque 1RM em mais nada sem ele pedir.
- **Foi removido por decisão do usuário e não deve voltar sem ele pedir:** fases cutting/bulking, níveis baixo/moderado/alto, faixas-alvo fixas por músculo, tonelagem, push/pull, séries por faixa de repetição, frequência por músculo, calendário de treinos e a aba Análise.

## Dashboard web (`site/`)

- HTML, CSS e JavaScript puros (módulos ES), sem build. `site/vendor/chart.umd.js` é o Chart.js 4.4.7 copiado para o repositório (MIT).
- Duas abas: **Visão geral** (5 cartões da semana, que não mudam com filtros; os filtros de Período e Músculo logo abaixo; volume por músculo; séries, treinos, variação e duração por semana) e **Exercícios** (os filtros no topo; o que mudou do treino anterior para o último; exercícios sem evolução; evolução do exercício escolhido em carga e em repetições; recordes).
- Os filtros são **um só elemento** (`actions.filters`), criado uma vez e posto pelas views no lugar certo de cada aba. Período: 4, 8, 12 semanas, 6 meses, 1 ano, todo o histórico ou "Personalizado" (campo numérico de 1 a 520 semanas; valor inválido volta ao anterior).
- `site/js/data.js` tenta `data/dashboard.json` (dados reais, **no `.gitignore`**, nunca commitar) e, se não existir, usa `data/sample.json`. O 404 do primeiro no console é esperado. Uma etiqueta no topo diz "Seus dados" ou "Dados de exemplo".
- Regras do site: texto dos dados entra no DOM por `textContent`/nós de texto, nunca `innerHTML`; todo gráfico tem a tabela equivalente (`card({ table })`); status (abaixo, dentro, acima, faltam, evoluindo...) sempre com ícone e texto; um eixo por gráfico (carga e repetições são dois gráficos); cores só pelas variáveis CSS, com valores para claro e escuro; um cartão só desenha depois de estar no documento (`card.render()`).
- **Tooltip (`attachTip` em `ui.js`): nunca reescreva o conteúdo a cada `pointermove`.** O mouse gera centenas de eventos por segundo; o conteúdo só é reescrito quando o mouse muda de elemento, e a posição é aplicada uma vez por quadro (`requestAnimationFrame`). O `#tip` **não** pode ser `aria-live`/`role=status` (cada troca viraria um aviso para tecnologias assistivas, causa conhecida de travamento do navegador); ele é `role=tooltip` com `aria-hidden`, e o mesmo conteúdo está no `aria-label` do elemento e na tabela equivalente. Há um teste que conta as reescritas durante 200 movimentos.
- Os testes de navegador (`site/tests`, 49 verificações) sempre usam os dados de exemplo: o servidor de teste esconde `data/dashboard.json`. Além dos testes, **confira sempre as capturas de tela** (claro, escuro, 390 px): o código sozinho não mostra colisões de rótulos. O workflow publica uma **lista explícita de arquivos** (site + `sample.json`); nunca troque isso por copiar a pasta inteira.
- `src/test/resources/fixtures/`: as 5 rotinas **fictícias** (PUSH, PUSH 2, PULL, PULL 2, LEGS) e os templates delas, no formato da API do Hevy, geradas por `SampleData`. O teste do volume planejado confere com uma conta feita à mão. Nunca coloque rotinas ou treinos reais aqui.

## Segredos

- `HEVY_API_KEY`, a credencial da conta de serviço do Google e o ID da planilha vêm de variáveis de ambiente. Nunca vão para código, log, README ou commit.
- O `.gitignore` já exclui `.env`, `secrets/`, `service-account*.json`, `credentials*.json` e `site/data/dashboard.json`. Não ignorar `*.json` inteiro, porque as fixtures de teste são `.json`.
