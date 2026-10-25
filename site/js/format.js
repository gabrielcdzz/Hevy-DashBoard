// Formatação de números e datas em português do Brasil.
// Datas chegam como texto ISO (2026-10-24) e são tratadas em UTC para o fuso do navegador não deslocar o dia.

const nf0 = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 0 });
const nf1 = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 1 });
const nf1fixed = new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 });

export const num = (n) => nf0.format(n);
export const num1 = (n) => nf1.format(n);
export const kg = (n) => `${nf1.format(n)} kg`;

/** Variação com sinal: +12% ou −8%. */
export function signedPercent(n) {
  const sign = n > 0 ? '+' : n < 0 ? '−' : '';
  return `${sign}${nf0.format(Math.abs(n))}%`;
}

/** Toneladas com uma casa: 31,1 t. */
export const tons = (kgValue) => `${nf1fixed.format(kgValue / 1000)} t`;

export function minutes(value) {
  const m = Math.round(value);
  if (m < 60) return `${m} min`;
  const h = Math.floor(m / 60);
  const rest = m % 60;
  return rest === 0 ? `${h} h` : `${h} h ${rest} min`;
}

export function parseISO(iso) {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d));
}

export function toISO(date) {
  return date.toISOString().slice(0, 10);
}

export function addDays(iso, days) {
  const date = parseISO(iso);
  date.setUTCDate(date.getUTCDate() + days);
  return toISO(date);
}

/** A segunda-feira da semana de uma data. */
export function mondayOf(iso) {
  const date = parseISO(iso);
  const sinceMonday = (date.getUTCDay() + 6) % 7;
  date.setUTCDate(date.getUTCDate() - sinceMonday);
  return toISO(date);
}

/** 24/10/2026 */
export function fmtDate(iso) {
  const [y, m, d] = iso.split('-');
  return `${d}/${m}/${y}`;
}

/** 24/10 */
export function fmtDay(iso) {
  const [, m, d] = iso.split('-');
  return `${d}/${m}`;
}

/** 24/10/26 */
export function fmtDateShort(iso) {
  const [y, m, d] = iso.split('-');
  return `${d}/${m}/${y.slice(2)}`;
}

const MONTHS = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez'];
const MONTHS_LONG = ['janeiro', 'fevereiro', 'março', 'abril', 'maio', 'junho', 'julho', 'agosto', 'setembro', 'outubro', 'novembro', 'dezembro'];
const WEEKDAYS = ['domingo', 'segunda-feira', 'terça-feira', 'quarta-feira', 'quinta-feira', 'sexta-feira', 'sábado'];

export const monthShort = (iso) => MONTHS[Number(iso.slice(5, 7)) - 1];
export const monthLong = (iso) => MONTHS_LONG[Number(iso.slice(5, 7)) - 1];
export const weekdayName = (iso) => WEEKDAYS[parseISO(iso).getUTCDay()];

/** Rótulo do eixo: o mês aparece só na primeira semana dele (e o ano, em janeiro). */
export function monthTick(labels, index) {
  const iso = labels[index];
  const prev = index > 0 ? labels[index - 1] : null;
  if (prev && prev.slice(0, 7) === iso.slice(0, 7)) return '';
  // O primeiro rótulo some se o mês seguinte começa logo depois, senão os dois ficam colados.
  if (index === 0 && labels[1] && labels[1].slice(0, 7) !== iso.slice(0, 7)) return '';
  const month = monthShort(iso);
  return iso.slice(5, 7) === '01' || index === 0 ? `${month}/${iso.slice(2, 4)}` : month;
}

/** Ordem fixa dos músculos nas listas, para a posição de cada um não mudar entre semanas. */
export const MUSCLE_ORDER = ['chest', 'back', 'shoulders', 'biceps', 'triceps', 'forearms',
  'quadriceps', 'hamstrings', 'glutes', 'calves', 'abdominals'];
