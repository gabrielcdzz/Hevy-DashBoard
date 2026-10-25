# Plano do projeto

> Especificação original do projeto. As decisões tomadas depois, o estado atual e as próximas etapas estão em `docs/contexto.md`, que prevalece em caso de conflito. **Várias coisas deste plano foram removidas por decisão do usuário** (Looker Studio, 1RM, fases cutting/bulking, faixas-alvo fixas, a aba Análise, o calendário de treinos); a lista está na seção 8 do `contexto.md`.

## 1. Objetivo

Criar um fluxo automático que lê os treinos do Hevy, calcula séries diretas e indiretas por grupo muscular, grava os dados em uma planilha e apresenta indicadores em um dashboard interativo. O projeto também será usado como item de currículo, portanto testes, organização do código e documentação fazem parte do escopo.

## 2. Arquitetura

Fluxo de dados: Hevy (API) → script em Java → Google Sheets → Looker Studio.

A sincronização usa o endpoint de eventos de treino da API do Hevy, que lista treinos atualizados ou apagados desde uma data. Assim, o script processa somente o que mudou desde a última execução. Os eventos são por treino (salvo, editado ou apagado), não por série; editar ou remover uma série de um treino salvo é coberto pelo evento de atualização.

Os exercícios da API trazem músculo primário e músculos secundários, permitindo calcular séries diretas e indiretas.

## 3. Linguagem e ferramentas

- Linguagem: Java.
- API do Hevy: `java.net.http.HttpClient`, com a chave de API no header. Base: `https://api.hevyapp.com/v1`.
- JSON: Jackson, mapeando respostas para classes como `Workout`, `Exercise`, `SetEntry` e `ExerciseTemplate`.
- Planilha: biblioteca oficial do Google Sheets para Java, com conta de serviço. Caso seja usado Excel, considerar Apache POI.
- Agendamento: `ScheduledExecutorService`, cron ou GitHub Actions.
- Build e versionamento: Maven e Git/GitHub desde o primeiro dia.
- Dashboard inicial: Looker Studio conectado à planilha.
- Estrutura sugerida do repositório: pacotes separados para `client` (Hevy), `model`, `service` (cálculo de volume) e `sheets`. A lógica de cálculo deve ficar isolada para facilitar os testes.

## 4. Regras de cálculo definidas

- **Série válida:** toda série de trabalho, ou seja, tudo que não for aquecimento. Séries até a falha e dropsets entram.
- **Série direta:** conta para o músculo primário do exercício.
- **Série indireta:** conta em campo separado para os músculos secundários. Exemplo: supino reto = 1 série direta de peito e 1 indireta de tríceps e de ombro.
- **Formato de exibição:** "Bíceps: 8 diretas (indiretas 6)".
- **Janela de análise:** semana completa, de segunda a domingo.

## 5. Dados do dashboard

### Aba 1 — Visão geral

- Séries diretas e indiretas por músculo na semana.
- Faixa-alvo por músculo, definida pelo usuário, indicando o que está abaixo ou acima.
- Semana atual versus semana anterior.
- Consistência: treinos por semana, sequência de semanas completas e calendário de calor com os dias treinados.
- Duração média dos treinos.

### Aba 2 — Exercícios

- Evolução de carga nos principais exercícios.
- 1RM estimado por exercício ao longo do tempo, pela fórmula de Epley: `1RM = carga × (1 + reps / 30)`.
- Recordes pessoais (PRs): maior carga, maior número de repetições e maior 1RM estimado de cada exercício, com a data.
- Alerta de estagnação: exercícios sem progressão há 3 ou 4 semanas.

### Aba 3 — Análise

- Frequência por músculo: quantas vezes por semana cada grupo é treinado.
- Volume em kg (tonelagem): soma de carga × repetições por treino e por semana.
- Equilíbrio do treino: push versus pull e superior versus inferior.
- Séries por faixa de repetição: 1–5, 6–12 e 13+.

## 6. Lapidação e prioridades

### Primeira versão

- Cards de resumo no topo da Visão geral: treinos da semana, séries totais e PRs do mês.
- Filtros de período, músculo e exercício em todas as abas.
- Variação semanal de volume (%), para enxergar saltos bruscos e semanas de deload.
- Marcador de fase nos gráficos de evolução (por exemplo, início de cutting ou bulking), usando uma tabela simples de fases e datas.

### Segunda fase

- Força relativa: 1RM estimado dividido pelo peso corporal.
- Próximo PR por exercício: carga necessária, com X repetições, para bater o melhor 1RM estimado.
- RPE médio por exercício e por semana, caso o RPE seja preenchido nas séries do Hevy.
- Notas de treino cruzadas com quedas de desempenho.
- Volume por rotina ou dia de treino.

## 7. Boas práticas de dados

- Guardar os dados brutos, não só o resumo: cada série com data, exercício, músculo, tipo, carga e repetições em uma aba ou tabela própria. Os totais semanais serão calculados a partir desses dados, permitindo mudar regras sem perder histórico.
- Manter uma tabela de ajustes manuais para exercícios personalizados do Hevy e para músculos secundários considerados irrelevantes.
- Manter a chave de API fora do repositório, usando variável de ambiente e `.gitignore`.

## 8. Ordem de desenvolvimento

1. Validar as regras de cálculo: comparar o volume calculado para uma semana com a planilha atual.
2. Escrever o script em Java (cliente da API, cálculo de volume e escrita na planilha), com testes unitários da função de cálculo.
3. Montar a aba Visão geral no Looker Studio.
4. Adicionar as abas Exercícios e Análise.
5. Polir para o currículo: README completo, testes, capturas do dashboard e, se fizer sentido, banco de dados e dashboard web próprio.

## 9. Currículo e GitHub

- Manter um repositório com README claro: problema, solução, como executar e capturas do dashboard funcionando.
- Escrever o README conforme o projeto avança, não apenas no final.
- Criar testes unitários para a lógica de cálculo, que concentra o raciocínio do projeto.
- Descrever com precisão o que é código próprio (sincronização e cálculo de volume) e o que é ferramenta de terceiros (servidor MCP).
- Exemplo de linha para o currículo: "Sincronizador de treinos: integra a API do Hevy a uma planilha, calculando séries semanais por grupo muscular de forma automática (Java, REST, Git)."

## 10. Pontos em aberto

- Escolher Google Sheets ou Excel e confirmar como a planilha está organizada hoje.
- Definir a faixa-alvo de séries semanais por músculo.
- Escolher os principais exercícios para acompanhar a evolução de carga.
- Decidir se vale migrar depois para um dashboard web próprio e para um banco de dados.
- Confirmar se o peso corporal e o RPE são registrados no Hevy, para decidir se força relativa e RPE médio entram na segunda fase.
