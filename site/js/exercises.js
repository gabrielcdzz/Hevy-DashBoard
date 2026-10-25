// Aba "Exercícios": alerta de estagnação, evolução de carga e 1RM estimado, recordes e PRs recentes.
import { el, card, legend, tableElement } from './ui.js';
import { visibleWeeks, exercisesFor, weeklyExercise } from './data.js';
import { mountChart, color, alpha, weekTitle } from './charts.js';
import { num, num1, kg, signedPercent, fmtDate, fmtDateShort } from './format.js';

const TREND = {
  progressing: { icon: '▲', text: 'Progredindo', cls: 'within' },
  stagnant: { icon: '!', text: 'Estagnado', cls: 'warn' },
  inactive: { icon: '–', text: 'Fora do treino', cls: '' },
  no_baseline: { icon: '·', text: 'Sem base de comparação', cls: '' },
};

const PR_TYPE = {
  weight: { label: 'Maior carga', format: (v) => kg(v) },
  reps: { label: 'Mais repetições', format: (v) => `${num(v)} reps` },
  one_rep_max: { label: 'Maior 1RM estimado', format: (v) => kg(v) },
};

export function buildExercises(model, state, actions) {
  const list = exercisesFor(model, state);
  const selected = model.exerciseById.get(state.exercise);
  return [
    stagnationCard(model, list),
    evolutionCard(model, state, selected),
    prCard(model, list),
    recordsCard(model, list, actions),
  ];
}

function trendBadge(progress) {
  const t = TREND[progress];
  return el('span', { class: `status ${t.cls}` }, el('span', { 'aria-hidden': 'true', text: t.icon }), t.text);
}

// ---------- alerta de estagnação ----------

function stagnationCard(model, list) {
  const weeks = model.data.config.stagnationWeeks;
  const stagnant = list.filter((e) => e.inRoutine && e.progress === 'stagnant')
    .sort((a, b) => a.name.localeCompare(b.name, 'pt-BR'));
  const title = stagnant.length === 0
    ? `Nenhum exercício das rotinas está sem progressão`
    : `${stagnant.length} ${stagnant.length === 1 ? 'exercício sem progressão' : 'exercícios sem progressão'} nas últimas ${weeks} semanas`;

  return card({
    title,
    subtitle: `Compara o melhor 1RM estimado das últimas ${weeks} semanas com o das ${weeks} semanas anteriores. Exercícios que não foram treinados no período não entram.`,
    cls: 'col-12',
    body(view) {
      if (stagnant.length === 0) {
        view.append(el('p', { class: 'empty' }, el('span', { class: 'status within', 'aria-hidden': 'true', text: '✓ ' }), 'Todos os exercícios treinados recentemente melhoraram ou ainda não têm base de comparação.'));
        return;
      }
      view.append(el('ul', { class: 'alert-list' }, stagnant.map((e) => {
        const change = e.previousBest ? ((e.recentBest - e.previousBest) / e.previousBest) * 100 : null;
        return el('li', {},
          el('span', { class: 'status warn', 'aria-hidden': 'true', text: '!' }),
          el('span', { class: 'nm', text: e.name }),
          el('span', { class: 'det', text: `1RM estimado ${kg(e.recentBest)} nas últimas ${weeks} semanas, contra ${kg(e.previousBest)} nas ${weeks} anteriores${change != null ? ` (${signedPercent(change)})` : ''}` }));
      })));
    },
  });
}

// ---------- evolução do exercício escolhido ----------

