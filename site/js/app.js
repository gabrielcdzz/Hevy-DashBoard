// Ponto de entrada do dashboard: carrega os dados, liga filtros, abas e tema, e desenha a aba ativa.
import { loadData, buildModel, PERIODS, exercisesFor } from './data.js';
import { el, hideTip } from './ui.js';
import { buildOverview } from './overview.js';
import { buildExercises } from './exercises.js';
import { buildAnalysis } from './analysis.js';

const TABS = {
  'visao-geral': buildOverview,
  exercicios: buildExercises,
  analise: buildAnalysis,
};

const state = { tab: 'visao-geral', period: 12, muscle: 'all', exercise: null };
let model = null;
let cards = [];

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
  $('meta').textContent = `Atualizado em ${generated.toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })}`;
  $('footer').textContent = d.sample
    ? 'Dados de exemplo gerados pelo projeto. Código aberto no GitHub.'
    : 'Dados sincronizados do Hevy.';
  if (d.sample) {
    $('banner').replaceChildren(el('div', { class: 'sample-banner', role: 'note' },
      el('strong', { text: 'Dados de exemplo. ' }),
      'Este é um usuário fictício, gerado pelo próprio projeto, para demonstrar o dashboard sem expor treinos reais.'));
  }

  setupFilters();
  setupTabs();
  setupTheme();
  window.addEventListener('hashchange', () => selectTab(tabFromHash(), false));
  selectTab(tabFromHash(), false);
}

// ---------- filtros ----------

function setupFilters() {
  const period = $('f-period');
  period.replaceChildren(...PERIODS.map((p) => el('option', { value: String(p.value), text: p.label, selected: p.value === state.period })));
  period.addEventListener('change', () => { state.period = Number(period.value); refresh(); });

  const muscle = $('f-muscle');
  muscle.replaceChildren(
    el('option', { value: 'all', text: 'Todos os músculos' }),
    ...model.muscles.map((m) => el('option', { value: m, text: model.label(m) })));
  muscle.addEventListener('change', () => { state.muscle = muscle.value; refreshExerciseOptions(); refresh(); });

  $('f-exercise').addEventListener('change', (event) => { state.exercise = event.target.value; refresh(); });
  refreshExerciseOptions();
}

/** O filtro de exercício lista só os do músculo escolhido; os das rotinas atuais vêm primeiro. */
function refreshExerciseOptions() {
  const exercises = exercisesFor(model, state);
  const select = $('f-exercise');
  const group = (label, list) => (list.length
    ? el('optgroup', { label }, list.map((e) => el('option', { value: e.id, text: e.name })))
    : null);
  const byName = (a, b) => a.name.localeCompare(b.name, 'pt-BR');
  select.replaceChildren(
    group('Das rotinas atuais', exercises.filter((e) => e.inRoutine).sort(byName)),
    group('Outros exercícios', exercises.filter((e) => !e.inRoutine).sort(byName)));

  if (!exercises.some((e) => e.id === state.exercise)) {
    const mostTrained = [...exercises].sort((a, b) =>
      Number(b.inRoutine) - Number(a.inRoutine) || b.sessions.length - a.sessions.length)[0];
    state.exercise = mostTrained ? mostTrained.id : null;
  }
  if (state.exercise) select.value = state.exercise;
  select.disabled = exercises.length === 0;
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
  $('f-exercise').closest('.field').hidden = name !== 'exercicios';
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
  cards.forEach((c) => c.destroy && c.destroy());
  const panel = $('panel');
  const sections = TABS[state.tab](model, state, {
    selectExercise(id) {
      state.exercise = id;
      $('f-exercise').value = id;
      render();
    },
  });
  const grid = el('div', { class: 'grid' }, sections);
  panel.replaceChildren(grid);
  cards = [...grid.querySelectorAll('.card')].filter((node) => typeof node.render === 'function');
  cards.forEach((node) => node.render());
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
