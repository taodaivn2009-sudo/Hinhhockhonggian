import {
  Built3DScene,
  Camera3D,
  ClickableSegment3D,
  Scene3DSnapshot,
  Vec3,
  add3,
  dist3,
  dot3,
  formatMathNum,
  getCameraBasis,
  intersectRayPlane3D,
  len3,
  lerp3,
  mathToWorld,
  norm3,
  projectPoint,
  scale3,
  screenToRay3D,
  sub3,
  worldToMath
} from './geo3d';

export type DragConstraint3D = 'horizontal' | 'vertical' | 'segment';

export type InteractionMode3D =
  | 'view'
  | 'drag'
  | 'add_point'
  | 'connect_dashed'
  | 'connect_solid'
  | 'connect_vector';

export interface SpecialRatioSpec {
  value: number;
  label: string;
}

export const SPECIAL_EDGE_RATIOS: SpecialRatioSpec[] = [
  { value: 0.25, label: '1/4' },
  { value: 1 / 3, label: '1/3' },
  { value: 0.5, label: 'Trung điểm (1/2)' },
  { value: 2 / 3, label: '2/3' },
  { value: 0.75, label: '3/4' }
];

export interface CameraPreset3D {
  id: 'perspective' | 'front' | 'top' | 'side' | 'iso';
  label: string;
  theta: number;
  phi: number;
  radius: number;
}

export const CAMERA_VIEW_PRESETS_3D: CameraPreset3D[] = [
  { id: 'perspective', label: 'Phối cảnh 3D chuẩn', theta: 0.72, phi: 1.15, radius: 11.5 },
  { id: 'front', label: 'Mặt trước (Oxz)', theta: 0.0, phi: 1.48, radius: 11.5 },
  { id: 'top', label: 'Từ trên xuống (Oxy)', theta: 0.02, phi: 0.14, radius: 12.2 },
  { id: 'side', label: 'Mặt bên (Oyz)', theta: Math.PI / 2, phi: 1.48, radius: 11.5 },
  { id: 'iso', label: 'Đẳng cự SGK (Iso)', theta: 0.58, phi: 1.08, radius: 11.8 }
];

export interface SnapResult3D {
  pos: Vec3;
  isSnapped: boolean;
  reason: string;
}

export interface EdgeHit3D {
  fromName: string;
  toName: string;
  fromPos: Vec3;
  toPos: Vec3;
  ratio: number;
  worldPos: Vec3;
  screenDist: number;
  snapLabel: string;
}

export interface RelationResult3D {
  updates: Record<string, Vec3>;
  linkedVertices: string[];
  relationHint: string;
}

export interface DragRail3D {
  fromName: string;
  toName: string;
  fromPos: Vec3;
  toPos: Vec3;
}

export function snapRatioToSpecial(rawT: number, snapEnabled: boolean): { ratio: number; label: string } {
  const clamped = Math.max(0.05, Math.min(0.95, rawT));
  if (!snapEnabled) return { ratio: Math.round(clamped * 100) / 100, label: '' };
  for (const sp of SPECIAL_EDGE_RATIOS) {
    if (Math.abs(clamped - sp.value) < 0.048) {
      return { ratio: sp.value, label: `Hút: ${sp.label}` };
    }
  }
  return { ratio: Math.round(clamped * 20) / 20, label: '' };
}

/**
 * Magnetic snap to special base targets (vertices, base centroid, edge midpoints)
 * when dragging apex S or foot O horizontally — mirrors Android ShapeBuilder.snapToSpecialBaseTargets.
 */
