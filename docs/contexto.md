# Decisões e estado do projeto

> Complementa o `docs/plano.md` (especificação original). Quando os dois divergirem, vale este arquivo.
> Atualizado em 24/10/2026.

O ponto central é **praticidade**: o usuário muda uma série no Hevy (adiciona, remove, edita) e o dashboard se atualiza sozinho, sem digitar nada.

Fluxo: **Hevy API → Java → Google Sheets → Looker Studio**, rodando no **GitHub Actions**.

## 1. Decisões tomadas

| Decisão | Motivo |
|---|---|
| **Java 21 + Maven** | Escolha do usuário, pensando no currículo. Java 21 tem suporte de longo prazo. |
| **Google Sheets** (não Excel) | O Looker Studio conecta direto no Sheets. |
| **Planilha nova do zero** | A planilha antiga era simples e foi descartada. A nova segue a estrutura dos dados do Hevy. |
| **Guardar as séries brutas**, não só os totais | Permite mudar as regras depois e recalcular sem perder histórico. |
| **Importar todo o histórico** na primeira execução | Gráficos de evolução mais longos. |
| **GitHub Actions agendado, sem botão manual** | O usuário considerou um botão "Sincronizar" e desistiu. Vale a sincronização automática. |
| **Agendamento a cada 1 hora** | Repositório privado tem cerca de 2.000 min/mês grátis, e a cada 30 min fica perto do limite. Se o repositório ficar público, pode baixar para 30 min. |
| **Fuso America/Sao_Paulo** para definir a semana | O Hevy grava em UTC. Um treino às 22h de domingo não pode cair na semana seguinte. |
| **README em português** | Decisão do usuário. |
| **Evolução de carga: todos os exercícios das 5 rotinas** | Alguns exercícios se repetem entre as rotinas. |
| **Faixa-alvo por músculo: valores provisórios** | O usuário ajusta depois na aba `config`. |
| **Fase atual: cutting, desde 14/09/2026** | Vai para a tabela de fases (marcador nos gráficos de evolução). |
| **Peito com volume baixo (5 séries/semana) é proposital** | Não tratar como alerta nem sugerir mudança. |
| `groupId` = `com.gabriel`, pacote `com.gabriel.hevydashboard` | Já em uso. Mudar exige renomear os pacotes. |

## 2. Regras de cálculo do volume semanal

1. **Série válida = série de trabalho.** Conta tudo cujo `type` não é `warmup`: entram `normal`, `failure` e `dropset`.
2. **Série direta:** 1 série para o músculo primário do exercício (`primary_muscle_group` do template).
3. **Série indireta:** conta **só para bíceps e tríceps** (decisão de 24/10/2026). Bíceps recebe das puxadas e tríceps dos empurrões. Os outros secundários do Hevy (ombro no supino, antebraço nas roscas, lombar no stiff, panturrilha no leg curl) são ignorados, porque trabalham pouco e só inflavam os números.
   - Exemplo: supino inclinado (primário `chest`, secundários `triceps` e `shoulders`) com 2 séries de trabalho = 2 diretas de peito e 2 indiretas de tríceps.
4. **Agrupamento:** `lats` (dorsal) e `upper_back` (costas superiores) são contados juntos como `back` (costas), porque trabalham juntos em quase todo exercício de costas. Um mesmo grupo recebe indireta no máximo uma vez por exercício e nunca recebe indireta do próprio exercício em que é o primário.
5. **Formato de exibição:** `Bíceps: 11 diretas (indiretas 10)`.
6. **Janela:** semana completa, de segunda a domingo, no fuso de Brasília.
7. **Volume planejado × executado:** o planejado vem das 5 rotinas salvas (aba `volume_rotinas`) e o executado vem dos treinos feitos (aba `volume_semanal`). A regra de contagem é a mesma nos dois.
8. **Séries com carga 0 ou sem repetições** (Shoulder Press, Chest Fly, Rear Delt Reverse Fly e Seated Incline Curl, nas rotinas) são de exercícios que o usuário ainda não fez, mas vai fazer. Elas contam como séries no volume. Na tonelagem, no 1RM e nos PRs, devem ser ignoradas para não distorcer os números. O mesmo vale para séries sem carga (peso corporal) e para séries com mais de 50 repetições, tratadas como erro de digitação (decidido em 24/10/2026, depois de achar 3 séries de 79 e 101 repetições no histórico).

