export type Geo2DToolId =
  | 'pan'
  | 'trace'
  | 'point'
  | 'segment'
  | 'line'
  | 'ray'
  | 'vector'
  | 'circle'
  | 'polygon'
  | 'angle';

export const GEO2D_TOOLS: { id: Geo2DToolId; label: string; hint: string }[] = [
  { id: 'pan', label: 'Di chuyển', hint: 'Kéo nền để trượt Oxy • Cuộn để thu phóng • Kéo trực tiếp điểm để dời' },
  { id: 'trace', label: 'Dò đồ thị', hint: 'Chạm & kéo ngang để dò tọa độ (x; f(x)), đạo hàm f\'(x) và tiếp tuyến' },
  { id: 'point', label: 'Điểm', hint: 'Chạm vào mặt phẳng Oxy để đặt điểm mới' },
  { id: 'segment', label: 'Đoạn thẳng', hint: 'Chọn 2 điểm (hoặc chạm 2 vị trí) để vẽ và đo độ dài đoạn thẳng' },
  { id: 'line', label: 'Đường thẳng', hint: 'Chọn 2 điểm để vẽ đường thẳng vô hạn và xem phương trình' },
  { id: 'ray', label: 'Tia', hint: 'Chọn điểm gốc rồi chọn điểm định hướng để vẽ tia' },
  { id: 'vector', label: 'Vectơ', hint: 'Chọn điểm đầu và điểm cuối để vẽ và tính tọa độ vectơ' },
  { id: 'circle', label: 'Đường tròn', hint: 'Chọn tâm đường tròn rồi chọn 1 điểm trên đường tròn' },
  { id: 'polygon', label: 'Đa giác', hint: 'Chạm lần lượt các đỉnh, chạm lại đỉnh đầu tiên để khép kín và tính S, P' },
  { id: 'angle', label: 'Đo góc', hint: 'Chọn lần lượt 3 điểm A → B (đỉnh góc) → C để đo góc ∠ABC' }
];

export interface Geo2DPoint {
  id: string;
  name: string;
  x: number;
  y: number;
  color: string;
}

export type Geo2DObject =
  | { id: string; kind: 'segment'; p1Id: string; p2Id: string; color: string; dashed?: boolean }
  | { id: string; kind: 'line'; p1Id: string; p2Id: string; color: string }
  | { id: string; kind: 'ray'; startId: string; throughId: string; color: string }
  | { id: string; kind: 'vector'; fromId: string; toId: string; color: string }
  | { id: string; kind: 'circle'; centerId: string; edgePointId?: string; fixedRadius?: number; color: string }
  | { id: string; kind: 'polygon'; pointIds: string[]; color: string }
  | { id: string; kind: 'angle'; pAId: string; vertexId: string; pCId: string; color: string };

export interface GraphFunctionItem {
  id: string;
  label: string;
  expression: string;
  color: string;
  visible: boolean;
  showDerivative: boolean;
}

export type KeyPointKind = 'root' | 'y_intercept' | 'max' | 'min' | 'inflection' | 'intersection';

export interface KeyPoint2D {
  x: number;
  y: number;
  kind: KeyPointKind;
  color: string;
}

export type GraphAsymptote =
  | { kind: 'vertical'; x: number; color: string; label: string }
  | { kind: 'horizontal'; y: number; color: string; label: string }
  | { kind: 'oblique'; a: number; b: number; color: string; label: string };

export interface Geo2DSnapshot {
  points: Geo2DPoint[];
  objects: Geo2DObject[];
  functions: GraphFunctionItem[];
  paramM: number;
}

export type FunctionCategoryId = 'exp_log' | 'param' | 'poly' | 'rational' | 'trig' | 'special';

export const FUNCTION_CATEGORIES: { id: FunctionCategoryId; title: string }[] = [
  { id: 'param', title: 'Tham số m' },
  { id: 'exp_log', title: 'Mũ & Loga' },
  { id: 'poly', title: 'Đa thức' },
  { id: 'rational', title: 'Phân thức & Căn' },
  { id: 'trig', title: 'Lượng giác' },
  { id: 'special', title: 'Đặc biệt' }
];

export interface FunctionTemplateItem {
  expr: string;
  note: string;
  category: FunctionCategoryId;
}

