import {
  Built3DScene,
  Camera3D,
  Vec3,
  add3,
  cross3,
  dist3,
  dot3,
  formatMathNum,
  formatPlaneEquation,
  len3,
  lerp3,
  norm3,
  projectPoint,
  scale3,
  sub3,
  worldToMath
} from './geo3d';

export interface OccluderFace {
  points: Vec3[];
  normal: Vec3;
  center: Vec3;
}

export type RoundBodyHint3D =
  | { kind: 'cylinder'; centerBottom: Vec3; centerTop: Vec3; radius: number }
  | { kind: 'cone'; apex: Vec3; centerBottom: Vec3; radius: number }
  | { kind: 'sphere'; center: Vec3; radius: number };

export interface SphereResult3D {
  kind: 'circum' | 'inscribed';
  centerWorld: Vec3;
  centerMath: Vec3;
  radius: number;
  surfaceArea: number;
  volume: number;
  formulaHint: string;
}

export interface DihedralAngleResult3D {
  angleDegrees: number;
  plane1Eq: string;
  plane2Eq: string;
  cosPhi: number;
}

export interface SkewLinesResult3D {
  distance: number;
  isParallel: boolean;
  isIntersecting: boolean;
  closestOnLine1: Vec3;
  closestOnLine2: Vec3;
}

export interface UnfoldedFace3D {
  points: Vec3[];
  isBase: boolean;
}

export function getCameraWorldPosition(cam: Camera3D): Vec3 {
  return {
    x: cam.target.x + cam.radius * Math.sin(cam.phi) * Math.sin(cam.theta),
    y: cam.target.y + cam.radius * Math.cos(cam.phi),
    z: cam.target.z + cam.radius * Math.sin(cam.phi) * Math.cos(cam.theta)
  };
}

export function buildOccluderFaces(scene: Built3DScene): OccluderFace[] {
  if (scene.vertices.length === 0 || scene.faces.length === 0) return [];
  // Compute solid centroid to guarantee outward-pointing face normals
  let centroid: Vec3 = { x: 0, y: 0, z: 0 };
  for (const v of scene.vertices) centroid = add3(centroid, v.pos);
  centroid = scale3(centroid, 1 / scene.vertices.length);

  const out: OccluderFace[] = [];
  for (const f of scene.faces) {
    if (f.length < 3) continue;
    const pts = f.map((i) => scene.vertices[i].pos);
    let center: Vec3 = { x: 0, y: 0, z: 0 };
    for (const p of pts) center = add3(center, p);
    center = scale3(center, 1 / pts.length);
    const rawN = cross3(sub3(pts[1], pts[0]), sub3(pts[2], pts[0]));
    const nLen = len3(rawN);
    if (nLen < 1e-5) continue;
    let n = scale3(rawN, 1 / nLen);
    if (dot3(n, sub3(center, centroid)) < 0) {
      n = scale3(n, -1);
    }
    out.push({ points: pts, normal: n, center });
  }
  return out;
}

export function getRoundBodyHint(shapeKey: string, a: number, h: number): RoundBodyHint3D | null {
  const safeA = Math.max(0.5, a);
  const safeH = Math.max(0.5, h);
  if (shapeKey === 'cylinder') {
    return {
      kind: 'cylinder',
      centerBottom: { x: 0, y: 0, z: 0 },
      centerTop: { x: 0, y: safeH, z: 0 },
      radius: safeA * 0.55
    };
  }
  if (shapeKey === 'cone') {
    return {
      kind: 'cone',
      apex: { x: 0, y: safeH, z: 0 },
      centerBottom: { x: 0, y: 0, z: 0 },
      radius: safeA * 0.55
    };
  }
  if (shapeKey === 'sphere') {
    const r = safeA * 0.6;
    return { kind: 'sphere', center: { x: 0, y: r, z: 0 }, radius: r };
  }
  return null;
}

