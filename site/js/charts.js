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

// ---------- plugins ----------

/** Linha vertical com o nome da fase (cutting, bulking) no começo dela. */
const phaseMarkers = {
  id: 'phaseMarkers',
  afterDatasetsDraw(chart, args, opts) {
    if (!opts?.phases?.length) return;
    const { ctx, chartArea, scales } = chart;
    const labels = chart.data.labels;
    const step = (chartArea.right - chartArea.left) / labels.length;
    ctx.save();
    ctx.font = `600 11px ${getComputedStyle(document.body).fontFamily}`;
    for (const phase of opts.phases) {
      if (phase.start < labels[0]) continue; // a fase começou antes do período mostrado
      let index = -1;
      labels.forEach((week, i) => { if (week <= phase.start) index = i; });
      if (index < 0) continue;
      const x = scales.x.getPixelForValue(index) - step / 2;
      ctx.strokeStyle = color('--axis');
      ctx.lineWidth = 1;
      ctx.beginPath();
      ctx.moveTo(x, chartArea.top);
      ctx.lineTo(x, chartArea.bottom);
      ctx.stroke();
      ctx.fillStyle = color('--ink-2');
      ctx.textBaseline = 'top';
      ctx.fillText(phase.name, x + 5, chartArea.top + 2);
    }
    ctx.restore();
  },
};

/** Faixa horizontal translúcida (a faixa-alvo de séries). */
const targetBand = {
  id: 'targetBand',
  beforeDatasetsDraw(chart, args, opts) {
    if (!opts?.min && opts?.min !== 0) return;
    const { ctx, chartArea, scales } = chart;
    const top = scales.y.getPixelForValue(Math.min(opts.max ?? scales.y.max, scales.y.max));
    const bottom = scales.y.getPixelForValue(Math.max(opts.min, scales.y.min));
    ctx.save();
    ctx.fillStyle = color('--band');
    ctx.fillRect(chartArea.left, top, chartArea.right - chartArea.left, bottom - top);
    ctx.restore();
  },
};

/** Linhas de referência (meta de treinos, zero): sólidas e finas, com o rótulo na ponta direita. */
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

/** Nome da série ao lado do último ponto da linha (rotulagem direta). */
const endLabels = {
  id: 'endLabels',
  afterDatasetsDraw(chart) {
    const { ctx } = chart;
    ctx.save();
    ctx.font = `600 11px ${getComputedStyle(document.body).fontFamily}`;
    ctx.fillStyle = color('--ink-2');
    ctx.textBaseline = 'middle';
    chart.data.datasets.forEach((dataset, i) => {
      if (!dataset.endLabel) return;
      const meta = chart.getDatasetMeta(i);
      for (let p = dataset.data.length - 1; p >= 0; p--) {
        if (dataset.data[p] == null) continue;
        const point = meta.data[p];
        ctx.fillText(dataset.endLabel, point.x + 8, point.y);
        break;
      }
    });
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

function baseOptions(isCategoryTime) {
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
          autoSkip: !isCategoryTime,
          callback(value, index) {
            const labels = this.chart.data.labels;
            return isCategoryTime ? monthTick(labels, index) : labels[index];
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
 * Cria um gráfico dentro de `view` (que já precisa estar no documento).
 *
 * @param weeksAxis true quando o eixo x é de semanas (rótulos ISO, mostrados como mês)
 * @param pluginOptions opções dos plugins: phases, band, lines
 */
export function mountChart(view, ctx, { type, labels, datasets, options = {}, plugins = {}, height = '', weeksAxis = true, label }) {
  const canvas = el('canvas', { role: 'img', 'aria-label': label || 'Gráfico' });
  view.append(el('div', { class: `chart-box ${height}` }, canvas));

  const config = {
    type,
    data: { labels, datasets },
    options: deepMerge(baseOptions(weeksAxis), {
      ...options,
      plugins: {
        ...(options.plugins || {}),
        phaseMarkers: { phases: plugins.phases || [] },
        targetBand: plugins.band || {},
        refLines: { lines: plugins.lines || [] },
      },
    }),
    plugins: [phaseMarkers, targetBand, refLines, endLabels],
  };
  // deepMerge copiou as opções; garante que o tooltip não perca os callbacks padrão ao receber os do chamador.
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
