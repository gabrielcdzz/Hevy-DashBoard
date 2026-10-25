// Testes de navegador do dashboard. Abre o site no Chrome (sem janela) e confere o comportamento:
// layout, tooltips, tabela equivalente de cada gráfico, filtros (inclusive o período personalizado),
// tema, abas e seleção de exercício.
//
// Uso: npm test        (dentro de site/tests; precisa do Google Chrome instalado)
// Variáveis: CHROME_PATH aponta para o executável do Chrome, se ele não estiver no lugar padrão.
//
// O teste sempre usa os dados de EXEMPLO: o arquivo data/dashboard.json (dados reais, só na máquina do
// dono) é escondido do servidor de teste, para o resultado ser o mesmo em qualquer computador.

const http = require('http');
const fs = require('fs');
const path = require('path');
const puppeteer = require('puppeteer-core');

const siteDir = path.resolve(__dirname, '..');
const TYPES = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.json': 'application/json; charset=utf-8' };
const HIDDEN = new Set(['/data/dashboard.json']);

const server = http.createServer((req, res) => {
  const url = decodeURIComponent(req.url.split('?')[0]);
  const file = path.join(siteDir, url === '/' ? '/index.html' : url);
  if (HIDDEN.has(url) || !file.startsWith(siteDir)) { res.writeHead(404); res.end('não encontrado'); return; }
  fs.readFile(file, (err, data) => {
    if (err) { res.writeHead(404); res.end('não encontrado'); return; }
    res.writeHead(200, { 'Content-Type': TYPES[path.extname(file)] || 'application/octet-stream' });
    res.end(data);
  });
});