function isPointInConvexPolygon3D(pt: Vec3, poly: Vec3[], normal: Vec3, edgeTolFactor: number): boolean {
  let hasPos = false;
  let hasNeg = false;
  const n = poly.length;
  for (let i = 0; i < n; i++) {
    const a = poly[i];
    const b = poly[(i + 1) % n];
    const edge = sub3(b, a);
    const edgeLen = len3(edge);
    if (edgeLen < 1e-5) continue;
    const tol = edgeLen * edgeTolFactor;
    const d = dot3(cross3(edge, sub3(pt, a)), normal);
    if (d > tol) hasPos = true;
    else if (d < -tol) hasNeg = true;
    if (hasPos && hasNeg) return false;
  }
  return true;
}

function isPointOccludedByRoundBody(q: Vec3, camPos: Vec3, hint: RoundBodyHint3D): boolean {
  const dir = sub3(q, camPos);
  const uMin = 0.005;
  const uMax = 0.98;
  if (hint.kind === 'sphere') {
    const fromCenter = sub3(q, hint.center);
    const distC = len3(fromCenter);
    if (Math.abs(distC - hint.radius) < 0.03 && dot3(fromCenter, sub3(camPos, q)) >= -0.02) {
      return false;
    }
    const oc = sub3(camPos, hint.center);
    const A = dot3(dir, dir);
    if (A < 1e-6) return false;
    const B = 2 * dot3(oc, dir);
    const C = dot3(oc, oc) - hint.radius * hint.radius;
    const disc = B * B - 4 * A * C;
    if (disc <= 0) return false;
    const s = Math.sqrt(disc);
    const u1 = (-B - s) / (2 * A);
    const u2 = (-B + s) / (2 * A);
    return (u1 >= uMin && u1 <= uMax) || (u2 >= uMin && u2 <= uMax);
  }
  if (hint.kind === 'cylinder') {
    const cx = hint.centerBottom.x;
    const cz = hint.centerBottom.z;
    const yMin = Math.min(hint.centerBottom.y, hint.centerTop.y);
    const yMax = Math.max(hint.centerBottom.y, hint.centerTop.y);
    const rSq = hint.radius * hint.radius;
    const qRad = Math.hypot(q.x - cx, q.z - cz);
    if (Math.abs(q.y - yMax) < 0.03 && qRad <= hint.radius + 0.03 && camPos.y > yMax) return false;
    if (Math.abs(q.y - yMin) < 0.03 && qRad <= hint.radius + 0.03 && camPos.y < yMin) return false;
    if (q.y >= yMin - 0.03 && q.y <= yMax + 0.03 && Math.abs(qRad - hint.radius) < 0.03) {
      if (dot3({ x: q.x - cx, y: 0, z: q.z - cz }, sub3(camPos, q)) >= -0.02) return false;
    }
    if (Math.abs(dir.y) > 1e-5) {
      for (const yCap of [yMax, yMin]) {
        const u = (yCap - camPos.y) / dir.y;
        if (u >= uMin && u <= uMax) {
          const hx = camPos.x + dir.x * u - cx;
          const hz = camPos.z + dir.z * u - cz;
          if (hx * hx + hz * hz <= rSq + 1e-3) return true;
        }
      }
    }
    const ox = camPos.x - cx;
    const oz = camPos.z - cz;
    const A = dir.x * dir.x + dir.z * dir.z;
    if (A > 1e-6) {
      const B = 2 * (ox * dir.x + oz * dir.z);
      const C = ox * ox + oz * oz - rSq;
      const disc = B * B - 4 * A * C;
      if (disc > 0) {
        const s = Math.sqrt(disc);
        for (const u of [(-B - s) / (2 * A), (-B + s) / (2 * A)]) {
          if (u >= uMin && u <= uMax) {
            const hy = camPos.y + dir.y * u;
            if (hy >= yMin - 1e-3 && hy <= yMax + 1e-3) return true;
          }
        }
      }
    }
    return false;
  }
  // Cone
  const cx = hint.centerBottom.x;
  const cz = hint.centerBottom.z;
  const yBase = hint.centerBottom.y;
  const yApex = hint.apex.y;
  const h = yApex - yBase;
  if (Math.abs(h) < 1e-4) return false;
  const rSq = hint.radius * hint.radius;
  const qRad = Math.hypot(q.x - cx, q.z - cz);
  if (Math.abs(q.y - yBase) < 0.03 && qRad <= hint.radius + 0.03 && camPos.y < yBase) return false;
  const expR = hint.radius * ((yApex - q.y) / h);
  if (expR >= -0.02 && Math.abs(qRad - expR) < 0.03 && qRad > 1e-4) {
    const outN: Vec3 = { x: (q.x - cx) / qRad, y: hint.radius / h, z: (q.z - cz) / qRad };
    if (dot3(outN, sub3(camPos, q)) >= -0.02) return false;
  }
  if (Math.abs(dir.y) > 1e-5) {
    const uBot = (yBase - camPos.y) / dir.y;
    if (uBot >= uMin && uBot <= uMax) {
      const hx = camPos.x + dir.x * uBot - cx;
      const hz = camPos.z + dir.z * uBot - cz;
      if (hx * hx + hz * hz <= rSq + 1e-3) return true;
    }
  }
  const k = hint.radius / h;
  const k2 = k * k;
  const ox = camPos.x - hint.apex.x;
  const oy = camPos.y - hint.apex.y;
  const oz = camPos.z - hint.apex.z;
  const A = dir.x * dir.x + dir.z * dir.z - k2 * dir.y * dir.y;
  const B = 2 * (ox * dir.x + oz * dir.z - k2 * oy * dir.y);
  const C = ox * ox + oz * oz - k2 * oy * oy;
  const yLo = Math.min(yBase, yApex) - 1e-3;
  const yHi = Math.max(yBase, yApex) + 1e-3;
  if (Math.abs(A) > 1e-6) {
    const disc = B * B - 4 * A * C;
    if (disc > 0) {
      const s = Math.sqrt(disc);
      for (const u of [(-B - s) / (2 * A), (-B + s) / (2 * A)]) {
        if (u >= uMin && u <= uMax) {
          const hy = camPos.y + dir.y * u;
          if (hy >= yLo && hy <= yHi) return true;
        }
      }
    }
  }
  return false;
}