export const FUNCTION_LIBRARY: FunctionTemplateItem[] = [
  // Tham số m
  { expr: 'x^3 - 3*m*x + 1', note: 'Bậc 3 thay đổi cực trị', category: 'param' },
  { expr: 'x^4 - 2*m*x^2 + 1', note: 'Trùng phương 1 / 3 cực trị', category: 'param' },
  { expr: '(m*x - 1)/(x - 1)', note: 'Nhất biến thay đổi TCN', category: 'param' },
  { expr: 'm*x^2 - 2*x + 1', note: 'Parabol biện luận nghiệm', category: 'param' },
  { expr: 'x^3 - 3*x + m', note: 'Tịnh tiến đếm giao điểm Ox', category: 'param' },
  { expr: 'sin(m*x)', note: 'Biến đổi tần số sóng', category: 'param' },

  // Mũ & Loga
  { expr: 'e^x', note: 'Hàm mũ tự nhiên', category: 'exp_log' },
  { expr: '2^x', note: 'Hàm mũ cơ số 2', category: 'exp_log' },
  { expr: '(1/2)^x', note: 'Mũ nghịch biến', category: 'exp_log' },
  { expr: 'ln(x)', note: 'Logarit tự nhiên', category: 'exp_log' },
  { expr: 'log(x)', note: 'Logarit thập phân', category: 'exp_log' },
  { expr: 'log2(x)', note: 'Logarit cơ số 2', category: 'exp_log' },
  { expr: 'log(3; x)', note: 'Logarit cơ số 3', category: 'exp_log' },
  { expr: 'x*e^(-x)', note: 'Hàm mũ tắt dần', category: 'exp_log' },

  // Đa thức
  { expr: 'x^2 - 2*x - 1', note: 'Parabol bậc 2', category: 'poly' },
  { expr: 'x^3 - 3*x', note: 'Hàm bậc 3 có 2 cực trị', category: 'poly' },
  { expr: 'x^4 - 4*x^2 + 1', note: 'Hàm trùng phương', category: 'poly' },
  { expr: '2*x + 1', note: 'Đường thẳng bậc 1', category: 'poly' },

  // Phân thức & Căn
  { expr: '1/x', note: 'Hypebol cơ bản', category: 'rational' },
  { expr: '(2*x - 1)/(x - 1)', note: 'Hàm nhất biến', category: 'rational' },
  { expr: '(x^2 + 1)/x', note: 'Có tiệm cận xiên', category: 'rational' },
  { expr: 'sqrt(x + 2)', note: 'Căn bậc 2', category: 'rational' },
  { expr: 'cbrt(x)', note: 'Căn bậc 3', category: 'rational' },

  // Lượng giác
  { expr: 'sin(x)', note: 'Sóng sin cơ bản', category: 'trig' },
  { expr: 'cos(x)', note: 'Sóng cos cơ bản', category: 'trig' },
  { expr: 'tan(x)', note: 'Hàm tang tuần hoàn', category: 'trig' },
  { expr: 'sin(2*x) + cos(x)', note: 'Tổng hợp điều hòa', category: 'trig' },

  // Đặc biệt
  { expr: '|x - 1| - 2', note: 'Trị tuyệt đối chữ V', category: 'special' },
  { expr: '|x^2 - 4|', note: 'Trị tuyệt đối parabol', category: 'special' },
  { expr: 'e^(-x^2)', note: 'Đường cong Gauss', category: 'special' },
  { expr: 'sinh(x)', note: 'Sin hyperbolic', category: 'special' }
];

export const QUICK_MATH_KEYS = [
  'x', 'm', '^2', '^3', '^', 'e^', '2^x', 'ln(', 'log(', 'log2(', 'log(3; ',
  'sqrt(', 'cbrt(', 'sin(', 'cos(', 'tan(', 'abs(', 'pi', '(', ')'
];

export interface Preset2DItem {
  id: string;
  title: string;
  subtitle: string;
}

