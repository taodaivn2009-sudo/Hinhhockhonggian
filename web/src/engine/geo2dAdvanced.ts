import { evaluateMathExpr, format2DNum, numericalDerivative } from './geo2d';

// ==================== SYMBOLIC DIFFERENTIATOR (AST -> f'(x) string) ====================

type AstNode =
  | { kind: 'num'; val: number }
  | { kind: 'x' }
  | { kind: 'm' }
  | { kind: 'neg'; inner: AstNode }
  | { kind: 'bin'; op: '+' | '-' | '*' | '/' | '^'; left: AstNode; right: AstNode }
  | { kind: 'fn'; name: string; arg: AstNode; base?: AstNode };

type AstToken =
  | { type: 'num'; val: number }
  | { type: 'id'; val: string }
  | { type: 'op'; val: '+' | '-' | '*' | '/' | '^' }
  | { type: 'semi' }
  | { type: 'lparen' }
  | { type: 'rparen' };

const AST_FUNCS = new Set([
  'sin', 'cos', 'tan', 'cot', 'asin', 'acos', 'atan',
  'sinh', 'cosh', 'tanh', 'sqrt', 'cbrt', 'abs', 'ln', 'log', 'log2', 'log10', 'exp'
]);

function preprocessRaw(raw: string): string {
  let s = raw
    .trim()
    .replace(/π/g, 'pi')
    .replace(/√/g, 'sqrt')
    .replace(/×/g, '*')
    .replace(/·/g, '*')
    .replace(/÷/g, '/')
    .replace(/−/g, '-');
  while (s.includes('|')) {
    const a = s.indexOf('|');
    const b = s.indexOf('|', a + 1);
    if (b === -1) break;
    s = `${s.slice(0, a)}abs(${s.slice(a + 1, b)})${s.slice(b + 1)}`;
  }
  return s.replace(/\s+/g, '');
}

function tokenizeAst(raw: string): AstToken[] {
  const s = preprocessRaw(raw);
  const rawToks: AstToken[] = [];
  let i = 0;
  while (i < s.length) {
    const ch = s[i];
    if ((ch >= '0' && ch <= '9') || ch === '.') {
      let j = i;
      while (j < s.length && ((s[j] >= '0' && s[j] <= '9') || s[j] === '.')) j++;
      rawToks.push({ type: 'num', val: parseFloat(s.slice(i, j)) });
      i = j;
    } else if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')) {
      let j = i;
      while (
        j < s.length &&
        ((s[j] >= 'a' && s[j] <= 'z') || (s[j] >= 'A' && s[j] <= 'Z') || (s[j] >= '0' && s[j] <= '9'))
      ) {
        j++;
      }
      rawToks.push({ type: 'id', val: s.slice(i, j).toLowerCase() });
      i = j;
    } else if (ch === '+' || ch === '-' || ch === '*' || ch === '/' || ch === '^') {
      rawToks.push({ type: 'op', val: ch });
      i++;
    } else if (ch === ';' || ch === ',') {
      rawToks.push({ type: 'semi' });
      i++;
    } else if (ch === '(') {
      rawToks.push({ type: 'lparen' });
      i++;
    } else if (ch === ')') {
      rawToks.push({ type: 'rparen' });
      i++;
    } else {
      i++;
    }
  }
  const out: AstToken[] = [];
  for (const cur of rawToks) {
    if (out.length > 0) {
      const prev = out[out.length - 1];
      const prevIsVal =
        prev.type === 'num' || prev.type === 'rparen' || (prev.type === 'id' && !AST_FUNCS.has(prev.val));
      const curStartsVal = cur.type === 'id' || cur.type === 'lparen' || cur.type === 'num';
      if (prevIsVal && curStartsVal) out.push({ type: 'op', val: '*' });
    }
    out.push(cur);
  }
  return out;
}

