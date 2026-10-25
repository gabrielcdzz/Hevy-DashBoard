// Ponto de entrada do dashboard: carrega os dados, liga filtros, abas e tema, e desenha a aba ativa.
import {
  loadData, buildModel, PERIODS, CUSTOM_PERIOD, MAX_CUSTOM_WEEKS, parseCustomWeeks, exercisesFor,
} from './data.js';
import { el, hideTip } from './ui.js';
import { buildOverview } from './overview.js';
import { buildExercises } from './exercises.js';

const TABS = {
  'visao-geral': buildOverview,
  exercicios: buildExercises,
};

const state = { tab: 'visao-geral', period: '12', customWeeks: 12, muscle: 'all', exercise: null };
let model = null;
let cards = [];

/** Os controles dos filtros. A linha de filtros é criada uma vez e mudada de lugar conforme a aba. */
const ui = {};
const actions = {
  filters: null,
  selectExercise(id) {
    state.exercise = id;
    ui.exercise.value = id;
    render();
  },
};

const $ = (id) => document.getElementById(id);

async function main() {
  const panel = $('panel');
  try {
    model = buildModel(await loadData());
  } catch (error) {
    panel.replaceChildren(el('p', { class: 'empty', text: `Não foi possível carregar os dados: ${error.message}` }));
    return;
  }
  const d = model.data;

  // A hora vem em UTC; o navegador mostra no fuso de quem está olhando.
  const generated = new Date(d.generatedAt);
  // A etiqueta deixa claro, no topo da página, de quem são os números que estão na tela.
  $('meta').replaceChildren(
    el('span', { class: `badge ${d.sample ? '' : 'mine'}`, id: 'source-badge', text: d.sample ? 'Dados de exemplo' : 'Seus dados' }),
    ` Atualizado em ${generated.toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })}`);
  $('footer').textContent = d.sample
    ? 'Dados de exemplo gerados pelo projeto. Código aberto no GitHub.'
    : 'Dados sincronizados do Hevy.';
  if (d.sample) {
    $('banner').replaceChildren(el('div', { class: 'sample-banner', role: 'note' },
      el('strong', { text: 'Dados de exemplo. ' }),
      'Este é um usuário fictício, gerado pelo próprio projeto, para demonstrar o dashboard sem expor treinos reais.'));
  }

  actions.filters = buildFilters();
  refreshExerciseOptions();
  setupTabs();
  setupTheme();
  window.addEventListener('hashchange', () => selectTab(tabFromHash(), false));
  selectTab(tabFromHash(), false);
}

// ---------- filtros ----------

function field(label, control) {
  return el('div', { class: 'field' }, el('label', { for: control.id, text: label }), control);
}

function buildFilters() {
  ui.period = el('select', { id: 'f-period' },
    PERIODS.map((p) => el('option', { value: p.value, text: p.label })),
    el('option', { value: CUSTOM_PERIOD, text: 'Personalizado…' }));
  ui.period.value = state.period;

  ui.weeks = el('input', {
    id: 'f-weeks', type: 'number', min: '1', max: String(MAX_CUSTOM_WEEKS), step: '1', inputmode: 'numeric',
    value: String(state.customWeeks), 'aria-describedby': 'f-weeks-help',
  });
  ui.muscle = el('select', { id: 'f-muscle' },
    el('option', { value: 'all', text: 'Todos os músculos' }),
    model.muscles.map((m) => el('option', { value: m, text: model.label(m) })));
  ui.exercise = el('select', { id: 'f-exercise' });

  ui.period.addEventListener('change', () => {
    state.period = ui.period.value;
    syncCustomField();
    refresh();
  });
  ui.weeks.addEventListener('change', () => {
    const weeks = parseCustomWeeks(ui.weeks.value);
    if (weeks === null) {
      ui.weeks.value = String(state.customWeeks); // valor inválido: volta ao anterior
      return;
    }
    state.customWeeks = weeks;
    ui.weeks.value = String(weeks);
    refresh();
  });
  ui.muscle.addEventListener('change', () => {
    state.muscle = ui.muscle.value;
    refreshExerciseOptions();
    refresh();
  });
  ui.exercise.addEventListener('change', () => {
    state.exercise = ui.exercise.value;
    refresh();
  });

  const section = el('section', { class: 'filters col-12', 'aria-label': 'Filtros' },
    field('Período', ui.period),
    field('Semanas', ui.weeks),
    field('Músculo', ui.muscle),
    field('Exercício', ui.exercise),
    el('span', { id: 'f-weeks-help', class: 'visually-hidden', text: `Número de semanas, de 1 a ${MAX_CUSTOM_WEEKS}` }));
  syncCustomField();
  return section;
}

