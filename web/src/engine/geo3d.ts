export interface Vec3 {
  x: number;
  y: number;
  z: number;
}

export type ShapeCategoryId = 'pyramid' | 'prism' | 'box' | 'round';

export interface ShapeSpec {
  key: string;
  title: string;
  subtitle: string;
  category: ShapeCategoryId;
  hasB?: boolean;
  hasH?: boolean;
  isRound?: boolean;
}

export const SHAPE_CATEGORIES: { id: ShapeCategoryId; title: string }[] = [
  { id: 'pyramid', title: '1. Hình Chóp & Tứ Diện (6)' },
  { id: 'prism', title: '2. Hình Lăng Trụ (3)' },
  { id: 'box', title: '3. Hình Hộp & Đa Diện (3)' },
  { id: 'round', title: '4. Khối Tròn Xoay (3)' }
];

export const ALL_3D_SHAPES: ShapeSpec[] = [
  { key: 'pyramid3', title: 'Chóp tam giác đều', subtitle: 'S.ABC (SO ⊥ đáy)', category: 'pyramid', hasH: true },
  { key: 'pyramid3_sa', title: 'Chóp SA ⊥ (ABC)', subtitle: 'Tam giác có SA ⊥ đáy', category: 'pyramid', hasH: true },
  { key: 'tetra_ortho', title: 'Tứ diện vuông', subtitle: 'O.ABC (OA, OB, OC ⊥)', category: 'pyramid', hasB: true, hasH: true },
  { key: 'pyramid4', title: 'Chóp tứ giác đều', subtitle: 'S.ABCD (SO ⊥ đáy)', category: 'pyramid', hasH: true },
  { key: 'pyramid4_sa', title: 'Chóp SA ⊥ (ABCD)', subtitle: 'Đáy vuông, SA ⊥ đáy', category: 'pyramid', hasH: true },
  { key: 'pyramid_trap', title: 'Chóp đáy hình thang', subtitle: 'Thang vuông tại A, B', category: 'pyramid', hasH: true },

  { key: 'prism3', title: 'Lăng trụ tam giác', subtitle: "Đứng ABC.A'B'C'", category: 'prism', hasH: true },
  { key: 'prism3_oblique', title: 'Lăng trụ xiên', subtitle: "A'O ⊥ (ABC)", category: 'prism', hasH: true },
  { key: 'prism6', title: 'Lăng trụ lục giác', subtitle: 'Lục giác đều 6 cạnh', category: 'prism', hasH: true },

  { key: 'cube', title: 'Hình lập phương', subtitle: "ABCD.A'B'C'D' (cạnh a)", category: 'box', hasH: false },
  { key: 'box', title: 'Hộp chữ nhật', subtitle: 'Kích thước a × b × h', category: 'box', hasB: true, hasH: true },
  { key: 'octahedron', title: 'Bát diện đều', subtitle: '8 mặt tam giác đều', category: 'box', hasH: false },

  { key: 'cylinder', title: 'Hình trụ tròn', subtitle: "Trục OO', bán kính R", category: 'round', hasH: true, isRound: true },
  { key: 'cone', title: 'Hình nón tròn', subtitle: 'Đỉnh S, đáy tâm O', category: 'round', hasH: true, isRound: true },
  { key: 'sphere', title: 'Hình cầu', subtitle: 'Tâm O, bán kính R', category: 'round', hasH: false, isRound: true }
];

export type LineStyle3D = 'solid' | 'dashed' | 'vector_solid' | 'vector_dashed';

export interface CustomEdgePoint3D {
  id: string;
  name: string;
  from: string;
  to: string;
  ratio: number;
}

export interface CustomXYZPoint3D {
  id: string;
  name: string;
  x: number; // Math Oxyz x
  y: number; // Math Oxyz y
  z: number; // Math Oxyz z (vertical)
}

export interface CustomLine3D {
  id: string;
  p1: string;
  p2: string;
  style: LineStyle3D;
  color: string;
}

export interface CustomPlane3D {
  id: string;
  pts: string[];
  color: string;
}

export interface DynamicSectionSpec {
  p1: string;
  p2: string;
  p3: string;
}

export interface DynamicSectionResult {
  polygon: Vec3[];
  area: number;
}

export interface LinePlaneAnalysis3D {
  planeEquation: string;
  distA: number;
  distB: number;
  angleDegrees: number;
  lineLength: number;
}

export interface Scene3DSnapshot {
  shapeKey: string;
  a: number;
  b: number;
  h: number;
  originPoint: string;
  vertexOverrides?: Record<string, Vec3>;
  customPoints: CustomEdgePoint3D[];
  customXYZPoints: CustomXYZPoint3D[];
  customLines: CustomLine3D[];
  customPlanes: CustomPlane3D[];
  dynamicSection: DynamicSectionSpec | null;
}