function parseToAst(raw: string): AstNode | null {
  const toks = tokenizeAst(raw);
  if (toks.length === 0) return null;
  let pos = 0;

  const peekOp = (): '+' | '-' | '*' | '/' | '^' | null => {
    const t = toks[pos];
    return t && t.type === 'op' ? t.val : null;
  };

  function parseAdd(): AstNode {
    let left = parseMul();
    let op = peekOp();
    while (op === '+' || op === '-') {
      pos++;
      left = { kind: 'bin', op, left, right: parseMul() };
      op = peekOp();
    }
    return left;
  }

  function parseMul(): AstNode {
    let left = parsePow();
    let op = peekOp();
    while (op === '*' || op === '/') {
      pos++;
      left = { kind: 'bin', op, left, right: parsePow() };
      op = peekOp();
    }
    return left;
  }

  function parsePow(): AstNode {
    const base = parseUnary();
    if (peekOp() === '^') {
      pos++;
      return { kind: 'bin', op: '^', left: base, right: parsePow() };
    }
    return base;
  }

  function parseUnary(): AstNode {
    const op = peekOp();
    if (op === '+') {
      pos++;
      return parseUnary();
    }
    if (op === '-') {
      pos++;
      return { kind: 'neg', inner: parsePow() };
    }
    return parsePrimary();
  }

  function parsePrimary(): AstNode {
    const t = toks[pos];
    if (!t) throw new Error('EOF');
    if (t.type === 'num') {
      pos++;
      return { kind: 'num', val: t.val };
    }
    if (t.type === 'lparen') {
      pos++;
      const inner = parseAdd();
      if (toks[pos]?.type === 'rparen') pos++;
      return inner;
    }
    if (t.type === 'id') {
      pos++;
      if (t.val === 'x') return { kind: 'x' };
      if (t.val === 'm') return { kind: 'm' };
      if (t.val === 'pi') return { kind: 'num', val: Math.PI };
      if (t.val === 'e') return { kind: 'num', val: Math.E };
      if (AST_FUNCS.has(t.val) && toks[pos]?.type === 'lparen') {
        pos++;
        const first = parseAdd();
        if (t.val === 'log' && toks[pos]?.type === 'semi') {
          pos++;
          const second = parseAdd();
          if (toks[pos]?.type === 'rparen') pos++;
          return { kind: 'fn', name: 'log', arg: second, base: first };
        }
        if (toks[pos]?.type === 'rparen') pos++;
        return { kind: 'fn', name: t.val, arg: first };
      }
    }
    throw new Error('Bad token');
  }

  try {
    return parseAdd();
  } catch {
    return null;
  }
}

function hasX(n: AstNode): boolean {
  switch (n.kind) {
    case 'num':
    case 'm':
      return false;
    case 'x':
      return true;
    case 'neg':
      return hasX(n.inner);
    case 'bin':
      return hasX(n.left) || hasX(n.right);
    case 'fn':
      return hasX(n.arg) || (n.base ? hasX(n.base) : false);
  }
}

