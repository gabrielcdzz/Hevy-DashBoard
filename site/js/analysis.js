// Aba "Análise": frequência por músculo, tonelagem, equilíbrio do treino e séries por faixa de repetição.
import { el, card, legend, attachTip } from './ui.js';
import { visibleWeeks } from './data.js';
import { mountChart, color, alpha, columnStyle, weekTitle } from './charts.js';
import { num, num1, tons, fmtDate } from './format.js';

export function buildAnalysis(model, state) {
  const weeks = visibleWeeks(model, state);
  return [
    frequencyCard(model, state, weeks),
    tonnageCard(model, weeks),
    balanceCard(weeks),
    repRangesCard(model, weeks),
  ];
}

// ---------- frequência por músculo ----------

function frequencyCard(model, state, weeks) {
  const closed = weeks.filter((w) => !w.inProgress);
  const muscles = model.muscles.filter((m) => state.muscle === 'all' || m === state.muscle);
  const rows = muscles.map((muscle) => {
    const total = closed.reduce((sum, w) => sum + model.volumeAt(w.week, muscle).sessions, 0);
    return { muscle, perWeek: closed.length ? total / closed.length : 0 };
  });

  return card({
    title: 'Frequência por músculo',
    subtitle: `Quantos dias por semana cada músculo recebeu série direta, em média nas ${closed.length} semanas fechadas do período`,
    cls: 'col-6',
    body(view) {
      const list = el('div', { class: 'rows', role: 'list' });
      // A escala acompanha o maior valor (no máximo 7 dias), senão as barras ficam minúsculas.
      const scaleMax = Math.min(7, Math.ceil(Math.max(0, ...rows.map((r) => r.perWeek))) + 1);
      for (const r of rows) {
        const unit = r.perWeek === 1 ? 'dia' : 'dias';
        const row = el('div', { class: 'vrow', role: 'listitem', tabindex: '0', 'aria-label': `${model.label(r.muscle)}: ${num1(r.perWeek)} ${unit} por semana` },
          el('span', { class: 'name', text: model.label(r.muscle) }),
          el('div', { class: 'track' },
            el('span', { class: 'base' }),
            el('span', { class: 'bar', style: `width:${(r.perWeek / scaleMax) * 100}%` })),
          el('div', { class: 'nums' }, el('span', {}, el('strong', { text: num1(r.perWeek) }), ` ${unit} por semana`)));
        attachTip(row, () => [{ title: model.label(r.muscle) }, { label: 'Dias por semana', value: num1(r.perWeek) }]);
        list.append(row);
      }
      view.append(list);
    },
    table: () => ({
      headers: ['Músculo', 'Dias por semana (média)'],
      numeric: [1],
      rows: rows.map((r) => [model.label(r.muscle), num1(r.perWeek)]),
    }),
  });
}

// ---------- tonelagem ----------

function tonnageCard(model, weeks) {
  return card({
    title: 'Volume em quilos (tonelagem)',
    subtitle: 'Soma de carga × repetições das séries de trabalho com carga, em toneladas por semana',
    cls: 'col-6',
    body(view, ctx) {
      mountChart(view, ctx, {
        type: 'bar',
        labels: weeks.map((w) => w.week),
        datasets: [{
          label: 'Tonelagem',
          data: weeks.map((w) => w.tonnageKg / 1000),
          backgroundColor: weeks.map((w) => (w.inProgress ? alpha(color('--s1'), 0.45) : color('--s1'))),
          keyColor: color('--s1'),
          ...columnStyle,
        }],
        height: 'tall',
        options: {
          scales: { y: { ticks: { callback: (v) => `${v} t` } } },
          plugins: {
            tooltip: {
              callbacks: {
                title: (items) => weekTitle(items[0].label, weeks[items[0].dataIndex].inProgress),
                label: (item) => ` ${tons(weeks[item.dataIndex].tonnageKg)} (${num(weeks[item.dataIndex].tonnageKg)} kg)`,
              },
            },
          },
        },
        plugins: { phases: model.data.phases },
        label: 'Tonelagem por semana, em toneladas',
      });
    },
    table: () => ({
      headers: ['Semana', 'Tonelagem (kg)', 'Treinos'],
      numeric: [1, 2],
      rows: weeks.map((w) => [fmtDate(w.week), num(w.tonnageKg), w.workouts]),
    }),
  });
}

// ---------- equilíbrio do treino ----------

