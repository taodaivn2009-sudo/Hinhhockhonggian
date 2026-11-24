export interface GraphFunctionItem {
  id: string;
  expression: string;
  color: string;
  visible: boolean;
  showDerivative: boolean;
}

export interface KeyPoint2D {
  x: number;
  y: number;
  kind: 'root' | 'max' | 'min';
}

export interface Preset2DShape {
  id: string;
  title: string;
  points: { name: string; x: number; y: number }[];
  closed?: boolean;
  circleRadius?: number;
}

export const PRESET_2D_SHAPES: Preset2DShape[] = [
  { id: 'empty', title: 'Hệ trục Oxy trống', points: [] },
  {
    id: 'equilateral',
    title: 'Tam giác đều ABC',
    points: [
      { name: 'A', x: 0, y: 2.6 },
      { name: 'B', x: -2.25, y: -1.3 },
      { name: 'C', x: 2.25, y: -1.3 }
    ],
    closed: true
  },
  {
    id: 'right_triangle',
    title: 'Tam giác vuông ABC',
    points: [
      { name: 'A', x: 0, y: 0 },
      { name: 'B', x: 4, y: 0 },
      { name: 'C', x: 0, y: 3 }
    ],
    closed: true
  },
  {
    id: 'square',
    title: 'Hình vuông ABCD',
    points: [
      { name: 'A', x: -2, y: 2 },
      { name: 'B', x: 2, y: 2 },
      { name: 'C', x: 2, y: -2 },
      { name: 'D', x: -2, y: -2 }
    ],
    closed: true
  },
  {
    id: 'rectangle',
    title: 'Hình chữ nhật ABCD',
    points: [
      { name: 'A', x: -3, y: 1.5 },
      { name: 'B', x: 3, y: 1.5 },
      { name: 'C', x: 3, y: -1.5 },
      { name: 'D', x: -3, y: -1.5 }
    ],
    closed: true
  },
  {
    id: 'parallelogram',
    title: 'Hình bình hành ABCD',
    points: [
      { name: 'A', x: -2, y: 2 },
      { name: 'B', x: 3, y: 2 },
      { name: 'C', x: 2, y: -1.5 },
      { name: 'D', x: -3, y: -1.5 }
    ],
    closed: true
  },
  {
    id: 'rhombus',
    title: 'Hình thoi ABCD',
    points: [
      { name: 'A', x: 0, y: 2.5 },
      { name: 'B', x: 3.5, y: 0 },
      { name: 'C', x: 0, y: -2.5 },
      { name: 'D', x: -3.5, y: 0 }
    ],
    closed: true
  },
  {
    id: 'trapezoid',
    title: 'Hình thang cân ABCD',
    points: [
      { name: 'A', x: -1.5, y: 2 },
      { name: 'B', x: 1.5, y: 2 },
      { name: 'C', x: 3, y: -1.5 },
      { name: 'D', x: -3, y: -1.5 }
    ],
    closed: true
  },
  {
    id: 'circle',
    title: 'Đường tròn tâm I (R=3)',
    points: [{ name: 'I', x: 0, y: 0 }],
    circleRadius: 3
  }
];

export const FUNCTION_TEMPLATES = [
  { label: 'Bậc 3', expr: 'x^3 - 3*x' },
  { label: 'Tham số m', expr: 'x^3 - 3*m*x + 1' },
  { label: 'Phân thức', expr: '(2*x - 1)/(x - 1)' },
  { label: 'Lượng giác', expr: '2*sin(x)' },
  { label: 'Mũ e^x', expr: 'e^x - 2' },
  { label: 'Logarit', expr: 'ln(x)' },
  { label: 'Trị tuyệt đối', expr: 'abs(x^2 - 2*x - 3)' }
];

/**
 * Safe recursive-descent mathematical expression evaluator (zero eval/Function).
 * Supports + - * / ^, unary +/-, implicit multiplication (2x, 3sin(x)), x, m, pi, e,
 * sin, cos, tan, asin, acos, atan, sqrt, abs, ln, log, exp.
 */
type Token =
  | { type: 'num'; val: number }
  | { type: 'id'; val: string }
  | { type: 'op'; val: string }
  | { type: 'lparen' }
  | { type: 'rparen' };

function tokenize(raw: string): Token[] {
  const s = raw.replace(/\s+/g, '').replace(/,/g, '.');
  const rawTokens: Token[] = [];
  let i = 0;
  while (i < s.length) {
    const ch = s[i];
    if ((ch >= '0' && ch <= '9') || ch === '.') {
      let j = i;
      while (j < s.length && ((s[j] >= '0' && s[j] <= '9') || s[j] === '.')) j++;
      rawTokens.push({ type: 'num', val: parseFloat(s.slice(i, j)) });
      i = j;
    } else if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')) {
      let j = i;
      while (j < s.length && ((s[j] >= 'a' && s[j] <= 'z') || (s[j] >= 'A' && s[j] <= 'Z'))) j++;
      rawTokens.push({ type: 'id', val: s.slice(i, j).toLowerCase() });
      i = j;
    } else if ('+-*/^'.includes(ch)) {
      rawTokens.push({ type: 'op', val: ch });
      i++;
    } else if (ch === '(') {
      rawTokens.push({ type: 'lparen' });
      i++;
    } else if (ch === ')') {
      rawTokens.push({ type: 'rparen' });
      i++;
    } else {
      i++;
    }
  }

  // Insert implicit multiplication (e.g., 2x -> 2*x, x(x+1) -> x*(x+1), 2sin(x) -> 2*sin(x))
  const out: Token[] = [];
  const fns = new Set(['sin', 'cos', 'tan', 'asin', 'acos', 'atan', 'sqrt', 'abs', 'ln', 'log', 'exp']);
  for (let k = 0; k < rawTokens.length; k++) {
    const cur = rawTokens[k];
    if (out.length > 0) {
      const prev = out[out.length - 1];
      const prevIsVal =
        prev.type === 'num' ||
        prev.type === 'rparen' ||
        (prev.type === 'id' && !fns.has(prev.val));
      const curStartsVal = cur.type === 'id' || cur.type === 'lparen' || cur.type === 'num';
      if (prevIsVal && curStartsVal) {
        out.push({ type: 'op', val: '*' });
      }
    }
    out.push(cur);
  }
  return out;
}