function diffAst(n: AstNode): AstNode | null {
  switch (n.kind) {
    case 'num':
    case 'm':
      return { kind: 'num', val: 0 };
    case 'x':
      return { kind: 'num', val: 1 };
    case 'neg': {
      const di = diffAst(n.inner);
      return di ? { kind: 'neg', inner: di } : null;
    }
    case 'bin': {
      const dl = diffAst(n.left);
      const dr = diffAst(n.right);
      if (!dl || !dr) return null;
      if (n.op === '+' || n.op === '-') return { kind: 'bin', op: n.op, left: dl, right: dr };
      if (n.op === '*') {
        return {
          kind: 'bin',
          op: '+',
          left: { kind: 'bin', op: '*', left: dl, right: n.right },
          right: { kind: 'bin', op: '*', left: n.left, right: dr }
        };
      }
      if (n.op === '/') {
        return {
          kind: 'bin',
          op: '/',
          left: {
            kind: 'bin',
            op: '-',
            left: { kind: 'bin', op: '*', left: dl, right: n.right },
            right: { kind: 'bin', op: '*', left: n.left, right: dr }
          },
          right: { kind: 'bin', op: '^', left: n.right, right: { kind: 'num', val: 2 } }
        };
      }
      // Power u^v
      const lx = hasX(n.left);
      const rx = hasX(n.right);
      if (!lx && !rx) return { kind: 'num', val: 0 };
      if (lx && !rx) {
        return {
          kind: 'bin',
          op: '*',
          left: {
            kind: 'bin',
            op: '*',
            left: n.right,
            right: {
              kind: 'bin',
              op: '^',
              left: n.left,
              right: { kind: 'bin', op: '-', left: n.right, right: { kind: 'num', val: 1 } }
            }
          },
          right: dl
        };
      }
      if (!lx && rx) {
        const isE = n.left.kind === 'num' && Math.abs(n.left.val - Math.E) < 1e-9;
        const lnFactor: AstNode = isE ? { kind: 'num', val: 1 } : { kind: 'fn', name: 'ln', arg: n.left };
        return { kind: 'bin', op: '*', left: { kind: 'bin', op: '*', left: n, right: lnFactor }, right: dr };
      }
      return {
        kind: 'bin',
        op: '*',
        left: n,
        right: {
          kind: 'bin',
          op: '+',
          left: { kind: 'bin', op: '*', left: dr, right: { kind: 'fn', name: 'ln', arg: n.left } },
          right: { kind: 'bin', op: '/', left: { kind: 'bin', op: '*', left: n.right, right: dl }, right: n.left }
        }
      };
    }
    case 'fn': {
      const du = diffAst(n.arg);
      if (!du) return null;
      let outer: AstNode | null = null;
      switch (n.name) {
        case 'sin':
          outer = { kind: 'fn', name: 'cos', arg: n.arg };
          break;
        case 'cos':
          outer = { kind: 'neg', inner: { kind: 'fn', name: 'sin', arg: n.arg } };
          break;
        case 'tan':
          outer = {
            kind: 'bin',
            op: '/',
            left: { kind: 'num', val: 1 },
            right: { kind: 'bin', op: '^', left: { kind: 'fn', name: 'cos', arg: n.arg }, right: { kind: 'num', val: 2 } }
          };
          break;
        case 'cot':
          outer = {
            kind: 'bin',
            op: '/',
            left: { kind: 'num', val: -1 },
            right: { kind: 'bin', op: '^', left: { kind: 'fn', name: 'sin', arg: n.arg }, right: { kind: 'num', val: 2 } }
          };
          break;
        case 'sinh':
          outer = { kind: 'fn', name: 'cosh', arg: n.arg };
          break;
        case 'cosh':
          outer = { kind: 'fn', name: 'sinh', arg: n.arg };
          break;
        case 'tanh':
          outer = {
            kind: 'bin',
            op: '/',
            left: { kind: 'num', val: 1 },
            right: { kind: 'bin', op: '^', left: { kind: 'fn', name: 'cosh', arg: n.arg }, right: { kind: 'num', val: 2 } }
          };
          break;
        case 'sqrt':
          outer = {
            kind: 'bin',
            op: '/',
            left: { kind: 'num', val: 1 },
            right: { kind: 'bin', op: '*', left: { kind: 'num', val: 2 }, right: { kind: 'fn', name: 'sqrt', arg: n.arg } }
          };
          break;
        case 'cbrt':
          outer = {
            kind: 'bin',
            op: '/',
            left: { kind: 'num', val: 1 },
            right: {
              kind: 'bin',
              op: '*',
              left: { kind: 'num', val: 3 },
              right: { kind: 'bin', op: '^', left: n.arg, right: { kind: 'bin', op: '/', left: { kind: 'num', val: 2 }, right: { kind: 'num', val: 3 } } }
            }
          };
          break;
        case 'abs':
          outer = { kind: 'bin', op: '/', left: n.arg, right: { kind: 'fn', name: 'abs', arg: n.arg } };
          break;
        case 'ln':
          outer = { kind: 'bin', op: '/', left: { kind: 'num', val: 1 }, right: n.arg };
          break;
        case 'log2':
          outer = {
            kind: 'bin',
            op: '/',
            left: { kind: 'num', val: 1 },
            right: { kind: 'bin', op: '*', left: n.arg, right: { kind: 'fn', name: 'ln', arg: { kind: 'num', val: 2 } } }
          };
          break;
        case 'log10':
        case 'log': {
          const baseNode: AstNode = n.base || { kind: 'num', val: 10 };
          outer = {
            kind: 'bin',
            op: '/',
            left: { kind: 'num', val: 1 },
            right: { kind: 'bin', op: '*', left: n.arg, right: { kind: 'fn', name: 'ln', arg: baseNode } }
          };
          break;
        }
        case 'exp':
          outer = { kind: 'fn', name: 'exp', arg: n.arg };
          break;
        default:
          return null;
      }
      return { kind: 'bin', op: '*', left: outer, right: du };
    }
  }
}

