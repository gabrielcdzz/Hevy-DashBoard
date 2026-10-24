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
8. **Séries com carga 0 ou sem repetições** (Shoulder Press, Chest Fly, Rear Delt Reverse Fly e Seated Incline Curl, nas rotinas) são de exercícios que o usuário ainda não fez, mas vai fazer. Elas contam como séries no volume. Na tonelagem, no 1RM e nos PRs, devem ser ignoradas para não distorcer os números.

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
- Google Cloud configurado: credencial em `secrets/service-account.json` (ignorada pelo Git), variáveis `GOOGLE_APPLICATION_CREDENTIALS` e `SPREADSHEET_ID` definidas com `setx`. A planilha ainda está vazia.

## 4. Estrutura planejada da planilha (Google Sheets)

| Aba | Conteúdo | Quem escreve |
|---|---|---|
| `series_treinos` | Uma linha por série dos treinos executados | Script |
| `series_rotinas` | Uma linha por série das 5 rotinas (planejado) | Script |
| `ajustes_manuais` | Músculos que recebem indireta e agrupamento de músculos | Usuário (o script cria com os valores iniciais) |
| `config` | Faixa-alvo por músculo, tabela de fases, parâmetros | Usuário (o script cria com valores iniciais) |
| `volume_semanal` | Diretas e indiretas por músculo e por semana (executado) | Script (sempre reescrita) |
| `volume_rotinas` | Diretas e indiretas por músculo, por rotina e total (planejado) | Script (sempre reescrita) |

Colunas:

- **`series_treinos`:** `workout_id`, `data`, `semana_inicio` (a segunda-feira), `rotina`, `exercicio`, `template_id`, `musculo_primario`, `musculos_secundarios`, `tipo_serie`, `valida`, `carga_kg`, `reps`, `rpe`
- **`series_rotinas`:** `routine_id`, `rotina`, `exercicio`, `template_id`, `indice_serie`, `tipo_serie`, `valida`, `carga_kg`, `reps`, `musculo_primario`, `musculos_secundarios`
- **`ajustes_manuais`:** duas tabelas que viram `VolumeRules` no código. A primeira, `musculos_com_indireta`, começa com `biceps` e `triceps`. A segunda, `agrupamento` (`musculo_hevy`, `grupo`), começa com `lats → back` e `upper_back → back`.
- **`config`:** faixa-alvo (`musculo`, `min`, `max`, com valores provisórios), fases (`fase`, `inicio`, `fim`, com a primeira linha `cutting | 2026-09-14 | (vazio)`) e semanas sem progressão para o alerta de estagnação (3 ou 4).

As abas `volume_*` nunca devem ser editadas à mão, porque o script as sobrescreve.

**Proposta ainda não discutida com o usuário:** guardar a data da última sincronização numa aba `estado_sync`. O GitHub Actions não guarda estado entre execuções, então essa data precisa ficar em algum lugar.

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
4. **Pacote `sheets`:** criar as abas se não existirem, escrever as séries brutas, ler `ajustes_manuais` e `config`, escrever as abas `volume_*`.
5. **Orquestração (`SyncService` + `Main`):** importação completa na primeira vez, depois incremental, conforme a seção 5.
6. **Workflow do GitHub Actions** e cadastro dos secrets.
7. **Looker Studio, aba Visão geral:** diretas e indiretas por músculo × faixa-alvo, semana atual × anterior, treinos por semana, sequência de semanas completas, calendário de calor, duração média.
8. **Abas Exercícios e Análise.** Os dados extras saem do script ou de campos calculados:
   - Exercícios: evolução de carga, 1RM pela fórmula de Epley (`carga × (1 + reps/30)`), PRs (maior carga, mais reps e maior 1RM, com data) e alerta de estagnação
   - Análise: frequência por músculo, tonelagem (`carga × reps`), push × pull, superior × inferior, séries por faixa de repetição (1–5, 6–12, 13+)
9. **Lapidação da primeira versão:** cards de resumo no topo, filtros de período, músculo e exercício, variação semanal de volume (%), marcador de fase.
10. **Currículo:** README completo em português (problema, solução, como executar, capturas do dashboard), deixando claro o que é código próprio e o que é ferramenta de terceiros.

**Segunda fase:** força relativa (1RM ÷ peso corporal), próximo PR, RPE médio, notas de treino cruzadas com quedas de desempenho, volume por rotina. Antes, verificar se o peso corporal e o RPE estão preenchidos no Hevy.

**Em aberto:** valores iniciais da faixa-alvo, 3 ou 4 semanas para o alerta de estagnação, repositório público ou privado, migração futura para banco de dados e dashboard web próprio.
