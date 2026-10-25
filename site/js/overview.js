// Aba "Visão geral": os cartões da semana (que não mudam com os filtros), o volume por músculo contra o
// planejado das rotinas e, abaixo dos filtros, o histórico: séries, treinos, variação e duração.
import { el, card, legend, attachTip } from './ui.js';
import { visibleWeeks } from './data.js';
import { mountChart, color, alpha, columnStyle, weekTitle } from './charts.js';
import { num, signedPercent, minutes, fmtDate, monthLong } from './format.js';

export function buildOverview(model, state, actions) {
  const d = model.data;
  const weeks = visibleWeeks(model, state);
  const current = model.weeks[model.weeks.length - 1];
  const previous = model.weeks[model.weeks.length - 2];

  const summary = el('div', { class: 'grid col-12' }, heroCard(d, current), tilesGrid(model, current, previous));
  return [
    summary,
    actions.filters, // os filtros ficam abaixo dos cartões da semana, que não dependem deles
    volumeCard(model, state, current, previous),
    setsCard(model, state, weeks),
    workoutsCard(model, weeks),
    changeCard(model, state, weeks),
    durationCard(model, weeks),
  ];
}

// ---------- números de destaque (não mudam com os filtros) ----------

function heroCard(d, current) {
  const goal = d.config.workoutsPerWeek;
  const ratio = Math.min(1, current.workouts / goal);
  const note = current.complete ? 'Meta da semana atingida'
    : current.inProgress ? 'Semana em andamento' : 'Abaixo da meta';
  return el('section', { class: 'card col-4', 'aria-label': 'Treinos desta semana' },
    el('div', { class: 'hero' },
      el('span', { class: 'label', text: 'Treinos esta semana' }),
      el('span', { class: 'value' }, String(current.workouts), el('small', { text: `de ${goal}` })),
      el('div', { class: 'meter', role: 'img', 'aria-label': `${current.workouts} de ${goal} treinos` },
        el('span', { style: `width:${ratio * 100}%` })),
      el('span', { class: 'sub', text: `${note} · semana de ${fmtDate(current.week)}` })));
}

function tilesGrid(model, current, previous) {
  const d = model.data;
  const month = d.today.slice(0, 7);
  const prsThisMonth = d.prEvents.filter((p) => p.date.startsWith(month)).length;

  let setsNote;
  let setsNoteClass = '';
  if (current.inProgress || current.changePercent == null) {
    setsNote = `semana em andamento · anterior: ${previous ? num(previous.sets) : '—'}`;
  } else {
    setsNote = `${signedPercent(current.changePercent)} em relação à semana anterior`;
    setsNoteClass = current.changePercent >= 0 ? 'delta-up' : 'delta-down';
  }

  const recent = model.weeks.filter((w) => !w.inProgress && w.averageDurationMinutes != null).slice(-4);
  const recentWorkouts = recent.reduce((sum, w) => sum + w.workouts, 0);
  const recentDuration = recentWorkouts
    ? recent.reduce((sum, w) => sum + w.averageDurationMinutes * w.workouts, 0) / recentWorkouts : null;

  return el('div', { class: 'grid col-8' },
    tile('Séries na semana', num(current.sets), model.plannedTotal > 0 ? `de ${num(model.plannedTotal)} planejadas` : '', setsNote, setsNoteClass),
    tile('Recordes no mês', num(prsThisMonth), '', `${monthLong(d.today)}: carga, repetições ou 1RM`),
    tile('Semanas completas seguidas', num(d.consistency.currentStreakWeeks), '', `melhor sequência: ${num(d.consistency.bestStreakWeeks)} · meta: ${d.config.workoutsPerWeek} treinos por semana`),
    tile('Duração média do treino', recentDuration != null ? minutes(recentDuration) : '—', '',
      d.averageDurationMinutes != null ? `últimas 4 semanas · histórico: ${minutes(d.averageDurationMinutes)}` : ''));
}

function tile(label, value, unit, note, noteClass = '') {
  return el('section', { class: 'card col-6 tile', 'aria-label': label },
    el('div', { class: 'label', text: label }),
    el('div', { class: 'value' }, value, unit ? el('small', { text: unit }) : null),
    note ? el('div', { class: `note ${noteClass}`, text: note }) : null);
}

// ---------- volume por músculo contra o planejado das rotinas ----------

/** Cada situação tem ícone e texto, porque a cor sozinha não basta. */
const STATUS = {
  within: { icon: '✓', cls: 'within', text: () => 'Dentro do planejado' },
  above: { icon: '▲', cls: '', text: (diff) => `Acima do planejado (+${diff})` },
  below: { icon: '▼', cls: '', text: (diff) => `Abaixo do planejado (−${diff})` },
  remaining: { icon: '○', cls: '', text: (diff) => (diff === 1 ? 'Falta 1 série' : `Faltam ${diff} séries`) },
};