export interface NamedVertex {
  name: string;
  pos: Vec3;
}

export interface ShapeEdge {
  from: number;
  to: number;
}

export interface ShapeMetrics {
  realH: number;
  sDay: number;
  sXq: number;
  sTp: number;
  vol: number;
  formulaLines: string[];
  dimensionRows: { label: string; value: string }[];
}

export interface Built3DScene {
  vertices: NamedVertex[];
  faces: number[][];
  edges: ShapeEdge[];
  pointRegistry: Record<string, Vec3>;
  metrics: ShapeMetrics;
  dynamicSection: DynamicSectionResult | null;
}

export function add3(a: Vec3, b: Vec3): Vec3 {
  return { x: a.x + b.x, y: a.y + b.y, z: a.z + b.z };
}
export function sub3(a: Vec3, b: Vec3): Vec3 {
  return { x: a.x - b.x, y: a.y - b.y, z: a.z - b.z };
}
export function scale3(v: Vec3, s: number): Vec3 {
  return { x: v.x * s, y: v.y * s, z: v.z * s };
}
export function lerp3(a: Vec3, b: Vec3, t: number): Vec3 {
  return { x: a.x + (b.x - a.x) * t, y: a.y + (b.y - a.y) * t, z: a.z + (b.z - a.z) * t };
}
export function cross3(a: Vec3, b: Vec3): Vec3 {
  return {
    x: a.y * b.z - a.z * b.y,
    y: a.z * b.x - a.x * b.z,
    z: a.x * b.y - a.y * b.x
  };
}
export function dot3(a: Vec3, b: Vec3): number {
  return a.x * b.x + a.y * b.y + a.z * b.z;
}
export function len3(v: Vec3): number {
  return Math.hypot(v.x, v.y, v.z);
}
export function dist3(a: Vec3, b: Vec3): number {
  return len3(sub3(a, b));
}
export function norm3(v: Vec3): Vec3 {
  const len = len3(v) || 1;
  return { x: v.x / len, y: v.y / len, z: v.z / len };
}

/**
 * Convert renderer world (x, y=up, z=depth) relative to originWorld -> Math Oxyz (x, y=depth, z=up)
 */
export function worldToMath(worldPos: Vec3, originWorld: Vec3): Vec3 {
  return {
    x: worldPos.x - originWorld.x,
    y: worldPos.z - originWorld.z,
    z: worldPos.y - originWorld.y
  };
}

export function mathToWorld(mathPos: Vec3, originWorld: Vec3): Vec3 {
  return {
    x: originWorld.x + mathPos.x,
    y: originWorld.y + mathPos.z,
    z: originWorld.z + mathPos.y
  };
}

export function formatMathNum(v: number): string {
  if (Math.abs(v) < 1e-4) return '0';
  const rounded = Math.round(v * 100) / 100;
  if (Math.abs(rounded - Math.round(rounded)) < 1e-4) return String(Math.round(rounded));
  return rounded.toFixed(2).replace(/0+$/, '').replace(/\.$/, '');
}

export function nextAutoPointName(registry: Record<string, Vec3>): string {
  const candidates = ['M', 'N', 'P', 'Q', 'I', 'J', 'K', 'H', 'E', 'F', 'G', 'R', 'T', 'U', 'V'];
  for (const c of candidates) {
    if (!registry[c]) return c;
  }
  let idx = 1;
  while (registry[`M${idx}`]) idx++;
  return `M${idx}`;
}

function uniqueEdgesFromFaces(faces: number[][]): ShapeEdge[] {
  const map = new Map<string, ShapeEdge>();
  for (const f of faces) {
    for (let i = 0; i < f.length; i++) {
      const u = f[i];
      const v = f[(i + 1) % f.length];
      const key = u < v ? `${u}-${v}` : `${v}-${u}`;
      if (!map.has(key)) map.set(key, { from: u, to: v });
    }
  }
  return Array.from(map.values());
}

function polygonArea3D(pts: Vec3[]): number {
  if (pts.length < 3) return 0;
  let area = 0;
  const p0 = pts[0];
  for (let i = 1; i < pts.length - 1; i++) {
    area += 0.5 * len3(cross3(sub3(pts[i], p0), sub3(pts[i + 1], p0)));
  }
  return area;
}

