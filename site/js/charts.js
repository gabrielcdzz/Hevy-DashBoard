// Gráficos (Chart.js). As cores vêm das variáveis CSS do tema, lidas na hora de desenhar,
// então claro e escuro usam os mesmos gráficos com cores diferentes.
import { el } from './ui.js';
import { fmtDate, monthTick } from './format.js';

export const color = (name) => getComputedStyle(document.documentElement).getPropertyValue(name).trim();

/** #rrggbb + opacidade → rgba(), porque o canvas não entende color-mix. */
export function alpha(hex, opacity) {
  const value = hex.replace('#', '');
  const r = parseInt(value.slice(0, 2), 16);
  const g = parseInt(value.slice(2, 4), 16);
  const b = parseInt(value.slice(4, 6), 16);
  return `rgba(${r}, ${g}, ${b}, ${opacity})`;
}

// ---------- plugin ----------

/** Linhas de referência (o planejado, zero): sólidas e finas, com o rótulo na ponta direita. */
const refLines = {
  id: 'refLines',
  afterDatasetsDraw(chart, args, opts) {
    if (!opts?.lines?.length) return;
    const { ctx, chartArea, scales } = chart;
    ctx.save();
    ctx.font = `11px ${getComputedStyle(document.body).fontFamily}`;
    for (const line of opts.lines) {
      const y = scales.y.getPixelForValue(line.y);
      if (y < chartArea.top || y > chartArea.bottom) continue;
      ctx.strokeStyle = color(line.strong ? '--ink-2' : '--axis');
      ctx.lineWidth = 1;
      ctx.beginPath();
      ctx.moveTo(chartArea.left, y);
      ctx.lineTo(chartArea.right, y);
      ctx.stroke();
      if (line.label) {
        ctx.fillStyle = color('--ink-2');
        ctx.textAlign = line.left ? 'left' : 'right';
        ctx.textBaseline = 'bottom';
        ctx.fillText(line.label, line.left ? chartArea.left + 4 : chartArea.right - 2, y - 3);
      }
    }
    ctx.restore();
  },
};

// ---------- opções base ----------

function deepMerge(target, source) {
  for (const [key, value] of Object.entries(source || {})) {
    if (value && typeof value === 'object' && !Array.isArray(value)) {
      target[key] = deepMerge(target[key] && typeof target[key] === 'object' ? target[key] : {}, value);
    } else {
      target[key] = value;
    }
  }
  return target;
}

function baseOptions() {
  const font = { family: getComputedStyle(document.body).fontFamily, size: 11 };
  return {
    responsive: true,
    maintainAspectRatio: false,
    animation: false,
    interaction: { mode: 'index', intersect: false },
    layout: { padding: { top: 4, right: 4 } },
    plugins: {
      legend: { display: false },
      tooltip: {
        backgroundColor: color('--surface'),
        titleColor: color('--ink'),
        bodyColor: color('--ink-2'),
        borderColor: color('--axis'),
        borderWidth: 1,
        padding: 10,
        boxPadding: 4,
        usePointStyle: false,
        titleFont: { ...font, size: 12, weight: '600' },
        bodyFont: { ...font, size: 12 },
        callbacks: {
          labelColor: (item) => ({
            borderColor: 'transparent',
            backgroundColor: item.dataset.keyColor || item.dataset.borderColor || color('--s1'),
            borderRadius: 2,
          }),
        },
      },
    },
    scales: {
      x: {
        grid: { display: false },
        border: { color: color('--axis') },
        ticks: {
          color: color('--muted'),
          font,
          maxRotation: 0,
          autoSkip: false,
          // Os rótulos são as segundas-feiras (texto ISO); no eixo aparece só o mês, na primeira semana dele.
          callback(value, index) {
            return monthTick(this.chart.data.labels, index);
          },
        },
      },
      y: {
        beginAtZero: true,
        grid: { color: color('--grid'), lineWidth: 1 },
        border: { display: false },
        ticks: { color: color('--muted'), font, maxTicksLimit: 6 },
      },
    },
  };
}

/**
 * Cria um gráfico de semanas dentro de `view` (que já precisa estar no documento).
 *
 * @param plugins.lines linhas de referência: [{ y, label, strong, left }]
 */
export function mountChart(view, ctx, { type, labels, datasets, options = {}, plugins = {}, height = '', label }) {
  const canvas = el('canvas', { role: 'img', 'aria-label': label || 'Gráfico' });
  view.append(el('div', { class: `chart-box ${height}` }, canvas));

  const config = {
    type,
    data: { labels, datasets },
    options: deepMerge(baseOptions(), {
      ...options,
      plugins: {
        ...(options.plugins || {}),
        refLines: { lines: plugins.lines || [] },
      },
    }),
    plugins: [refLines],
  };
  return ctx.register(new Chart(canvas, config));
}

/** Estilo de coluna: no máximo 24px, canto superior arredondado, base reta. */
export const columnStyle = {
  maxBarThickness: 24,
  borderRadius: { topLeft: 4, topRight: 4 },
  borderSkipped: 'bottom',
};

/** Título do tooltip para um rótulo de semana. */
export const weekTitle = (iso, inProgress) => `Semana de ${fmtDate(iso)}${inProgress ? ' (em andamento)' : ''}`;