export const PRESET_2D_SHAPES: Preset2DItem[] = [
  { id: 'empty', title: 'Hệ trục Oxy trống', subtitle: 'Làm mới để vẽ tự do' },
  { id: 'equilateral', title: 'Tam giác đều ABC', subtitle: '3 cạnh bằng nhau, góc 60°' },
  { id: 'right_triangle', title: 'Tam giác vuông ABC', subtitle: 'Vuông tại A, kèm đo góc 90°' },
  { id: 'square', title: 'Hình vuông ABCD', subtitle: '4 cạnh bằng nhau, tính S & P' },
  { id: 'rectangle', title: 'Hình chữ nhật ABCD', subtitle: 'Chiều dài 6, chiều rộng 3' },
  { id: 'parallelogram', title: 'Hình bình hành ABCD', subtitle: 'Cạnh đối song song bằng nhau' },
  { id: 'rhombus', title: 'Hình thoi ABCD', subtitle: '2 đường chéo vuông góc tại O' },
  { id: 'trapezoid', title: 'Hình thang cân ABCD', subtitle: '2 đáy song song, 2 cạnh bên bằng nhau' },
  { id: 'circle', title: 'Đường tròn tâm I (R=3)', subtitle: 'Tâm I(0; 0), bán kính R = 3' }
];

export function buildPreset2DGeometry(id: string): { points: Geo2DPoint[]; objects: Geo2DObject[] } {
  const mkPt = (name: string, x: number, y: number, color = '#38bdf8'): Geo2DPoint => ({
    id: `pt_${id}_${name}`,
    name,
    x,
    y,
    color
  });

  if (id === 'equilateral') {
    const pts = [mkPt('A', 0, 2.6), mkPt('B', -2.25, -1.3), mkPt('C', 2.25, -1.3)];
    return {
      points: pts,
      objects: [
        { id: 'poly_eq', kind: 'polygon', pointIds: pts.map((p) => p.id), color: '#6366f1' },
        { id: 'ang_eq', kind: 'angle', pAId: pts[1].id, vertexId: pts[0].id, pCId: pts[2].id, color: '#fbbf24' }
      ]
    };
  }
  if (id === 'right_triangle') {
    const pts = [mkPt('A', 0, 0), mkPt('B', 4, 0), mkPt('C', 0, 3)];
    return {
      points: pts,
      objects: [
        { id: 'poly_rt', kind: 'polygon', pointIds: pts.map((p) => p.id), color: '#38bdf8' },
        { id: 'ang_rt', kind: 'angle', pAId: pts[1].id, vertexId: pts[0].id, pCId: pts[2].id, color: '#fbbf24' }
      ]
    };
  }
  if (id === 'square') {
    const pts = [mkPt('A', -2, 2), mkPt('B', 2, 2), mkPt('C', 2, -2), mkPt('D', -2, -2)];
    return {
      points: pts,
      objects: [{ id: 'poly_sq', kind: 'polygon', pointIds: pts.map((p) => p.id), color: '#10b981' }]
    };
  }
  if (id === 'rectangle') {
    const pts = [mkPt('A', -3, 1.5), mkPt('B', 3, 1.5), mkPt('C', 3, -1.5), mkPt('D', -3, -1.5)];
    return {
      points: pts,
      objects: [{ id: 'poly_rect', kind: 'polygon', pointIds: pts.map((p) => p.id), color: '#38bdf8' }]
    };
  }
  if (id === 'parallelogram') {
    const pts = [mkPt('A', -2, 2), mkPt('B', 3, 2), mkPt('C', 2, -1.5), mkPt('D', -3, -1.5)];
    return {
      points: pts,
      objects: [{ id: 'poly_par', kind: 'polygon', pointIds: pts.map((p) => p.id), color: '#a855f7' }]
    };
  }
  if (id === 'rhombus') {
    const pts = [mkPt('A', 0, 2.5), mkPt('B', 3.5, 0), mkPt('C', 0, -2.5), mkPt('D', -3.5, 0)];
    return {
      points: pts,
      objects: [
        { id: 'poly_rh', kind: 'polygon', pointIds: pts.map((p) => p.id), color: '#f59e0b' },
        { id: 'seg_ac', kind: 'segment', p1Id: pts[0].id, p2Id: pts[2].id, color: '#94a3b8', dashed: true },
        { id: 'seg_bd', kind: 'segment', p1Id: pts[1].id, p2Id: pts[3].id, color: '#94a3b8', dashed: true }
      ]
    };
  }
  if (id === 'trapezoid') {
    const pts = [mkPt('A', -1.5, 2), mkPt('B', 1.5, 2), mkPt('C', 3, -1.5), mkPt('D', -3, -1.5)];
    return {
      points: pts,
      objects: [{ id: 'poly_tr', kind: 'polygon', pointIds: pts.map((p) => p.id), color: '#ec4899' }]
    };
  }
  if (id === 'circle') {
    const pts = [mkPt('I', 0, 0, '#10b981'), mkPt('M', 3, 0, '#38bdf8')];
    return {
      points: pts,
      objects: [{ id: 'cir_1', kind: 'circle', centerId: pts[0].id, edgePointId: pts[1].id, color: '#10b981' }]
    };
  }
  return { points: [], objects: [] };
}