function simplifyAst(n: AstNode): AstNode {
  if (n.kind === 'num' || n.kind === 'x' || n.kind === 'm') return n;
  if (n.kind === 'neg') {
    const s = simplifyAst(n.inner);
    if (s.kind === 'num') return { kind: 'num', val: -s.val };
    if (s.kind === 'neg') return s.inner;
    return { kind: 'neg', inner: s };
  }
  if (n.kind === 'fn') {
    const sa = simplifyAst(n.arg);
    const sb = n.base ? simplifyAst(n.base) : undefined;
    if (n.name === 'ln' && sa.kind === 'num' && Math.abs(sa.val - Math.E) < 1e-9) {
      return { kind: 'num', val: 1 };
    }
    return { kind: 'fn', name: n.name, arg: sa, base: sb };
  }
  const l = simplifyAst(n.left);
  const r = simplifyAst(n.right);
  if (l.kind === 'num' && r.kind === 'num') {
    let val = NaN;
    if (n.op === '+') val = l.val + r.val;
    else if (n.op === '-') val = l.val - r.val;
    else if (n.op === '*') val = l.val * r.val;
    else if (n.op === '/') val = r.val === 0 ? NaN : l.val / r.val;
    else if (n.op === '^') val = Math.pow(l.val, r.val);
    if (Number.isFinite(val) && (n.op !== '/' || Math.abs(val - Math.round(val)) < 1e-9)) {
      return { kind: 'num', val };
    }
  }
  if (n.op === '+') {
    if (l.kind === 'num' && Math.abs(l.val) < 1e-12) return r;
    if (r.kind === 'num' && Math.abs(r.val) < 1e-12) return l;
    if (r.kind === 'neg') return simplifyAst({ kind: 'bin', op: '-', left: l, right: r.inner });
  } else if (n.op === '-') {
    if (r.kind === 'num' && Math.abs(r.val) < 1e-12) return l;
    if (l.kind === 'num' && Math.abs(l.val) < 1e-12) return simplifyAst({ kind: 'neg', inner: r });
    if (r.kind === 'neg') return simplifyAst({ kind: 'bin', op: '+', left: l, right: r.inner });
  } else if (n.op === '*') {
    if ((l.kind === 'num' && Math.abs(l.val) < 1e-12) || (r.kind === 'num' && Math.abs(r.val) < 1e-12)) {
      return { kind: 'num', val: 0 };
    }
    if (l.kind === 'num' && Math.abs(l.val - 1) < 1e-12) return r;
    if (r.kind === 'num' && Math.abs(r.val - 1) < 1e-12) return l;
    if (l.kind === 'num' && Math.abs(l.val + 1) < 1e-12) return simplifyAst({ kind: 'neg', inner: r });
    if (r.kind === 'num' && Math.abs(r.val + 1) < 1e-12) return simplifyAst({ kind: 'neg', inner: l });
    if (l.kind === 'num' && r.kind === 'bin' && r.op === '*' && r.left.kind === 'num') {
      return simplifyAst({ kind: 'bin', op: '*', left: { kind: 'num', val: l.val * r.left.val }, right: r.right });
    }
    if (r.kind === 'num' && l.kind === 'bin' && l.op === '*' && l.left.kind === 'num') {
      return simplifyAst({ kind: 'bin', op: '*', left: { kind: 'num', val: r.val * l.left.val }, right: l.right });
    }
    if (r.kind === 'num' && l.kind !== 'num') return { kind: 'bin', op: '*', left: r, right: l };
  } else if (n.op === '/') {
    if (l.kind === 'num' && Math.abs(l.val) < 1e-12) return { kind: 'num', val: 0 };
    if (r.kind === 'num' && Math.abs(r.val - 1) < 1e-12) return l;
  } else if (n.op === '^') {
    if (r.kind === 'num' && Math.abs(r.val) < 1e-12) return { kind: 'num', val: 1 };
    if (r.kind === 'num' && Math.abs(r.val - 1) < 1e-12) return l;
    if (l.kind === 'num' && Math.abs(l.val) < 1e-12) return { kind: 'num', val: 0 };
    if (l.kind === 'num' && Math.abs(l.val - 1) < 1e-12) return { kind: 'num', val: 1 };
  }
  return { kind: 'bin', op: n.op, left: l, right: r };
}