export function computeDynamicCrossSection(
  p1: Vec3,
  p2: Vec3,
  p3: Vec3,
  edgePairs: [Vec3, Vec3][]
): DynamicSectionResult | null {
  const rawN = cross3(sub3(p2, p1), sub3(p3, p1));
  const nLen = len3(rawN);
  if (nLen < 1e-4) return null;
  const n = scale3(rawN, 1 / nLen);

  const hits: Vec3[] = [];
  const addUnique = (pt: Vec3) => {
    if (!hits.some((h) => dist3(h, pt) < 1.5e-3)) hits.push(pt);
  };

  const eps = 1e-4;
  for (const [u, v] of edgePairs) {
    const du = dot3(sub3(u, p1), n);
    const dv = dot3(sub3(v, p1), n);
    if (Math.abs(du) < eps && Math.abs(dv) < eps) {
      addUnique(u);
      addUnique(v);
    } else if (Math.abs(du) < eps) {
      addUnique(u);
    } else if (Math.abs(dv) < eps) {
      addUnique(v);
    } else if ((du > 0 && dv < 0) || (du < 0 && dv > 0)) {
      const t = du / (du - dv);
      if (t >= -1e-3 && t <= 1.001) {
        addUnique(lerp3(u, v, Math.min(1, Math.max(0, t))));
      }
    }
  }

  if (hits.length < 3) {
    addUnique(p1);
    addUnique(p2);
    addUnique(p3);
  }
  if (hits.length < 3) return null;

  let center: Vec3 = { x: 0, y: 0, z: 0 };
  for (const h of hits) center = add3(center, h);
  center = scale3(center, 1 / hits.length);

  const ref: Vec3 = Math.abs(n.y) < 0.9 ? { x: 0, y: 1, z: 0 } : { x: 1, y: 0, z: 0 };
  const uAxis = norm3(cross3(n, ref));
  const vAxis = norm3(cross3(n, uAxis));

  const ordered = [...hits].sort((a, b) => {
    const ra = sub3(a, center);
    const rb = sub3(b, center);
    return Math.atan2(dot3(ra, vAxis), dot3(ra, uAxis)) - Math.atan2(dot3(rb, vAxis), dot3(rb, uAxis));
  });

  const area = polygonArea3D(ordered);
  if (area < 1e-4) return null;
  return { polygon: ordered, area };
}

export function formatPlaneEquation(m1: Vec3, m2: Vec3, m3: Vec3): string {
  const rawN = cross3(sub3(m2, m1), sub3(m3, m1));
  const maxAbs = Math.max(Math.abs(rawN.x), Math.abs(rawN.y), Math.abs(rawN.z));
  if (maxAbs < 1e-4) return '3 điểm thẳng hàng';

  let a = rawN.x / maxAbs;
  let b = rawN.y / maxAbs;
  let c = rawN.z / maxAbs;
  let d = -(a * m1.x + b * m1.y + c * m1.z);

  const sign = Math.abs(a) > 1e-3 ? (a < 0 ? -1 : 1) : Math.abs(b) > 1e-3 ? (b < 0 ? -1 : 1) : c < 0 ? -1 : 1;
  a *= sign;
  b *= sign;
  c *= sign;
  d *= sign;

  let out = '';
  const appendTerm = (coef: number, name: string) => {
    if (Math.abs(coef) < 1e-3) return;
    const neg = coef < 0;
    const absC = Math.abs(coef);
    if (out.length > 0) out += neg ? ' - ' : ' + ';
    else if (neg) out += '-';
    if (!name) out += formatMathNum(absC);
    else if (Math.abs(absC - 1) < 1e-3) out += name;
    else out += `${formatMathNum(absC)}${name}`;
  };
  appendTerm(a, 'x');
  appendTerm(b, 'y');
  appendTerm(c, 'z');
  appendTerm(d, '');
  return out ? `${out} = 0` : '0 = 0';
}

export function analyzeLineAndPlane(
  lineA: Vec3,
  lineB: Vec3,
  p1: Vec3,
  p2: Vec3,
  p3: Vec3,
  originWorld: Vec3
): LinePlaneAnalysis3D | null {
  const m1 = worldToMath(p1, originWorld);
  const m2 = worldToMath(p2, originWorld);
  const m3 = worldToMath(p3, originWorld);
  const n = cross3(sub3(p2, p1), sub3(p3, p1));
  const nLen = len3(n);
  if (nLen < 1e-4) return null;

  const distA = Math.abs(dot3(sub3(lineA, p1), n)) / nLen;
  const distB = Math.abs(dot3(sub3(lineB, p1), n)) / nLen;
  const u = sub3(lineB, lineA);
  const uLen = len3(u);
  const sinPhi = uLen > 1e-4 ? Math.min(1, Math.max(0, Math.abs(dot3(u, n)) / (uLen * nLen))) : 0;
  const angleDegrees = (Math.asin(sinPhi) * 180) / Math.PI;

  return {
    planeEquation: formatPlaneEquation(m1, m2, m3),
    distA,
    distB,
    angleDegrees,
    lineLength: uLen
  };
}

