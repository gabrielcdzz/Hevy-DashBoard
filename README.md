# Hevy Dashboard

[![CI e site](https://github.com/gabrielcdzz/Hevy-DashBoard/actions/workflows/ci.yml/badge.svg)](https://github.com/gabrielcdzz/Hevy-DashBoard/actions/workflows/ci.yml)
![Java 21](https://img.shields.io/badge/Java-21-informational)
![Licença MIT](https://img.shields.io/badge/licen%C3%A7a-MIT-green)

Sincroniza os treinos do [Hevy](https://www.hevyapp.com/) com uma planilha do Google Sheets, calcula o volume semanal por grupo muscular (séries **diretas** e **indiretas**) compara com o que as suas rotinas planejam e mostra tudo em um dashboard web: consistência, evolução de carga e repetições de cada exercício, recordes pessoais e um alerta dos exercícios que pararam de evoluir.

**Demonstração com dados de exemplo:** https://gabrielcdzz.github.io/Hevy-DashBoard/

![Visão geral do dashboard](docs/img/visao-geral.png)

## O problema

O Hevy registra cada série, mas não responde às perguntas de quem treina com método:

- Quantas séries de cada músculo eu fiz **nesta semana**, contando também o trabalho indireto (o supino treina o tríceps)?
- Estou fazendo, em cada músculo, as séries que as minhas rotinas planejam?
- Em quais exercícios eu aumentei a carga ou as repetições desde o último treino, e em quais parei de evoluir?
- Estou sendo consistente, semana após semana?

## A solução

Um programa em Java que lê a API do Hevy, guarda cada série em uma planilha (para o histórico ser seu e as regras poderem mudar sem perder nada) e gera um arquivo de dados que um site estático transforma em gráficos.

- **Sincronização incremental.** A cada execução o programa pede ao Hevy só os treinos que foram salvos, editados ou apagados desde a última vez. Importar um histórico de alguns anos (centenas de treinos) leva cerca de 30 segundos; as execuções seguintes, cerca de 10.
- **Regras de contagem explícitas e testadas** (veja abaixo), com os números conferidos à mão contra o próprio Hevy.
- **A meta de cada músculo vem das próprias rotinas.** O programa soma as séries diretas de cada músculo nas rotinas do Hevy: com 5 séries de peito planejadas, fazer 4 na semana é abaixo, 5 é dentro e 6 é acima. Na semana em andamento aparece "faltam 2", e não um alarme falso. Mudou a rotina no Hevy, a meta muda sozinha.
- **Evolução em carga e repetições**, sem fórmulas de 1RM: compara a série mais pesada do último treino de cada exercício com a do treino anterior.
- **Configuração na própria planilha** (o que conta como série indireta e a meta de treinos por semana), sem mexer em código.
- **Dashboard com duas abas**, filtros de período (4 semanas a todo o histórico, ou um número de semanas à sua escolha), músculo e exercício, modo claro e escuro, versão para celular e uma tabela equivalente para cada gráfico.

## Capturas

| Exercícios (modo escuro) | No celular |
|---|---|
| ![Exercícios, modo escuro](docs/img/exercicios-escuro.png) | <img src="docs/img/celular.png" alt="Dashboard no celular" width="320"> |

Todas as imagens e o site publicado usam **dados de exemplo**: um usuário fictício gerado pelo próprio projeto.

## Como funciona

```mermaid
flowchart LR
    A[API do Hevy] -->|eventos desde a última sincronização| B[SyncService<br/>Java]
    B -->|séries brutas| C[(Google Sheets)]
    C -->|histórico completo| B
    B -->|métricas| D[dashboard.json]
    D --> E[Site<br/>HTML, JS e Chart.js]
```

1. **Busca.** `HevyClient` lê treinos, rotinas e exercícios, com paginação e novas tentativas em caso de erro passageiro.
2. **Guarda.** `WorkoutSetsTable` grava uma linha por série na aba `series_treinos`. Ao receber um treino editado ou apagado, apaga só as linhas dele e acrescenta as novas. Aplicar os mesmos eventos duas vezes não duplica nada, então uma execução que falha no meio se corrige na seguinte.
3. **Calcula.** O pacote `service` (sem acesso a rede ou planilha) transforma as séries em volume por músculo contra o planejado das rotinas, evolução dos exercícios, recordes e consistência.
4. **Exporta.** `DashboardExporter` junta tudo em um JSON, que o site lê.
5. **Lembra.** A data da última sincronização fica na aba `estado_sync` da planilha, porque quem agenda a execução não guarda estado entre uma execução e outra.

## Regras de cálculo

| Regra | Definição |
|---|---|
| Série válida | Toda série que não é aquecimento. Séries até a falha e dropsets contam. |
| Série direta | 1 série para o grupo do músculo primário do exercício. |
| Série indireta | Conta só para **bíceps e tríceps** (nas puxadas e nos empurrões). Os outros músculos secundários que o Hevy lista são ignorados. |
| Agrupamento | Dorsal e costas superiores contam juntos como "costas". |
| Semana | De segunda a domingo, no fuso de Brasília (um treino às 22h de domingo não pode cair na semana seguinte). |
| Meta semanal de um músculo | As séries diretas dele somadas nas rotinas atuais. Abaixo, dentro (igual) ou acima. Na semana em andamento, menos que a meta aparece como "faltam N". |
| Evolução de um exercício | A série mais pesada de cada treino. Subiu a carga: "aumentou a carga" (mesmo com menos repetições). Mesma carga e outras repetições: "mudou as repetições". |
| Entra em recordes e na evolução | Séries de trabalho com carga e entre 1 e 50 repetições. Acima disso é tratado como erro de digitação (mas continua contando no volume). |
| Sem evolução | Nas últimas N semanas não subiu a carga, nem as repetições com a mesma carga, em relação às N semanas anteriores. |
| Semana completa | Pelo menos a meta de treinos por semana (configurável). |

O exemplo da regra de séries indiretas: um supino inclinado (peito) com 2 séries de trabalho vale 2 diretas de peito e 2 indiretas de tríceps. As regras ficam em `docs/contexto.md`.

## Estrutura do projeto

```
src/main/java/com/gabriel/hevydashboard/
├── client/    HevyClient (API do Hevy), HevySource (interface), modelos de resposta
├── model/     records imutáveis que espelham o JSON do Hevy
├── service/   cálculos puros: VolumeCalculator, VolumeReport, ExerciseReport, ActivityReport
├── sheets/    SheetsClient (Google Sheets), tabelas da planilha, configurações, repetição de chamadas
├── sync/      SyncService: a execução completa
├── export/    DashboardExporter e os dados de exemplo
├── web/       SiteServer: servidor local (só localhost) que abre o dashboard
└── Main.java  comandos: dashboard, sync, serve, sample, check
site/          o dashboard (HTML, CSS e JavaScript puros) e seus testes de navegador
docs/          plano original, decisões e estado do projeto
abrir-dashboard.bat   clique duplo no Windows: sincroniza e abre o dashboard
```

A lógica de cálculo fica isolada em `service`, sem entrada e saída, para ser testada sozinha. O acesso ao Hevy e à planilha passa por interfaces (`HevySource`, `SheetsGateway`), o que permite testar a sincronização inteira sem rede.

## Como executar

Precisa do **JDK 21** e do **Maven**. Nada mais: o dashboard é aberto por um servidor que vem no próprio programa.

### Ver o exemplo (sem conta nenhuma)

```bash
mvn package                                                # compila e roda os testes
java -jar target/hevy-dashboard-0.1.0-SNAPSHOT.jar serve   # abre o dashboard no navegador
```

Sem `site/data/dashboard.json`, o site mostra os dados de exemplo (um usuário fictício). O servidor escuta só em `localhost`, então só o seu computador consegue abri-lo.

### Ver os seus próprios treinos

O site publicado na internet mostra **sempre** o exemplo. Para ver os seus treinos, você roda o programa no seu computador: ele busca os dados no Hevy e gera `site/data/dashboard.json`, um arquivo que **fica só na sua máquina** (está no `.gitignore`). Se esse arquivo existe, o site o usa no lugar do exemplo.

1. **Chave do Hevy.** No app, em Configurações, gere uma chave da API (exige Hevy Pro).
2. **Google Cloud.** Crie um projeto, ative a *Google Sheets API*, crie uma conta de serviço, baixe a credencial em JSON e guarde-a **fora do Git** (a pasta `secrets/` já está no `.gitignore`).
3. **Planilha.** Crie uma planilha vazia e compartilhe com o e-mail da conta de serviço, como Editor. O ID é o trecho da URL entre `/d/` e `/edit`.
4. **Variáveis de ambiente** (no Windows, com `setx`; depois feche e abra o terminal):

   | Variável | Conteúdo |
   |---|---|
   | `HEVY_API_KEY` | a chave do Hevy |
   | `GOOGLE_APPLICATION_CREDENTIALS` | caminho do JSON da conta de serviço |
   | `SPREADSHEET_ID` | o ID da planilha |

5. **Abrir.** Dê dois cliques em **`abrir-dashboard.bat`** (Windows) ou rode:

   ```bash
   java -jar target/hevy-dashboard-0.1.0-SNAPSHOT.jar dashboard
   ```

   O programa sincroniza com o Hevy (só o que mudou desde a última vez) e abre o dashboard com os seus dados. Na primeira execução ele importa o histórico inteiro e cria, na planilha, as abas de configuração (`ajustes_manuais` e `config`) com valores iniciais que você pode editar.

Quando atualizar o código, rode `mvn package` de novo para gerar o jar novo.

| Comando | O que faz |
|---|---|
| `dashboard` | sincroniza com o Hevy e abre o dashboard (o do dia a dia) |
| `sync` | só sincroniza e gera `site/data/dashboard.json` |
| `serve [porta]` | só abre o dashboard (a porta padrão é 8080) |
| `sample` | regera os dados de exemplo em `site/data/sample.json` |
| `check` | testa a conexão com o Hevy e com a planilha |

## Testes

```bash
mvn test                      # testes do Java
cd site/tests && npm ci && npm test   # testes de navegador (precisa do Google Chrome)
```

- **Java:** testes unitários dos cálculos (com as rotinas de um usuário fictício e contas feitas à mão), da leitura da API contra um servidor local falso, da planilha e da sincronização completa contra versões falsas do Hevy e do Sheets. Nenhum teste usa a rede.
- **Navegador:** o site é aberto no Chrome sem janela, e o teste confere o layout, tooltips, a tabela equivalente de cada gráfico, os filtros (inclusive o período personalizado e os valores inválidos), tema, troca de aba, seleção de exercício e se nada estoura a largura de um celular.
- **CI:** o GitHub Actions roda os dois conjuntos, confere que o `site/data/sample.json` está em dia com o código e publica o site.

## Privacidade e segurança

- Os **dados reais ficam na máquina do dono**. O arquivo `site/data/dashboard.json` está no `.gitignore`, e o site publicado usa só os dados de exemplo. O workflow monta a pasta publicada por uma lista explícita de arquivos, para nada além do site e do exemplo chegar ao ar.
- **Nenhum segredo** vai para o repositório: a chave do Hevy e a credencial do Google são lidas de variáveis de ambiente, e o workflow nem precisa delas.
- A chave do Hevy nunca é escrita em log ou mensagem de erro.

## Decisões e aprendizados

- **A planilha é o armazenamento.** Guardar as séries brutas (e não só os totais) permite mudar as regras depois e recalcular tudo sem perder histórico.
- **Datas lidas sem formatação.** O Google Sheets devolve datas no formato do idioma da planilha, e reinterpretá-las pode trocar dia e mês. O programa lê o valor interno (um número de dias) e converte.
- **Limite de requisições do Google.** Uma sincronização faz mais de 30 chamadas, e três execuções seguidas estouraram a cota por minuto. O cliente agora repete com espera crescente.
- **Nomes de exercícios.** A API devolve os exercícios padrão em inglês, enquanto os treinos guardam o nome no idioma do app. Só os exercícios personalizados têm o nome atualizado pelo template (e é assim que renomear um exercício junta o histórico dele).
- **A meta de séries vem das rotinas, não de faixas fixas.** Assim ela acompanha o que você realmente planejou, e o programa não precisa de uma tabela de metas para manter.
- **Evolução sem 1RM.** Carga e repetições são o que se anota e o que se sente no treino; uma fórmula de estimativa só adicionava um número que não se usa. A comparação é feita com a série mais pesada de cada treino.
- **Dados reais acham erros que o exemplo não acha.** Séries com 101 repetições (erro de digitação) distorciam os recordes; exercícios recriados com o mesmo nome dividiam o histórico em dois.

## Código próprio e de terceiros

**Código próprio:** tudo em `src/` (cliente da API, modelos, cálculos, planilha, sincronização, exportação) e em `site/` (`index.html`, `css/`, `js/`, `tests/`).

**De terceiros:**

| O quê | Para quê | Licença |
|---|---|---|
| [Chart.js](https://www.chartjs.org/) 4.4.7 (copiado em `site/vendor/`) | gráficos do site | MIT |
| [Jackson](https://github.com/FasterXML/jackson) | leitura e escrita de JSON | Apache 2.0 |
| Google API Client e Google Sheets API (Java) | acesso à planilha | Apache 2.0 |
| [JUnit 5](https://junit.org/junit5/) | testes do Java | EPL 2.0 |
| [puppeteer-core](https://pptr.dev/) | testes de navegador | Apache 2.0 |
| [API do Hevy](https://api.hevyapp.com/docs/) | origem dos treinos | termos do Hevy |

O projeto foi desenvolvido com apoio de um assistente de IA (Claude), e o autor revisa e responde pelo código.

## Próximos passos

- **Versão com contas**, para outras pessoas conectarem o próprio Hevy. A API do Hevy não tem login OAuth: usa uma chave pessoal, então isso pede um servidor, um banco de dados e a guarda criptografada das chaves.
- RPE médio por exercício e força relativa (carga dividida pelo peso corporal), que dependem de dados que ainda não estão preenchidos no Hevy.

## Licença

[MIT](LICENSE)