function astToPretty(n: AstNode, parentPrec = 0): string {
  switch (n.kind) {
    case 'num':
      if (Math.abs(n.val - Math.PI) < 1e-9) return 'π';
      if (Math.abs(n.val - Math.E) < 1e-9) return 'e';
      return format2DNum(n.val);
    case 'x':
      return 'x';
    case 'm':
      return 'm';
    case 'neg': {
      const core = `-${astToPretty(n.inner, 3)}`;
      return parentPrec >= 2 ? `(${core})` : core;
    }
    case 'fn':
      if (n.name === 'log' && n.base) return `log_${astToPretty(n.base, 4)}(${astToPretty(n.arg, 0)})`;
      return `${n.name}(${astToPretty(n.arg, 0)})`;
    case 'bin': {
      const prec = n.op === '+' || n.op === '-' ? 1 : n.op === '*' || n.op === '/' ? 2 : 4;
      const lStr = astToPretty(n.left, prec);
      const rStr = astToPretty(n.right, n.op === '-' || n.op === '/' ? prec + 1 : prec);
      let core: string;
      if (n.op === '+') core = `${lStr} + ${rStr}`;
      else if (n.op === '-') core = `${lStr} - ${rStr}`;
      else if (n.op === '*') {
        const compact =
          n.left.kind === 'num' &&
          (n.right.kind === 'x' || n.right.kind === 'm' || n.right.kind === 'fn' || (n.right.kind === 'bin' && n.right.op === '^'));
        core = compact ? `${lStr}${rStr}` : `${lStr}·${rStr}`;
      } else if (n.op === '/') {
        core = `${lStr} / ${rStr}`;
      } else {
        core = `${lStr}^${rStr}`;
      }
      return prec < parentPrec ? `(${core})` : core;
    }
  }
}

export function deriveSymbolicExpr(rawExpr: string): string | null {
  const ast = parseToAst(rawExpr);
  if (!ast) return null;
  const diff = diffAst(ast);
  if (!diff) return null;
  const simp = simplifyAst(simplifyAst(diff));
  return astToPretty(simp);
}

// ==================== DEFINITE INTEGRAL, AREA & REVOLUTION VOLUME ====================

export interface Integral2DResult {
  a: number;
  b: number;
  signedIntegral: number; // ∫_a^b (f(x) - g(x)) dx
  boundedArea: number;    // ∫_a^b |f(x) - g(x)| dx
  revolutionVolume: number; // π ∫_a^b |f(x)^2 - g(x)^2| dx
  samples: { x: number; yTop: number; yBot: number }[];
}

export function computeIntegral2D(
  exprF: string,
  exprG: string | null,
  a: number,
  b: number,
  m: number
): Integral2DResult | null {
  if (!Number.isFinite(a) || !Number.isFinite(b) || Math.abs(b - a) < 1e-6) return null;
  const lo = Math.min(a, b);
  const hi = Math.max(a, b);
  const sign = b >= a ? 1 : -1;
  const N = 360; // even number for composite Simpson's rule
  const h = (hi - lo) / N;

  let signedSum = 0;
  let areaSum = 0;
  let volSum = 0;
  const samples: { x: number; yTop: number; yBot: number }[] = [];

  for (let i = 0; i <= N; i++) {
    const x = lo + i * h;
    const yf = evaluateMathExpr(exprF, x, m);
    const yg = exprG ? evaluateMathExpr(exprG, x, m) : 0;
    if (!Number.isFinite(yf) || !Number.isFinite(yg) || Math.abs(yf) > 1e5 || Math.abs(yg) > 1e5) {
      continue;
    }
    const w = i === 0 || i === N ? 1 : i % 2 === 0 ? 2 : 4;
    const diff = yf - yg;
    signedSum += w * diff;
    areaSum += w * Math.abs(diff);
    volSum += w * Math.abs(yf * yf - yg * yg);
    if (i % 3 === 0) {
      samples.push({ x, yTop: yf, yBot: yg });
    }
  }

  const factor = h / 3;
  return {
    a,
    b,
    signedIntegral: sign * signedSum * factor,
    boundedArea: areaSum * factor,
    revolutionVolume: Math.PI * volSum * factor,
    samples
  };
}

// ==================== CASIO-STYLE VALUE TABLE (x, f(x), f'(x)) ====================

export interface ValueTableRow2D {
  x: number;
  y: number;
  dy: number;
}

export function buildValueTable2D(
  expr: string,
  start: number,
  end: number,
  step: number,
  m: number
): ValueTableRow2D[] {
  const safeStep = Math.max(0.05, Math.abs(step));
  const lo = Math.min(start, end);
  const hi = Math.max(start, end);
  const rows: ValueTableRow2D[] = [];
  let x = lo;
  let guard = 0;
  while (x <= hi + 1e-6 && guard < 120) {
    const cleanX = Math.round(x * 10000) / 10000;
    rows.push({
      x: cleanX,
      y: evaluateMathExpr(expr, cleanX, m),
      dy: numericalDerivative(expr, cleanX, m)
    });
    x += safeStep;
    guard++;
  }
  return rows;
}