function isPointOccludedBySolid(
  q: Vec3,
  camPos: Vec3,
  occluderFaces: OccluderFace[],
  roundBodyHint: RoundBodyHint3D | null
): boolean {
  if (roundBodyHint) {
    return isPointOccludedByRoundBody(q, camPos, roundBodyHint);
  }
  if (occluderFaces.length > 0) {
    let onFrontFace = false;
    let onBackFace = false;
    for (const face of occluderFaces) {
      if (face.points.length < 3) continue;
      const planeDist = Math.abs(dot3(sub3(q, face.points[0]), face.normal));
      if (planeDist < 0.015 && isPointInConvexPolygon3D(q, face.points, face.normal, 0.01)) {
        if (dot3(face.normal, sub3(camPos, face.center)) > 0) onFrontFace = true;
        else onBackFace = true;
      }
    }
    if (onFrontFace) return false;
    if (onBackFace) return true;

    const dir = sub3(q, camPos);
    for (const face of occluderFaces) {
      if (face.points.length < 3) continue;
      if (dot3(face.normal, sub3(camPos, face.center)) <= 0) continue;
      const denom = dot3(dir, face.normal);
      if (denom >= -1e-5) continue;
      const u = dot3(sub3(face.points[0], camPos), face.normal) / denom;
      if (u > 0.005 && u < 0.985) {
        const hit = add3(camPos, scale3(dir, u));
        if (isPointInConvexPolygon3D(hit, face.points, face.normal, 0.002)) return true;
      }
    }
  }
  return false;
}