export function evaluateMathExpr(expression: string, x: number, m: number): number {
  const tokens = tokenize(expression);
  if (tokens.length === 0) return NaN;
  let pos = 0;

  function parseAddSub(): number {
    let left = parseMulDiv();
    while (pos < tokens.length && tokens[pos].type === 'op' && (tokens[pos].val === '+' || tokens[pos].val === '-')) {
      const op = (tokens[pos++] as { type: 'op'; val: string }).val;
      const right = parseMulDiv();
      left = op === '+' ? left + right : left - right;
    }
    return left;
  }

  function parseMulDiv(): number {
    let left = parsePower();
    while (pos < tokens.length && tokens[pos].type === 'op' && (tokens[pos].val === '*' || tokens[pos].val === '/')) {
      const op = (tokens[pos++] as { type: 'op'; val: string }).val;
      const right = parsePower();
      left = op === '*' ? left * right : left / right;
    }
    return left;
  }

  function parsePower(): number {
    let base = parseUnary();
    if (pos < tokens.length && tokens[pos].type === 'op' && tokens[pos].val === '^') {
      pos++;
      const exp = parsePower();
      base = Math.pow(base, exp);
    }
    return base;
  }

  function parseUnary(): number {
    if (pos < tokens.length && tokens[pos].type === 'op' && (tokens[pos].val === '+' || tokens[pos].val === '-')) {
      const op = (tokens[pos++] as { type: 'op'; val: string }).val;
      const v = parseUnary();
      return op === '-' ? -v : v;
    }
    return parsePrimary();
  }

  function parsePrimary(): number {
    const tok = tokens[pos];
    if (!tok) return NaN;
    if (tok.type === 'num') {
      pos++;
      return tok.val;
    }
    if (tok.type === 'lparen') {
      pos++;
      const val = parseAddSub();
      if (pos < tokens.length && tokens[pos].type === 'rparen') pos++;
      return val;
    }
    if (tok.type === 'id') {
      pos++;
      const id = tok.val;
      if (id === 'x') return x;
      if (id === 'm') return m;
      if (id === 'pi') return Math.PI;
      if (id === 'e') return Math.E;
      if (pos < tokens.length && tokens[pos].type === 'lparen') {
        pos++;
        const arg = parseAddSub();
        if (pos < tokens.length && tokens[pos].type === 'rparen') pos++;
        switch (id) {
          case 'sin': return Math.sin(arg);
          case 'cos': return Math.cos(arg);
          case 'tan': return Math.tan(arg);
          case 'asin': return Math.asin(arg);
          case 'acos': return Math.acos(arg);
          case 'atan': return Math.atan(arg);
          case 'sqrt': return Math.sqrt(arg);
          case 'abs': return Math.abs(arg);
          case 'ln': return Math.log(arg);
          case 'log': return Math.log10(arg);
          case 'exp': return Math.exp(arg);
          default: return NaN;
        }
      }
      return NaN;
    }
    return NaN;
  }

  try {
    const res = parseAddSub();
    return Number.isFinite(res) ? res : NaN;
  } catch {
    return NaN;
  }
}

export function numericalDerivative(expression: string, x: number, m: number): number {
  const h = 1e-4;
  const f1 = evaluateMathExpr(expression, x + h, m);
  const f0 = evaluateMathExpr(expression, x - h, m);
  if (!Number.isFinite(f1) || !Number.isFinite(f0)) return NaN;
  return (f1 - f0) / (2 * h);
}

export function detectKeyPoints(expression: string, m: number, xMin: number, xMax: number): KeyPoint2D[] {
  const pts: KeyPoint2D[] = [];
  const steps = 280;
  const dx = (xMax - xMin) / steps;
  let prevX = xMin;
  let prevY = evaluateMathExpr(expression, prevX, m);
  let prevD = numericalDerivative(expression, prevX, m);

  for (let i = 1; i <= steps; i++) {
    const x = xMin + i * dx;
    const y = evaluateMathExpr(expression, x, m);
    const d = numericalDerivative(expression, x, m);

    if (Number.isFinite(prevY) && Number.isFinite(y) && Math.abs(y - prevY) < 12) {
      if (prevY * y < 0) {
        const rootX = prevX - (prevY * (x - prevX)) / (y - prevY);
        pts.push({ x: rootX, y: 0, kind: 'root' });
      }
    }
    if (Number.isFinite(prevD) && Number.isFinite(d) && Math.abs(d - prevD) < 25) {
      if (prevD > 0 && d < 0) {
        const ex = (prevX + x) / 2;
        const ey = evaluateMathExpr(expression, ex, m);
        if (Number.isFinite(ey)) pts.push({ x: ex, y: ey, kind: 'max' });
      } else if (prevD < 0 && d > 0) {
        const ex = (prevX + x) / 2;
        const ey = evaluateMathExpr(expression, ex, m);
        if (Number.isFinite(ey)) pts.push({ x: ex, y: ey, kind: 'min' });
      }
    }
    prevX = x;
    prevY = y;
    prevD = d;
  }
  return pts.slice(0, 14);
}
