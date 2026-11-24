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

export interface NamedVertex {
  name: string;
  pos: Vec3;
}

export interface ShapeEdge {
  from: number;
  to: number;
  dashedHint?: boolean;
}

export interface ShapeMetrics {
  realH: number;
  sDay: number;
  sTp: number;
  vol: number;
  formulaNote: string;
}

export interface Built3DScene {
  vertices: NamedVertex[];
  faces: number[][];
  edges: ShapeEdge[];
  metrics: ShapeMetrics;
}

function sub(a: Vec3, b: Vec3): Vec3 {
  return { x: a.x - b.x, y: a.y - b.y, z: a.z - b.z };
}
function cross(a: Vec3, b: Vec3): Vec3 {
  return {
    x: a.y * b.z - a.z * b.y,
    y: a.z * b.x - a.x * b.z,
    z: a.x * b.y - a.y * b.x
  };
}
function dot(a: Vec3, b: Vec3): number {
  return a.x * b.x + a.y * b.y + a.z * b.z;
}
function norm(v: Vec3): Vec3 {
  const len = Math.hypot(v.x, v.y, v.z) || 1;
  return { x: v.x / len, y: v.y / len, z: v.z / len };
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

export function build3DShape(key: string, a: number, b: number, h: number): Built3DScene {
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
    const faces = [
      [0, 2, 1],
      [0, 1, 3],
      [1, 2, 3],
      [2, 0, 3]
    ];
    const sDay = (Math.sqrt(3) / 4) * safeA * safeA;
    const slant = Math.hypot(safeH, safeA / (2 * Math.sqrt(3)));
    const sTp = sDay + (key === 'pyramid3' ? 1.5 * safeA * slant : safeA * safeH + 0.5 * safeA * Math.hypot(safeH, safeA * Math.sqrt(3) / 2));
    return {
      vertices,
      faces,
      edges: uniqueEdgesFromFaces(faces),
      metrics: { realH: safeH, sDay, sTp, vol: (sDay * safeH) / 3, formulaNote: 'V = ⅓ · Sđ · h' }
    };
  }

  if (key === 'tetra_ortho') {
    const vertices: NamedVertex[] = [
      { name: 'O', pos: { x: 0, y: 0, z: 0 } },
      { name: 'A', pos: { x: safeA, y: 0, z: 0 } },
      { name: 'B', pos: { x: 0, y: 0, z: safeB } },
      { name: 'C', pos: { x: 0, y: safeH, z: 0 } }
    ];
    const faces = [
      [0, 2, 1],
      [0, 1, 3],
      [0, 3, 2],
      [1, 2, 3]
    ];
    const sDay = 0.5 * safeA * safeB;
    const sABC = 0.5 * Math.hypot(safeA * safeB, safeB * safeH, safeA * safeH);
    return {
      vertices,
      faces,
      edges: uniqueEdgesFromFaces(faces),
      metrics: {
        realH: safeH,
        sDay,
        sTp: sDay + 0.5 * safeA * safeH + 0.5 * safeB * safeH + sABC,
        vol: (safeA * safeB * safeH) / 6,
        formulaNote: 'V = ⅙ · a · b · h'
      }
    };
  }

  if (key === 'pyramid4' || key === 'pyramid4_sa' || key === 'pyramid_trap') {
    const half = safeA / 2;
    const topHalf = key === 'pyramid_trap' ? safeA * 0.55 : safeA;
    const vA: Vec3 = { x: -half, y: 0, z: half };
    const vB: Vec3 = { x: -half + topHalf, y: 0, z: half };
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
    const faces = [
      [0, 3, 2, 1],
      [0, 1, 4],
      [1, 2, 4],
      [2, 3, 4],
      [3, 0, 4]
    ];
    const sDay = key === 'pyramid_trap' ? ((safeA + topHalf) * safeA) / 2 : safeA * safeA;
    const slant = Math.hypot(safeH, half);
    const sTp = sDay + 2 * safeA * slant;
    return {
      vertices,
      faces,
      edges: uniqueEdgesFromFaces(faces),
      metrics: { realH: safeH, sDay, sTp, vol: (sDay * safeH) / 3, formulaNote: 'V = ⅓ · Sđ · h' }
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
    const faces = [
      [0, 2, 1],
      [3, 4, 5],
      [0, 1, 4, 3],
      [1, 2, 5, 4],
      [2, 0, 3, 5]
    ];
    const sDay = (Math.sqrt(3) / 4) * safeA * safeA;
    const sideH = Math.hypot(safeH, shiftX);
    return {
      vertices,
      faces,
      edges: uniqueEdgesFromFaces(faces),
      metrics: { realH: safeH, sDay, sTp: 2 * sDay + 3 * safeA * sideH, vol: sDay * safeH, formulaNote: 'V = Sđ · h' }
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
    const faces: number[][] = [
      [0, 5, 4, 3, 2, 1],
      [6, 7, 8, 9, 10, 11]
    ];
    for (let i = 0; i < 6; i++) {
      const n = (i + 1) % 6;
      faces.push([i, n, n + 6, i + 6]);
    }
    const sDay = ((3 * Math.sqrt(3)) / 2) * safeA * safeA;
    return {
      vertices,
      faces,
      edges: uniqueEdgesFromFaces(faces),
      metrics: { realH: safeH, sDay, sTp: 2 * sDay + 6 * safeA * safeH, vol: sDay * safeH, formulaNote: 'V = Sđ · h' }
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
    return {
      vertices,
      faces,
      edges: uniqueEdgesFromFaces(faces),
      metrics: {
        realH: 2 * r,
        sDay: safeA * safeA,
        sTp: 2 * Math.sqrt(3) * safeA * safeA,
        vol: (Math.sqrt(2) / 3) * safeA * safeA * safeA,
        formulaNote: 'V = (a³√2) / 3'
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
      const sDay = Math.PI * r * r;
      return {
        vertices,
        faces,
        edges,
        metrics: { realH: safeH, sDay, sTp: 2 * sDay + 2 * Math.PI * r * safeH, vol: sDay * safeH, formulaNote: 'V = πR²h' }
      };
    } else {
      const apexIdx = vertices.length;
      vertices.push({ name: 'S', pos: { x: 0, y: safeH, z: 0 } });
      for (let i = 0; i < segs; i++) {
        const n = (i + 1) % segs;
        faces.push([i, n, apexIdx]);
      }
      edges.push({ from: 0, to: apexIdx }, { from: segs / 2, to: apexIdx });
      const l = Math.hypot(r, safeH);
      const sDay = Math.PI * r * r;
      return {
        vertices,
        faces,
        edges,
        metrics: { realH: safeH, sDay, sTp: sDay + Math.PI * r * l, vol: (sDay * safeH) / 3, formulaNote: 'V = ⅓πR²h' }
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
    const sTp = 4 * Math.PI * r * r;
    return {
      vertices,
      faces,
      edges,
      metrics: { realH: 2 * r, sDay: Math.PI * r * r, sTp, vol: (4 / 3) * Math.PI * r * r * r, formulaNote: 'V = ⁴⁄₃πR³' }
    };
  }

  // Default: Cube or Rectangular Box
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
    [0, 3, 2, 1],
    [4, 5, 6, 7],
    [0, 1, 5, 4],
    [1, 2, 6, 5],
    [2, 3, 7, 6],
    [3, 0, 4, 7]
  ];
  const sDay = w * d;
  return {
    vertices,
    faces,
    edges: uniqueEdgesFromFaces(faces),
    metrics: {
      realH: height,
      sDay,
      sTp: 2 * (w * d + w * height + d * height),
      vol: w * d * height,
      formulaNote: key === 'cube' ? 'V = a³' : 'V = a · b · h'
    }
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
  const forward = norm(sub(cam.target, camPos));
  const worldUp: Vec3 = { x: 0, y: 1, z: 0 };
  const right = norm(cross(forward, worldUp));
  const up = cross(right, forward);

  const rel = sub(p, camPos);
  const cx = dot(rel, right);
  const cy = dot(rel, up);
  const cz = Math.max(0.2, dot(rel, forward));
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
  const labels = scene.vertices
    .map((v, idx) => {
      if (!v.name) return '';
      const p = projected[idx];
      return `<text x="${(p.x + 8).toFixed(1)}" y="${(p.y - 6).toFixed(1)}" fill="#f8fafc" font-size="16" font-weight="bold" font-family="sans-serif">${v.name}</text>`;
    })
    .filter(Boolean)
    .join('\n  ');
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${width} ${height}" width="${width}" height="${height}">
  <rect width="100%" height="100%" fill="#090d16"/>
  ${polys}
  ${lines}
  ${labels}
</svg>`;
}