export function isSegmentOccludedBySolid(
  p1: Vec3,
  p2: Vec3,
  camPos: Vec3,
  occluderFaces: OccluderFace[],
  roundBodyHint: RoundBodyHint3D | null
): boolean {
  if (occluderFaces.length === 0 && !roundBodyHint) return false;
  if (dist3(p1, p2) < 1e-4) return false;
  const qMid = lerp3(p1, p2, 0.5);
  if (isPointOccludedBySolid(qMid, camPos, occluderFaces, roundBodyHint)) return true;
  const q1 = lerp3(p1, p2, 0.25);
  const q3 = lerp3(p1, p2, 0.75);
  return (
    isPointOccludedBySolid(q1, camPos, occluderFaces, roundBodyHint) &&
    isPointOccludedBySolid(q3, camPos, occluderFaces, roundBodyHint)
  );
}

/**
 * Exact 3D Circumscribed Sphere (Mặt cầu ngoại tiếp) for all 15 solids.
 */
export function computeCircumSphere(
  scene: Built3DScene,
  shapeKey: string,
  a: number,
  b: number,
  h: number,
  originWorld: Vec3
): SphereResult3D | null {
  const safeA = Math.max(0.5, a);
  const safeB = Math.max(0.5, b);
  const safeH = Math.max(0.5, h);

  let center: Vec3 = { x: 0, y: 0, z: 0 };
  let radius = 0;
  let formulaHint = 'R = √(R_đáy² + (h/2)²)';

  if (shapeKey === 'sphere') {
    const r = safeA * 0.6;
    center = { x: 0, y: r, z: 0 };
    radius = r;
    formulaHint = 'Mặt cầu tâm O, bán kính R';
  } else if (shapeKey === 'cube') {
    center = { x: 0, y: safeA / 2, z: 0 };
    radius = (safeA * Math.sqrt(3)) / 2;
    formulaHint = 'R = a√3 / 2';
  } else if (shapeKey === 'box') {
    center = { x: 0, y: safeH / 2, z: 0 };
    radius = 0.5 * Math.hypot(safeA, safeB, safeH);
    formulaHint = 'R = ½√(a² + b² + h²)';
  } else if (shapeKey === 'tetra_ortho') {
    center = { x: safeA / 2, y: safeH / 2, z: safeB / 2 };
    radius = 0.5 * Math.hypot(safeA, safeB, safeH);
    formulaHint = 'R = ½√(OA² + OB² + OC²)';
  } else if (shapeKey === 'octahedron') {
    const r = safeA / Math.sqrt(2);
    center = { x: 0, y: r, z: 0 };
    radius = r;
    formulaHint = 'R = a√2 / 2';
  } else if (shapeKey === 'pyramid3') {
    const rBase = safeA / Math.sqrt(3);
    const rVal = (safeH * safeH + rBase * rBase) / (2 * safeH);
    center = { x: 0, y: safeH - rVal, z: 0 };
    radius = rVal;
    formulaHint = 'R = (h² + R_đáy²) / (2h) = b² / (2h)';
  } else if (shapeKey === 'pyramid3_sa') {
    const rBase = safeA / Math.sqrt(3);
    center = { x: 0, y: safeH / 2, z: 0 };
    radius = Math.hypot(rBase, safeH / 2);
    formulaHint = 'R = √(R_đáy² + (SA/2)²)';
  } else if (shapeKey === 'pyramid4') {
    const rBase = safeA / Math.sqrt(2);
    const rVal = (safeH * safeH + rBase * rBase) / (2 * safeH);
    center = { x: 0, y: safeH - rVal, z: 0 };
    radius = rVal;
    formulaHint = 'R = (h² + (a√2/2)²) / (2h)';
  } else if (shapeKey === 'pyramid4_sa') {
    const rBase = safeA / Math.sqrt(2);
    center = { x: 0, y: safeH / 2, z: 0 };
    radius = Math.hypot(rBase, safeH / 2);
    formulaHint = 'R = ½√(SA² + AC²)';
  } else if (shapeKey === 'prism3' || shapeKey === 'prism3_oblique') {
    const shiftX = shapeKey === 'prism3_oblique' ? safeA * 0.35 : 0;
    const rBase = safeA / Math.sqrt(3);
    center = { x: shiftX / 2, y: safeH / 2, z: 0 };
    radius = Math.hypot(rBase, safeH / 2, shiftX / 2);
    formulaHint = 'R = √(R_đáy² + (h/2)²)';
  } else if (shapeKey === 'prism6') {
    center = { x: 0, y: safeH / 2, z: 0 };
    radius = Math.hypot(safeA, safeH / 2);
    formulaHint = 'R = √(a² + (h/2)²)';
  } else if (shapeKey === 'cylinder') {
    const r = safeA * 0.55;
    center = { x: 0, y: safeH / 2, z: 0 };
    radius = Math.hypot(r, safeH / 2);
    formulaHint = 'R_mc = √(R_đáy² + (h/2)²)';
  } else if (shapeKey === 'cone') {
    const r = safeA * 0.55;
    const rVal = (safeH * safeH + r * r) / (2 * safeH);
    center = { x: 0, y: safeH - rVal, z: 0 };
    radius = rVal;
    formulaHint = 'R_mc = l² / (2h)';
  } else {
    // General fallback: bounding sphere from vertices
    if (scene.vertices.length === 0) return null;
    let c: Vec3 = { x: 0, y: 0, z: 0 };
    for (const v of scene.vertices) c = add3(c, v.pos);
    center = scale3(c, 1 / scene.vertices.length);
    radius = Math.max(...scene.vertices.map((v) => dist3(center, v.pos)));
    formulaHint = 'Mặt cầu ngoại tiếp gần đúng qua các đỉnh';
  }

  return {
    kind: 'circum',
    centerWorld: center,
    centerMath: worldToMath(center, originWorld),
    radius,
    surfaceArea: 4 * Math.PI * radius * radius,
    volume: (4 / 3) * Math.PI * radius * radius * radius,
    formulaHint
  };
}