export function snapToSpecialBaseTargets3D(
  shapeKey: string,
  draggedVertex: string,
  candidatePos: Vec3,
  registry: Record<string, Vec3>
): SnapResult3D | null {
  if (draggedVertex !== 'S' && draggedVertex !== 'O' && draggedVertex !== "O'" && draggedVertex !== "A'") {
    return null;
  }
  const baseCandidates = ['A', 'B', 'C', 'D', 'E', 'F'].filter((k) => registry[k] && k !== draggedVertex);
  if (baseCandidates.length === 0) return null;

  // 1. Snap vertically above a base vertex (e.g. SA ⊥ đáy)
  for (const bName of baseCandidates) {
    const bp = registry[bName];
    const horizDist = Math.hypot(candidatePos.x - bp.x, candidatePos.z - bp.z);
    if (horizDist < 0.36) {
      return {
        pos: { x: bp.x, y: candidatePos.y, z: bp.z },
        isSnapped: true,
        reason:
          draggedVertex === 'S'
            ? `Hút: S thẳng đứng trên ${bName} (S${bName} ⊥ đáy)`
            : `Hút: Trùng vị trí đỉnh đáy ${bName}`
      };
    }
  }

  // 2. Snap to centroid of base (chóp đều / tâm đáy)
  let cx = 0;
  let cz = 0;
  for (const bName of baseCandidates) {
    cx += registry[bName].x;
    cz += registry[bName].z;
  }
  cx /= baseCandidates.length;
  cz /= baseCandidates.length;
  if (Math.hypot(candidatePos.x - cx, candidatePos.z - cz) < 0.36) {
    const isPyramid = shapeKey.startsWith('pyramid') || shapeKey === 'cone';
    return {
      pos: { x: cx, y: candidatePos.y, z: cz },
      isSnapped: true,
      reason: isPyramid ? 'Hút: Tâm đáy (Chóp đều SO ⊥ đáy)' : 'Hút: Tâm mặt đáy'
    };
  }

  // 3. Snap to midpoint of any base edge ((SAB) ⊥ đáy)
  for (let i = 0; i < baseCandidates.length; i++) {
    const n1 = baseCandidates[i];
    const n2 = baseCandidates[(i + 1) % baseCandidates.length];
    const p1 = registry[n1];
    const p2 = registry[n2];
    const mx = (p1.x + p2.x) * 0.5;
    const mz = (p1.z + p2.z) * 0.5;
    if (Math.hypot(candidatePos.x - mx, candidatePos.z - mz) < 0.30) {
      return {
        pos: { x: mx, y: candidatePos.y, z: mz },
        isSnapped: true,
        reason:
          draggedVertex === 'S'
            ? `Hút: Trung điểm ${n1}${n2} ((S${n1}${n2}) ⊥ đáy)`
            : `Hút: Trung điểm cạnh đáy ${n1}${n2}`
      };
    }
  }

  return null;
}

/**
 * Magnetic snap to Oxyz origin, axes, Oxy plane, and 0.5 math grid — mirrors Android snapMathCoordinates.
 */
export function snapMathCoordinates3D(
  worldPos: Vec3,
  originWorld: Vec3,
  snapX: boolean,
  snapY: boolean,
  snapZ: boolean
): SnapResult3D {
  const m = worldToMath(worldPos, originWorld);
  let mx = m.x;
  let my = m.y;
  let mz = m.z;
  const zeroTol = 0.24;
  const stepTol = 0.16;

  let snappedAny = false;
  const snapAxis = (val: number, enabled: boolean): number => {
    if (!enabled) return val;
    if (Math.abs(val) < zeroTol) {
      snappedAny = true;
      return 0;
    }
    const halfStep = Math.round(val * 2) / 2;
    if (Math.abs(val - halfStep) < stepTol) {
      snappedAny = true;
      return halfStep;
    }
    return val;
  };

  mx = snapAxis(mx, snapX);
  my = snapAxis(my, snapY);
  mz = snapAxis(mz, snapZ);

  if (!snappedAny) {
    return { pos: worldPos, isSnapped: false, reason: '' };
  }

  const zZero = Math.abs(mz) < 1e-3;
  const xZero = Math.abs(mx) < 1e-3;
  const yZero = Math.abs(my) < 1e-3;

  let reason = `Hút lưới (${formatMathNum(mx)}; ${formatMathNum(my)}; ${formatMathNum(mz)})`;
  if (xZero && yZero && zZero) reason = 'Hút: Gốc tọa độ O(0; 0; 0)';
  else if (yZero && zZero) reason = `Hút: Trục Ox (${formatMathNum(mx)}; 0; 0)`;
  else if (xZero && zZero) reason = `Hút: Trục Oy (0; ${formatMathNum(my)}; 0)`;
  else if (xZero && yZero) reason = `Hút: Trục Oz (0; 0; ${formatMathNum(mz)})`;
  else if (zZero && snapZ) reason = 'Hút: Mặt phẳng đáy (Oxy)';

  return {
    pos: mathToWorld({ x: mx, y: my, z: mz }, originWorld),
    isSnapped: true,
    reason
  };
}