function balanceCard(weeks) {
  const sum = (key) => weeks.reduce((total, w) => total + w[key], 0);
  const push = sum('push');
  const pull = sum('pull');
  const legs = sum('legs');
  const core = sum('core');

  const movement = [
    { label: 'Push (peito, ombros, tríceps)', value: push, color: '--s1' },
    { label: 'Pull (costas, bíceps, antebraço)', value: pull, color: '--s2' },
    { label: 'Pernas', value: legs, color: '--s3' },
    { label: 'Core', value: core, color: '--s4' },
  ];
  const bodyHalves = [
    { label: 'Superior (push + pull)', value: push + pull, color: '--s1' },
    { label: 'Inferior (pernas)', value: legs, color: '--s3' },
  ];

  return card({
    title: 'Equilíbrio do treino',
    subtitle: 'Séries diretas do período, pelo músculo principal de cada exercício; o ombro conta como push',
    cls: 'col-6',
    body(view) {
      view.append(stackBlock('Por tipo de movimento', movement), stackBlock('Superior contra inferior (sem o core)', bodyHalves));
    },
    table: () => ({
      headers: ['Grupo', 'Séries', '% do total'],
      numeric: [1, 2],
      rows: [...movement, ...bodyHalves].map((item, i) => {
        const total = i < movement.length ? push + pull + legs + core : push + pull + legs;
        return [item.label, num(item.value), `${num1(total ? (item.value / total) * 100 : 0)}%`];
      }),
    }),
  });
}

/** Uma barra empilhada com a legenda em texto (o valor nunca depende só da cor). */
function stackBlock(title, items) {
  const total = items.reduce((sum, i) => sum + i.value, 0);
  const bar = el('div', { class: 'stack', role: 'img', 'aria-label': `${title}: ${items.map((i) => `${i.label} ${num(i.value)}`).join(', ')}` });
  const labels = el('ul', { class: 'stack-legend' });
  for (const item of items) {
    const percent = total ? (item.value / total) * 100 : 0;
    const segment = el('span', { style: `flex:${Math.max(item.value, 0)};background:${color(item.color)}` });
    attachTip(segment, () => [{ title: item.label }, { label: 'Séries', value: num(item.value) }, { label: 'Parte do total', value: `${num1(percent)}%` }]);
    if (item.value > 0) bar.append(segment);
    labels.append(el('li', {},
      el('span', { class: 'key box', style: `background:${color(item.color)}` }),
      `${item.label}: `, el('b', { text: `${num(item.value)} (${num1(percent)}%)` })));
  }
  return el('div', {}, el('h3', { class: 'sub', style: 'margin:8px 0 0;font-size:12px', text: title }), bar, labels);
}

// ---------- faixas de repetição ----------

function repRangesCard(model, weeks) {
  const low = weeks.reduce((s, w) => s + w.repsLow, 0);
  const mid = weeks.reduce((s, w) => s + w.repsMid, 0);
  const high = weeks.reduce((s, w) => s + w.repsHigh, 0);
  const total = low + mid + high;
  const pct = (v) => num1(total ? (v / total) * 100 : 0);
  const bands = [
    { label: '1 a 5 repetições (força)', key: 'repsLow', value: low, color: '--ord-1' },
    { label: '6 a 12 repetições (hipertrofia)', key: 'repsMid', value: mid, color: '--ord-2' },
    { label: '13 ou mais repetições (resistência)', key: 'repsHigh', value: high, color: '--ord-3' },
  ];

  return card({
    title: 'Séries por faixa de repetição',
    subtitle: 'Séries de trabalho por semana, separadas pelo número de repetições',
    cls: 'col-6',
    body(view, ctx) {
      view.append(el('ul', { class: 'stack-legend' }, bands.map((b) => el('li', {},
        el('span', { class: 'key box', style: `background:${color(b.color)}` }),
        `${b.label}: `, el('b', { text: `${num(b.value)} (${pct(b.value)}%)` })))));
      mountChart(view, ctx, {
        type: 'bar',
        labels: weeks.map((w) => w.week),
        datasets: bands.map((b) => ({
          label: b.label.split(' (')[0],
          data: weeks.map((w) => w[b.key]),
          backgroundColor: color(b.color),
          keyColor: color(b.color),
          borderColor: color('--surface'),
          borderWidth: 1,
          borderSkipped: false,
          maxBarThickness: 24,
        })),
        options: {
          scales: { x: { stacked: true }, y: { stacked: true } },
          plugins: {
            tooltip: {
              callbacks: {
                title: (items) => weekTitle(items[0].label, weeks[items[0].dataIndex].inProgress),
              },
            },
          },
        },
        plugins: { phases: model.data.phases },
        label: 'Séries por faixa de repetição, por semana',
      });
    },
    table: () => ({
      headers: ['Semana', '1 a 5', '6 a 12', '13 ou mais'],
      numeric: [1, 2, 3],
      rows: weeks.map((w) => [fmtDate(w.week), w.repsLow, w.repsMid, w.repsHigh]),
    }),
  });
}