/**
 * Inscribed Sphere (Mặt cầu nội tiếp): r = 3V / S_tp for polyhedra tangent to all faces,
 * and exact formulas for cube, octahedron, pyramids, cone, cylinder, sphere.
 */
export function computeInSphere(
  scene: Built3DScene,
  shapeKey: string,
  a: number,
  b: number,
  h: number,
  originWorld: Vec3
): SphereResult3D | null {
  const safeA = Math.max(0.5, a);
  const safeB = Math.max(0.5, b);
  const safeH = Math.max(0.5, h);

  let center: Vec3 = { x: 0, y: 0, z: 0 };
  let radius = 0;
  let formulaHint = 'r = 3V / S_tp';

  if (shapeKey === 'sphere') {
    const r = safeA * 0.6;
    center = { x: 0, y: r, z: 0 };
    radius = r;
    formulaHint = 'Mặt cầu nội tiếp trùng chính nó';
  } else if (shapeKey === 'cube') {
    radius = safeA / 2;
    center = { x: 0, y: radius, z: 0 };
    formulaHint = 'r = a / 2';
  } else if (shapeKey === 'box') {
    radius = Math.min(safeA, safeB, safeH) / 2;
    center = { x: 0, y: safeH / 2, z: 0 };
    formulaHint = 'r = min(a, b, h) / 2 (lớn nhất trong hộp)';
  } else if (shapeKey === 'octahedron') {
    radius = (safeA * Math.sqrt(6)) / 6;
    center = { x: 0, y: safeA / Math.sqrt(2), z: 0 };
    formulaHint = 'r = a√6 / 6';
  } else if (shapeKey === 'cylinder') {
    const r = safeA * 0.55;
    radius = Math.min(r, safeH / 2);
    center = { x: 0, y: safeH / 2, z: 0 };
    formulaHint = 'r = min(R_đáy, h/2)';
  } else if (shapeKey === 'cone') {
    const r = safeA * 0.55;
    const l = Math.hypot(r, safeH);
    radius = (r * safeH) / (r + l);
    center = { x: 0, y: radius, z: 0 };
    formulaHint = 'r = (R·h) / (R + l)';
  } else if (shapeKey === 'prism3' || shapeKey === 'prism3_oblique') {
    const shiftX = shapeKey === 'prism3_oblique' ? safeA * 0.35 : 0;
    const rInBase = safeA / (2 * Math.sqrt(3));
    radius = Math.min(rInBase, safeH / 2);
    center = { x: shiftX / 2, y: safeH / 2, z: 0 };
    formulaHint = 'r = min(r_đáy, h/2)';
  } else if (shapeKey === 'prism6') {
    const rInBase = (safeA * Math.sqrt(3)) / 2;
    radius = Math.min(rInBase, safeH / 2);
    center = { x: 0, y: safeH / 2, z: 0 };
    formulaHint = 'r = min(a√3/2, h/2)';
  } else {
    // Pyramids & tetrahedra: r = 3V / S_tp
    const sTp = scene.metrics.sTp;
    if (sTp < 1e-4) return null;
    radius = (3 * scene.metrics.vol) / sTp;
    if (shapeKey === 'tetra_ortho') {
      center = { x: radius, y: radius, z: radius };
    } else if (shapeKey === 'pyramid3' || shapeKey === 'pyramid4') {
      center = { x: 0, y: radius, z: 0 };
    } else {
      // Centroid of base shifted up by radius
      const basePts = scene.vertices.filter((v) => Math.abs(v.pos.y) < 1e-3);
      let bx = 0;
      let bz = 0;
      for (const bp of basePts) {
        bx += bp.pos.x;
        bz += bp.pos.z;
      }
      const n = Math.max(1, basePts.length);
      center = { x: bx / n, y: radius, z: bz / n };
    }
    formulaHint = 'r = 3V / S_tp';
  }

  return {
    kind: 'inscribed',
    centerWorld: center,
    centerMath: worldToMath(center, originWorld),
    radius,
    surfaceArea: 4 * Math.PI * radius * radius,
    volume: (4 / 3) * Math.PI * radius * radius * radius,
    formulaHint
  };
}