export function rawParamOnRail3D(
  sx: number,
  sy: number,
  rail: DragRail3D,
  cam: Camera3D,
  width: number,
  height: number
): number | null {
  const pA = projectPoint(rail.fromPos, cam, width, height);
  const pB = projectPoint(rail.toPos, cam, width, height);
  const dx = pB.x - pA.x;
  const dy = pB.y - pA.y;
  const lenSq = dx * dx + dy * dy;
  if (lenSq < 4) return null;
  return ((sx - pA.x) * dx + (sy - pA.y) * dy) / lenSq;
}

/**
 * Computes candidate world position for dragging a vertex along one of the 3 constraints:
 * - horizontal: Oxy plane at startPos.y (with grab-offset preservation & grazing-angle fallback)
 * - vertical: Oz altitude line passing through startPos (with grab-offset & top-view fallback)
 * - segment: along a 3D rail segment with special ratio snapping & grab-offset preservation
 */
export function computeConstrainedDrag3D(params: {
  sx: number;
  sy: number;
  startX: number;
  startY: number;
  cam: Camera3D;
  width: number;
  height: number;
  constraint: DragConstraint3D;
  startPos: Vec3;
  initialHorizHit: Vec3 | null;
  initialVertHit: Vec3 | null;
  initialRailT: number;
  isEdgePoint: boolean;
  rail: DragRail3D | null;
  dragSensitivity: number;
  snapEnabled: boolean;
  shapeKey: string;
  draggedVertex: string;
  originWorld: Vec3;
  registry: Record<string, Vec3>;
}): { pos: Vec3; snap: SnapResult3D; ratio?: number } {
  const {
    sx,
    sy,
    startX,
    startY,
    cam,
    width,
    height,
    constraint,
    startPos,
    initialHorizHit,
    initialVertHit,
    initialRailT,
    isEdgePoint,
    rail,
    dragSensitivity,
    snapEnabled,
    shapeKey,
    draggedVertex,
    originWorld,
    registry
  } = params;

  const deltaScreenX = (sx - startX) * dragSensitivity;
  const deltaScreenY = (sy - startY) * dragSensitivity;
  const effX = startX + deltaScreenX;
  const effY = startY + deltaScreenY;

  const basis = getCameraBasis(cam);
  const focal = Math.min(width, height) * 1.15;
  const viewDepth = Math.max(1.5, dot3(sub3(startPos, basis.camPos), basis.forward));
  const worldPerPx = viewDepth / Math.max(1, focal);
  const ray = screenToRay3D(effX, effY, cam, width, height);

  if (constraint === 'horizontal') {
    let raw: Vec3;
    const hit =
      initialHorizHit && Math.abs(ray.dir.y) >= 0.16
        ? intersectRayPlane3D(ray.origin, ray.dir, startPos, { x: 0, y: 1, z: 0 })
        : null;

    let usedRayHit = false;
    if (hit && initialHorizHit) {
      const dx = hit.x - initialHorizHit.x;
      const dz = hit.z - initialHorizHit.z;
      if (Math.hypot(dx, dz) <= viewDepth * 2.2) {
        raw = {
          x: Math.max(-7.5, Math.min(7.5, startPos.x + dx)),
          y: startPos.y,
          z: Math.max(-7.5, Math.min(7.5, startPos.z + dz))
        };
        usedRayHit = true;
      } else {
        raw = startPos;
      }
    } else {
      raw = startPos;
    }

    if (!usedRayHit) {
      // Grazing-angle fallback (Front / Side view): map screen delta smoothly onto horizontal XZ camera axes
      const rightLen = Math.hypot(basis.right.x, basis.right.z);
      const rightXZ: Vec3 =
        rightLen > 1e-3 ? { x: basis.right.x / rightLen, y: 0, z: basis.right.z / rightLen } : { x: 1, y: 0, z: 0 };
      const fwdLen = Math.hypot(basis.forward.x, basis.forward.z);
      const fwdXZ: Vec3 =
        fwdLen > 1e-3 ? { x: basis.forward.x / fwdLen, y: 0, z: basis.forward.z / fwdLen } : { x: 0, y: 0, z: -1 };
      const moveX = rightXZ.x * (deltaScreenX * worldPerPx) - fwdXZ.x * (deltaScreenY * worldPerPx);
      const moveZ = rightXZ.z * (deltaScreenX * worldPerPx) - fwdXZ.z * (deltaScreenY * worldPerPx);
      raw = {
        x: Math.max(-7.5, Math.min(7.5, startPos.x + moveX)),
        y: startPos.y,
        z: Math.max(-7.5, Math.min(7.5, startPos.z + moveZ))
      };
    }

    if (!snapEnabled) {
      return { pos: raw, snap: { pos: raw, isSnapped: false, reason: '' } };
    }
    const baseSnap = snapToSpecialBaseTargets3D(shapeKey, draggedVertex, raw, registry);
    if (baseSnap) return { pos: baseSnap.pos, snap: baseSnap };

    const gridSnap = snapMathCoordinates3D(raw, originWorld, true, true, false);
    return { pos: gridSnap.pos, snap: gridSnap };
  }

  if (constraint === 'vertical') {
    const horizNormalLen = Math.hypot(basis.forward.x, basis.forward.z);
    const planeNormal: Vec3 =
      horizNormalLen > 1e-3
        ? { x: basis.forward.x / horizNormalLen, y: 0, z: basis.forward.z / horizNormalLen }
        : { x: 0, y: 0, z: 1 };
    const hit =
      initialVertHit && Math.abs(dot3(ray.dir, planeNormal)) >= 0.16
        ? intersectRayPlane3D(ray.origin, ray.dir, startPos, planeNormal)
        : null;
    const rawY =
      hit && initialVertHit
        ? startPos.y + (hit.y - initialVertHit.y)
        : startPos.y - deltaScreenY * worldPerPx;
    const clampedY = Math.max(-4.0, Math.min(9.5, rawY));
    const raw: Vec3 = { x: startPos.x, y: clampedY, z: startPos.z };

    if (!snapEnabled) {
      return { pos: raw, snap: { pos: raw, isSnapped: false, reason: '' } };
    }
    const gridSnap = snapMathCoordinates3D(raw, originWorld, false, false, true);
    return { pos: gridSnap.pos, snap: gridSnap };
  }

  // Segment constraint: project cursor onto 2D projected segment rail with grab-offset preservation
  if (rail) {
    const ptrT = rawParamOnRail3D(effX, effY, rail, cam, width, height);
    const rawT = ptrT !== null ? ptrT + initialRailT : 0.5;
    const minT = isEdgePoint ? 0.04 : -0.5;
    const maxT = isEdgePoint ? 0.96 : 1.5;
    let ratio = Math.max(minT, Math.min(maxT, rawT));
    let label = '';
    if (snapEnabled) {
      if (!isEdgePoint && Math.abs(ratio) < 0.055) {
        ratio = 0;
        label = `Hút: Đỉnh ${rail.fromName}`;
      } else if (!isEdgePoint && Math.abs(ratio - 1) < 0.055) {
        ratio = 1;
        label = `Hút: Đỉnh ${rail.toName}`;
      } else {
        for (const sp of SPECIAL_EDGE_RATIOS) {
          if (Math.abs(ratio - sp.value) < 0.052) {
            ratio = sp.value;
            label = `Hút: ${sp.label}`;
            break;
          }
        }
      }
    }
    const pos = lerp3(rail.fromPos, rail.toPos, ratio);
    return {
      pos,
      ratio,
      snap: {
        pos,
        isSnapped: Boolean(label),
        reason: label
          ? `${label} trên ${rail.fromName}${rail.toName}`
          : `Tỉ lệ k = ${formatMathNum(ratio)} trên ${rail.fromName}${rail.toName}`
      }
    };
  }

  return { pos: startPos, snap: { pos: startPos, isSnapped: false, reason: '' } };
}