const results = [];
const check = (name, ok, extra = '') => {
  results.push(ok);
  console.log(`${ok ? 'OK    ' : 'FALHOU'} ${name}${extra ? ` — ${extra}` : ''}`);
};
const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function main() {
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const base = `http://localhost:${server.address().port}/`;

  const browser = await puppeteer.launch({
    headless: 'new',
    ...(process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : { channel: 'chrome' }),
    args: process.env.CI ? ['--no-sandbox'] : [],
  });
  const page = await browser.newPage();
  await page.setViewport({ width: 1280, height: 900 });
  await page.emulateMediaFeatures([{ name: 'prefers-color-scheme', value: 'light' }]);

  // O 404 do dashboard.json é esperado: ele não existe no site publicado, e o site cai nos dados de exemplo.
  const problems = [];
  page.on('console', (msg) => { if (msg.type() === 'error' && !msg.text().includes('404')) problems.push(msg.text()); });
  page.on('pageerror', (error) => problems.push(error.message));

  const chartLabels = () => page.evaluate(() => Chart.getChart(document.querySelector('canvas')).data.labels.length);
  const pageText = () => page.evaluate(() => document.body.innerText);
  const titles = () => page.$$eval('#panel h2', (nodes) => nodes.map((n) => n.textContent));

  await page.goto(base, { waitUntil: 'networkidle0' });
  await wait(300);

  // ---------- visão geral: layout ----------
  check('o site carrega com os dados de exemplo', await page.$eval('.sample-banner', (n) => n.textContent.includes('Dados de exemplo')));
  check('a etiqueta do topo diz que são dados de exemplo', await page.$eval('#source-badge', (n) => n.textContent === 'Dados de exemplo'));
  check('há só duas abas: Visão geral e Exercícios', JSON.stringify(await page.$$eval('.tab', (t) => t.map((n) => n.textContent))) === '["Visão geral","Exercícios"]');

  const ordem = await page.evaluate(() => {
    const filhos = [...document.querySelectorAll('#panel > .grid > *')];
    return {
      cartoes: filhos.findIndex((n) => n.classList.contains('grid') && n.querySelector('.hero')),
      filtros: filhos.findIndex((n) => n.classList.contains('filters')),
      volume: filhos.findIndex((n) => n.getAttribute('aria-label') === 'Volume por músculo na semana'),
    };
  });
  check('os filtros ficam abaixo dos 5 cartões da semana e acima do restante',
    ordem.cartoes === 0 && ordem.filtros === 1 && ordem.volume === 2, JSON.stringify(ordem));
  check('a Visão geral tem 5 cartões de resumo', (await page.$$('.hero, .tile')).length === 5);
  check('o calendário de treinos não existe mais', !(await page.$('.cal-grid')) && !(await pageText()).includes('Calendário'));
  check('o filtro de exercício está escondido na Visão geral', await page.$eval('#f-exercise', (n) => n.closest('.field').hidden));
  check('o campo de semanas está escondido enquanto o período não é personalizado', await page.$eval('#f-weeks', (n) => n.closest('.field').hidden));

  // ---------- volume por músculo contra o planejado ----------
  const linha = await page.$eval('.vrow', (n) => n.textContent);
  check('o volume mostra o planejado e a situação', /\d+ de \d+ diretas/.test(linha) && /planejado|Falta|Faltam/.test(linha), linha.slice(0, 80));
  check('há uma marca do planejado em cada músculo das rotinas', (await page.$$('.vrow .track .planned')).length >= 8);

  // ---------- tooltips ----------
  await page.hover('.vrow');
  await wait(100);
  let tip = await page.$eval('#tip', (n) => ({ on: n.classList.contains('on'), text: n.textContent }));
  check('tooltip da linha de volume aparece', tip.on && tip.text.includes('Planejado nas rotinas'), tip.text.slice(0, 60));
  await page.mouse.move(5, 5);
  await wait(100);
  check('tooltip some ao tirar o mouse', await page.$eval('#tip', (n) => !n.classList.contains('on')));

  const canvas = await page.$('canvas');
  await canvas.evaluate((n) => n.scrollIntoView({ block: 'center' })); // o gráfico fica abaixo da dobra
  await wait(100);
  const box = await canvas.boundingBox();
  await page.mouse.move(box.x + box.width * 0.5, box.y + box.height * 0.6);
  await wait(200);
  const chartTip = await page.evaluate(() => {
    const chart = Chart.getChart(document.querySelector('canvas'));
    return chart.tooltip.opacity > 0 ? chart.tooltip.title.join(' ') : '';
  });
  check('tooltip do gráfico mostra a semana', /Semana de \d\d\/\d\d\/\d{4}/.test(chartTip), chartTip);

  // ---------- tabela equivalente ----------
  const toggle = await page.$('.card[aria-label="Séries por semana"] .view-toggle');
  await toggle.click();
  check('"Ver tabela" mostra a tabela', !!(await page.$('.card[aria-label="Séries por semana"] table.data')));
  await toggle.click();
  check('"Ver gráfico" volta ao gráfico', !!(await page.$('.card[aria-label="Séries por semana"] canvas')));
  const semTabela = await page.$$eval('#panel .card', (cards) => cards
    .filter((c) => c.querySelector('canvas') && !c.querySelector('.view-toggle'))
    .map((c) => c.getAttribute('aria-label')));
  check('todo cartão com gráfico oferece a tabela', semTabela.length === 0, semTabela.join(', '));

  // ---------- filtro de período ----------
  const opcoes = await page.$$eval('#f-period option', (o) => o.map((n) => n.value));
  check('o período tem 4 semanas e a opção personalizada, além das que já existiam',
    JSON.stringify(opcoes) === JSON.stringify(['4', '8', '12', '26', '52', '0', 'custom']), opcoes.join(','));

  await page.select('#f-period', '4');
  await wait(400);
  check('período de 4 semanas mostra 4 semanas', (await chartLabels()) === 4, `${await chartLabels()} semanas`);

  await page.select('#f-period', 'custom');
  await wait(300);
  check('"Personalizado" faz o campo de semanas aparecer', await page.$eval('#f-weeks', (n) => !n.closest('.field').hidden));
  await page.$eval('#f-weeks', (n) => { n.value = '7'; n.dispatchEvent(new Event('change', { bubbles: true })); });
  await wait(400);
  check('período personalizado de 7 semanas mostra 7 semanas', (await chartLabels()) === 7, `${await chartLabels()} semanas`);

  for (const invalido of ['0', '-3', 'abc', '9999', '']) {
    await page.$eval('#f-weeks', (n, v) => { n.value = v; n.dispatchEvent(new Event('change', { bubbles: true })); }, invalido);
    await wait(150);
  }
  check('valores inválidos de semanas são recusados e o anterior continua', (await chartLabels()) === 7);
  check('o campo volta ao último valor válido', (await page.$eval('#f-weeks', (n) => n.value)) === '7');

  await page.select('#f-period', '0');
  await wait(400);
  check('"todo o histórico" mostra todas as semanas', (await chartLabels()) > 55, `${await chartLabels()} semanas`);
  check('o campo de semanas some de novo fora do personalizado', await page.$eval('#f-weeks', (n) => n.closest('.field').hidden));
  await page.select('#f-period', '12');
  await wait(300);

  // ---------- filtro de músculo ----------
  await page.select('#f-muscle', 'biceps');
  await wait(400);
  const t = await titles();
  check('filtro de músculo muda o gráfico de séries', t.some((x) => x.includes('Séries de Bíceps por semana')));
  check('filtro de músculo também filtra a variação semanal', t.some((x) => x.includes('Variação semanal: Bíceps')), t.join(' | ').slice(0, 140));
  check('filtro de músculo deixa só uma linha de volume', (await page.$$eval('.vrow', (n) => n.length)) === 1);
  check('os 5 cartões da semana não mudam com o filtro', (await page.$$('.hero, .tile')).length === 5);
  await page.select('#f-muscle', 'all');
  await wait(300);

  // ---------- tema ----------
  await page.click('#theme-btn');
  await wait(300);
  check('botão de tema alterna para escuro', (await page.evaluate(() => document.documentElement.dataset.theme)) === 'dark');
  check('o fundo muda com o tema', (await page.evaluate(() => getComputedStyle(document.body).backgroundColor)) === 'rgb(13, 13, 13)');
  await page.click('#theme-btn');
  await wait(200);

  const textoVisaoGeral = await pageText();
  check('a Visão geral não fala de 1RM, cutting ou bulking', !/1RM|cutting|bulking/i.test(textoVisaoGeral));

  // ---------- exercícios ----------
  await page.click('[data-tab="exercicios"]');
  await wait(400);
  check('o filtro de exercício aparece na aba de exercícios', await page.$eval('#f-exercise', (n) => !n.closest('.field').hidden));
  const primeiroFilho = await page.evaluate(() => document.querySelector('#panel > .grid > *').classList.contains('filters'));
  check('na aba de exercícios os filtros ficam no topo', primeiroFilho);

  const tExercicios = await titles();
  check('a aba mostra o que mudou do treino anterior para o último', tExercicios.some((x) => x.startsWith('O que mudou do treino anterior')));
  const colunas = await page.$$eval('.change-col h3', (n) => n.map((h) => h.textContent.replace(/\d+$/, '').trim()));
  check('as três listas de mudança existem', JSON.stringify(colunas) === JSON.stringify(['Aumentou a carga', 'Mesma carga, mudou as repetições', 'Diminuiu a carga']), colunas.join(' | '));
  check('o alerta de exercícios sem evolução continua', tExercicios.some((x) => /sem evolução/.test(x)));
  check('a evolução tem dois gráficos, carga e repetições', (await page.$$('canvas')).length === 2);
  const textoExercicios = await pageText();
  check('a aba de exercícios não fala de 1RM', !/1RM|1 RM|one rep/i.test(textoExercicios));

  const evolucao = () => page.evaluate(() => [...document.querySelectorAll('#panel h2')].find((h) => h.textContent.startsWith('Evolução'))?.textContent);
  const antes = await evolucao();
  const linhas = await page.$$('table.data tbody tr');
  await linhas[2].click();
  await wait(400);
  const depois = await evolucao();
  check('clicar em uma linha troca o exercício da evolução', !!depois && depois !== antes, `${antes} → ${depois}`);
  const selecionado = await page.$eval('#f-exercise', (n) => n.options[n.selectedIndex].text);
  check('o seletor de exercício acompanha o clique', !!depois && depois.includes(selecionado), selecionado);

  // Mudar de aba e voltar mantém o que foi escolhido nos filtros.
  await page.click('[data-tab="visao-geral"]');
  await wait(300);
  check('voltar para a Visão geral mantém o período escolhido', (await page.$eval('#f-period', (n) => n.value)) === '12');

  // ---------- celular ----------
  await page.setViewport({ width: 390, height: 800 });
  for (const tab of ['visao-geral', 'exercicios']) {
    await page.click(`[data-tab="${tab}"]`);
    await wait(400);
    const estoura = await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth);
    check(`aba ${tab} cabe na tela do celular`, !estoura);
  }

  check('sem erros no console', problems.length === 0, problems.join(' | '));
  const passed = results.filter(Boolean).length;
  console.log(`\n${passed}/${results.length} verificações passaram`);
  await browser.close();
  server.close();
  process.exit(passed === results.length ? 0 : 1);
}

main().catch((error) => {
  console.error('FALHOU', error);
  process.exit(1);
});