export function format2DNum(v: number): string {
  if (!Number.isFinite(v)) return '—';
  if (Math.abs(v) < 1e-4) return '0';
  const r = Math.round(v * 100) / 100;
  if (Math.abs(r - Math.round(r)) < 1e-4) return String(Math.round(r));
  return r.toFixed(2).replace(/0+$/, '').replace(/\.$/, '');
}

export function polygonAreaPerimeter2D(pts: Geo2DPoint[]): { area: number; perimeter: number } {
  if (pts.length < 3) return { area: 0, perimeter: 0 };
  let sum = 0;
  let perim = 0;
  for (let i = 0; i < pts.length; i++) {
    const a = pts[i];
    const b = pts[(i + 1) % pts.length];
    sum += a.x * b.y - b.x * a.y;
    perim += Math.hypot(b.x - a.x, b.y - a.y);
  }
  return { area: Math.abs(sum) * 0.5, perimeter: perim };
}

export function lineEquation2D(p1: Geo2DPoint, p2: Geo2DPoint): string {
  const dx = p2.x - p1.x;
  const dy = p2.y - p1.y;
  if (Math.hypot(dx, dy) < 1e-5) return 'Trùng điểm';
  if (Math.abs(dx) < 1e-5) return `x = ${format2DNum(p1.x)}`;
  const a = dy / dx;
  const b = p1.y - a * p1.x;
  const aStr = Math.abs(a) < 1e-4 ? '' : Math.abs(a - 1) < 1e-4 ? 'x' : Math.abs(a + 1) < 1e-4 ? '-x' : `${format2DNum(a)}x`;
  const bStr =
    Math.abs(b) < 1e-4
      ? aStr ? '' : '0'
      : aStr
        ? b > 0 ? ` + ${format2DNum(b)}` : ` - ${format2DNum(-b)}`
        : format2DNum(b);
  return `y = ${aStr}${bStr}`;
}

export function angleDegrees2D(a: Geo2DPoint, vertex: Geo2DPoint, c: Geo2DPoint): number {
  const v1x = a.x - vertex.x;
  const v1y = a.y - vertex.y;
  const v2x = c.x - vertex.x;
  const v2y = c.y - vertex.y;
  const l1 = Math.hypot(v1x, v1y);
  const l2 = Math.hypot(v2x, v2y);
  if (l1 < 1e-5 || l2 < 1e-5) return 0;
  const cosVal = Math.min(1, Math.max(-1, (v1x * v2x + v1y * v2y) / (l1 * l2)));
  return (Math.acos(cosVal) * 180) / Math.PI;
}

/**
 * Preprocess |expr| into abs(expr) safely for nested/simple vertical bars.
 */
function preprocessExpression(raw: string): string {
  let s = raw
    .trim()
    .replace(/π/g, 'pi')
    .replace(/√/g, 'sqrt')
    .replace(/×/g, '*')
    .replace(/÷/g, '/')
    .replace(/−/g, '-');

  // Convert simple |...| to abs(...)
  while (s.includes('|')) {
    const first = s.indexOf('|');
    const second = s.indexOf('|', first + 1);
    if (second === -1) break;
    s = `${s.slice(0, first)}abs(${s.slice(first + 1, second)})${s.slice(second + 1)}`;
  }
  return s;
}

type Token =
  | { type: 'num'; val: number }
  | { type: 'id'; val: string }
  | { type: 'op'; val: string }
  | { type: 'semi' }
  | { type: 'lparen' }
  | { type: 'rparen' };

const FUNC_NAMES = new Set([
  'sin', 'cos', 'tan', 'cot', 'asin', 'acos', 'atan',
  'sinh', 'cosh', 'tanh',
  'sqrt', 'cbrt', 'abs', 'ln', 'log', 'log2', 'log10', 'exp'
]);