function evolutionCard(model, state, exercise) {
  if (!exercise) {
    return card({
      title: 'Evolução do exercício',
      cls: 'col-12',
      body: (view) => view.append(el('p', { class: 'empty', text: 'Nenhum exercício com carga para este músculo.' })),
    });
  }
  const weeks = visibleWeeks(model, state);
  const series = weeklyExercise(exercise, weeks);
  const hasData = series.some(Boolean);

  return card({
    title: `Evolução: ${exercise.name}`,
    subtitle: `${model.label(exercise.muscleGroup)} · último treino em ${fmtDate(exercise.lastTrained)} · ${TREND[exercise.progress].text.toLowerCase()}`,
    cls: 'col-8',
    body(view, ctx) {
      if (!hasData) {
        view.append(el('p', { class: 'empty', text: 'Este exercício não foi treinado no período escolhido. Experimente um período maior.' }));
        return;
      }
      view.append(legend([
        { shape: 'line', color: color('--s1'), label: 'Maior carga da semana (kg)' },
        { shape: 'line', color: color('--s2'), label: '1RM estimado (kg, fórmula de Epley)' },
      ]));
      const line = (label, key, c, endLabel) => ({
        label,
        data: series.map((p) => (p ? p[key] : null)),
        borderColor: c,
        backgroundColor: c,
        keyColor: c,
        borderWidth: 2,
        pointRadius: 0,
        pointHoverRadius: 5,
        pointHoverBackgroundColor: c,
        pointHoverBorderColor: color('--surface'),
        pointHoverBorderWidth: 2,
        tension: 0.15,
        spanGaps: true,
        endLabel,
      });
      mountChart(view, ctx, {
        type: 'line',
        labels: weeks.map((w) => w.week),
        datasets: [
          line('Maior carga', 'weightKg', color('--s1'), 'carga'),
          line('1RM estimado', 'oneRepMax', color('--s2'), '1RM'),
        ],
        height: 'tall',
        options: {
          layout: { padding: { right: 44 } },
          // grace afasta as linhas das bordas, senão uma carga constante fica escondida em cima do eixo
          scales: { y: { beginAtZero: false, grace: '10%', ticks: { callback: (v) => `${num1(v)} kg` } } },
          plugins: {
            tooltip: {
              callbacks: {
                title: (items) => weekTitle(items[0].label, weeks[items[0].dataIndex].inProgress),
                label: (item) => ` ${item.dataset.label}: ${kg(item.parsed.y)}`,
              },
            },
          },
        },
        plugins: { phases: model.data.phases },
        label: `Evolução de carga e 1RM estimado: ${exercise.name}`,
      });
    },
    table: () => ({
      headers: ['Semana', 'Maior carga', '1RM estimado'],
      numeric: [1, 2],
      rows: weeks.map((w, i) => [fmtDate(w.week), series[i] ? kg(series[i].weightKg) : '', series[i] ? kg(series[i].oneRepMax) : ''])
        .filter((row) => row[1] !== ''),
    }),
  });
}

// ---------- recordes ----------

function recordsCard(model, list, actions) {
  const weeks = model.data.config.stagnationWeeks;
  const sorted = [...list].sort((a, b) => Number(b.inRoutine) - Number(a.inRoutine) || a.name.localeCompare(b.name, 'pt-BR'));
  const mark = (m, unit) => (unit === 'reps' ? `${num(m.value)} reps` : kg(m.value));

  return card({
    title: 'Recordes pessoais e tendência',
    subtitle: `Clique em uma linha para ver a evolução do exercício. "Recente" são as últimas ${weeks} semanas e "anterior", as ${weeks} antes delas. Cada recorde mostra a primeira data em que foi alcançado.`,
    cls: 'col-12',
    body(view) {
      const rows = sorted.map((e) => [
        el('span', {}, e.name, e.inRoutine ? null : el('small', { class: 'sub', text: '  fora das rotinas' })),
        trendBadge(e.progress),
        e.recentBest != null ? kg(e.recentBest) : '—',
        e.previousBest != null ? kg(e.previousBest) : '—',
        `${mark(e.maxWeightKg)} · ${fmtDateShort(e.maxWeightKg.date)}`,
        `${mark(e.maxReps, 'reps')} · ${fmtDateShort(e.maxReps.date)}`,
        `${mark(e.bestOneRepMax)} · ${fmtDateShort(e.bestOneRepMax.date)}`,
        fmtDateShort(e.lastTrained),
      ]);
      view.append(tableElement({
        headers: ['Exercício', 'Tendência', '1RM recente', '1RM anterior', 'Maior carga', 'Mais repetições', 'Melhor 1RM', 'Último treino'],
        rows,
        numeric: [2, 3],
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
    subtitle: 'Cada vez que uma carga, número de repetições ou 1RM estimado passou o melhor anterior',
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