function buildBaseShape(key: string, a: number, b: number, h: number): {
  vertices: NamedVertex[];
  faces: number[][];
  edges: ShapeEdge[];
  metrics: ShapeMetrics;
} {
  const safeA = Math.max(0.5, a);
  const safeB = Math.max(0.5, b);
  const safeH = Math.max(0.5, h);

  if (key === 'pyramid3' || key === 'pyramid3_sa') {
    const r = safeA / Math.sqrt(3);
    const vA: Vec3 = { x: -safeA / 2, y: 0, z: r / 2 };
    const vB: Vec3 = { x: safeA / 2, y: 0, z: r / 2 };
    const vC: Vec3 = { x: 0, y: 0, z: -r };
    const vS: Vec3 = key === 'pyramid3_sa' ? { x: vA.x, y: safeH, z: vA.z } : { x: 0, y: safeH, z: 0 };
    const vertices: NamedVertex[] = [
      { name: 'A', pos: vA },
      { name: 'B', pos: vB },
      { name: 'C', pos: vC },
      { name: 'S', pos: vS }
    ];
    const faces = [[0, 2, 1], [0, 1, 3], [1, 2, 3], [2, 0, 3]];
    const sDay = (Math.sqrt(3) / 4) * safeA * safeA;
    const slant = Math.hypot(safeH, safeA / (2 * Math.sqrt(3)));
    const sXq = key === 'pyramid3'
      ? 1.5 * safeA * slant
      : safeA * safeH + 0.5 * safeA * Math.hypot(safeH, (safeA * Math.sqrt(3)) / 2);
    return {
      vertices,
      faces,
      edges: uniqueEdgesFromFaces(faces),
      metrics: {
        realH: safeH,
        sDay,
        sXq,
        sTp: sDay + sXq,
        vol: (sDay * safeH) / 3,
        dimensionRows: [
          { label: 'Cạnh đáy a', value: formatMathNum(safeA) },
          { label: 'Chiều cao h', value: formatMathNum(safeH) }
        ],
        formulaLines: ['• Sđáy = (a²√3) / 4', '• V = ⅓ · Sđáy · h']
      }
    };
  }

  if (key === 'tetra_ortho') {
    const vertices: NamedVertex[] = [
      { name: 'O', pos: { x: 0, y: 0, z: 0 } },
      { name: 'A', pos: { x: safeA, y: 0, z: 0 } },
      { name: 'B', pos: { x: 0, y: 0, z: safeB } },
      { name: 'C', pos: { x: 0, y: safeH, z: 0 } }
    ];
    const faces = [[0, 2, 1], [0, 1, 3], [0, 3, 2], [1, 2, 3]];
    const sDay = 0.5 * safeA * safeB;
    const sABC = 0.5 * Math.hypot(safeA * safeB, safeB * safeH, safeA * safeH);
    const sXq = 0.5 * safeA * safeH + 0.5 * safeB * safeH + sABC;
    return {
      vertices,
      faces,
      edges: uniqueEdgesFromFaces(faces),
      metrics: {
        realH: safeH,
        sDay,
        sXq,
        sTp: sDay + sXq,
        vol: (safeA * safeB * safeH) / 6,
        dimensionRows: [
          { label: 'OA = a', value: formatMathNum(safeA) },
          { label: 'OB = b', value: formatMathNum(safeB) },
          { label: 'OC = h', value: formatMathNum(safeH) }
        ],
        formulaLines: ['• OA, OB, OC đôi một vuông góc', '• V = ⅙ · OA · OB · OC', '• 1/d²(O,(ABC)) = 1/a² + 1/b² + 1/h²']
      }
    };
  }

  if (key === 'pyramid4' || key === 'pyramid4_sa' || key === 'pyramid_trap') {
    const half = safeA / 2;
    const topW = key === 'pyramid_trap' ? safeA * 0.55 : safeA;
    const vA: Vec3 = { x: -half, y: 0, z: half };
    const vB: Vec3 = { x: -half + topW, y: 0, z: half };
    const vC: Vec3 = { x: half, y: 0, z: -half };
    const vD: Vec3 = { x: -half, y: 0, z: -half };
    const vS: Vec3 = key === 'pyramid4' ? { x: 0, y: safeH, z: 0 } : { x: vA.x, y: safeH, z: vA.z };
    const vertices: NamedVertex[] = [
      { name: 'A', pos: vA },
      { name: 'B', pos: vB },
      { name: 'C', pos: vC },
      { name: 'D', pos: vD },
      { name: 'S', pos: vS }
    ];
    const faces = [[0, 3, 2, 1], [0, 1, 4], [1, 2, 4], [2, 3, 4], [3, 0, 4]];
    const sDay = key === 'pyramid_trap' ? ((safeA + topW) * safeA) / 2 : safeA * safeA;
    const sXq =
      polygonArea3D([vA, vB, vS]) +
      polygonArea3D([vB, vC, vS]) +
      polygonArea3D([vC, vD, vS]) +
      polygonArea3D([vD, vA, vS]);
    return {
      vertices,
      faces,
      edges: uniqueEdgesFromFaces(faces),
      metrics: {
        realH: safeH,
        sDay,
        sXq,
        sTp: sDay + sXq,
        vol: (sDay * safeH) / 3,
        dimensionRows: [
          { label: 'Cạnh đáy a', value: formatMathNum(safeA) },
          { label: 'Chiều cao h', value: formatMathNum(safeH) }
        ],
        formulaLines: [
          key === 'pyramid_trap' ? '• Sđáy = ½ · (AB + CD) · AD' : '• Sđáy = a²',
          '• V = ⅓ · Sđáy · h'
        ]
      }
    };
  }

  if (key === 'prism3' || key === 'prism3_oblique') {
    const r = safeA / Math.sqrt(3);
    const shiftX = key === 'prism3_oblique' ? safeA * 0.35 : 0;
    const base: Vec3[] = [
      { x: -safeA / 2, y: 0, z: r / 2 },
      { x: safeA / 2, y: 0, z: r / 2 },
      { x: 0, y: 0, z: -r }
    ];
    const vertices: NamedVertex[] = [
      { name: 'A', pos: base[0] },
      { name: 'B', pos: base[1] },
      { name: 'C', pos: base[2] },
      { name: "A'", pos: { x: base[0].x + shiftX, y: safeH, z: base[0].z } },
      { name: "B'", pos: { x: base[1].x + shiftX, y: safeH, z: base[1].z } },
      { name: "C'", pos: { x: base[2].x + shiftX, y: safeH, z: base[2].z } }
    ];
    const faces = [[0, 2, 1], [3, 4, 5], [0, 1, 4, 3], [1, 2, 5, 4], [2, 0, 3, 5]];
    const sDay = (Math.sqrt(3) / 4) * safeA * safeA;
    const sideH = Math.hypot(safeH, shiftX);
    const sXq = 3 * safeA * sideH;
    return {
      vertices,
      faces,
      edges: uniqueEdgesFromFaces(faces),
      metrics: {
        realH: safeH,
        sDay,
        sXq,
        sTp: 2 * sDay + sXq,
        vol: sDay * safeH,
        dimensionRows: [
          { label: 'Cạnh đáy a', value: formatMathNum(safeA) },
          { label: 'Chiều cao h', value: formatMathNum(safeH) }
        ],
        formulaLines: ['• Sđáy = (a²√3) / 4', '• V = Sđáy · h']
      }
    };
  }

  if (key === 'prism6') {
    const r = safeA;
    const vertices: NamedVertex[] = [];
    const names = ['A', 'B', 'C', 'D', 'E', 'F'];
    for (let i = 0; i < 6; i++) {
      const ang = (i * Math.PI) / 3;
      vertices.push({ name: names[i], pos: { x: r * Math.cos(ang), y: 0, z: r * Math.sin(ang) } });
    }
    for (let i = 0; i < 6; i++) {
      const ang = (i * Math.PI) / 3;
      vertices.push({ name: `${names[i]}'`, pos: { x: r * Math.cos(ang), y: safeH, z: r * Math.sin(ang) } });
    }
    const faces: number[][] = [[0, 5, 4, 3, 2, 1], [6, 7, 8, 9, 10, 11]];
    for (let i = 0; i < 6; i++) {
      const n = (i + 1) % 6;
      faces.push([i, n, n + 6, i + 6]);
    }
    const sDay = ((3 * Math.sqrt(3)) / 2) * safeA * safeA;
    const sXq = 6 * safeA * safeH;
    return {
      vertices,
      faces,
      edges: uniqueEdgesFromFaces(faces),
      metrics: {
        realH: safeH,
        sDay,
        sXq,
        sTp: 2 * sDay + sXq,
        vol: sDay * safeH,
        dimensionRows: [
          { label: 'Cạnh lục giác a', value: formatMathNum(safeA) },
          { label: 'Chiều cao h', value: formatMathNum(safeH) }
        ],
        formulaLines: ['• Sđáy = (3a²√3) / 2', '• V = Sđáy · h']
      }
    };
  }

  if (key === 'octahedron') {
    const r = safeA / Math.sqrt(2);
    const vertices: NamedVertex[] = [
      { name: 'A', pos: { x: r, y: r, z: 0 } },
      { name: 'B', pos: { x: 0, y: r, z: r } },
      { name: 'C', pos: { x: -r, y: r, z: 0 } },
      { name: 'D', pos: { x: 0, y: r, z: -r } },
      { name: 'S', pos: { x: 0, y: 2 * r, z: 0 } },
      { name: "S'", pos: { x: 0, y: 0, z: 0 } }
    ];
    const faces = [
      [0, 1, 4], [1, 2, 4], [2, 3, 4], [3, 0, 4],
      [1, 0, 5], [2, 1, 5], [3, 2, 5], [0, 3, 5]
    ];
    const sTp = 2 * Math.sqrt(3) * safeA * safeA;
    return {
      vertices,
      faces,
      edges: uniqueEdgesFromFaces(faces),
      metrics: {
        realH: 2 * r,
        sDay: safeA * safeA,
        sXq: sTp,
        sTp,
        vol: (Math.sqrt(2) / 3) * safeA * safeA * safeA,
        dimensionRows: [
          { label: 'Cạnh a', value: formatMathNum(safeA) },
          { label: 'Đường chéo SS\'', value: formatMathNum(2 * r) }
        ],
        formulaLines: ['• Stp = 2a²√3', '• V = (a³√2) / 3']
      }
    };
  }

  if (key === 'cylinder' || key === 'cone') {
    const segs = 20;
    const r = safeA * 0.55;
    const vertices: NamedVertex[] = [];
    for (let i = 0; i < segs; i++) {
      const ang = (i * 2 * Math.PI) / segs;
      vertices.push({ name: i === 0 ? 'A' : '', pos: { x: r * Math.cos(ang), y: 0, z: r * Math.sin(ang) } });
    }
    const faces: number[][] = [];
    const edges: ShapeEdge[] = [];
    for (let i = 0; i < segs; i++) edges.push({ from: i, to: (i + 1) % segs });

    if (key === 'cylinder') {
      for (let i = 0; i < segs; i++) {
        const ang = (i * 2 * Math.PI) / segs;
        vertices.push({ name: i === 0 ? "A'" : '', pos: { x: r * Math.cos(ang), y: safeH, z: r * Math.sin(ang) } });
      }
      for (let i = 0; i < segs; i++) {
        const n = (i + 1) % segs;
        faces.push([i, n, n + segs, i + segs]);
        edges.push({ from: i + segs, to: n + segs });
      }
      edges.push({ from: 0, to: segs }, { from: segs / 2, to: segs + segs / 2 });
      vertices.push({ name: 'O', pos: { x: 0, y: 0, z: 0 } }, { name: "O'", pos: { x: 0, y: safeH, z: 0 } });
      const sDay = Math.PI * r * r;
      const sXq = 2 * Math.PI * r * safeH;
      return {
        vertices,
        faces,
        edges,
        metrics: {
          realH: safeH,
          sDay,
          sXq,
          sTp: 2 * sDay + sXq,
          vol: sDay * safeH,
          dimensionRows: [
            { label: 'Bán kính R', value: formatMathNum(r) },
            { label: 'Chiều cao h', value: formatMathNum(safeH) }
          ],
          formulaLines: ['• Sxq = 2πRh', '• Stp = 2πR(R + h)', '• V = πR²h']
        }
      };
    } else {
      const apexIdx = vertices.length;
      vertices.push({ name: 'S', pos: { x: 0, y: safeH, z: 0 } });
      vertices.push({ name: 'O', pos: { x: 0, y: 0, z: 0 } });
      for (let i = 0; i < segs; i++) {
        const n = (i + 1) % segs;
        faces.push([i, n, apexIdx]);
      }
      edges.push({ from: 0, to: apexIdx }, { from: segs / 2, to: apexIdx });
      const l = Math.hypot(r, safeH);
      const sDay = Math.PI * r * r;
      const sXq = Math.PI * r * l;
      return {
        vertices,
        faces,
        edges,
        metrics: {
          realH: safeH,
          sDay,
          sXq,
          sTp: sDay + sXq,
          vol: (sDay * safeH) / 3,
          dimensionRows: [
            { label: 'Bán kính R', value: formatMathNum(r) },
            { label: 'Đường sinh l', value: formatMathNum(l) },
            { label: 'Chiều cao h', value: formatMathNum(safeH) }
          ],
          formulaLines: ['• l = √(R² + h²)', '• Sxq = πRl', '• V = ⅓πR²h']
        }
      };
    }
  }

  if (key === 'sphere') {
    const r = safeA * 0.6;
    const rings = 8;
    const segs = 16;
    const vertices: NamedVertex[] = [];
    const faces: number[][] = [];
    const edges: ShapeEdge[] = [];
    for (let i = 0; i <= rings; i++) {
      const phi = (i * Math.PI) / rings;
      for (let j = 0; j < segs; j++) {
        const theta = (j * 2 * Math.PI) / segs;
        vertices.push({
          name: i === rings / 2 && j === 0 ? 'A' : '',
          pos: {
            x: r * Math.sin(phi) * Math.cos(theta),
            y: r + r * Math.cos(phi),
            z: r * Math.sin(phi) * Math.sin(theta)
          }
        });
      }
    }
    for (let i = 0; i < rings; i++) {
      for (let j = 0; j < segs; j++) {
        const aIdx = i * segs + j;
        const bIdx = i * segs + ((j + 1) % segs);
        const cIdx = (i + 1) * segs + ((j + 1) % segs);
        const dIdx = (i + 1) * segs + j;
        faces.push([aIdx, bIdx, cIdx, dIdx]);
        if (i === rings / 2) edges.push({ from: aIdx, to: bIdx });
        if (j === 0 || j === segs / 4) edges.push({ from: aIdx, to: dIdx });
      }
    }
    vertices.push({ name: 'O', pos: { x: 0, y: r, z: 0 } });
    const sTp = 4 * Math.PI * r * r;
    return {
      vertices,
      faces,
      edges,
      metrics: {
        realH: 2 * r,
        sDay: Math.PI * r * r,
        sXq: sTp,
        sTp,
        vol: (4 / 3) * Math.PI * r * r * r,
        dimensionRows: [
          { label: 'Bán kính R', value: formatMathNum(r) },
          { label: 'Đường kính 2R', value: formatMathNum(2 * r) }
        ],
        formulaLines: ['• S = 4πR²', '• V = ⁴⁄₃πR³']
      }
    };
  }

  // Cube or Box
  const w = safeA;
  const d = key === 'box' ? safeB : safeA;
  const height = key === 'cube' ? safeA : safeH;
  const hw = w / 2;
  const hd = d / 2;
  const vertices: NamedVertex[] = [
    { name: 'A', pos: { x: -hw, y: 0, z: hd } },
    { name: 'B', pos: { x: hw, y: 0, z: hd } },
    { name: 'C', pos: { x: hw, y: 0, z: -hd } },
    { name: 'D', pos: { x: -hw, y: 0, z: -hd } },
    { name: "A'", pos: { x: -hw, y: height, z: hd } },
    { name: "B'", pos: { x: hw, y: height, z: hd } },
    { name: "C'", pos: { x: hw, y: height, z: -hd } },
    { name: "D'", pos: { x: -hw, y: height, z: -hd } }
  ];
  const faces = [
    [0, 3, 2, 1], [4, 5, 6, 7],
    [0, 1, 5, 4], [1, 2, 6, 5], [2, 3, 7, 6], [3, 0, 4, 7]
  ];
  const sDay = w * d;
  const sXq = 2 * (w + d) * height;
  return {
    vertices,
    faces,
    edges: uniqueEdgesFromFaces(faces),
    metrics: {
      realH: height,
      sDay,
      sXq,
      sTp: 2 * sDay + sXq,
      vol: w * d * height,
      dimensionRows:
        key === 'cube'
          ? [
              { label: 'Cạnh a', value: formatMathNum(w) },
              { label: 'Đường chéo AC\'', value: formatMathNum(w * Math.sqrt(3)) }
            ]
          : [
              { label: 'Chiều dài a', value: formatMathNum(w) },
              { label: 'Chiều rộng b', value: formatMathNum(d) },
              { label: 'Chiều cao h', value: formatMathNum(height) }
            ],
      formulaLines:
        key === 'cube'
          ? ['• Stp = 6a²', '• V = a³', '• AC\' = a√3']
          : ['• Sxq = 2(a + b)h', '• Stp = 2(ab + ah + bh)', '• V = a · b · h']
    }
  };
}