### Valores esperados (usar como teste)

Volume planejado das 5 rotinas, uma vez cada, com as regras acima. Conferido com o `set_count` do Hevy em 24/10/2026, já com o Straight Leg Deadlift no lugar do Deadlift (Smith Machine) na LEGS.

| Rotina | Séries de trabalho | Aquecimento | `set_count` |
|---|---|---|---|
| PUSH | 14 | 9 | 23 |
| PUSH 2 | 15 | 9 | 24 |
| PULL | 15 | 12 | 27 |
| PULL 2 | 16 | 12 | 28 |
| LEGS | 16 | 9 | 25 |
| **Total** | **76** | 51 | 127 |

| Músculo (Hevy) | Diretas | Indiretas |
|---|---|---|
| shoulders | 19 | 0 |
| biceps | 11 | 10 |
| back (lats + upper_back) | 10 | 0 |
| triceps | 8 | 6 |
| abdominals | 6 | 0 |
| hamstrings | 6 | 0 |
| chest | 5 | 0 |
| forearms | 4 | 0 |
| quadriceps | 4 | 0 |
| calves | 3 | 0 |

A soma das diretas (76) é igual ao total de séries de trabalho, e isso deve valer sempre.

### Exercícios das rotinas e músculos (do Hevy)

| Exercício | template_id | Primário | Secundários |
|---|---|---|---|
| Lat Pulldown (Machine) | 473CF5B8 | lats | upper_back, biceps, forearms |
| Chest Supported T Bar Row | 6A8D3193 | upper_back | lats, biceps, forearms |
| Seated Incline Curl (Dumbbell) | 8BAB2735 | biceps | (nenhum) |
| Single Arm Cable Row | D0C4A899 | upper_back | lats, biceps, forearms |
| Reverse Curl (Cable) | 9F48F858 | biceps | forearms |
| Behind the Back Wrist Curl (Barbell) | DDB29047 | forearms | (nenhum) |
| Crunch (Machine) | EB43ADD4 | abdominals | (nenhum) |
| Shoulder Press (Dumbbell) | 878CD1D0 | shoulders | triceps |
| Elevação Lateral (Halter) 1 (custom) | 9d23d3d9-7315-43ca-bd83-ddc83fab7185 | shoulders | (nenhum) |
| Chest Fly (Machine) | 78683336 | chest | (nenhum) |
| Lateral Raise (Cable) | BE289E45 | shoulders | (nenhum) |
| Overhead Triceps Extension (Cable) | B5EFBF9C | triceps | (nenhum) |
| Triceps Pushdown | 93A552C6 | triceps | (nenhum) |
| Leg Extension (Machine) | 75A4F6C4 | quadriceps | (nenhum) |
| Straight Leg Deadlift (stiff) | 2A48E443 | hamstrings | glutes, lower_back, upper_back, lats, ... |
| Lying Leg Curl (Machine) | B8127AD1 | hamstrings | calves |
| Standing Calf Raise (Machine) | E05C2C38 | calves | (nenhum) |
| Hammer Curl (Cable) | 36E8F14E | biceps | forearms |
| Remada Iso-Lateral (Máquina) (custom) | b2ca365b-1abf-4e13-923c-38ae30f557c6 | upper_back | lats, biceps, forearms |
| Rear Delt Reverse Fly (Machine) | D8281C62 | shoulders | upper_back |
| Preacher Curl (Machine) | 1E9A6B8E | biceps | (nenhum) |
| Supino Inclinado Halter (custom) | 3edf9228-8d10-4293-b44b-99f19e9a8d1d | chest | triceps, shoulders |

## 3. Estado atual