/**
 * Finds all candidate rails for dragging a vertex in 'segment' constraint mode.
 * Custom edge points return their exact parent segment; base vertices return all incident edges.
 */
export function findCandidateRailsForPoint3D(
  vertexName: string,
  scene: Built3DScene,
  snap: Scene3DSnapshot
): DragRail3D[] {
  // 1. Custom edge point -> locked to its exact parent edge
  const customPt = snap.customPoints.find((cp) => cp.name === vertexName);
  if (customPt) {
    const pFrom = scene.pointRegistry[customPt.from];
    const pTo = scene.pointRegistry[customPt.to];
    if (pFrom && pTo) {
      return [{ fromName: customPt.from, toName: customPt.to, fromPos: pFrom, toPos: pTo }];
    }
  }
  // 2. Incident segments connected directly to this vertex
  const incident: DragRail3D[] = [];
  for (const seg of scene.clickableSegments) {
    if (seg.fromName === vertexName || seg.toName === vertexName) {
      if (dist3(seg.fromPos, seg.toPos) > 0.05) {
        incident.push({
          fromName: seg.fromName,
          toName: seg.toName,
          fromPos: seg.fromPos,
          toPos: seg.toPos
        });
      }
    }
  }
  if (incident.length > 0) {
    return incident.sort((a, b) => dist3(b.fromPos, b.toPos) - dist3(a.fromPos, a.toPos));
  }
  // 3. Fallback for detached XYZ point -> nearest segment in scene
  const ptPos = scene.pointRegistry[vertexName];
  if (!ptPos) return [];
  let best: DragRail3D | null = null;
  let bestDist = Infinity;
  for (const seg of scene.clickableSegments) {
    const ab = sub3(seg.toPos, seg.fromPos);
    const lenSq = dot3(ab, ab);
    if (lenSq < 1e-5) continue;
    const t = Math.max(0, Math.min(1, dot3(sub3(ptPos, seg.fromPos), ab) / lenSq));
    const d = dist3(ptPos, add3(seg.fromPos, scale3(ab, t)));
    if (d < bestDist) {
      bestDist = d;
      best = {
        fromName: seg.fromName,
        toName: seg.toName,
        fromPos: seg.fromPos,
        toPos: seg.toPos
      };
    }
  }
  return best ? [best] : [];
}