export function build3DSceneFromSnapshot(snap: Scene3DSnapshot): Built3DScene {
  const base = buildBaseShape(snap.shapeKey, snap.a, snap.b, snap.h);
  const overrides = snap.vertexOverrides || {};
  const vertices: NamedVertex[] = base.vertices.map((v) =>
    v.name && overrides[v.name] ? { name: v.name, pos: overrides[v.name] } : v
  );
  const registry: Record<string, Vec3> = {};
  for (const v of vertices) {
    if (v.name) registry[v.name] = v.pos;
  }

  const originWorld = registry[snap.originPoint] || { x: 0, y: 0, z: 0 };

  // Add custom XYZ points first
  for (const pt of snap.customXYZPoints) {
    registry[pt.name] = mathToWorld({ x: pt.x, y: pt.y, z: pt.z }, originWorld);
  }

  // Add custom edge points in order (supports chaining points on previously created segments!)
  for (const cp of snap.customPoints) {
    const pFrom = registry[cp.from];
    const pTo = registry[cp.to];
    if (pFrom && pTo) {
      registry[cp.name] = lerp3(pFrom, pTo, cp.ratio);
    }
  }

  // Compute dynamic 3-point cross-section if defined
  let dynamicSection: DynamicSectionResult | null = null;
  if (snap.dynamicSection) {
    const p1 = registry[snap.dynamicSection.p1];
    const p2 = registry[snap.dynamicSection.p2];
    const p3 = registry[snap.dynamicSection.p3];
    if (p1 && p2 && p3) {
      const edgePairs: [Vec3, Vec3][] = base.edges.map((e) => [
        vertices[e.from].pos,
        vertices[e.to].pos
      ]);
      dynamicSection = computeDynamicCrossSection(p1, p2, p3, edgePairs);
    }
  }

  return {
    vertices,
    faces: base.faces,
    edges: base.edges,
    pointRegistry: registry,
    metrics: base.metrics,
    dynamicSection
  };
}