function statusOf(row) {
  const status = row.status ? STATUS[row.status] : null;
  return status ? { ...status, label: status.text(Math.abs(row.direct - row.planned)) } : null;
}

function volumeCard(model, state, current, previous) {
  const muscles = model.muscles.filter((m) => state.muscle === 'all' || m === state.muscle);
  const rows = muscles.map((muscle) => ({
    muscle,
    now: model.volumeAt(current.week, muscle),
    before: previous ? model.volumeAt(previous.week, muscle) : null,
    plan: model.data.plannedVolume[muscle] || null,
  }));
  const scaleMax = Math.max(10, ...rows.map((r) => Math.max(r.now.direct, r.before?.direct || 0, r.now.planned || 0))) * 1.12;

  return card({
    title: 'Volume por músculo na semana',
    subtitle: `Séries diretas da semana de ${fmtDate(current.week)}${current.inProgress ? ' (em andamento)' : ''} contra o volume planejado das suas rotinas`,
    cls: 'col-12',
    body(view) {
      view.append(legend([
        { shape: 'box', color: color('--s1'), label: 'Séries diretas desta semana' },
        { shape: 'tick-plan', label: 'Planejado nas rotinas' },
        { shape: 'tick-prev', label: 'Semana anterior' },
      ]));
      const list = el('div', { class: 'rows', role: 'list' });
      for (const r of rows) {
        const status = statusOf(r.now);
        const planned = r.now.planned;
        const row = el('div', { class: 'vrow', role: 'listitem', tabindex: '0', 'aria-label': describe(model, r, status) },
          el('span', { class: 'name', text: model.label(r.muscle) }),
          el('div', { class: 'track' },
            el('span', { class: 'base' }),
            el('span', { class: 'bar', style: `width:${(r.now.direct / scaleMax) * 100}%` }),
            r.before ? el('span', { class: 'prev', style: `left:calc(${(r.before.direct / scaleMax) * 100}% - 1px)` }) : null,
            planned ? el('span', { class: 'planned', style: `left:calc(${(planned / scaleMax) * 100}% - 1px)` }) : null),
          el('div', { class: 'nums' },
            el('span', {}, el('strong', { text: planned ? `${num(r.now.direct)} de ${num(planned)} diretas` : `${num(r.now.direct)} diretas` }),
              ` (indiretas ${num(r.now.indirect)})`),
            el('span', { class: 'line-2' },
              status
                ? el('span', { class: `status ${status.cls}` }, el('span', { 'aria-hidden': 'true', text: status.icon }), status.label)
                : el('span', { class: 'muted', text: 'fora das rotinas' }),
              el('span', { text: `${num(r.now.sessions)}× na semana` }))));
        attachTip(row, () => tipRows(model, r, status));
        list.append(row);
      }
      view.append(list);
    },
    table: () => ({
      headers: ['Músculo', 'Diretas', 'Planejado', 'Situação', 'Indiretas', 'Dias na semana', 'Semana anterior'],
      numeric: [1, 2, 4, 5, 6],
      rows: rows.map((r) => [
        model.label(r.muscle), r.now.direct, r.now.planned ?? '', statusOf(r.now)?.label ?? 'fora das rotinas',
        r.now.indirect, r.now.sessions, r.before ? r.before.direct : '']),
    }),
  });
}

function describe(model, r, status) {
  const plan = r.now.planned ? ` de ${r.now.planned} planejadas` : '';
  return `${model.label(r.muscle)}: ${r.now.direct} séries diretas${plan}${status ? `, ${status.label.toLowerCase()}` : ''}`;
}

function tipRows(model, r, status) {
  const lines = [{ title: model.label(r.muscle) }, { label: 'Diretas', value: num(r.now.direct) }];
  if (r.now.planned) lines.push({ label: 'Planejado nas rotinas', value: num(r.now.planned) });
  if (status) lines.push({ label: 'Situação', value: status.label });
  lines.push({ label: 'Indiretas', value: num(r.now.indirect) });
  lines.push({ label: 'Dias com série direta', value: num(r.now.sessions) });
  if (r.before) lines.push({ label: 'Diretas na semana anterior', value: num(r.before.direct) });
  if (r.plan && r.plan.indirect) lines.push({ muted: `As rotinas têm também ${r.plan.indirect} séries indiretas` });
  return lines;
}

// ---------- séries e treinos por semana (histórico, com os filtros) ----------

