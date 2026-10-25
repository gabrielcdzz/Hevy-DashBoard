// Testes de navegador do dashboard. Abre o site no Chrome (sem janela) e confere o comportamento:
// tooltips, tabela equivalente de cada gráfico, filtros, tema, abas e seleção de exercício.
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

  await page.goto(base, { waitUntil: 'networkidle0' });
  await wait(300);

  check('o site carrega com os dados de exemplo', await page.$eval('.sample-banner', (n) => n.textContent.includes('Dados de exemplo')));

  // 1. tooltips feitos à mão
  await page.hover('.vrow');
  await wait(100);
  let tip = await page.$eval('#tip', (n) => ({ on: n.classList.contains('on'), text: n.textContent }));
  check('tooltip da linha de volume aparece', tip.on && tip.text.includes('Diretas'), tip.text.slice(0, 60));

  await (await page.$('.cal-grid .cell.l3')).hover();
  await wait(100);
  tip = await page.$eval('#tip', (n) => ({ on: n.classList.contains('on'), text: n.textContent }));
  check('tooltip do calendário aparece', tip.on && /Treinos/.test(tip.text), tip.text.slice(0, 60));

  await page.mouse.move(5, 5);
  await wait(100);
  check('tooltip some ao tirar o mouse', await page.$eval('#tip', (n) => !n.classList.contains('on')));

  // 2. tooltip do gráfico (Chart.js)
  const canvas = await page.$('canvas');
  const box = await canvas.boundingBox();
  await page.mouse.move(box.x + box.width * 0.5, box.y + box.height * 0.6);
  await wait(200);
  const chartTip = await page.evaluate(() => {
    const chart = Chart.getChart(document.querySelector('canvas'));
    return chart.tooltip.opacity > 0 ? chart.tooltip.title.join(' ') : '';
  });
  check('tooltip do gráfico mostra a semana', /Semana de \d\d\/\d\d\/\d{4}/.test(chartTip), chartTip);

  // 3. tabela equivalente do gráfico
  const toggle = await page.$('.card[aria-label="Séries por semana"] .view-toggle');
  await toggle.click();
  check('"Ver tabela" mostra a tabela', !!(await page.$('.card[aria-label="Séries por semana"] table.data')));
  await toggle.click();
  check('"Ver gráfico" volta ao gráfico', !!(await page.$('.card[aria-label="Séries por semana"] canvas')));

  // 4. todo gráfico tem a tabela equivalente
  const semTabela = await page.$$eval('#panel .card', (cards) => cards
    .filter((c) => c.querySelector('canvas') && !c.querySelector('.view-toggle'))
    .map((c) => c.getAttribute('aria-label')));
  check('todo cartão com gráfico oferece a tabela', semTabela.length === 0, semTabela.join(', '));

  // 5. filtros
  await page.select('#f-muscle', 'biceps');
  await wait(400);
  const titles = await page.$$eval('#panel h2', (nodes) => nodes.map((n) => n.textContent));
  check('filtro de músculo muda o gráfico de séries', titles.some((t) => t.includes('Séries de Bíceps por semana')));
  check('filtro de músculo deixa só uma linha de volume', (await page.$$eval('.vrow', (n) => n.length)) === 1);
  await page.select('#f-muscle', 'all');
  await wait(300);

  await page.select('#f-period', '0');
  await wait(400);
  const weeksShown = await page.evaluate(() => Chart.getChart(document.querySelector('canvas')).data.labels.length);
  check('período "todo o histórico" mostra todas as semanas', weeksShown > 55, `${weeksShown} semanas`);
  await page.select('#f-period', '12');
  await wait(300);

  // 6. tema
  await page.click('#theme-btn');
  await wait(300);
  check('botão de tema alterna para escuro', (await page.evaluate(() => document.documentElement.dataset.theme)) === 'dark');
  check('o fundo muda com o tema', (await page.evaluate(() => getComputedStyle(document.body).backgroundColor)) === 'rgb(13, 13, 13)');
  await page.click('#theme-btn');
  await wait(200);

  // 7. exercícios
  await page.click('[data-tab="exercicios"]');
  await wait(400);
  check('filtro de exercício aparece na aba de exercícios', await page.$eval('#f-exercise', (n) => !n.closest('.field').hidden));
  const evolutionTitle = () => page.evaluate(() => [...document.querySelectorAll('#panel h2')].find((h) => h.textContent.startsWith('Evolução'))?.textContent);
  const before = await evolutionTitle();
  const tableRows = await page.$$('table.data tbody tr');
  await tableRows[2].click();
  await wait(400);
  const after = await evolutionTitle();
  check('clicar em uma linha troca o exercício da evolução', !!after && after !== before, `${before} → ${after}`);
  const selected = await page.$eval('#f-exercise', (n) => n.options[n.selectedIndex].text);
  check('o seletor de exercício acompanha o clique', !!after && after.includes(selected), selected);

  // 8. análise
  await page.click('[data-tab="analise"]');
  await wait(400);
  check('filtro de exercício some fora da aba de exercícios', await page.$eval('#f-exercise', (n) => n.closest('.field').hidden));
  check('aba de análise tem 2 gráficos', (await page.$$('canvas')).length === 2);

  // 9. celular: nada pode estourar a largura da tela
  await page.setViewport({ width: 390, height: 800 });
  for (const tab of ['visao-geral', 'exercicios', 'analise']) {
    await page.click(`[data-tab="${tab}"]`);
    await wait(400);
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth);
    check(`aba ${tab} cabe na tela do celular`, !overflow);
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