- Cliente do Hevy, mapeamento JSON e modelos prontos, com testes passando.
- **Conexão real confirmada (24/10/2026):** o jar devolveu 800 treinos, 5 rotinas e 456 templates. As séries de cada rotina (PULL 2: 28, PUSH 2: 24, LEGS: 25, PULL: 27, PUSH: 23) batem com o `set_count` do Hevy. O mapeamento do JSON está validado.
- **Dados reais do histórico (24/10/2026):** 800 treinos e 14.799 séries (12.390 de trabalho, 2.409 de aquecimento, nenhum `failure` nem `dropset`). Títulos dos treinos: `TREINO A` a `TREINO E` (os antigos) e `PUSH`, `PUSH 2`, `PULL`, `PULL 2`, `LEGS` (os atuais); a coluna `rotina` usa o título do treino. 239 séries não têm carga (peso corporal) e 20 não têm repetições. **Nenhuma série tem RPE**, então "RPE médio" da segunda fase não é viável enquanto o RPE não for preenchido no Hevy. O endpoint de eventos devolveu 803 eventos (800 `updated` e 3 `deleted`).
- A aba `series_treinos` da planilha real já foi preenchida com os 800 treinos (14.799 linhas). A `estado_sync` segue vazia de propósito: a primeira sincronização completa refaz a importação, sem duplicar.
- Google Cloud configurado: credencial em `secrets/service-account.json` (ignorada pelo Git), variáveis `GOOGLE_APPLICATION_CREDENTIALS` e `SPREADSHEET_ID` definidas com `setx`. A planilha ainda está vazia.

## 4. Estrutura planejada da planilha (Google Sheets)

| Aba | Conteúdo | Quem escreve |
|---|---|---|
| `series_treinos` | Uma linha por série dos treinos executados | Script |
| `series_rotinas` | Uma linha por série das 5 rotinas (planejado) | Script |
| `ajustes_manuais` | Músculos que recebem indireta e agrupamento de músculos | Usuário (o script cria com os valores iniciais) |
| `faixa_alvo` | Faixa-alvo de séries diretas por semana, por músculo | Usuário (o script cria com valores iniciais) |
| `niveis_volume` | Níveis baixo (1–9), moderado (10–20) e alto (21+) | Usuário (o script cria com valores iniciais) |
| `fases` | Tabela de fases (cutting, bulking) para o marcador nos gráficos | Usuário (o script cria com a fase atual) |
| `config` | Parâmetros simples (`semanas_estagnacao`) | Usuário (o script cria com valores iniciais) |
| `volume_semanal` | Diretas e indiretas por músculo e por semana (executado) | Script (sempre reescrita) |
| `volume_rotinas` | Diretas e indiretas por músculo, por rotina e total (planejado) | Script (sempre reescrita) |

Colunas:

- **`series_treinos`:** `workout_id`, `data`, `semana_inicio` (a segunda-feira), `rotina`, `exercicio`, `template_id`, `musculo_primario`, `musculos_secundarios`, `tipo_serie`, `valida`, `carga_kg`, `reps`, `rpe`
- **`series_rotinas`:** `routine_id`, `rotina`, `exercicio`, `template_id`, `indice_serie`, `tipo_serie`, `valida`, `carga_kg`, `reps`, `musculo_primario`, `musculos_secundarios`
- **`ajustes_manuais`:** `tipo`, `musculo`, `grupo`. Vira `VolumeRules` no código. Linhas `indireta | biceps` e `indireta | triceps` dizem quais músculos recebem indireta; linhas `agrupamento | lats | back` e `agrupamento | upper_back | back` juntam músculos.
- **`faixa_alvo`:** `musculo`, `min`, `max` (séries diretas por semana). Valores iniciais: 10–20 para tudo, exceto peito em 1–9 (baixo volume proposital). Aviso: glúteo e lombar não têm exercício direto nas rotinas atuais; o glúteo foi incluído com 10–20 e aparecerá "abaixo" até o usuário decidir remover a linha.
- **`niveis_volume`:** `nivel`, `min`, `max` (max vazio = sem teto). Baixo 1–9, moderado 10–20, alto 21+ (definido pelo usuário em 24/10/2026). A classificação usa as séries **diretas**.
- **`fases`:** `fase`, `inicio`, `fim`, com a primeira linha `cutting | 2026-09-14 | (vazio)`. Só o dashboard lê.
- **`config`:** `chave`, `valor`. Por enquanto só `semanas_estagnacao = 3` (o plano diz 3 ou 4).
- As faixas ficaram em abas separadas (e não numa só `config`) para cada uma ser uma tabela limpa para o Looker Studio.

