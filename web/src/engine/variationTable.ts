import {
  detectAsymptotes,
  detectKeyPoints,
  evaluateMathExpr,
  format2DNum,
  GraphAsymptote,
  numericalDerivative
} from './geo2d';

export type VariationNodeKind = 'neg_inf' | 'pos_inf' | 'critical' | 'discontinuity' | 'boundary';
export type MonotonyKind = 'inc' | 'dec' | 'const' | 'undef';

export interface VariationNode {
  x: number;
  xLabel: string;
  kind: VariationNodeKind;
  yPrimeLabel: string;
  yLeftLabel: string;
  yCenterLabel: string;
  yRightLabel: string;
}

export interface VariationInterval {
  fromX: number;
  toX: number;
  signLabel: string;
  monotony: MonotonyKind;
}

export interface VariationTableResult {
  expression: string;
  domainText: string;
  nodes: VariationNode[];
  intervals: VariationInterval[];
  asymptotes: string[];
  extrema: string[];
  inflections: string[];
}

function snapNum(v: number): number {
  const nearest = Math.round(v * 100) / 100;
  return Math.abs(nearest - Math.round(nearest)) < 0.015 ? Math.round(nearest) : nearest;
}

function formatLimit(expr: string, xTarget: number, dir: -1 | 1, m: number): string {
  const y1 = evaluateMathExpr(expr, xTarget + dir * 1e-3, m);
  const y2 = evaluateMathExpr(expr, xTarget + dir * 2e-4, m);
  if (!Number.isFinite(y1) && !Number.isFinite(y2)) return '✕';
  const y = Number.isFinite(y2) ? y2 : y1;
  if (Math.abs(y) > 45) return y > 0 ? '+∞' : '-∞';
  return format2DNum(y);
}

function formatLimitInf(expr: string, sign: -1 | 1, m: number): string {
  const y1 = evaluateMathExpr(expr, sign * 400, m);
  const y2 = evaluateMathExpr(expr, sign * 1200, m);
  if (!Number.isFinite(y1) && !Number.isFinite(y2)) return '✕';
  if (Number.isFinite(y1) && Number.isFinite(y2)) {
    if (Math.abs(y2 - y1) < 0.05 && Math.abs(y2) < 80) return format2DNum(y2);
    if (Math.abs(y2) > 80) return y2 > 0 ? '+∞' : '-∞';
    return y2 > y1 ? '+∞' : '-∞';
  }
  return Number.isFinite(y1) ? (y1 > 0 ? '+∞' : '-∞') : '✕';
}