/** O campo de semanas só aparece quando o período é "Personalizado". */
function syncCustomField() {
  ui.weeks.closest('.field').hidden = state.period !== CUSTOM_PERIOD;
}

/** O filtro de exercício lista só os do músculo escolhido; os das rotinas atuais vêm primeiro. */
function refreshExerciseOptions() {
  const exercises = exercisesFor(model, state);
  const group = (label, list) => (list.length
    ? el('optgroup', { label }, list.map((e) => el('option', { value: e.id, text: e.name })))
    : null);
  const byName = (a, b) => a.name.localeCompare(b.name, 'pt-BR');
  ui.exercise.replaceChildren(
    group('Das rotinas atuais', exercises.filter((e) => e.inRoutine).sort(byName)),
    group('Outros exercícios', exercises.filter((e) => !e.inRoutine).sort(byName)));

  if (!exercises.some((e) => e.id === state.exercise)) {
    const mostTrained = [...exercises].sort((a, b) =>
      Number(b.inRoutine) - Number(a.inRoutine) || b.sessions.length - a.sessions.length)[0];
    state.exercise = mostTrained ? mostTrained.id : null;
  }
  if (state.exercise) ui.exercise.value = state.exercise;
  ui.exercise.disabled = exercises.length === 0;
}

// ---------- abas ----------

function tabFromHash() {
  const name = location.hash.replace('#', '');
  return TABS[name] ? name : 'visao-geral';
}

function setupTabs() {
  document.querySelectorAll('.tab').forEach((button) => {
    button.addEventListener('click', () => selectTab(button.dataset.tab, true));
  });
}

function selectTab(name, updateHash) {
  state.tab = name;
  if (updateHash && location.hash !== `#${name}`) history.replaceState(null, '', `#${name}`);
  document.querySelectorAll('.tab').forEach((button) => {
    const selected = button.dataset.tab === name;
    button.setAttribute('aria-selected', String(selected));
    button.tabIndex = selected ? 0 : -1;
  });
  $('panel').setAttribute('aria-labelledby', `tab-${name}`);
  // O filtro de exercício só faz sentido na aba de exercícios.
  ui.exercise.closest('.field').hidden = name !== 'exercicios';
  render();
}

// ---------- desenho ----------

function refresh() {
  const panel = $('panel');
  panel.classList.add('loading'); // mantém o quadro anterior, só mais claro, enquanto redesenha
  requestAnimationFrame(() => { render(); panel.classList.remove('loading'); });
}

function render() {
  hideTip();
  // Redesenhar troca os elementos; quem estava usando um filtro com o teclado continua nele.
  const focusedId = document.activeElement && document.activeElement.id;
  cards.forEach((c) => c.destroy && c.destroy());
  const panel = $('panel');
  const sections = TABS[state.tab](model, state, actions);
  const grid = el('div', { class: 'grid' }, sections);
  panel.replaceChildren(grid);
  cards = [...grid.querySelectorAll('.card')].filter((node) => typeof node.render === 'function');
  cards.forEach((node) => node.render());
  if (focusedId && focusedId.startsWith('f-') && $(focusedId)) $(focusedId).focus();
}

// ---------- tema ----------

function setupTheme() {
  const saved = safeStorage('get');
  if (saved) document.documentElement.dataset.theme = saved;
  $('theme-btn').addEventListener('click', () => {
    const root = document.documentElement;
    const dark = root.dataset.theme
      ? root.dataset.theme === 'dark'
      : window.matchMedia('(prefers-color-scheme: dark)').matches;
    root.dataset.theme = dark ? 'light' : 'dark';
    safeStorage('set', root.dataset.theme);
    render();
  });
  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
    if (!document.documentElement.dataset.theme) render();
  });
}

function safeStorage(op, value) {
  try {
    return op === 'get' ? localStorage.getItem('hevy-theme') : localStorage.setItem('hevy-theme', value);
  } catch (error) {
    return null;
  }
}

main();