function tokenize(raw: string): Token[] {
  const s = preprocessExpression(raw).replace(/\s+/g, '');
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
      while (
        j < s.length &&
        ((s[j] >= 'a' && s[j] <= 'z') || (s[j] >= 'A' && s[j] <= 'Z') || (s[j] >= '0' && s[j] <= '9'))
      ) {
        j++;
      }
      rawTokens.push({ type: 'id', val: s.slice(i, j).toLowerCase() });
      i = j;
    } else if ('+-*/^'.includes(ch)) {
      rawTokens.push({ type: 'op', val: ch });
      i++;
    } else if (ch === ';' || ch === ',') {
      rawTokens.push({ type: 'semi' });
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

  // Insert implicit multiplication (2x -> 2*x, 2sin(x) -> 2*sin(x), (x+1)(x-1) -> (x+1)*(x-1))
  const out: Token[] = [];
  for (let k = 0; k < rawTokens.length; k++) {
    const cur = rawTokens[k];
    if (out.length > 0) {
      const prev = out[out.length - 1];
      const prevIsVal =
        prev.type === 'num' ||
        prev.type === 'rparen' ||
        (prev.type === 'id' && !FUNC_NAMES.has(prev.val));
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

  function peekOp(): string | null {
    const tok = tokens[pos];
    return tok && tok.type === 'op' ? tok.val : null;
  }

  function parseAddSub(): number {
    let left = parseMulDiv();
    let op = peekOp();
    while (op === '+' || op === '-') {
      pos++;
      const right = parseMulDiv();
      left = op === '+' ? left + right : left - right;
      op = peekOp();
    }
    return left;
  }

  function parseMulDiv(): number {
    let left = parsePower();
    let op = peekOp();
    while (op === '*' || op === '/') {
      pos++;
      const right = parsePower();
      left = op === '*' ? left * right : left / right;
      op = peekOp();
    }
    return left;
  }

  function parsePower(): number {
    let base = parseUnary();
    if (peekOp() === '^') {
      pos++;
      const exp = parsePower();
      base = Math.pow(base, exp);
    }
    return base;
  }

  function parseUnary(): number {
    const op = peekOp();
    if (op === '+' || op === '-') {
      pos++;
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
        const arg1 = parseAddSub();
        let arg2: number | null = null;
        if (pos < tokens.length && tokens[pos].type === 'semi') {
          pos++;
          arg2 = parseAddSub();
        }
        if (pos < tokens.length && tokens[pos].type === 'rparen') pos++;
        switch (id) {
          case 'sin': return Math.sin(arg1);
          case 'cos': return Math.cos(arg1);
          case 'tan': return Math.tan(arg1);
          case 'cot': return 1 / Math.tan(arg1);
          case 'asin': return Math.asin(arg1);
          case 'acos': return Math.acos(arg1);
          case 'atan': return Math.atan(arg1);
          case 'sinh': return Math.sinh(arg1);
          case 'cosh': return Math.cosh(arg1);
          case 'tanh': return Math.tanh(arg1);
          case 'sqrt': return Math.sqrt(arg1);
          case 'cbrt': return Math.cbrt(arg1);
          case 'abs': return Math.abs(arg1);
          case 'ln': return Math.log(arg1);
          case 'log2': return Math.log2(arg1);
          case 'log10': return Math.log10(arg1);
          case 'log':
            return arg2 !== null ? Math.log(arg2) / Math.log(arg1) : Math.log10(arg1);
          case 'exp': return Math.exp(arg1);
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

export function evaluateVerifyAnswer(rawInput: string): number | null {
  const cleaned = rawInput.trim().replace(/°/g, '').replace(/,/g, '.');
  if (!cleaned) return null;
  const v0 = evaluateMathExpr(cleaned, 0, 0);
  const v1 = evaluateMathExpr(cleaned, 1.73205, 0);
  if (!Number.isFinite(v0) || !Number.isFinite(v1) || Math.abs(v0 - v1) > 1e-6) return null;
  return v0;
}

export function isVerifyAnswerCorrect(expected: number, userVal: number): boolean {
  if (!Number.isFinite(expected) || !Number.isFinite(userVal)) return false;
  const tol = Math.max(0.03, Math.abs(expected) * 0.015);
  return Math.abs(expected - userVal) <= tol;
}

export function numericalDerivative(expression: string, x: number, m: number): number {
  const h = 1e-4;
  const f1 = evaluateMathExpr(expression, x + h, m);
  const f0 = evaluateMathExpr(expression, x - h, m);
  if (!Number.isFinite(f1) || !Number.isFinite(f0)) return NaN;
  return (f1 - f0) / (2 * h);
}

export function numericalSecondDerivative(expression: string, x: number, m: number): number {
  const h = 2e-3;
  const d1 = numericalDerivative(expression, x + h, m);
  const d0 = numericalDerivative(expression, x - h, m);
  if (!Number.isFinite(d1) || !Number.isFinite(d0)) return NaN;
  return (d1 - d0) / (2 * h);
}

export function detectKeyPoints(expression: string, m: number, xMin: number, xMax: number, color: string): KeyPoint2D[] {
  const pts: KeyPoint2D[] = [];
  const addNear = (pt: KeyPoint2D) => {
    if (!pts.some((p) => p.kind === pt.kind && Math.hypot(p.x - pt.x, p.y - pt.y) < 0.12)) {
      pts.push(pt);
    }
  };

  // Y-intercept (0, f(0))
  if (xMin <= 0 && xMax >= 0) {
    const y0 = evaluateMathExpr(expression, 0, m);
    if (Number.isFinite(y0) && Math.abs(y0) < 100) {
      addNear({ x: 0, y: y0, kind: 'y_intercept', color });
    }
  }

  const steps = 300;
  const dx = (xMax - xMin) / steps;
  let prevX = xMin;
  let prevY = evaluateMathExpr(expression, prevX, m);
  let prevD = numericalDerivative(expression, prevX, m);
  let prevD2 = numericalSecondDerivative(expression, prevX, m);

  for (let i = 1; i <= steps; i++) {
    const x = xMin + i * dx;
    const y = evaluateMathExpr(expression, x, m);
    const d = numericalDerivative(expression, x, m);
    const d2 = numericalSecondDerivative(expression, x, m);

    if (Number.isFinite(prevY) && Number.isFinite(y) && Math.abs(y - prevY) < 14) {
      if (prevY * y < 0) {
        const rootX = prevX - (prevY * (x - prevX)) / (y - prevY);
        addNear({ x: rootX, y: 0, kind: 'root', color });
      }
    }
    if (Number.isFinite(prevD) && Number.isFinite(d) && Math.abs(d - prevD) < 28) {
      if (prevD > 0 && d < 0) {
        const ex = prevX - (prevD * (x - prevX)) / (d - prevD);
        const ey = evaluateMathExpr(expression, ex, m);
        if (Number.isFinite(ey) && Math.abs(ey) < 100) addNear({ x: ex, y: ey, kind: 'max', color });
      } else if (prevD < 0 && d > 0) {
        const ex = prevX - (prevD * (x - prevX)) / (d - prevD);
        const ey = evaluateMathExpr(expression, ex, m);
        if (Number.isFinite(ey) && Math.abs(ey) < 100) addNear({ x: ex, y: ey, kind: 'min', color });
      }
    }
    if (Number.isFinite(prevD2) && Number.isFinite(d2) && Math.abs(d2 - prevD2) < 40) {
      if (prevD2 * d2 < 0) {
        const ix = (prevX + x) / 2;
        const iy = evaluateMathExpr(expression, ix, m);
        if (Number.isFinite(iy) && Math.abs(iy) < 100) addNear({ x: ix, y: iy, kind: 'inflection', color });
      }
    }

    prevX = x;
    prevY = y;
    prevD = d;
    prevD2 = d2;
  }
  return pts.slice(0, 18);
}

export function detectIntersections(
  expr1: string,
  expr2: string,
  m: number,
  xMin: number,
  xMax: number
): KeyPoint2D[] {
  const out: KeyPoint2D[] = [];
  const steps = 260;
  const dx = (xMax - xMin) / steps;
  let prevX = xMin;
  let prevDiff = evaluateMathExpr(expr1, prevX, m) - evaluateMathExpr(expr2, prevX, m);

  for (let i = 1; i <= steps; i++) {
    const x = xMin + i * dx;
    const y1 = evaluateMathExpr(expr1, x, m);
    const y2 = evaluateMathExpr(expr2, x, m);
    const diff = y1 - y2;
    if (Number.isFinite(prevDiff) && Number.isFinite(diff) && Math.abs(diff - prevDiff) < 14) {
      if (prevDiff * diff < 0) {
        const ix = prevX - (prevDiff * (x - prevX)) / (diff - prevDiff);
        const iy = evaluateMathExpr(expr1, ix, m);
        if (Number.isFinite(iy) && Math.abs(iy) < 100) {
          out.push({ x: ix, y: iy, kind: 'intersection', color: '#f43f5e' });
        }
      }
    }
    prevX = x;
    prevDiff = diff;
  }
  return out.slice(0, 10);
}

export function detectAsymptotes(
  expression: string,
  m: number,
  xMin: number,
  xMax: number,
  color: string
): GraphAsymptote[] {
  const out: GraphAsymptote[] = [];

  // 1. Vertical asymptotes (sign-flip large jump)
  const steps = 320;
  const dx = (xMax - xMin) / steps;
  let prevX = xMin;
  let prevY = evaluateMathExpr(expression, prevX, m);
  const vXs: number[] = [];

  for (let i = 1; i <= steps; i++) {
    const x = xMin + i * dx;
    const y = evaluateMathExpr(expression, x, m);
    const jumpSign = Number.isFinite(prevY) && Number.isFinite(y) && prevY * y < 0 && Math.abs(y - prevY) > 18;
    const poleSpike =
      (Number.isFinite(prevY) && Math.abs(prevY) > 40 && !Number.isFinite(y)) ||
      (!Number.isFinite(prevY) && Number.isFinite(y) && Math.abs(y) > 40);
    if (jumpSign || poleSpike) {
      let lo = prevX;
      let hi = x;
      for (let k = 0; k < 18; k++) {
        const mid = (lo + hi) * 0.5;
        const ym = evaluateMathExpr(expression, mid, m);
        if (!Number.isFinite(ym) || Math.abs(ym) > 1e5) {
          lo = mid;
          hi = mid;
          break;
        }
        if (Number.isFinite(prevY) && ym * prevY > 0) lo = mid;
        else hi = mid;
      }
      const px = Math.round(((lo + hi) * 0.5) * 100) / 100;
      const yL = evaluateMathExpr(expression, px - 1e-3, m);
      const yR = evaluateMathExpr(expression, px + 1e-3, m);
      if ((Math.abs(yL) > 25 || Math.abs(yR) > 25) && !vXs.some((vx) => Math.abs(vx - px) < 0.15)) {
        vXs.push(px);
        out.push({ kind: 'vertical', x: px, color, label: `TCĐ: x = ${format2DNum(px)}` });
      }
    }
    prevX = x;
    prevY = y;
  }

  // 2. Horizontal or Oblique asymptotes at ±∞
  const checkEnd = (sign: 1 | -1) => {
    const x1 = sign * 1200;
    const x2 = sign * 2400;
    const y1 = evaluateMathExpr(expression, x1, m);
    const y2 = evaluateMathExpr(expression, x2, m);
    if (!Number.isFinite(y1) || !Number.isFinite(y2)) return;

    // Horizontal: y2 ≈ y1
    if (Math.abs(y2 - y1) < 0.02 && Math.abs(y2) < 80) {
      const hy = Math.round(y2 * 100) / 100;
      if (!out.some((a) => a.kind === 'horizontal' && Math.abs(a.y - hy) < 0.08)) {
        out.push({ kind: 'horizontal', y: hy, color, label: `TCN: y = ${format2DNum(hy)}` });
      }
      return;
    }

    // Oblique: y ≈ a*x + b
    const a1 = y1 / x1;
    const a2 = y2 / x2;
    if (Math.abs(a2) > 0.02 && Math.abs(a2) < 30 && Math.abs(a2 - a1) < 0.01) {
      const b1 = y1 - a2 * x1;
      const b2 = y2 - a2 * x2;
      if (Math.abs(b2 - b1) < 0.05 && Math.abs(b2) < 60) {
        const ra = Math.round(a2 * 100) / 100;
        const rb = Math.round(b2 * 100) / 100;
        if (!out.some((a) => a.kind === 'oblique' && Math.abs(a.a - ra) < 0.05 && Math.abs(a.b - rb) < 0.08)) {
          const aStr = Math.abs(ra - 1) < 1e-3 ? 'x' : Math.abs(ra + 1) < 1e-3 ? '-x' : `${format2DNum(ra)}x`;
          const bStr = Math.abs(rb) < 1e-3 ? '' : rb > 0 ? ` + ${format2DNum(rb)}` : ` - ${format2DNum(-rb)}`;
          out.push({ kind: 'oblique', a: ra, b: rb, color, label: `TCX: y = ${aStr}${bStr}` });
        }
      }
    }
  };

  checkEnd(1);
  checkEnd(-1);
  return out.slice(0, 8);
}