export function analyzeVariationTable(expression: string, m: number): VariationTableResult | null {
  if (!expression.trim()) return null;

  const scanMin = -14;
  const scanMax = 14;
  const asymptotes = detectAsymptotes(expression, m, scanMin, scanMax, '#fbbf24');
  const poles = asymptotes
    .filter((a): a is Extract<GraphAsymptote, { kind: 'vertical' }> => a.kind === 'vertical')
    .map((a) => a.x)
    .sort((a, b) => a - b);

  const keyPts = detectKeyPoints(expression, m, scanMin, scanMax, '#38bdf8');
  const extremaPts = keyPts.filter((k) => k.kind === 'max' || k.kind === 'min');
  const inflectionPts = keyPts.filter((k) => k.kind === 'inflection');

  // Detect domain boundaries (e.g. sqrt(x + 2), ln(x))
  const boundaries: number[] = [];
  const steps = 280;
  const dx = (scanMax - scanMin) / steps;
  let prevDef = Number.isFinite(evaluateMathExpr(expression, scanMin, m));
  for (let i = 1; i <= steps; i++) {
    const cx = scanMin + i * dx;
    const curDef = Number.isFinite(evaluateMathExpr(expression, cx, m));
    if (curDef !== prevDef) {
      let lo = cx - dx;
      let hi = cx;
      for (let k = 0; k < 20; k++) {
        const mid = (lo + hi) * 0.5;
        if (Number.isFinite(evaluateMathExpr(expression, mid, m)) === prevDef) lo = mid;
        else hi = mid;
      }
      const b = snapNum((lo + hi) * 0.5);
      if (!poles.some((p) => Math.abs(p - b) < 0.12) && !boundaries.some((x) => Math.abs(x - b) < 0.12)) {
        boundaries.push(b);
      }
    }
    prevDef = curDef;
  }

  // Raw interior nodes
  const raw: { x: number; kind: VariationNodeKind }[] = [];
  for (const p of poles) raw.push({ x: p, kind: 'discontinuity' });
  for (const b of boundaries) raw.push({ x: b, kind: 'boundary' });
  for (const e of extremaPts) {
    const sx = snapNum(e.x);
    if (!raw.some((r) => Math.abs(r.x - sx) < 0.12)) raw.push({ x: sx, kind: 'critical' });
  }
  raw.sort((a, b) => a.x - b.x);
  const interior = raw.length > 6 ? [...raw].sort((a, b) => Math.abs(a.x) - Math.abs(b.x)).slice(0, 6).sort((a, b) => a.x - b.x) : raw;

  const defNegInf = Number.isFinite(evaluateMathExpr(expression, -80, m));
  const defPosInf = Number.isFinite(evaluateMathExpr(expression, 80, m));

  const nodes: VariationNode[] = [];
  if (defNegInf || interior.length === 0) {
    const lim = formatLimitInf(expression, -1, m);
    nodes.push({
      x: -Infinity,
      xLabel: '-∞',
      kind: 'neg_inf',
      yPrimeLabel: '',
      yLeftLabel: '',
      yCenterLabel: lim,
      yRightLabel: lim
    });
  }

  for (const rn of interior) {
    const xLabel = format2DNum(rn.x);
    if (rn.kind === 'discontinuity') {
      nodes.push({
        x: rn.x,
        xLabel,
        kind: 'discontinuity',
        yPrimeLabel: '||',
        yLeftLabel: formatLimit(expression, rn.x, -1, m),
        yCenterLabel: '||',
        yRightLabel: formatLimit(expression, rn.x, 1, m)
      });
    } else if (rn.kind === 'boundary') {
      const leftDef = Number.isFinite(evaluateMathExpr(expression, rn.x - 0.05, m));
      const rightDef = Number.isFinite(evaluateMathExpr(expression, rn.x + 0.05, m));
      const atVal = evaluateMathExpr(expression, rn.x, m);
      nodes.push({
        x: rn.x,
        xLabel,
        kind: 'boundary',
        yPrimeLabel: Number.isFinite(atVal) ? '|' : '||',
        yLeftLabel: leftDef ? formatLimit(expression, rn.x, -1, m) : '✕',
        yCenterLabel: Number.isFinite(atVal) ? format2DNum(atVal) : '||',
        yRightLabel: rightDef ? formatLimit(expression, rn.x, 1, m) : '✕'
      });
    } else {
      const yVal = evaluateMathExpr(expression, rn.x, m);
      const yStr = Number.isFinite(yVal) ? format2DNum(yVal) : '—';
      const dVal = numericalDerivative(expression, rn.x, m);
      nodes.push({
        x: rn.x,
        xLabel,
        kind: 'critical',
        yPrimeLabel: Number.isFinite(dVal) && Math.abs(dVal) < 0.25 ? '0' : '||',
        yLeftLabel: yStr,
        yCenterLabel: yStr,
        yRightLabel: yStr
      });
    }
  }

  if (defPosInf || nodes.length < 2) {
    const lim = formatLimitInf(expression, 1, m);
    nodes.push({
      x: Infinity,
      xLabel: '+∞',
      kind: 'pos_inf',
      yPrimeLabel: '',
      yLeftLabel: lim,
      yCenterLabel: lim,
      yRightLabel: ''
    });
  }

  // Intervals between consecutive nodes
  const intervals: VariationInterval[] = [];
  for (let i = 0; i < nodes.length - 1; i++) {
    const a = nodes[i].x;
    const b = nodes[i + 1].x;
    const sampleX =
      !Number.isFinite(a) && !Number.isFinite(b)
        ? 0
        : !Number.isFinite(a)
          ? b - 1.2
          : !Number.isFinite(b)
            ? a + 1.2
            : (a + b) * 0.5;
    const yMid = evaluateMathExpr(expression, sampleX, m);
    const dMid = numericalDerivative(expression, sampleX, m);
    if (!Number.isFinite(yMid) || !Number.isFinite(dMid)) {
      intervals.push({ fromX: a, toX: b, signLabel: '///', monotony: 'undef' });
    } else if (Math.abs(dMid) < 1e-4) {
      intervals.push({ fromX: a, toX: b, signLabel: '0', monotony: 'const' });
    } else if (dMid > 0) {
      intervals.push({ fromX: a, toX: b, signLabel: '+', monotony: 'inc' });
    } else {
      intervals.push({ fromX: a, toX: b, signLabel: '−', monotony: 'dec' });
    }
  }

  const domainText =
    poles.length > 0 && boundaries.length === 0
      ? `D = ℝ \\ {${poles.map(format2DNum).join('; ')}}`
      : boundaries.length > 0
        ? `Xác định trên các khoảng không gạch chéo (biên tại x = ${boundaries.map(format2DNum).join('; ')})`
        : 'D = ℝ';

  return {
    expression,
    domainText,
    nodes,
    intervals,
    asymptotes: asymptotes.map((a) => a.label),
    extrema: extremaPts.map(
      (e) => `${e.kind === 'max' ? 'Cực đại' : 'Cực tiểu'}: (${format2DNum(e.x)}; ${format2DNum(e.y)})`
    ),
    inflections: inflectionPts.map((p) => `Điểm uốn: (${format2DNum(p.x)}; ${format2DNum(p.y)})`)
  };
}
