// Aba "Exercícios": o que mudou do treino anterior para o último, o alerta de exercícios sem evolução,
// a evolução de um exercício (carga e repetições) e os recordes. Tudo em carga e repetições, sem 1RM.
import { el, card, tableElement } from './ui.js';
import { visibleWeeks, exercisesFor, weeklyExercise } from './data.js';
import { mountChart, color, columnStyle, weekTitle } from './charts.js';
import { num, num1, kg, fmtDate, fmtDay, fmtDateShort } from './format.js';

/** Tendência pela janela de semanas: subir a carga, ou as repetições com a mesma carga, é evolução. */
const TREND = {
  progressing: { icon: '▲', text: 'Evoluindo', cls: 'within' },
  stagnant: { icon: '!', text: 'Sem evolução', cls: 'warn' },
  inactive: { icon: '–', text: 'Fora do treino', cls: '' },
  no_baseline: { icon: '·', text: 'Sem base de comparação', cls: '' },
};

const PR_TYPE = {
  weight: { label: 'Maior carga', format: (v) => kg(v) },
  reps: { label: 'Mais repetições', format: (v) => `${num(v)} reps` },
};

export function buildExercises(model, state, actions) {
  const list = exercisesFor(model, state);
  const selected = model.exerciseById.get(state.exercise);
  return [
    actions.filters,
    lastWorkoutCard(model, state, list),
    stagnationCard(model, list),
    evolutionCard(model, state, selected),
    prCard(model, list),
    recordsCard(model, list, actions),
  ];
}

const formatSet = (point) => `${kg(point.weightKg)} × ${num(point.reps)}`;

function trendBadge(progress) {
  const t = TREND[progress];
  return el('span', { class: `status ${t.cls}` }, el('span', { 'aria-hidden': 'true', text: t.icon }), t.text);
}

// ---------- o que mudou do treino anterior para o último ----------

/** Descreve a mudança de um treino para o outro: o ícone, o texto curto e o detalhe com carga ou repetições. */
function describeChange(change) {
  const { previous, current } = change;
  switch (change.type) {
    case 'load_up':
      return { icon: '▲', up: true, label: 'Subiu a carga', detail: `${kg(previous.weightKg)} → ${kg(current.weightKg)}${previous.reps !== current.reps ? ` · repetições ${previous.reps} → ${current.reps}` : ''}` };
    case 'load_down':
      return { icon: '▼', up: false, label: 'Diminuiu a carga', detail: `${kg(previous.weightKg)} → ${kg(current.weightKg)}${previous.reps !== current.reps ? ` · repetições ${previous.reps} → ${current.reps}` : ''}` };
    case 'reps_up':
      return { icon: '▲', up: true, label: 'Mais repetições', detail: `${kg(current.weightKg)} · ${previous.reps} → ${current.reps} repetições` };
    case 'reps_down':
      return { icon: '▼', up: false, label: 'Menos repetições', detail: `${kg(current.weightKg)} · ${previous.reps} → ${current.reps} repetições` };
    default:
      return { icon: '=', up: false, label: 'Sem mudança', detail: formatSet(current) };
  }
}

function lastWorkoutCard(model, state, list) {
  const weeks = visibleWeeks(model, state);
  const since = weeks.length ? weeks[0].week : '0000-00-00';
  // Só os exercícios das rotinas atuais que foram treinados dentro do período escolhido.
  const eligible = list
    .filter((e) => e.inRoutine && e.lastChange && e.lastTrained >= since)
    .sort((a, b) => a.name.localeCompare(b.name, 'pt-BR'));

  const loadUp = eligible.filter((e) => e.lastChange.type === 'load_up');
  const reps = eligible.filter((e) => e.lastChange.type === 'reps_up' || e.lastChange.type === 'reps_down');
  const loadDown = eligible.filter((e) => e.lastChange.type === 'load_down');
  const same = eligible.filter((e) => e.lastChange.type === 'same');

  const column = (title, items) => el('section', { class: 'change-col' },
    el('h3', {}, title, el('span', { class: 'count', text: String(items.length) })),
    items.length === 0
      ? el('p', { class: 'empty', text: 'Nenhum exercício.' })
      : el('ul', { class: 'alert-list' }, items.map((e) => {
        const c = describeChange(e.lastChange);
        return el('li', {},
          el('span', { class: `status ${c.up ? 'within' : ''}`, 'aria-hidden': 'true', text: c.icon }),
          el('span', { class: 'nm', text: e.name }),
          el('span', { class: 'det', text: c.detail }),
          el('span', { class: 'det muted', text: `${fmtDay(e.lastChange.previous.date)} → ${fmtDay(e.lastChange.current.date)}` }));
      })));

  return card({
    title: 'O que mudou do treino anterior para o último',
    subtitle: 'Compara a série mais pesada do último treino de cada exercício das rotinas com a do treino anterior. A carga vem primeiro: se ela subiu, é "subiu a carga", mesmo com menos repetições.',
    cls: 'col-12',
    body(view) {
      view.append(el('div', { class: 'cols-3' },
        column('Aumentou a carga', loadUp),
        column('Mesma carga, mudou as repetições', reps),
        column('Diminuiu a carga', loadDown)));
      view.append(el('p', { class: 'sub', style: 'margin-top:12px',
        text: same.length === 0 ? 'Todos os exercícios mudaram de um treino para o outro.'
          : `Sem mudança de um treino para o outro (${same.length}): ${same.map((e) => e.name).join(', ')}.` }));
    },
    table: () => ({
      headers: ['Exercício', 'Treino anterior', 'Último treino', 'O que mudou'],
      rows: eligible.map((e) => [e.name,
        `${fmtDateShort(e.lastChange.previous.date)}: ${formatSet(e.lastChange.previous)}`,
        `${fmtDateShort(e.lastChange.current.date)}: ${formatSet(e.lastChange.current)}`,
        describeChange(e.lastChange).label]),
    }),
  });
}