/**
 * Angle between two planes (P1P2P3) and (Q1Q2Q3) in [0°, 90°].
 */
export function computeDihedralAngle(
  p1: Vec3,
  p2: Vec3,
  p3: Vec3,
  q1: Vec3,
  q2: Vec3,
  q3: Vec3,
  originWorld: Vec3
): DihedralAngleResult3D | null {
  const n1 = cross3(sub3(p2, p1), sub3(p3, p1));
  const n2 = cross3(sub3(q2, q1), sub3(q3, q1));
  const l1 = len3(n1);
  const l2 = len3(n2);
  if (l1 < 1e-4 || l2 < 1e-4) return null;

  const cosPhi = Math.min(1, Math.max(0, Math.abs(dot3(n1, n2)) / (l1 * l2)));
  const angleDegrees = (Math.acos(cosPhi) * 180) / Math.PI;
  const plane1Eq = formatPlaneEquation(
    worldToMath(p1, originWorld),
    worldToMath(p2, originWorld),
    worldToMath(p3, originWorld)
  );
  const plane2Eq = formatPlaneEquation(
    worldToMath(q1, originWorld),
    worldToMath(q2, originWorld),
    worldToMath(q3, originWorld)
  );
  return { angleDegrees, plane1Eq, plane2Eq, cosPhi };
}

/**
 * Shortest distance and common perpendicular endpoints between two 3D lines (a1,a2) and (b1,b2).
 */
