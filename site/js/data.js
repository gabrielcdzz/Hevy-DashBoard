// Carregamento do arquivo de dados e funções de consulta usadas pelas três abas.
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

const NOT_MUSCLES = new Set(['cardio', 'full_body', 'other']);

export const PERIODS = [
  { value: 8, label: 'Últimas 8 semanas' },
  { value: 12, label: 'Últimas 12 semanas' },
  { value: 26, label: 'Últimos 6 meses' },
  { value: 52, label: 'Último ano' },
  { value: 0, label: 'Todo o histórico' },
];

export function buildModel(data) {
  const volByWeek = new Map();
  for (const row of data.volume) {
    if (!volByWeek.has(row.week)) volByWeek.set(row.week, new Map());
    volByWeek.get(row.week).set(row.muscle, row);
  }

  const seen = new Set(Object.keys(data.config.targets));
  // Cardio e "corpo inteiro" não são músculos: não entram nas listas de volume nem no filtro de músculo.
  for (const row of data.volume) if (row.direct > 0 && !NOT_MUSCLES.has(row.muscle)) seen.add(row.muscle);
  const muscles = [...seen].sort((a, b) => rank(a) - rank(b) || label(a).localeCompare(label(b), 'pt-BR'));

  function label(muscle) {
    return data.muscleLabels[muscle] || muscle;
  }
  function rank(muscle) {
    const index = MUSCLE_ORDER.indexOf(muscle);
    return index < 0 ? 99 : index;
  }

  return {
    data,
    weeks: data.weeks,
    muscles,
    label,
    /** Linha de volume de um músculo em uma semana (sempre existe para os músculos com faixa-alvo). */
    volumeAt(week, muscle) {
      return volByWeek.get(week)?.get(muscle) || { week, muscle, direct: 0, indirect: 0, sessions: 0, level: 'nenhum' };
    },
    exerciseById: new Map(data.exercises.map((e) => [e.id, e])),
  };
}

export function visibleWeeks(model, state) {
  return state.period > 0 ? model.weeks.slice(-state.period) : model.weeks;
}

/** Os exercícios que passam pelo filtro de músculo. */
export function exercisesFor(model, state) {
  return model.data.exercises.filter((e) => state.muscle === 'all' || e.muscleGroup === state.muscle);
}

/**
 * Evolução semanal de um exercício: para cada semana visível, a maior carga e o melhor 1RM dos treinos dela
 * (null nas semanas sem treino do exercício).
 */
export function weeklyExercise(exercise, weeks) {
  const byWeek = new Map();
  for (const session of exercise.sessions) {
    const week = mondayOf(session.date);
    const current = byWeek.get(week);
    byWeek.set(week, {
      weightKg: Math.max(current?.weightKg ?? 0, session.weightKg),
      oneRepMax: Math.max(current?.oneRepMax ?? 0, session.oneRepMax),
    });
  }
  return weeks.map((w) => byWeek.get(w.week) || null);
}