// ---------- exercícios sem evolução (janela de semanas) ----------

function stagnationCard(model, list) {
  const weeks = model.data.config.stagnationWeeks;
  const stagnant = list.filter((e) => e.inRoutine && e.trend.progress === 'stagnant')
    .sort((a, b) => a.name.localeCompare(b.name, 'pt-BR'));
  const title = stagnant.length === 0
    ? 'Nenhum exercício das rotinas está sem evolução'
    : `${stagnant.length} ${stagnant.length === 1 ? 'exercício sem evolução' : 'exercícios sem evolução'} nas últimas ${weeks} semanas`;

  return card({
    title,
    subtitle: `Compara a série mais pesada das últimas ${weeks} semanas com a das ${weeks} semanas anteriores (não depende do filtro de período). Subir a carga, ou as repetições com a mesma carga, conta como evolução. Exercícios que não foram treinados no período não entram.`,
    cls: 'col-12',
    body(view) {
      if (stagnant.length === 0) {
        view.append(el('p', { class: 'empty' }, el('span', { class: 'status within', 'aria-hidden': 'true', text: '✓ ' }), 'Todos os exercícios treinados recentemente evoluíram ou ainda não têm base de comparação.'));
        return;
      }
      view.append(el('ul', { class: 'alert-list' }, stagnant.map((e) => el('li', {},
        el('span', { class: 'status warn', 'aria-hidden': 'true', text: '!' }),
        el('span', { class: 'nm', text: e.name }),
        el('span', { class: 'det', text: `última série mais pesada: ${formatSet(e.trend.recent)} nas últimas ${weeks} semanas, contra ${formatSet(e.trend.previous)} nas ${weeks} anteriores` })))));
    },
  });
}

// ---------- evolução do exercício escolhido ----------