/**
 * Finds the best rail segment for dragging a vertex in 'segment' constraint mode.
 */
export function findBestRailForPoint3D(
  vertexName: string,
  scene: Built3DScene,
  snap: Scene3DSnapshot
): DragRail3D | null {
  return findCandidateRailsForPoint3D(vertexName, scene, snap)[0] || null;
}

/**
 * Finds the nearest 3D clickable segment under screen point (sx, sy) for direct "Chấm điểm trên cạnh".
 */
export function findNearestEdgeHit3D(
  sx: number,
  sy: number,
  segments: ClickableSegment3D[],
  cam: Camera3D,
  width: number,
  height: number,
  maxDistPx: number,
  snapEnabled: boolean
): EdgeHit3D | null {
  let bestHit: EdgeHit3D | null = null;
  let bestDist = maxDistPx;

  for (const seg of segments) {
    const a = projectPoint(seg.fromPos, cam, width, height);
    const b = projectPoint(seg.toPos, cam, width, height);
    const dx = b.x - a.x;
    const dy = b.y - a.y;
    const lenSq = dx * dx + dy * dy;
    if (lenSq < 16) continue;
    const rawT = Math.max(0.05, Math.min(0.95, ((sx - a.x) * dx + (sy - a.y) * dy) / lenSq));
    const projX = a.x + dx * rawT;
    const projY = a.y + dy * rawT;
    const screenDist = Math.hypot(sx - projX, sy - projY);
    if (screenDist < bestDist) {
      bestDist = screenDist;
      const { ratio, label } = snapRatioToSpecial(rawT, snapEnabled);
      bestHit = {
        fromName: seg.fromName,
        toName: seg.toName,
        fromPos: seg.fromPos,
        toPos: seg.toPos,
        ratio,
        worldPos: lerp3(seg.fromPos, seg.toPos, ratio),
        screenDist,
        snapLabel: label
      };
    }
  }
  return bestHit;
}

/**
 * Mirrors Android ShapeBuilder.applyGeometricRelationships():
 * Maintains perpendicularity, parallelism, top-face coplanarity, and radius symmetry when Khóa hình is ON.
 */