export interface Camera3D {
  theta: number;
  phi: number;
  radius: number;
  target: Vec3;
}

export function projectPoint(
  p: Vec3,
  cam: Camera3D,
  width: number,
  height: number
): { x: number; y: number; depth: number } {
  const camPos: Vec3 = {
    x: cam.target.x + cam.radius * Math.sin(cam.phi) * Math.sin(cam.theta),
    y: cam.target.y + cam.radius * Math.cos(cam.phi),
    z: cam.target.z + cam.radius * Math.sin(cam.phi) * Math.cos(cam.theta)
  };
  const forward = norm3(sub3(cam.target, camPos));
  const worldUp: Vec3 = { x: 0, y: 1, z: 0 };
  const right = norm3(cross3(forward, worldUp));
  const up = cross3(right, forward);

  const rel = sub3(p, camPos);
  const cx = dot3(rel, right);
  const cy = dot3(rel, up);
  const cz = Math.max(0.2, dot3(rel, forward));
  const focal = Math.min(width, height) * 1.15;

  return {
    x: width * 0.5 + (cx / cz) * focal,
    y: height * 0.52 - (cy / cz) * focal,
    depth: cz
  };
}

export function exportSceneToObj(scene: Built3DScene, title: string): string {
  const lines = [`# Tool Toan 3D Export - ${title}`];
  for (const v of scene.vertices) {
    lines.push(`v ${v.pos.x.toFixed(4)} ${v.pos.y.toFixed(4)} ${v.pos.z.toFixed(4)}`);
  }
  for (const f of scene.faces) {
    lines.push(`f ${f.map((idx) => idx + 1).join(' ')}`);
  }
  return lines.join('\n');
}