function evolutionCard(model, state, exercise) {
  if (!exercise) {
    return card({
      title: 'Evolução do exercício',
      cls: 'col-8',
      body: (view) => view.append(el('p', { class: 'empty', text: 'Nenhum exercício com carga para este músculo.' })),
    });
  }
  const weeks = visibleWeeks(model, state);
  const series = weeklyExercise(exercise, weeks);
  const hasData = series.some(Boolean);
  const tooltipTitle = (items) => weekTitle(items[0].label, weeks[items[0].dataIndex].inProgress);

  return card({
    title: `Evolução: ${exercise.name}`,
    subtitle: `${model.label(exercise.muscleGroup)} · último treino em ${fmtDate(exercise.lastTrained)} · série mais pesada de cada semana`,
    cls: 'col-8',
    body(view, ctx) {
      if (!hasData) {
        view.append(el('p', { class: 'empty', text: 'Este exercício não foi treinado no período escolhido. Experimente um período maior.' }));
        return;
      }
      view.append(el('h3', { class: 'chart-title', text: 'Carga' }));
      mountChart(view, ctx, {
        type: 'line',
        labels: weeks.map((w) => w.week),
        datasets: [{
          label: 'Carga',
          data: series.map((p) => (p ? p.weightKg : null)),
          borderColor: color('--s1'),
          backgroundColor: color('--s1'),
          keyColor: color('--s1'),
          borderWidth: 2,
          pointRadius: 0,
          pointHoverRadius: 5,
          pointHoverBackgroundColor: color('--s1'),
          pointHoverBorderColor: color('--surface'),
          pointHoverBorderWidth: 2,
          tension: 0.15,
          spanGaps: true,
        }],
        height: 'short',
        options: {
          // grace afasta a linha das bordas, senão uma carga constante fica escondida em cima do eixo
          scales: { y: { beginAtZero: false, grace: '10%', ticks: { callback: (v) => `${num1(v)} kg` } } },
          plugins: {
            tooltip: {
              callbacks: {
                title: tooltipTitle,
                label: (item) => ` ${kg(item.parsed.y)} × ${series[item.dataIndex].reps} repetições`,
              },
            },
          },
        },
        label: `Carga da série mais pesada por semana: ${exercise.name}`,
      });

      view.append(el('h3', { class: 'chart-title', text: 'Repetições nessa série' }));
      mountChart(view, ctx, {
        type: 'bar',
        labels: weeks.map((w) => w.week),
        datasets: [{
          label: 'Repetições',
          data: series.map((p) => (p ? p.reps : null)),
          backgroundColor: color('--s2'),
          keyColor: color('--s2'),
          ...columnStyle,
        }],
        height: 'short',
        options: {
          scales: { y: { ticks: { precision: 0 } } },
          plugins: {
            tooltip: {
              callbacks: {
                title: tooltipTitle,
                label: (item) => ` ${item.parsed.y} repetições com ${kg(series[item.dataIndex].weightKg)}`,
              },
            },
          },
        },
        label: `Repetições da série mais pesada por semana: ${exercise.name}`,
      });
    },
    table: () => ({
      headers: ['Semana', 'Carga', 'Repetições'],
      numeric: [1, 2],
      rows: weeks.map((w, i) => [fmtDate(w.week), series[i] ? kg(series[i].weightKg) : '', series[i] ? series[i].reps : ''])
        .filter((row) => row[1] !== ''),
    }),
  });
}

// ---------- recordes ----------

function recordsCard(model, list, actions) {
  const weeks = model.data.config.stagnationWeeks;
  const sorted = [...list].sort((a, b) => Number(b.inRoutine) - Number(a.inRoutine) || a.name.localeCompare(b.name, 'pt-BR'));

  return card({
    title: 'Recordes pessoais e evolução',
    subtitle: `Clique em uma linha para ver a evolução do exercício. "Tendência" compara as últimas ${weeks} semanas com as ${weeks} anteriores. Cada recorde mostra a primeira data em que foi alcançado.`,
    cls: 'col-12',
    body(view) {
      const rows = sorted.map((e) => {
        const change = e.lastChange ? describeChange(e.lastChange) : null;
        return [
          el('span', {}, e.name, e.inRoutine ? null : el('small', { class: 'sub', text: '  fora das rotinas' })),
          change
            ? el('span', { class: `status ${change.up ? 'within' : ''}` }, el('span', { 'aria-hidden': 'true', text: change.icon }), `${change.label}: ${change.detail}`)
            : '—',
          trendBadge(e.trend.progress),
          `${kg(e.maxWeightKg.value)} · ${fmtDateShort(e.maxWeightKg.date)}`,
          `${num(e.maxReps.value)} reps · ${fmtDateShort(e.maxReps.date)}`,
          fmtDateShort(e.lastTrained),
        ];
      });
      view.append(tableElement({
        headers: ['Exercício', 'Último treino contra o anterior', 'Tendência', 'Maior carga', 'Mais repetições', 'Último treino'],
        rows,
        tall: true,
        onRow: (index) => actions.selectExercise(sorted[index].id),
      }));
      if (sorted.length === 0) view.append(el('p', { class: 'empty', text: 'Nenhum exercício com carga para este músculo.' }));
    },
  });
}

// ---------- recordes recentes ----------

function prCard(model, list) {
  const allowed = new Set(list.map((e) => e.name));
  const events = model.data.prEvents.filter((p) => allowed.has(p.exercise)).slice(-8).reverse();
  return card({
    title: 'Recordes mais recentes',
    subtitle: 'Cada vez que uma carga ou um número de repetições passou o melhor anterior',
    cls: 'col-4',
    body(view) {
      if (events.length === 0) {
        view.append(el('p', { class: 'empty', text: 'Nenhum recorde registrado.' }));
        return;
      }
      view.append(el('ul', { class: 'pr-list' }, events.map((p) => el('li', {},
        el('span', { class: 'd', text: fmtDateShort(p.date) }),
        el('span', {}, p.exercise, el('div', { class: 't', text: PR_TYPE[p.type].label })),
        el('span', { class: 'v', text: PR_TYPE[p.type].format(p.value) })))));
    },
  });
}