function setsCard(model, state, weeks) {
  const muscle = state.muscle;
  const tooltipTitle = { callbacks: { title: (items) => weekTitle(items[0].label, weeks[items[0].dataIndex].inProgress) } };

  if (muscle !== 'all') {
    const rows = weeks.map((w) => model.volumeAt(w.week, muscle));
    const planned = model.data.plannedVolume[muscle]?.direct;
    return card({
      title: `Séries de ${model.label(muscle)} por semana`,
      subtitle: planned ? `Diretas e indiretas; a linha é o planejado das rotinas (${planned} diretas por semana)` : 'Diretas e indiretas por semana',
      cls: 'col-6',
      body(view, ctx) {
        view.append(legend([
          { shape: 'box', color: color('--s1'), label: 'Diretas' },
          { shape: 'box', color: color('--s2'), label: 'Indiretas' },
        ]));
        mountChart(view, ctx, {
          type: 'bar',
          labels: weeks.map((w) => w.week),
          datasets: [
            { label: 'Diretas', data: rows.map((r) => r.direct), backgroundColor: color('--s1'), keyColor: color('--s1'), ...columnStyle },
            { label: 'Indiretas', data: rows.map((r) => r.indirect), backgroundColor: color('--s2'), keyColor: color('--s2'), ...columnStyle },
          ],
          options: { plugins: { tooltip: tooltipTitle } },
          plugins: { lines: planned ? [{ y: planned, label: `planejado: ${planned}` }] : [] },
          label: `Séries diretas e indiretas de ${model.label(muscle)} por semana`,
        });
      },
      table: () => ({
        headers: ['Semana', 'Diretas', 'Indiretas', 'Dias', 'Planejado'],
        numeric: [1, 2, 3, 4],
        rows: weeks.map((w, i) => [fmtDate(w.week), rows[i].direct, rows[i].indirect, rows[i].sessions, planned ?? '']),
      }),
    });
  }

  const planned = model.plannedTotal;
  return card({
    title: 'Séries por semana',
    subtitle: `Séries de trabalho (sem aquecimento)${planned ? '; a linha é o total planejado das rotinas' : ''}. A coluna clara é a semana em andamento`,
    cls: 'col-6',
    body(view, ctx) {
      mountChart(view, ctx, {
        type: 'bar',
        labels: weeks.map((w) => w.week),
        datasets: [{
          label: 'Séries',
          data: weeks.map((w) => w.sets),
          backgroundColor: weeks.map((w) => (w.inProgress ? alpha(color('--s1'), 0.45) : color('--s1'))),
          keyColor: color('--s1'),
          ...columnStyle,
        }],
        options: { plugins: { tooltip: tooltipTitle } },
        plugins: { lines: planned ? [{ y: planned, label: `planejado: ${planned}` }] : [] },
        label: 'Séries de trabalho por semana',
      });
    },
    table: () => ({
      headers: ['Semana', 'Séries', 'Treinos', 'Variação'],
      numeric: [1, 2, 3],
      rows: weeks.map((w) => [fmtDate(w.week) + (w.inProgress ? ' (em andamento)' : ''), w.sets, w.workouts, w.changePercent == null ? '' : signedPercent(w.changePercent)]),
    }),
  });
}

function workoutsCard(model, weeks) {
  const goal = model.data.config.workoutsPerWeek;
  return card({
    title: 'Treinos por semana',
    subtitle: `Meta de ${goal} treinos; em destaque, as semanas que a atingiram`,
    cls: 'col-6',
    body(view, ctx) {
      view.append(legend([
        { shape: 'box', color: color('--s1'), label: 'Semana completa' },
        { shape: 'box', color: color('--neutral-mark'), label: 'Abaixo da meta' },
      ]));
      mountChart(view, ctx, {
        type: 'bar',
        labels: weeks.map((w) => w.week),
        datasets: [{
          label: 'Treinos',
          data: weeks.map((w) => w.workouts),
          backgroundColor: weeks.map((w) => (w.complete ? color('--s1') : color('--neutral-mark'))),
          keyColor: color('--s1'),
          ...columnStyle,
        }],
        options: {
          scales: { y: { suggestedMax: goal + 1, ticks: { stepSize: 1 } } },
          plugins: { tooltip: { callbacks: { title: (items) => weekTitle(items[0].label, weeks[items[0].dataIndex].inProgress) } } },
        },
        plugins: { lines: [{ y: goal, label: `meta: ${goal}` }] },
        label: 'Treinos por semana',
      });
    },
    table: () => ({
      headers: ['Semana', 'Treinos', 'Meta atingida'],
      numeric: [1],
      rows: weeks.map((w) => [fmtDate(w.week) + (w.inProgress ? ' (em andamento)' : ''), w.workouts, w.complete ? 'sim' : 'não']),
    }),
  });
}