export function applyGeometricRelationships3D(
  shapeKey: string,
  draggedName: string,
  newPos: Vec3,
  prevPos: Vec3,
  registry: Record<string, Vec3>
): RelationResult3D {
  const updates: Record<string, Vec3> = { [draggedName]: newPos };
  const linked: string[] = [];
  let relationHint = '';
  const delta = sub3(newPos, prevPos);

  const isPyramid = shapeKey.startsWith('pyramid') || shapeKey === 'cone';
  if (isPyramid) {
    if (draggedName === 'S' && registry['O']) {
      const o = registry['O'];
      updates['O'] = { x: newPos.x, y: o.y, z: newPos.z };
      linked.push('O');
      relationHint = 'Khóa hình: SO ⊥ đáy (O chạy theo hình chiếu S)';
    } else if (draggedName === 'O' && registry['S']) {
      const s = registry['S'];
      updates['S'] = { x: newPos.x, y: s.y, z: newPos.z };
      linked.push('S');
      relationHint = 'Khóa hình: SO ⊥ đáy (S dịch theo chân đường cao O)';
    } else if (['A', 'B', 'C', 'D'].includes(draggedName) && registry['S']) {
      const s = registry['S'];
      // If S was directly above this base vertex (SA ⊥ đáy), move S together!
      if (Math.hypot(s.x - prevPos.x, s.z - prevPos.z) < 0.12) {
        updates['S'] = { x: newPos.x, y: s.y, z: newPos.z };
        linked.push('S');
        if (registry['O']) {
          updates['O'] = { x: newPos.x, y: registry['O'].y, z: newPos.z };
          linked.push('O');
        }
        relationHint = `Khóa hình: S${draggedName} ⊥ đáy`;
      }
      // Cone radius symmetry
      if (shapeKey === 'cone' && (draggedName === 'A' || draggedName === 'B') && registry['O']) {
        const o = registry['O'];
        const opp = draggedName === 'A' ? 'B' : 'A';
        const r = Math.max(0.25, Math.hypot(newPos.x - o.x, newPos.z - o.z));
        updates[opp] = { x: 2 * o.x - newPos.x, y: o.y, z: 2 * o.z - newPos.z };
        linked.push(opp);
        relationHint = `Khóa hình nón: R = ${formatMathNum(r)}`;
      }
    }
    return { updates, linkedVertices: linked, relationHint };
  }

  if (shapeKey === 'cylinder') {
    const o = registry['O'];
    const o1 = registry["O'"];
    if (draggedName === "O'" && o) {
      updates['O'] = { x: newPos.x, y: o.y, z: newPos.z };
      linked.push('O');
      if (registry['A']) {
        updates['A'] = add3(registry['A'], { x: delta.x, y: 0, z: delta.z });
        linked.push('A');
      }
      relationHint = "Khóa trục trụ: OO' ⊥ đáy";
    } else if (draggedName === 'O' && o1) {
      updates["O'"] = { x: newPos.x, y: o1.y, z: newPos.z };
      linked.push("O'");
      if (registry['A']) {
        updates['A'] = add3(registry['A'], delta);
        linked.push('A');
      }
      relationHint = "Khóa trục trụ: dịch chuyển đồng bộ đáy & O'";
    } else if ((draggedName === 'A' || draggedName === 'B') && o) {
      const r = Math.max(0.25, Math.hypot(newPos.x - o.x, newPos.z - o.z));
      const opp = draggedName === 'A' ? 'B' : 'A';
      updates[opp] = { x: 2 * o.x - newPos.x, y: o.y, z: 2 * o.z - newPos.z };
      linked.push(opp);
      relationHint = `Khóa bán kính trụ: R = ${formatMathNum(r)}`;
    }
    return { updates, linkedVertices: linked, relationHint };
  }

  if (shapeKey === 'sphere') {
    const o = registry['O'];
    if (draggedName === 'O') {
      for (const k of ['A', 'B', 'N']) {
        if (registry[k]) {
          updates[k] = add3(registry[k], delta);
          linked.push(k);
        }
      }
      relationHint = 'Tịnh tiến toàn bộ mặt cầu theo tâm O';
    } else if (o && (draggedName === 'A' || draggedName === 'B' || draggedName === 'N')) {
      const r = Math.max(0.3, dist3(o, newPos));
      updates['A'] = { x: o.x + r, y: o.y, z: o.z };
      updates['B'] = { x: o.x - r, y: o.y, z: o.z };
      updates['N'] = { x: o.x, y: o.y + r, z: o.z };
      linked.push(...['A', 'B', 'N'].filter((k) => k !== draggedName));
      relationHint = `Khóa mặt cầu: Bán kính R = ${formatMathNum(r)}`;
    }
    return { updates, linkedVertices: linked, relationHint };
  }

  if (shapeKey === 'prism3_oblique') {
    if (draggedName === "A'") {
      for (const partner of ["B'", "C'"]) {
        if (registry[partner]) {
          updates[partner] = add3(registry[partner], delta);
          linked.push(partner);
        }
      }
      if (registry['O']) {
        updates['O'] = { x: newPos.x, y: registry['O'].y, z: newPos.z };
        linked.push('O');
      }
      relationHint = "Khóa lăng trụ xiên: AA' ∥ BB' ∥ CC' & A'O ⊥ đáy";
    } else if (draggedName === 'O' && registry["A'"]) {
      const a1 = registry["A'"];
      const dHoriz = { x: newPos.x - prevPos.x, y: 0, z: newPos.z - prevPos.z };
      for (const topKey of ["A'", "B'", "C'"]) {
        if (registry[topKey]) {
          updates[topKey] = add3(registry[topKey], dHoriz);
          linked.push(topKey);
        }
      }
      if (a1) relationHint = "Khóa lăng trụ xiên: dịch mặt trên theo chân đường cao O";
    }
    return { updates, linkedVertices: linked, relationHint };
  }

  // Right Prisms, Cube, Box
  const isRightPrism = shapeKey === 'prism3' || shapeKey === 'prism6' || shapeKey === 'cube' || shapeKey === 'box';
  if (isRightPrism) {
    const baseNames =
      shapeKey === 'prism3' ? ['A', 'B', 'C'] : shapeKey === 'prism6' ? ['A', 'B', 'C', 'D', 'E', 'F'] : ['A', 'B', 'C', 'D'];
    const topNames = baseNames.map((n) => `${n}'`);

    if (draggedName === 'O') {
      for (const k of [...baseNames, ...topNames]) {
        if (registry[k]) {
          updates[k] = add3(registry[k], delta);
          linked.push(k);
        }
      }
      relationHint = 'Tịnh tiến toàn khối theo tâm O';
    } else if (baseNames.includes(draggedName)) {
      const topPartner = `${draggedName}'`;
      if (registry[topPartner]) {
        updates[topPartner] = { x: newPos.x, y: registry[topPartner].y, z: newPos.z };
        linked.push(topPartner);
      }
      // Recenter O
      if (registry['O']) {
        let sx = 0;
        let sz = 0;
        for (const bn of baseNames) {
          const p = bn === draggedName ? newPos : registry[bn];
          sx += p.x;
          sz += p.z;
        }
        updates['O'] = { x: sx / baseNames.length, y: registry['O'].y, z: sz / baseNames.length };
        linked.push('O');
      }
      relationHint = `Khóa cạnh bên thẳng đứng: ${draggedName}${topPartner} ⊥ đáy`;
    } else if (topNames.includes(draggedName)) {
      const basePartner = draggedName.slice(0, -1);
      if (registry[basePartner]) {
        updates[basePartner] = { x: newPos.x, y: registry[basePartner].y, z: newPos.z };
        linked.push(basePartner);
      }
      // Level all other top vertices to keep top face parallel to base!
      for (const tn of topNames) {
        if (tn !== draggedName && registry[tn]) {
          updates[tn] = { x: registry[tn].x, y: newPos.y, z: registry[tn].z };
          linked.push(tn);
        }
      }
      relationHint = 'Khóa mặt trên song song đáy & cạnh bên vuông góc';
    }
  }

  return { updates, linkedVertices: linked, relationHint };
}

/**
 * Helper to compute a crisp 3D right-angle L-mark at `foot` between `top - foot` and `ref - foot`.
 */
export function buildRightAngleMark3D(top: Vec3, foot: Vec3, ref: Vec3, size = 0.32): [Vec3, Vec3, Vec3] | null {
  const uRaw = sub3(top, foot);
  const vRaw = sub3(ref, foot);
  if (len3(uRaw) < 0.25 || len3(vRaw) < 0.25) return null;
  const u = scale3(norm3(uRaw), size);
  const v = scale3(norm3(vRaw), size);
  // Only render if reasonably perpendicular
  if (Math.abs(dot3(norm3(uRaw), norm3(vRaw))) > 0.28) return null;
  const p1 = add3(foot, u);
  const p2 = add3(p1, v);
  const p3 = add3(foot, v);
  return [p1, p2, p3];
}
