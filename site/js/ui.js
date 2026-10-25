// Componentes de interface: criação de elementos, cartões (com a versão em tabela de cada gráfico) e tooltip.
// Todo texto vindo dos dados entra no DOM por textContent / nós de texto, nunca por innerHTML.

export function el(tag, props = {}, ...children) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(props)) {
    if (value == null || value === false) continue;
    if (key === 'class') node.className = value;
    else if (key === 'text') node.textContent = value;
    else if (key === 'style') node.style.cssText = value;
    else if (key.startsWith('on')) node.addEventListener(key.slice(2), value);
    else node.setAttribute(key, value === true ? '' : value);
  }
  for (const child of children.flat()) {
    if (child == null || child === false) continue;
    node.append(child);
  }
  return node;
}

/**
 * Cartão com título e, opcionalmente, o botão que troca o gráfico pela tabela equivalente.
 *
 * @param body  (view, ctx) => void  desenha o conteúdo dentro de `view`; `ctx.register(chart)` guarda os gráficos
 *              para serem destruídos quando o cartão for redesenhado
 * @param table () => { headers: string[], rows: any[][], numeric?: number[] }  a mesma informação em tabela
 *
 * O cartão só pode ser desenhado depois de estar no documento: chame `card.render()` depois de inseri-lo.
 */
export function card({ title, subtitle, cls = 'col-12', body, table, id }) {
  const view = el('div');
  const toggle = table ? el('button', { class: 'view-toggle', type: 'button', 'aria-pressed': 'false', text: 'Ver tabela' }) : null;
  const section = el('section', { class: `card ${cls}`, id, 'aria-label': title },
    el('div', { class: 'card-head' },
      el('div', { class: 'titles' }, el('h2', { text: title }), subtitle ? el('p', { class: 'sub', text: subtitle }) : null),
      toggle),
    view);

  let charts = [];
  let showingTable = false;
  const ctx = { register: (chart) => { charts.push(chart); return chart; } };

  section.render = () => {
    charts.forEach((chart) => chart.destroy());
    charts = [];
    view.replaceChildren();
    if (showingTable && table) view.append(tableElement(table()));
    else body(view, ctx);
  };
  section.destroy = () => { charts.forEach((chart) => chart.destroy()); charts = []; };

  if (toggle) {
    toggle.addEventListener('click', () => {
      showingTable = !showingTable;
      toggle.textContent = showingTable ? 'Ver gráfico' : 'Ver tabela';
      toggle.setAttribute('aria-pressed', String(showingTable));
      section.render();
    });
  }
  return section;
}

export function tableElement({ headers, rows, numeric = [], onRow, tall = false }) {
  const head = el('tr', {}, headers.map((h, i) => el('th', { scope: 'col', class: numeric.includes(i) ? 'num' : '', text: h })));
  const body = rows.map((row, r) => {
    const tr = el('tr', onRow ? { class: 'clickable', tabindex: '0' } : {},
      row.map((cell, i) => (cell instanceof Node
        ? el('td', { class: numeric.includes(i) ? 'num' : '' }, cell)
        : el('td', { class: numeric.includes(i) ? 'num' : '', text: cell == null ? '' : String(cell) }))));
    if (onRow) {
      tr.addEventListener('click', () => onRow(r));
      tr.addEventListener('keydown', (event) => { if (event.key === 'Enter') onRow(r); });
    }
    return tr;
  });
  return el('div', { class: tall ? 'table-wrap tall' : 'table-wrap' }, el('table', { class: 'data' }, el('thead', {}, head), el('tbody', {}, body)));
}

/** Legenda: chaves de cor ao lado do texto (o texto nunca usa a cor da série). */
export function legend(items) {
  return el('ul', { class: 'legend' }, items.map((item) =>
    el('li', {}, el('span', { class: `key ${item.shape || 'box'}`, style: item.color ? `background:${item.color}` : '' }), item.label)));
}

export function emptyMessage(text) {
  return el('p', { class: 'empty', text });
}

// ---------- tooltip ----------
//
// Um mouse gera centenas de eventos de movimento por segundo. Por isso o conteúdo do tooltip só é reescrito
// quando o mouse passa para outro elemento, e a posição é aplicada uma vez por quadro de tela. Reescrever o
// conteúdo a cada movimento custa layout e, numa região "viva" (aria-live), vira um aviso para tecnologias
// assistivas a cada vez, o que pode travar o navegador. O tooltip é só um reforço para o mouse: o mesmo conteúdo
// está no aria-label do elemento e na tabela equivalente, então ele fica escondido dessas tecnologias.

const tip = () => document.getElementById('tip');

let shownFor = null;   // o elemento dono do conteúdo que está no tooltip agora
let size = { w: 0, h: 0 };
let pending = null;    // a última posição pedida, aplicada no próximo quadro
let frame = 0;

/** rows: [{ title }, { label, value }, { muted }] */
function fill(rows) {
  const box = tip();
  box.replaceChildren(...rows.map((row) => {
    if (row.title) return el('div', { class: 't-title', text: row.title });
    if (row.muted) return el('div', { class: 't-muted', text: row.muted });
    return el('div', { class: 't-row' }, el('span', { text: row.label }), el('span', { text: row.value }));
  }));
  size = { w: box.offsetWidth, h: box.offsetHeight }; // medido uma vez por conteúdo, não por movimento
}

function place() {
  frame = 0;
  if (!pending) return;
  const pad = 14;
  const { x: pointerX, y: pointerY } = pending;
  let x = pointerX + pad;
  let y = pointerY + pad;
  if (x + size.w > window.innerWidth - 8) x = pointerX - size.w - pad;
  if (y + size.h > window.innerHeight - 8) y = pointerY - size.h - pad;
  const box = tip();
  box.style.left = `${Math.max(8, x)}px`;
  box.style.top = `${Math.max(8, y)}px`;
  pending = null;
}

/**
 * Mostra o tooltip de `owner` perto de `point`. `rowsFn` só é chamada (e o conteúdo só é reescrito)
 * quando o dono muda.
 */
export function showTip(owner, point, rowsFn) {
  if (shownFor !== owner) {
    fill(rowsFn());
    shownFor = owner;
  }
  tip().classList.add('on');
  pending = point;
  if (!frame) frame = requestAnimationFrame(place);
}

export function hideTip() {
  shownFor = null;
  pending = null;
  if (frame) {
    cancelAnimationFrame(frame);
    frame = 0;
  }
  tip().classList.remove('on');
}

/** Liga o tooltip a um elemento: mouse e foco do teclado mostram o mesmo conteúdo. */
export function attachTip(node, rowsFn) {
  node.addEventListener('pointermove', (event) => showTip(node, { x: event.clientX, y: event.clientY }, rowsFn));
  node.addEventListener('pointerleave', hideTip);
  node.addEventListener('focus', () => {
    const rect = node.getBoundingClientRect();
    showTip(node, { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 }, rowsFn);
  });
  node.addEventListener('blur', hideTip);
}