export function computeSkewLinesDistance(a1: Vec3, a2: Vec3, b1: Vec3, b2: Vec3): SkewLinesResult3D | null {
  const u = sub3(a2, a1);
  const v = sub3(b2, b1);
  const w0 = sub3(a1, b1);
  const a = dot3(u, u);
  const b = dot3(u, v);
  const c = dot3(v, v);
  if (a < 1e-6 || c < 1e-6) return null;

  const d = dot3(u, w0);
  const e = dot3(v, w0);
  const denom = a * c - b * b;

  if (Math.abs(denom) < 1e-5) {
    // Parallel lines: project b1 onto line1
    const s = -d / a;
    const h1 = add3(a1, scale3(u, s));
    const dist = dist3(h1, b1);
    return {
      distance: dist,
      isParallel: true,
      isIntersecting: dist < 1e-3,
      closestOnLine1: h1,
      closestOnLine2: b1
    };
  }

  const sc = (b * e - c * d) / denom;
  const tc = (a * e - b * d) / denom;
  const h1 = add3(a1, scale3(u, sc));
  const h2 = add3(b1, scale3(v, tc));
  const distance = dist3(h1, h2);
  return {
    distance,
    isParallel: false,
    isIntersecting: distance < 1e-3,
    closestOnLine1: h1,
    closestOnLine2: h2
  };
}

function rotatePointAroundAxis(pt: Vec3, origin: Vec3, axisUnit: Vec3, angleRad: number): Vec3 {
  const p = sub3(pt, origin);
  const cosA = Math.cos(angleRad);
  const sinA = Math.sin(angleRad);
  const kDotP = dot3(axisUnit, p);
  const kCrossP = cross3(axisUnit, p);
  return add3(origin, {
    x: p.x * cosA + kCrossP.x * sinA + axisUnit.x * kDotP * (1 - cosA),
    y: p.y * cosA + kCrossP.y * sinA + axisUnit.y * kDotP * (1 - cosA),
    z: p.z * cosA + kCrossP.z * sinA + axisUnit.z * kDotP * (1 - cosA)
  });
}

/**
 * Unfolds polyhedron faces onto the y = 0 base plane as unfoldRatio goes from 0 -> 1.
 * Works cleanly for pyramids, tetrahedra, prisms, cubes, boxes, and octahedron!
 */