export function exportSceneToSvg(
  scene: Built3DScene,
  snap: Scene3DSnapshot,
  cam: Camera3D,
  accent: string,
  width = 960,
  height = 720
): string {
  const projected = scene.vertices.map((v) => projectPoint(v.pos, cam, width, height));
  const polys = scene.faces
    .map((f) => {
      const pts = f.map((i) => `${projected[i].x.toFixed(1)},${projected[i].y.toFixed(1)}`).join(' ');
      return `<polygon points="${pts}" fill="${accent}" fill-opacity="0.16" stroke="none" />`;
    })
    .join('\n  ');
  const lines = scene.edges
    .map((e) => {
      const a = projected[e.from];
      const b = projected[e.to];
      return `<line x1="${a.x.toFixed(1)}" y1="${a.y.toFixed(1)}" x2="${b.x.toFixed(1)}" y2="${b.y.toFixed(1)}" stroke="#e2e8f0" stroke-width="2" />`;
    })
    .join('\n  ');
  const customLinesSvg = snap.customLines
    .map((cl) => {
      const p1 = scene.pointRegistry[cl.p1];
      const p2 = scene.pointRegistry[cl.p2];
      if (!p1 || !p2) return '';
      const a = projectPoint(p1, cam, width, height);
      const b = projectPoint(p2, cam, width, height);
      const dash = cl.style === 'dashed' || cl.style === 'vector_dashed' ? ' stroke-dasharray="6,5"' : '';
      return `<line x1="${a.x.toFixed(1)}" y1="${a.y.toFixed(1)}" x2="${b.x.toFixed(1)}" y2="${b.y.toFixed(1)}" stroke="${cl.color}" stroke-width="2.4"${dash} />`;
    })
    .filter(Boolean)
    .join('\n  ');
  const labels = Object.entries(scene.pointRegistry)
    .map(([name, pos]) => {
      const p = projectPoint(pos, cam, width, height);
      return `<text x="${(p.x + 8).toFixed(1)}" y="${(p.y - 6).toFixed(1)}" fill="#f8fafc" font-size="16" font-weight="bold" font-family="sans-serif">${name}</text>`;
    })
    .join('\n  ');
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${width} ${height}" width="${width}" height="${height}">
  <rect width="100%" height="100%" fill="#090d16"/>
  ${polys}
  ${lines}
  ${customLinesSvg}
  ${labels}
</svg>`;
}