As abas `volume_*` nunca devem ser editadas à mão, porque o script as sobrescreve.

**`estado_sync` (decidido e implementado em 24/10/2026):** guarda a data da última sincronização (`chave | valor` e a linha `ultima_sync | 2026-10-24T22:00:00Z`). O GitHub Actions não guarda estado entre execuções, então essa data fica na própria planilha. Foi preferido a um arquivo no repositório, que exigiria um commit do workflow a cada hora.

## 5. Execução no GitHub Actions

- Workflow em `.github/workflows/sync.yml`:
  - `schedule` a cada 1 hora (`cron: '0 * * * *'`). O agendamento do GitHub pode atrasar.
  - `workflow_dispatch` para rodar manualmente, útil em testes.
  - Passos: checkout → setup-java 21 (com cache do Maven) → `mvn -B package` (já roda os testes) → `java -jar target/*.jar`.
- **Secrets** (cadastrados pelo usuário em Settings → Secrets and variables → Actions): `HEVY_API_KEY`, a credencial JSON da conta de serviço do Google (ex.: `GOOGLE_SERVICE_ACCOUNT_JSON`) e o ID da planilha (ex.: `SPREADSHEET_ID`).
- **Lógica de cada execução:**
  1. Ler `estado_sync`. Se estiver vazia (primeira vez), importar todo o histórico.
  2. Treinos: `getWorkoutEventsSince(ultimaSync)`. Em `updated`, substituir as linhas daquele `workout_id`. Em `deleted`, remover as linhas daquele `workout_id`.
  3. Rotinas: o endpoint de eventos não cobre rotinas, então reler as 5 rotinas inteiras a cada execução (é pouco dado).
  4. Templates: buscar para mapear `template_id` → músculos (inclui os 3 exercícios personalizados).
  5. Recalcular `volume_semanal` e `volume_rotinas` a partir das abas brutas, com as regras de `ajustes_manuais`.
  6. Gravar a nova data em `estado_sync`.
- O cache do Looker Studio é de 15 minutos ou mais, então uma mudança no Hevy aparece no dashboard em até cerca de 1h15.

## 6. Próximas etapas (em ordem)

1. ~~Confirmar a conexão real~~ (feito).
2. ~~**Cálculo de volume (`service`)**~~ (feito em 24/10/2026, `VolumeCalculatorTest` e `WeeksTest` passando):
   - regra de série válida, contagem direta e indireta, ajustes manuais, semana de segunda a domingo em America/Sao_Paulo
   - testes JUnit, incluindo um teste com as 5 rotinas que confira **exatamente** os números da seção 2, mais casos de borda: treino no domingo à noite em UTC, dropset e falha contam, aquecimento não conta, indireta só para bíceps e tríceps, dorsal e costas superiores agrupados
3. ~~**Configurar o Google Cloud**~~ (feito em 24/10/2026; projeto `hevy-dashboard-512621`, conta de serviço `hevy-sync`, planilha "Hevy Dashboard", acesso confirmado pelo `Main`) (o usuário faz, com o Claude guiando passo a passo): criar o projeto, ativar a Google Sheets API, criar a conta de serviço, baixar a credencial JSON (que nunca vai para o repositório; deve ficar em `secrets/`), criar a planilha e compartilhar com o e-mail da conta de serviço.
4. **Pacote `sheets`:** criar as abas se não existirem, escrever as séries brutas, ler `ajustes_manuais` e `config`, escrever as abas `volume_*`. Feito: conexão, `SyncState`, `series_treinos`. Falta: `series_rotinas`, `ajustes_manuais`, `config`, `volume_semanal`, `volume_rotinas`.
5. **Orquestração (`SyncService` + `Main`):** importação completa na primeira vez, depois incremental, conforme a seção 5.
6. **Workflow do GitHub Actions** e cadastro dos secrets.
7. **Looker Studio, aba Visão geral:** diretas e indiretas por músculo × faixa-alvo, semana atual × anterior, treinos por semana, sequência de semanas completas, calendário de calor, duração média.
8. **Abas Exercícios e Análise.** Os dados extras saem do script ou de campos calculados:
   - Exercícios: evolução de carga, 1RM pela fórmula de Epley (`carga × (1 + reps/30)`), PRs (maior carga, mais reps e maior 1RM, com data) e alerta de estagnação
   - Análise: frequência por músculo, tonelagem (`carga × reps`), push × pull, superior × inferior, séries por faixa de repetição (1–5, 6–12, 13+)