export function buildUnfoldedFaces3D(
  scene: Built3DScene,
  shapeKey: string,
  unfoldRatio: number
): UnfoldedFace3D[] | null {
  if (shapeKey === 'cylinder' || shapeKey === 'cone' || shapeKey === 'sphere') return null;
  const t = Math.max(0, Math.min(1, unfoldRatio));
  if (t <= 0.001) return null;

  const occluders = buildOccluderFaces(scene);
  if (occluders.length === 0) return null;

  // Identify base face (face with lowest average y)
  let baseIdx = 0;
  let minY = Infinity;
  occluders.forEach((f, idx) => {
    if (f.center.y < minY) {
      minY = f.center.y;
      baseIdx = idx;
    }
  });
  const baseFace = occluders[baseIdx];
  const baseNormal = baseFace.normal; // points downward (-y) for standard base
  const result: UnfoldedFace3D[] = [{ points: baseFace.points, isBase: true }];

  // Helper to find shared hinge edge between two faces
  const findSharedEdge = (fA: Vec3[], fB: Vec3[]): [Vec3, Vec3] | null => {
    const shared: Vec3[] = [];
    for (const p of fA) {
      if (fB.some((q) => dist3(p, q) < 1e-3)) shared.push(p);
    }
    return shared.length >= 2 ? [shared[0], shared[1]] : null;
  };

  // Track which side faces unfolded directly from base so top face can hinge off the first side face
  let firstSideFaceOrig: OccluderFace | null = null;
  let firstSideHinge: [Vec3, Vec3] | null = null;
  let firstSideAngle = 0;
  let firstSideAxis: Vec3 = { x: 1, y: 0, z: 0 };

  const remaining: OccluderFace[] = [];

  for (let i = 0; i < occluders.length; i++) {
    if (i === baseIdx) continue;
    const face = occluders[i];
    const hinge = findSharedEdge(baseFace.points, face.points);
    if (!hinge) {
      remaining.push(face);
      continue;
    }

    // Orient hinge axis so rotating the face outward aligns face.normal with baseNormal
    const [h0, h1] = hinge;
    const axis = norm3(sub3(h1, h0));
    const cosAngle = Math.max(-1, Math.min(1, dot3(face.normal, baseNormal)));
    const fullAngle = Math.acos(cosAngle);

    // Pick sign that moves face.center farther from baseFace.center (unfolding outward)
    const testPos = rotatePointAroundAxis(face.center, h0, axis, fullAngle * 0.25);
    const testNeg = rotatePointAroundAxis(face.center, h0, axis, -fullAngle * 0.25);
    const sign = dist3(testPos, baseFace.center) >= dist3(testNeg, baseFace.center) ? 1 : -1;
    const angle = sign * fullAngle * t;

    const unfoldedPts = face.points.map((p) => rotatePointAroundAxis(p, h0, axis, angle));
    result.push({ points: unfoldedPts, isBase: false });

    if (!firstSideFaceOrig) {
      firstSideFaceOrig = face;
      firstSideHinge = hinge;
      firstSideAngle = angle;
      firstSideAxis = axis;
    }
  }

  // Unfold top cap or upper faces (e.g. prisms, cube, box, octahedron) attached to first side face
  for (const remFace of remaining) {
    if (firstSideFaceOrig && firstSideHinge) {
      const topHinge = findSharedEdge(firstSideFaceOrig.points, remFace.points);
      if (topHinge) {
        const [th0, th1] = topHinge;
        const tAxis = norm3(sub3(th1, th0));
        const cosA = Math.max(-1, Math.min(1, dot3(remFace.normal, firstSideFaceOrig.normal)));
        const fullA = Math.acos(cosA);
        const testP = rotatePointAroundAxis(remFace.center, th0, tAxis, fullA * 0.25);
        const testN = rotatePointAroundAxis(remFace.center, th0, tAxis, -fullA * 0.25);
        const sgn = dist3(testP, firstSideFaceOrig.center) >= dist3(testN, firstSideFaceOrig.center) ? 1 : -1;
        const step1Pts = remFace.points.map((p) => rotatePointAroundAxis(p, th0, tAxis, sgn * fullA * t));
        const step2Pts = step1Pts.map((p) =>
          rotatePointAroundAxis(p, firstSideHinge![0], firstSideAxis, firstSideAngle)
        );
        result.push({ points: step2Pts, isBase: false });
        continue;
      }
    }
    result.push({ points: remFace.points, isBase: false });
  }

  return result;
}

/**
 * Generates compact 2D SVG polyline segments for mini 3D shape thumbnails (38x38 box).
 */
export function buildShapeThumbnailEdges(
  scene: Built3DScene,
  size = 38
): { x1: number; y1: number; x2: number; y2: number }[] {
  const cam: Camera3D = { theta: 0.72, phi: 1.18, radius: 11.5, target: { x: 0, y: 1.6, z: 0 } };
  const proj = scene.vertices.map((v) => projectPoint(v.pos, cam, size, size));
  let minX = Infinity;
  let maxX = -Infinity;
  let minY = Infinity;
  let maxY = -Infinity;
  for (const p of proj) {
    if (p.x < minX) minX = p.x;
    if (p.x > maxX) maxX = p.x;
    if (p.y < minY) minY = p.y;
    if (p.y > maxY) maxY = p.y;
  }
  const span = Math.max(1e-3, Math.max(maxX - minX, maxY - minY));
  const pad = 5;
  const scale = (size - pad * 2) / span;
  const cx = (minX + maxX) * 0.5;
  const cy = (minY + maxY) * 0.5;
  const mapPt = (p: { x: number; y: number }) => ({
    x: size * 0.5 + (p.x - cx) * scale,
    y: size * 0.5 + (p.y - cy) * scale
  });
  return scene.edges.slice(0, 48).map((e) => {
    const a = mapPt(proj[e.from]);
    const b = mapPt(proj[e.to]);
    return { x1: a.x, y1: a.y, x2: b.x, y2: b.y };
  });
}

export function formatCoord3D(v: Vec3): string {
  return `(${formatMathNum(v.x)}; ${formatMathNum(v.y)}; ${formatMathNum(v.z)})`;
}
