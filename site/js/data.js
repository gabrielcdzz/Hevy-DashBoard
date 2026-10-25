// Carregamento do arquivo de dados e funções de consulta usadas pelas abas.
import { MUSCLE_ORDER, mondayOf } from './format.js';

/**
 * Procura primeiro os dados reais (data/dashboard.json, gerado pela sincronização e fora do Git) e,
 * se não existirem, usa os dados de exemplo.
 */
export async function loadData() {
  for (const url of ['data/dashboard.json', 'data/sample.json']) {
    try {
      const response = await fetch(url, { cache: 'no-cache' });
      if (response.ok) return await response.json();
    } catch (error) {
      // tenta o próximo arquivo
    }
  }
  throw new Error('Nenhum arquivo de dados foi encontrado em data/.');
}

/** Cardio e "corpo inteiro" não são músculos: não entram nas listas de volume nem no filtro de músculo. */
const NOT_MUSCLES = new Set(['cardio', 'full_body', 'other']);

/** Opções do filtro de período, em semanas (0 = todo o histórico). "Personalizado" é tratado à parte. */
export const PERIODS = [
  { value: '4', label: 'Últimas 4 semanas' },
  { value: '8', label: 'Últimas 8 semanas' },
  { value: '12', label: 'Últimas 12 semanas' },
  { value: '26', label: 'Últimos 6 meses' },
  { value: '52', label: 'Último ano' },
  { value: '0', label: 'Todo o histórico' },
];

export const CUSTOM_PERIOD = 'custom';
export const MAX_CUSTOM_WEEKS = 520;

/** Quantas semanas o filtro de período mostra agora (0 = todas). */
export function periodWeeks(state) {
  return state.period === CUSTOM_PERIOD ? state.customWeeks : Number(state.period);
}

/** Aceita só inteiros de 1 a 520; qualquer outra coisa devolve null. */
export function parseCustomWeeks(text) {
  const value = Number.parseInt(String(text).trim(), 10);
  return Number.isInteger(value) && value >= 1 && value <= MAX_CUSTOM_WEEKS ? value : null;
}

export function buildModel(data) {
  const volByWeek = new Map();
  for (const row of data.volume) {
    if (!volByWeek.has(row.week)) volByWeek.set(row.week, new Map());
    volByWeek.get(row.week).set(row.muscle, row);
  }

  // Os músculos das rotinas (os que têm planejado) e os que aparecem com séries diretas no histórico.
  const seen = new Set();
  for (const [muscle, plan] of Object.entries(data.plannedVolume)) if (plan.direct > 0) seen.add(muscle);
  for (const row of data.volume) if (row.direct > 0 && !NOT_MUSCLES.has(row.muscle)) seen.add(row.muscle);
  const muscles = [...seen].sort((a, b) => rank(a) - rank(b) || label(a).localeCompare(label(b), 'pt-BR'));

  function label(muscle) {
    return data.muscleLabels[muscle] || muscle;
  }
  function rank(muscle) {
    const index = MUSCLE_ORDER.indexOf(muscle);
    return index < 0 ? 99 : index;
  }

  const plannedTotal = Object.values(data.plannedVolume).reduce((sum, plan) => sum + plan.direct, 0);

  return {
    data,
    weeks: data.weeks,
    muscles,
    label,
    plannedTotal,
    /** Linha de volume de um músculo em uma semana (sempre existe para os músculos planejados). */
    volumeAt(week, muscle) {
      return volByWeek.get(week)?.get(muscle) || { week, muscle, direct: 0, indirect: 0, sessions: 0 };
    },
    exerciseById: new Map(data.exercises.map((e) => [e.id, e])),
  };
}

export function visibleWeeks(model, state) {
  const count = periodWeeks(state);
  return count > 0 ? model.weeks.slice(-count) : model.weeks;
}

/** Os exercícios que passam pelo filtro de músculo. */
export function exercisesFor(model, state) {
  return model.data.exercises.filter((e) => state.muscle === 'all' || e.muscleGroup === state.muscle);
}

/**
 * Evolução semanal de um exercício: para cada semana visível, a série mais pesada dos treinos dela
 * (a maior carga e, nela, as repetições mais altas), ou null nas semanas sem treino do exercício.
 */
export function weeklyExercise(exercise, weeks) {
  const byWeek = new Map();
  for (const session of exercise.sessions) {
    const week = mondayOf(session.date);
    const current = byWeek.get(week);
    const heavier = !current || session.weightKg > current.weightKg
      || (session.weightKg === current.weightKg && session.reps > current.reps);
    if (heavier) byWeek.set(week, { weightKg: session.weightKg, reps: session.reps });
  }
  return weeks.map((w) => byWeek.get(w.week) || null);
}