// ---------- variação e duração ----------

const CLAMP_MIN = -100;
const CLAMP_MAX = 150;

/** Variação semanal das séries: do total ou, com um músculo escolhido, das séries diretas dele. */
function changeCard(model, state, weeks) {
  const muscle = state.muscle;
  const closed = weeks.filter((w) => !w.inProgress);
  const rows = closed.map((w) => {
    if (muscle === 'all') return { change: w.changePercent ?? null, sets: w.sets };
    const row = model.volumeAt(w.week, muscle);
    return { change: row.changePercent ?? null, sets: row.direct };
  });
  const subject = muscle === 'all' ? 'Séries' : `Séries diretas de ${model.label(muscle)}`;

  return card({
    title: muscle === 'all' ? 'Variação semanal de volume' : `Variação semanal: ${model.label(muscle)}`,
    subtitle: `${subject} em relação à semana anterior; quedas fortes costumam ser semanas de deload`,
    cls: 'col-6',
    body(view, ctx) {
      mountChart(view, ctx, {
        type: 'bar',
        labels: closed.map((w) => w.week),
        datasets: [{
          label: 'Variação',
          data: rows.map((r) => (r.change == null ? null : Math.max(CLAMP_MIN, Math.min(CLAMP_MAX, r.change)))),
          backgroundColor: rows.map((r) => ((r.change ?? 0) >= 0 ? color('--diverge-pos') : color('--diverge-neg'))),
          keyColor: color('--diverge-pos'),
          ...columnStyle,
          borderRadius: 3,
          borderSkipped: false,
        }],
        options: {
          scales: { y: { beginAtZero: false, suggestedMin: -60, suggestedMax: 60, ticks: { callback: (v) => `${v}%` } } },
          plugins: {
            tooltip: {
              callbacks: {
                title: (items) => weekTitle(items[0].label, false),
                label: (item) => {
                  const r = rows[item.dataIndex];
                  return r.change == null ? ' sem semana anterior para comparar' : ` ${signedPercent(r.change)} (${num(r.sets)} séries)`;
                },
              },
            },
          },
        },
        plugins: { lines: [{ y: 0, strong: true }] },
        label: `Variação percentual das ${subject.toLowerCase()} por semana`,
      });
    },
    table: () => ({
      headers: ['Semana', 'Séries', 'Variação'],
      numeric: [1, 2],
      rows: closed.map((w, i) => [fmtDate(w.week), rows[i].sets, rows[i].change == null ? '' : signedPercent(rows[i].change)]),
    }),
  });
}

function durationCard(model, weeks) {
  const avg = model.data.averageDurationMinutes;
  return card({
    title: 'Duração média dos treinos',
    subtitle: avg != null
      ? `Minutos por treino, média de cada semana. A linha reta é a média de todo o histórico (${minutes(avg)})`
      : 'Minutos por treino, média de cada semana',
    cls: 'col-6',
    body(view, ctx) {
      mountChart(view, ctx, {
        type: 'line',
        labels: weeks.map((w) => w.week),
        datasets: [{
          label: 'Duração média (min)',
          data: weeks.map((w) => w.averageDurationMinutes ?? null),
          borderColor: color('--s1'),
          backgroundColor: alpha(color('--s1'), 0.1),
          keyColor: color('--s1'),
          borderWidth: 2,
          pointRadius: 0,
          pointHoverRadius: 5,
          pointHoverBackgroundColor: color('--s1'),
          pointHoverBorderColor: color('--surface'),
          pointHoverBorderWidth: 2,
          tension: 0.25,
          fill: true,
          spanGaps: true,
        }],
        options: {
          scales: { y: { beginAtZero: false, grace: '10%', ticks: { callback: (v) => `${v} min` } } },
          plugins: {
            tooltip: {
              callbacks: {
                title: (items) => weekTitle(items[0].label, weeks[items[0].dataIndex].inProgress),
                label: (item) => ` ${minutes(item.parsed.y)}`,
              },
            },
          },
        },
        plugins: { lines: avg != null ? [{ y: avg }] : [] },
        label: 'Duração média dos treinos por semana',
      });
    },
    table: () => ({
      headers: ['Semana', 'Duração média', 'Treinos'],
      numeric: [1, 2],
      rows: weeks.map((w) => [fmtDate(w.week), w.averageDurationMinutes == null ? '' : minutes(w.averageDurationMinutes), w.workouts]),
    }),
  });
}