9. **Lapidação da primeira versão:** cards de resumo no topo, filtros de período, músculo e exercício, variação semanal de volume (%), marcador de fase.
10. **Currículo:** README completo em português (problema, solução, como executar, capturas do dashboard), deixando claro o que é código próprio e o que é ferramenta de terceiros.

**Segunda fase:** força relativa (1RM ÷ peso corporal), próximo PR, RPE médio, notas de treino cruzadas com quedas de desempenho, volume por rotina. Antes, verificar se o peso corporal e o RPE estão preenchidos no Hevy.

**Em aberto:** 3 ou 4 semanas para o alerta de estagnação e migração futura para banco de dados.

## 7. Mudança de rumo (24/10/2026): dashboard web próprio

O usuário liberou o projeto inteiro (inclusive commits e front end) e decidiu:

- **Dashboard web próprio, em vez de Looker Studio.** As abas Visão geral, Exercícios e Análise da seção 5 do `plano.md` viram páginas/seções do site. As etapas 7 a 9 da seção 6 (Looker Studio) ficam substituídas por isso.
- **Repositório público** (`github.com/gabrielcdzz/Hevy-DashBoard`). Actions e GitHub Pages gratuitos, então a sincronização pode rodar a cada 30 minutos.
- **Autonomia:** o Claude decide e avisa, com commits em etapas pequenas. Pergunta antes de `git push`, de apagar dados na planilha, de criar serviços externos e de publicar dados reais.
- **`series_rotinas` descartada.** O volume planejado das rotinas é calculado direto das rotinas.

Arquitetura nova: Hevy API → Java (sincronização e cálculo) → Google Sheets (séries brutas, configurações editáveis e estado) + arquivo de dados do dashboard → site estático publicado no GitHub Pages pelo GitHub Actions.

**Privacidade (repositório e site públicos):** dados reais de treino nunca entram em commit. O arquivo de dados do site é gerado pelo Actions e publicado como artefato do Pages, fora do histórico do Git. Publicar os treinos reais num site público expõe datas, exercícios e cargas, então isso exige confirmação do usuário antes de ligar. O site deve funcionar também com dados de exemplo, para servir de portfólio. As fixtures de `src/test/resources/fixtures/` já mostram as rotinas atuais.

**Decidido: a planilha é o armazenamento.** A sincronização incremental só traz o que mudou, então o histórico completo vem de reler a aba `series_treinos` com valores sem formatação (datas como número de série). As métricas recebem `SetRecord`s e não sabem de onde eles vêm. Para a duração média dos treinos, a aba ganhou a coluna `duracao_min`.

**Dados reais (24/10/2026):** histórico de 27/02/2023 a 24/10/2026, 800 treinos, duração média de 91 minutos. Na semana de 12/10: bíceps 12 diretas (11 indiretas), ombros 12, costas 11, tríceps 8 (5), peito 7. Dos 21 exercícios das rotinas atuais, 6 estão progredindo, 11 estagnados, 1 inativo e 3 sem base de comparação (janelas de 3 semanas). Há 56 exercícios com carga no histórico e 603 recordes batidos, 27 deles em outubro/2026. Três séries parecem erro de digitação no Hevy (elevação lateral halter 10 kg x 101 em 19/06/2023, remada iso-lateral 43 kg x 101 em 30/08/2023 e rosca Scott 55 kg x 79 em 04/09/2023) e o usuário pode corrigi-las no app.

**Próxima versão (fora do escopo atual):** sistema com contas e login para outras pessoas conectarem o próprio Hevy. A API do Hevy não tem OAuth, só chave pessoal (exige Hevy Pro), então isso pede backend, banco de dados e guarda criptografada das chaves. O `HevyClient` e as métricas já não dependem de uma chave fixa nem da planilha, para facilitar essa fase.

**Decisão técnica ainda aberta:** qual biblioteca de gráficos usar no front.
