import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Axis3D,
  Box,
  Calculator,
  Camera,
  Check,
  ChevronDown,
  ChevronUp,
  Crosshair,
  Lock,
  Magnet,
  Maximize2,
  Minimize2,
  Minus,
  Move,
  MoveHorizontal,
  MoveVertical,
  Pencil,
  Plus,
  Redo2,
  RefreshCw,
  RotateCcw,
  Save,
  Scissors,
  Sliders,
  Undo2,
  Unlock
} from 'lucide-react';
import {
  ALL_3D_SHAPES,
  Camera3D,
  LineStyle3D,
  Scene3DSnapshot,
  Vec3,
  build3DSceneFromSnapshot,
  exportSceneToObj,
  exportSceneToSvg,
  formatMathNum,
  nextAutoPointName,
  projectPoint,
  sub3,
  dot3,
  cross3,
  norm3,
  worldToMath
} from '../engine/geo3d';
import {
  buildOccluderFaces,
  buildUnfoldedFaces3D,
  computeCircumSphere,
  computeInSphere,
  computeSkewLinesDistance,
  formatCoord3D,
  getCameraWorldPosition,
  getRoundBodyHint,
  isSegmentOccludedBySolid
} from '../engine/geo3dAdvanced';
import {
  CAMERA_VIEW_PRESETS_3D,
  DragConstraint3D,
  DragRail3D,
  EdgeHit3D,
  InteractionMode3D,
  applyGeometricRelationships3D,
  buildRightAngleMark3D,
  computeConstrainedDrag3D,
  findBestRailForPoint3D,
  findNearestEdgeHit3D
} from '../engine/geo3dDragSnap';
import { buildShareUrl3D } from '../services/shareUrl';
import { AiDrawingPlan, Sensitivity3D, ThemeMode } from '../types/app';
import { SavedProject3D, Space3DExportModal } from './Space3DExportModal';
import { InspectorTab3D, Space3DInspector } from './Space3DInspector';

const SAVED_3D_KEY = 'tooltoan_saved_3d_v2';

const DEFAULT_SNAPSHOT: Scene3DSnapshot = {
  shapeKey: 'pyramid4',
  a: 3.6,
  b: 3.0,
  h: 4.2,
  originPoint: 'A',
  vertexOverrides: {},
  customPoints: [],
  customXYZPoints: [],
  customLines: [],
  customPlanes: [],
  dynamicSection: null,
  showSection: false,
  secRatio: 0.5
};

const DEFAULT_CAMERA: Camera3D = {
  theta: 0.72,
  phi: 1.15,
  radius: 11.5,
  target: { x: 0, y: 1.5, z: 0 }
};

interface Space3DWorkspaceProps {
  accentHex: string;
  themeMode: ThemeMode;
  isImmersive: boolean;
  onToggleImmersive: () => void;
  sensitivity: Sensitivity3D;
  onUpdateSensitivity: (next: Sensitivity3D) => void;
  externalPlan: AiDrawingPlan | null;
  onPlanConsumed: () => void;
  externalSnapshot: Scene3DSnapshot | null;
  onExternalSnapshotConsumed: () => void;
}

interface LiveDragFeedback {
  vertexName: string;
  worldPos: Vec3;
  isSnapped: boolean;
  snapReason: string;
  relationHint: string;
  linkedVertices: string[];
  rail: DragRail3D | null;
}

export const Space3DWorkspace: React.FC<Space3DWorkspaceProps> = ({
  accentHex,
  themeMode,
  isImmersive,
  onToggleImmersive,
  sensitivity,
  onUpdateSensitivity,
  externalPlan,
  onPlanConsumed,
  externalSnapshot,
  onExternalSnapshotConsumed
}) => {
  const [snap, setSnap] = useState<Scene3DSnapshot>(DEFAULT_SNAPSHOT);
  const [undoStack, setUndoStack] = useState<Scene3DSnapshot[]>([]);
  const [redoStack, setRedoStack] = useState<Scene3DSnapshot[]>([]);

  const [camera, setCamera] = useState<Camera3D>(DEFAULT_CAMERA);
  const [mode, setMode] = useState<InteractionMode3D>('view');
  const [dragConstraint, setDragConstraint] = useState<DragConstraint3D>('horizontal');
  const [keepRelations, setKeepRelations] = useState<boolean>(true);
  const [snapEnabled, setSnapEnabled] = useState<boolean>(true);

  const [showFaces, setShowFaces] = useState<boolean>(true);
  const [showHiddenDashed, setShowHiddenDashed] = useState<boolean>(true);
  const [showAxes, setShowAxes] = useState<boolean>(true);
  const [autoRotate, setAutoRotate] = useState<boolean>(false);
  const [showCircumSphere, setShowCircumSphere] = useState<boolean>(false);
  const [showInSphere, setShowInSphere] = useState<boolean>(false);
  const [unfoldRatio, setUnfoldRatio] = useState<number>(0);

  const [inspectorOpen, setInspectorOpen] = useState<boolean>(false);
  const [inspectorTab, setInspectorTab] = useState<InspectorTab3D>('shapes');
  const [dockCollapsed, setDockCollapsed] = useState<boolean>(false);
  const [cameraMenuOpen, setCameraMenuOpen] = useState<boolean>(false);
  const [exportModalOpen, setExportModalOpen] = useState<boolean>(false);
  const [transparentPng, setTransparentPng] = useState<boolean>(false);

  const [selectedFirstPt, setSelectedFirstPt] = useState<string | null>(null);
  const [inspectedVertex, setInspectedVertex] = useState<string | null>(null);
  const [edgePreview, setEdgePreview] = useState<EdgeHit3D | null>(null);
  const [dragFeedback, setDragFeedback] = useState<LiveDragFeedback | null>(null);
  const [toastMsg, setToastMsg] = useState<string | null>(null);

  const [skewOverlay, setSkewOverlay] = useState<{ a1: string; a2: string; b1: string; b2: string } | null>(null);

  // Replay
  const [replayStep, setReplayStep] = useState<number | null>(null);
  const [isReplaying, setIsReplaying] = useState<boolean>(false);

  // Saved projects
  const [savedProjects, setSavedProjects] = useState<SavedProject3D[]>(() => {
    try {
      const raw = localStorage.getItem(SAVED_3D_KEY);
      return raw ? (JSON.parse(raw) as SavedProject3D[]) : [];
    } catch {
      return [];
    }
  });

  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const pointersRef = useRef<Map<number, { x: number; y: number }>>(new Map());
  const pinchStartDistRef = useRef<number | null>(null);
  const pinchStartRadiusRef = useRef<number>(DEFAULT_CAMERA.radius);

  const dragSessionRef = useRef<{
    type: 'orbit' | 'vertex';
    pointerId: number;
    startX: number;
    startY: number;
    lastX: number;
    lastY: number;
    moved: boolean;
    vertexName?: string;
    startWorldPos?: Vec3;
    rail?: DragRail3D | null;
    preDragSnap?: Scene3DSnapshot;
    isCustomEdgePoint?: boolean;
    isCustomXyzPoint?: boolean;
  } | null>(null);

  const showToast = useCallback((msg: string) => {
    setToastMsg(msg);
    window.setTimeout(() => setToastMsg((prev) => (prev === msg ? null : prev)), 2400);
  }, []);

  const commitSnap = useCallback((updater: (prev: Scene3DSnapshot) => Scene3DSnapshot) => {
    setSnap((prev) => {
      const next = updater(prev);
      setUndoStack((u) => [...u.slice(-39), prev]);
      setRedoStack([]);
      return next;
    });
  }, []);

  const handleUndo = useCallback(() => {
    setUndoStack((u) => {
      if (u.length === 0) return u;
      const last = u[u.length - 1];
      setSnap((cur) => {
        setRedoStack((r) => [...r.slice(-39), cur]);
        return last;
      });
      return u.slice(0, -1);
    });
  }, []);

  const handleRedo = useCallback(() => {
    setRedoStack((r) => {
      if (r.length === 0) return r;
      const next = r[r.length - 1];
      setSnap((cur) => {
        setUndoStack((u) => [...u.slice(-39), cur]);
        return next;
      });
      return r.slice(0, -1);
    });
  }, []);

  // Persist saved slots
  useEffect(() => {
    localStorage.setItem(SAVED_3D_KEY, JSON.stringify(savedProjects));
  }, [savedProjects]);

  // Apply external shared snapshot
  useEffect(() => {
    if (!externalSnapshot) return;
    commitSnap(() => ({ ...DEFAULT_SNAPSHOT, ...externalSnapshot }));
    onExternalSnapshotConsumed();
    showToast('Đã mở hình 3D từ liên kết chia sẻ');
  }, [externalSnapshot, commitSnap, onExternalSnapshotConsumed, showToast]);

  // Apply external AI drawing plan
  useEffect(() => {
    if (!externalPlan || externalPlan.target !== 'space3d') return;
    commitSnap((prev) => {
      const nextShape = externalPlan.shape3dKey && ALL_3D_SHAPES.some((s) => s.key === externalPlan.shape3dKey)
        ? externalPlan.shape3dKey
        : prev.shapeKey;
      return {
        ...DEFAULT_SNAPSHOT,
        shapeKey: nextShape,
        a: externalPlan.a ?? prev.a,
        b: externalPlan.b ?? prev.b,
        h: externalPlan.h ?? prev.h,
        customPoints: (externalPlan.edgePoints || []).map((ep, idx) => ({
          id: `ai_ep_${Date.now()}_${idx}`,
          name: ep.name,
          from: ep.from,
          to: ep.to,
          ratio: Math.max(0.05, Math.min(0.95, ep.ratio))
        })),
        customXYZPoints: (externalPlan.xyzPoints || []).map((xp, idx) => ({
          id: `ai_xp_${Date.now()}_${idx}`,
          name: xp.name,
          x: xp.x,
          y: xp.y,
          z: xp.z
        })),
        customLines: (externalPlan.segments || []).map((sg, idx) => ({
          id: `ai_ln_${Date.now()}_${idx}`,
          p1: sg.p1,
          p2: sg.p2,
          style: sg.style || 'solid',
          color: sg.color || '#38bdf8'
        })),
        customPlanes: (externalPlan.planes || []).map((pts, idx) => ({
          id: `ai_pl_${Date.now()}_${idx}`,
          pts,
          color: '#f59e0b'
        })),
        dynamicSection: externalPlan.crossSection
          ? { p1: externalPlan.crossSection[0], p2: externalPlan.crossSection[1], p3: externalPlan.crossSection[2] }
          : null
      };
    });
    if (externalPlan.showCircumSphere !== undefined) {
      setShowCircumSphere(Boolean(externalPlan.showCircumSphere));
    }
    onPlanConsumed();
    showToast(`Đã áp dụng: ${externalPlan.title}`);
  }, [externalPlan, commitSnap, onPlanConsumed, showToast]);

  // Keyboard shortcuts
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const target = e.target as HTMLElement | null;
      const isTyping = target && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.tagName === 'SELECT');
      if (isTyping) return;
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'z' && !e.shiftKey) {
        e.preventDefault();
        handleUndo();
      } else if ((e.ctrlKey || e.metaKey) && (e.key.toLowerCase() === 'y' || (e.shiftKey && e.key.toLowerCase() === 'z'))) {
        e.preventDefault();
        handleRedo();
      } else if (e.key === 'Escape') {
        if (cameraMenuOpen) setCameraMenuOpen(false);
        else if (mode !== 'view') {
          setMode('view');
          setSelectedFirstPt(null);
          setEdgePreview(null);
        } else if (inspectorOpen) {
          setInspectorOpen(false);
        }
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [handleUndo, handleRedo, mode, inspectorOpen, cameraMenuOpen]);

  // Built 3D Scene + Derived Geometry
  const currentShape = useMemo(
    () => ALL_3D_SHAPES.find((s) => s.key === snap.shapeKey) || ALL_3D_SHAPES[3],
    [snap.shapeKey]
  );

  const scene = useMemo(() => build3DSceneFromSnapshot(snap), [snap]);
  const allPointNames = useMemo(() => Object.keys(scene.pointRegistry), [scene.pointRegistry]);
  const originWorld = useMemo(
    () => scene.pointRegistry[snap.originPoint] || { x: 0, y: 0, z: 0 },
    [scene.pointRegistry, snap.originPoint]
  );
  const hasMovedVertices = useMemo(
    () => Object.keys(snap.vertexOverrides || {}).length > 0,
    [snap.vertexOverrides]
  );

  const circumSphere = useMemo(
    () => computeCircumSphere(scene, snap.shapeKey, snap.a, snap.b, snap.h, originWorld),
    [scene, snap.shapeKey, snap.a, snap.b, snap.h, originWorld]
  );
  const inSphere = useMemo(
    () => computeInSphere(scene, snap.shapeKey, snap.a, snap.b, snap.h, originWorld),
    [scene, snap.shapeKey, snap.a, snap.b, snap.h, originWorld]
  );
  const unfoldedFaces = useMemo(
    () => buildUnfoldedFaces3D(scene, snap.shapeKey, unfoldRatio),
    [scene, snap.shapeKey, unfoldRatio]
  );
  const skewLinesData = useMemo(() => {
    if (!skewOverlay) return null;
    const a1 = scene.pointRegistry[skewOverlay.a1];
    const a2 = scene.pointRegistry[skewOverlay.a2];
    const b1 = scene.pointRegistry[skewOverlay.b1];
    const b2 = scene.pointRegistry[skewOverlay.b2];
    if (!a1 || !a2 || !b1 || !b2) return null;
    return computeSkewLinesDistance(a1, a2, b1, b2);
  }, [skewOverlay, scene.pointRegistry]);

  // Replay timer
  const totalConstructionSteps =
    snap.customPoints.length + snap.customXYZPoints.length + snap.customLines.length + snap.customPlanes.length;

  useEffect(() => {
    if (!isReplaying) return;
    if (totalConstructionSteps === 0) {
      setIsReplaying(false);
      return;
    }
    const id = window.setInterval(() => {
      setReplayStep((cur) => {
        const next = (cur ?? 0) + 1;
        if (next >= totalConstructionSteps) {
          setIsReplaying(false);
          return totalConstructionSteps;
        }
        return next;
      });
    }, 850);
    return () => window.clearInterval(id);
  }, [isReplaying, totalConstructionSteps]);

  // Auto-rotate animation
  useEffect(() => {
    if (!autoRotate) return;
    let rafId = 0;
    let last = performance.now();
    const tick = (now: number) => {
      const dt = Math.min(0.05, (now - last) / 1000);
      last = now;
      if (!dragSessionRef.current) {
        setCamera((c) => ({ ...c, theta: c.theta + dt * 0.42 }));
      }
      rafId = requestAnimationFrame(tick);
    };
    rafId = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(rafId);
  }, [autoRotate]);

  // ===================== CANVAS 3D RENDERING ENGINE =====================
  const renderSceneToCanvas = useCallback(
    (ctx: CanvasRenderingContext2D, width: number, height: number, clearTransparent = false) => {
      ctx.clearRect(0, 0, width, height);
      if (!clearTransparent) {
        const bgGrad = ctx.createRadialGradient(
          width * 0.5,
          height * 0.44,
          10,
          width * 0.5,
          height * 0.5,
          Math.max(width, height) * 0.72
        );
        if (themeMode === 'light') {
          bgGrad.addColorStop(0, '#f8fbff');
          bgGrad.addColorStop(1, '#dce8f8');
        } else {
          bgGrad.addColorStop(0, '#101d36');
          bgGrad.addColorStop(1, '#060c1a');
        }
        ctx.fillStyle = bgGrad;
        ctx.fillRect(0, 0, width, height);
      }

      const isLight = themeMode === 'light';
      const camPos = getCameraWorldPosition(camera);
      const proj = (p: Vec3) => projectPoint(p, camera, width, height);

      // Helper to draw wireframe sphere (circumscribed / inscribed)
      const drawWireSphere = (center: Vec3, radius: number, color: string, label: string) => {
        ctx.save();
        ctx.strokeStyle = color;
        ctx.lineWidth = 1.15;
        ctx.setLineDash([4, 4]);
        const segs = 36;
        // 3 great circles
        const drawCircle = (fn: (a: number) => Vec3) => {
          ctx.beginPath();
          for (let i = 0; i <= segs; i++) {
            const ang = (i * 2 * Math.PI) / segs;
            const p = proj(fn(ang));
            if (i === 0) ctx.moveTo(p.x, p.y);
            else ctx.lineTo(p.x, p.y);
          }
          ctx.stroke();
        };
        drawCircle((a) => ({ x: center.x + radius * Math.cos(a), y: center.y, z: center.z + radius * Math.sin(a) }));
        drawCircle((a) => ({ x: center.x + radius * Math.cos(a), y: center.y + radius * Math.sin(a), z: center.z }));
        drawCircle((a) => ({ x: center.x, y: center.y + radius * Math.sin(a), z: center.z + radius * Math.cos(a) }));

        // Center dot + label
        ctx.setLineDash([]);
        const cp = proj(center);
        ctx.fillStyle = color;
        ctx.beginPath();
        ctx.arc(cp.x, cp.y, 4, 0, Math.PI * 2);
        ctx.fill();
        ctx.font = '700 11px "JetBrains Mono", monospace';
        ctx.fillText(label, cp.x + 7, cp.y - 5);
        ctx.restore();
      };

      // 1. Subtle Oxy Ground Grid
      ctx.save();
      ctx.strokeStyle = isLight ? 'rgba(15, 23, 42, 0.08)' : 'rgba(148, 163, 184, 0.11)';
      ctx.lineWidth = 1;
      const gridLim = 5;
      for (let g = -gridLim; g <= gridLim; g++) {
        const a1 = proj({ x: g, y: 0, z: -gridLim });
        const b1 = proj({ x: g, y: 0, z: gridLim });
        ctx.beginPath();
        ctx.moveTo(a1.x, a1.y);
        ctx.lineTo(b1.x, b1.y);
        ctx.stroke();

        const a2 = proj({ x: -gridLim, y: 0, z: g });
        const b2 = proj({ x: gridLim, y: 0, z: g });
        ctx.beginPath();
        ctx.moveTo(a2.x, a2.y);
        ctx.lineTo(b2.x, b2.y);
        ctx.stroke();
      }
      ctx.restore();

      // 2. Oxyz Coordinate Axes anchored at originWorld
      if (showAxes) {
        const axisLen = 4.6;
        const axes: { end: Vec3; color: string; label: string }[] = [
          { end: { x: originWorld.x + axisLen, y: originWorld.y, z: originWorld.z }, color: '#f43f5e', label: 'x' },
          { end: { x: originWorld.x, y: originWorld.y, z: originWorld.z + axisLen }, color: '#10b981', label: 'y' },
          { end: { x: originWorld.x, y: originWorld.y + axisLen, z: originWorld.z }, color: '#38bdf8', label: 'z' }
        ];
        const oProj = proj(originWorld);
        ctx.save();
        for (const ax of axes) {
          const eProj = proj(ax.end);
          ctx.strokeStyle = ax.color;
          ctx.lineWidth = 1.8;
          ctx.beginPath();
          ctx.moveTo(oProj.x, oProj.y);
          ctx.lineTo(eProj.x, eProj.y);
          ctx.stroke();

          // Arrow tip
          const ang = Math.atan2(eProj.y - oProj.y, eProj.x - oProj.x);
          ctx.fillStyle = ax.color;
          ctx.beginPath();
          ctx.moveTo(eProj.x, eProj.y);
          ctx.lineTo(eProj.x - 9 * Math.cos(ang - 0.35), eProj.y - 9 * Math.sin(ang - 0.35));
          ctx.lineTo(eProj.x - 9 * Math.cos(ang + 0.35), eProj.y - 9 * Math.sin(ang + 0.35));
          ctx.closePath();
          ctx.fill();

          ctx.font = '800 13px "Plus Jakarta Sans", sans-serif';
          ctx.fillText(ax.label, eProj.x + 6, eProj.y + 4);
        }
        ctx.restore();
      }

      // 3. Circumscribed & Inscribed Spheres (behind/around solid)
      if (showCircumSphere && circumSphere) {
        drawWireSphere(circumSphere.centerWorld, circumSphere.radius, 'rgba(56, 189, 248, 0.7)', `I_ng (R=${formatMathNum(circumSphere.radius)})`);
      }
      if (showInSphere && inSphere) {
        drawWireSphere(inSphere.centerWorld, inSphere.radius, 'rgba(16, 185, 129, 0.78)', `I_n (r=${formatMathNum(inSphere.radius)})`);
      }

      // 4. Solid Geometry OR Unfolded Polyhedron Net
      const occluderFaces = buildOccluderFaces(scene);
      const roundBodyHint = unfoldRatio > 0.01 ? null : getRoundBodyHint(snap.shapeKey, snap.a, snap.h);

      if (unfoldedFaces && unfoldRatio > 0.001) {
        // Render unfolded net
        const sortedNet = [...unfoldedFaces]
          .map((uf) => {
            const pPts = uf.points.map(proj);
            const avgDepth = pPts.reduce((s, p) => s + p.depth, 0) / Math.max(1, pPts.length);
            return { uf, pPts, avgDepth };
          })
          .sort((a, b) => b.avgDepth - a.avgDepth);

        ctx.save();
        for (const item of sortedNet) {
          ctx.beginPath();
          item.pPts.forEach((p, idx) => (idx === 0 ? ctx.moveTo(p.x, p.y) : ctx.lineTo(p.x, p.y)));
          ctx.closePath();
          ctx.fillStyle = item.uf.isBase
            ? 'rgba(16, 185, 129, 0.25)'
            : isLight
            ? 'rgba(2, 132, 199, 0.16)'
            : 'rgba(56, 189, 248, 0.22)';
          ctx.fill();
          ctx.strokeStyle = item.uf.isBase ? '#10b981' : isLight ? '#0f172a' : '#e2e8f0';
          ctx.lineWidth = 2;
          ctx.stroke();
        }
        ctx.restore();
      } else {
        // Directional Shaded Solid Faces (Painter's Algorithm)
        if (showFaces) {
          const lightDir = norm3({ x: 0.45, y: 0.85, z: 0.55 });
          const faceItems = scene.faces
            .map((face) => {
              const pts3D = face.map((i) => scene.vertices[i].pos);
              const pts2D = pts3D.map(proj);
              const avgDepth = pts2D.reduce((s, p) => s + p.depth, 0) / Math.max(1, pts2D.length);
              const n = pts3D.length >= 3 ? norm3(cross3(sub3(pts3D[1], pts3D[0]), sub3(pts3D[2], pts3D[0]))) : { x: 0, y: 1, z: 0 };
              const shade = 0.5 + 0.5 * Math.abs(dot3(n, lightDir));
              return { pts2D, avgDepth, shade };
            })
            .sort((a, b) => b.avgDepth - a.avgDepth);

          ctx.save();
          for (const f of faceItems) {
            ctx.beginPath();
            f.pts2D.forEach((p, idx) => (idx === 0 ? ctx.moveTo(p.x, p.y) : ctx.lineTo(p.x, p.y)));
            ctx.closePath();
            const alpha = (isLight ? 0.10 : 0.12) + f.shade * (isLight ? 0.12 : 0.14);
            ctx.fillStyle = accentHex;
            ctx.globalAlpha = alpha;
            ctx.fill();
          }
          ctx.restore();
        }

        // Horizontal Cross-Section Plane (Mặt cắt ngang song song đáy)
        if (snap.showSection && scene.horizontalSection && scene.horizontalSection.length >= 3) {
          const sec2D = scene.horizontalSection.map(proj);
          ctx.save();
          ctx.beginPath();
          sec2D.forEach((p, idx) => (idx === 0 ? ctx.moveTo(p.x, p.y) : ctx.lineTo(p.x, p.y)));
          ctx.closePath();
          ctx.fillStyle = 'rgba(245, 158, 11, 0.28)';
          ctx.fill();
          ctx.strokeStyle = '#fbbf24';
          ctx.lineWidth = 2.2;
          ctx.stroke();
          ctx.restore();
        }

        // Dynamic 3-Point Cross-Section
        if (scene.dynamicSection && scene.dynamicSection.polygon.length >= 3) {
          const poly2D = scene.dynamicSection.polygon.map(proj);
          ctx.save();
          ctx.beginPath();
          poly2D.forEach((p, idx) => (idx === 0 ? ctx.moveTo(p.x, p.y) : ctx.lineTo(p.x, p.y)));
          ctx.closePath();
          ctx.fillStyle = 'rgba(16, 185, 129, 0.28)';
          ctx.fill();
          ctx.strokeStyle = '#34d399';
          ctx.lineWidth = 2.4;
          ctx.stroke();
          ctx.restore();
        }

        // Custom Planes (respecting replay step)
        const maxPlanes =
          replayStep === null
            ? snap.customPlanes.length
            : Math.max(0, replayStep - snap.customPoints.length - snap.customXYZPoints.length - snap.customLines.length);

        for (let i = 0; i < Math.min(snap.customPlanes.length, maxPlanes); i++) {
          const pl = snap.customPlanes[i];
          const pts3D = pl.pts.map((n) => scene.pointRegistry[n]).filter(Boolean);
          if (pts3D.length < 3) continue;
          const pts2D = pts3D.map(proj);
          ctx.save();
          ctx.beginPath();
          pts2D.forEach((p, idx) => (idx === 0 ? ctx.moveTo(p.x, p.y) : ctx.lineTo(p.x, p.y)));
          ctx.closePath();
          ctx.fillStyle = pl.color;
          ctx.globalAlpha = 0.24;
          ctx.fill();
          ctx.globalAlpha = 0.85;
          ctx.strokeStyle = pl.color;
          ctx.lineWidth = 1.6;
          ctx.stroke();
          ctx.restore();
        }

        // Solid Edges with Exact SGK Hidden-Line Occlusion
        ctx.save();
        for (const e of scene.edges) {
          const p1 = scene.vertices[e.from].pos;
          const p2 = scene.vertices[e.to].pos;
          const a = proj(p1);
          const b = proj(p2);
          const hidden =
            showHiddenDashed && isSegmentOccludedBySolid(p1, p2, camPos, occluderFaces, roundBodyHint);
          ctx.beginPath();
          ctx.moveTo(a.x, a.y);
          ctx.lineTo(b.x, b.y);
          if (hidden) {
            ctx.setLineDash([6, 5]);
            ctx.strokeStyle = isLight ? 'rgba(30, 41, 59, 0.45)' : 'rgba(148, 163, 184, 0.56)';
            ctx.lineWidth = 1.5;
          } else {
            ctx.setLineDash([]);
            ctx.strokeStyle = isLight ? '#0f172a' : '#f1f5f9';
            ctx.lineWidth = 2.15;
          }
          ctx.stroke();
        }
        ctx.restore();

        // Altitude Line + Red Right-Angle L Corner Mark
        if (scene.altitude) {
          const { top, foot, ref } = scene.altitude;
          const aTop = proj(top);
          const aFoot = proj(foot);
          ctx.save();
          ctx.setLineDash([5, 4]);
          ctx.strokeStyle = '#f43f5e';
          ctx.lineWidth = 1.85;
          ctx.beginPath();
          ctx.moveTo(aTop.x, aTop.y);
          ctx.lineTo(aFoot.x, aFoot.y);
          ctx.stroke();

          // Right-angle L symbol at altitude foot
          const lPts = buildRightAngleMark3D(top, foot, ref, 0.34);
          if (lPts) {
            const p1 = proj(lPts[0]);
            const p2 = proj(lPts[1]);
            const p3 = proj(lPts[2]);
            ctx.setLineDash([]);
            ctx.strokeStyle = '#fb7185';
            ctx.lineWidth = 1.8;
            ctx.beginPath();
            ctx.moveTo(p1.x, p1.y);
            ctx.lineTo(p2.x, p2.y);
            ctx.lineTo(p3.x, p3.y);
            ctx.stroke();
          }
          ctx.restore();
        }
      }

      // 5. Custom Constructed Lines & Vectors (respecting replay step)
      const maxLines =
        replayStep === null
          ? snap.customLines.length
          : Math.max(0, replayStep - snap.customPoints.length - snap.customXYZPoints.length);

      ctx.save();
      for (let i = 0; i < Math.min(snap.customLines.length, maxLines); i++) {
        const cl = snap.customLines[i];
        const p1 = scene.pointRegistry[cl.p1];
        const p2 = scene.pointRegistry[cl.p2];
        if (!p1 || !p2) continue;
        const a = proj(p1);
        const b = proj(p2);
        const isVector = cl.style === 'vector_solid' || cl.style === 'vector_dashed';
        const isForceDash = cl.style === 'dashed' || cl.style === 'vector_dashed';
        const autoDash =
          !isForceDash &&
          showHiddenDashed &&
          unfoldRatio < 0.01 &&
          isSegmentOccludedBySolid(p1, p2, camPos, occluderFaces, roundBodyHint);

        ctx.strokeStyle = cl.color;
        ctx.lineWidth = 2.4;
        ctx.setLineDash(isForceDash || autoDash ? [6, 5] : []);
        ctx.beginPath();
        ctx.moveTo(a.x, a.y);
        ctx.lineTo(b.x, b.y);
        ctx.stroke();

        if (isVector) {
          const ang = Math.atan2(b.y - a.y, b.x - a.x);
          ctx.setLineDash([]);
          ctx.fillStyle = cl.color;
          ctx.beginPath();
          ctx.moveTo(b.x, b.y);
          ctx.lineTo(b.x - 11 * Math.cos(ang - 0.34), b.y - 11 * Math.sin(ang - 0.34));
          ctx.lineTo(b.x - 11 * Math.cos(ang + 0.34), b.y - 11 * Math.sin(ang + 0.34));
          ctx.closePath();
          ctx.fill();
        }
      }
      ctx.restore();

      // 6. Skew Lines Common Perpendicular H1H2
      if (skewLinesData && !skewLinesData.isIntersecting) {
        const h1 = proj(skewLinesData.closestOnLine1);
        const h2 = proj(skewLinesData.closestOnLine2);
        ctx.save();
        ctx.strokeStyle = '#f43f5e';
        ctx.lineWidth = 2.8;
        ctx.setLineDash([4, 3]);
        ctx.beginPath();
        ctx.moveTo(h1.x, h1.y);
        ctx.lineTo(h2.x, h2.y);
        ctx.stroke();
        ctx.setLineDash([]);
        ctx.fillStyle = '#f43f5e';
        for (const [pt, lbl] of [
          [h1, 'H₁'],
          [h2, 'H₂']
        ] as const) {
          ctx.beginPath();
          ctx.arc(pt.x, pt.y, 4.5, 0, Math.PI * 2);
          ctx.fill();
          ctx.font = '800 12px "Plus Jakarta Sans", sans-serif';
          ctx.fillText(lbl, pt.x + 7, pt.y - 5);
        }
        ctx.restore();
      }

      // 7. Drag Visual Guides (Pink Constraint Rail + Emerald Magnetic Snap Projection Guides)
      if (dragFeedback) {
        ctx.save();
        if (dragConstraint === 'vertical') {
          const pBot = proj({ x: dragFeedback.worldPos.x, y: -2.0, z: dragFeedback.worldPos.z });
          const pTop = proj({ x: dragFeedback.worldPos.x, y: 8.5, z: dragFeedback.worldPos.z });
          ctx.strokeStyle = '#ec4899';
          ctx.lineWidth = 2.2;
          ctx.setLineDash([6, 4]);
          ctx.beginPath();
          ctx.moveTo(pBot.x, pBot.y);
          ctx.lineTo(pTop.x, pTop.y);
          ctx.stroke();
        } else if (dragConstraint === 'segment' && dragFeedback.rail) {
          const rA = proj(dragFeedback.rail.fromPos);
          const rB = proj(dragFeedback.rail.toPos);
          ctx.strokeStyle = '#ec4899';
          ctx.lineWidth = 3.2;
          ctx.setLineDash([]);
          ctx.beginPath();
          ctx.moveTo(rA.x, rA.y);
          ctx.lineTo(rB.x, rB.y);
          ctx.stroke();
        }

        if (dragFeedback.isSnapped) {
          const wp = dragFeedback.worldPos;
          const sp = proj(wp);
          const footOxy = proj({ x: wp.x, y: originWorld.y, z: wp.z });
          const footOx = proj({ x: wp.x, y: originWorld.y, z: originWorld.z });
          const footOy = proj({ x: originWorld.x, y: originWorld.y, z: wp.z });

          // 3 Dashed Emerald Snap Projection Guides
          ctx.strokeStyle = '#10b981';
          ctx.lineWidth = 1.6;
          ctx.setLineDash([4, 4]);
          ctx.beginPath();
          ctx.moveTo(sp.x, sp.y);
          ctx.lineTo(footOxy.x, footOxy.y);
          ctx.moveTo(footOxy.x, footOxy.y);
          ctx.lineTo(footOx.x, footOx.y);
          ctx.moveTo(footOxy.x, footOxy.y);
          ctx.lineTo(footOy.x, footOy.y);
          ctx.stroke();

          // Glowing Emerald Snap Ring
          ctx.setLineDash([]);
          ctx.strokeStyle = '#34d399';
          ctx.lineWidth = 2.6;
          ctx.beginPath();
          ctx.arc(sp.x, sp.y, 14, 0, Math.PI * 2);
          ctx.stroke();
        }
        ctx.restore();
      }

      // 8. Direct "Chấm điểm trên cạnh" Hover Preview
      if (mode === 'add_point' && edgePreview) {
        const a = proj(edgePreview.fromPos);
        const b = proj(edgePreview.toPos);
        const hit = proj(edgePreview.worldPos);
        ctx.save();
        ctx.strokeStyle = '#f59e0b';
        ctx.lineWidth = 3.2;
        ctx.beginPath();
        ctx.moveTo(a.x, a.y);
        ctx.lineTo(b.x, b.y);
        ctx.stroke();

        ctx.fillStyle = 'rgba(245, 158, 11, 0.25)';
        ctx.beginPath();
        ctx.arc(hit.x, hit.y, 13, 0, Math.PI * 2);
        ctx.fill();

        ctx.fillStyle = '#fbbf24';
        ctx.strokeStyle = '#ffffff';
        ctx.lineWidth = 2;
        ctx.beginPath();
        ctx.arc(hit.x, hit.y, 5.5, 0, Math.PI * 2);
        ctx.fill();
        ctx.stroke();
        ctx.restore();
      }

      // 9. Named Vertices & Color-Coded Pill Labels
      const customPointNames = new Set([
        ...snap.customPoints.map((c) => c.name),
        ...snap.customXYZPoints.map((c) => c.name)
      ]);
      const visibleCustomPts =
        replayStep === null
          ? customPointNames
          : new Set([
              ...snap.customPoints.slice(0, Math.min(snap.customPoints.length, replayStep)).map((c) => c.name),
              ...snap.customXYZPoints
                .slice(0, Math.max(0, replayStep - snap.customPoints.length))
                .map((c) => c.name)
            ]);

      ctx.save();
      for (const [name, worldPos] of Object.entries(scene.pointRegistry)) {
        if (customPointNames.has(name) && !visibleCustomPts.has(name)) continue;
        const p = proj(worldPos);
        const isDragging = dragFeedback?.vertexName === name;
        const isLinked = Boolean(dragFeedback?.linkedVertices.includes(name));
        const isSelected = selectedFirstPt === name || inspectedVertex === name;
        const isOrigin = snap.originPoint === name;
        const isCustom = customPointNames.has(name);

        let dotColor = isLight ? '#0284c7' : '#e2e8f0';
        let pillBg = isLight ? 'rgba(255,255,255,0.86)' : 'rgba(15,23,42,0.82)';
        let pillText = isLight ? '#0f172a' : '#f8fafc';
        let pillBorder = isLight ? 'rgba(15,23,42,0.16)' : 'rgba(255,255,255,0.22)';

        if (isDragging) {
          dotColor = '#ec4899';
          pillBg = 'rgba(236,72,153,0.92)';
          pillText = '#ffffff';
          pillBorder = '#fbcfe8';
        } else if (isLinked) {
          dotColor = '#38bdf8';
          pillBg = 'rgba(14,165,233,0.9)';
          pillText = '#ffffff';
          pillBorder = '#bae6fd';
        } else if (isSelected) {
          dotColor = '#10b981';
          pillBg = 'rgba(16,185,129,0.92)';
          pillText = '#ffffff';
          pillBorder = '#a7f3d0';
        } else if (isOrigin) {
          dotColor = '#f43f5e';
          pillBg = 'rgba(244,63,94,0.88)';
          pillText = '#ffffff';
          pillBorder = '#fecdd3';
        } else if (isCustom) {
          dotColor = '#f59e0b';
          pillBg = 'rgba(245,158,11,0.9)';
          pillText = '#0f172a';
          pillBorder = '#fde68a';
        }

        // Glow ring for drag mode or active states
        if (isDragging || isLinked || isSelected || mode === 'drag') {
          ctx.beginPath();
          ctx.arc(p.x, p.y, isDragging ? 11 : mode === 'drag' ? 7.5 : 9, 0, Math.PI * 2);
          ctx.fillStyle = isDragging
            ? 'rgba(236,72,153,0.28)'
            : isLinked
            ? 'rgba(56,189,248,0.28)'
            : isSelected
            ? 'rgba(16,185,129,0.28)'
            : 'rgba(236,72,153,0.14)';
          ctx.fill();
        }

        ctx.beginPath();
        ctx.arc(p.x, p.y, isDragging ? 5.2 : 4.2, 0, Math.PI * 2);
        ctx.fillStyle = dotColor;
        ctx.fill();
        ctx.strokeStyle = isLight ? '#ffffff' : '#060d1c';
        ctx.lineWidth = 1.5;
        ctx.stroke();

        // Pill label
        ctx.font = '800 12px "Plus Jakarta Sans", sans-serif';
        const tw = ctx.measureText(name).width;
        const px = p.x + 8;
        const py = p.y - 18;
        const pw = tw + 10;
        const ph = 18;
        ctx.beginPath();
        ctx.roundRect(px, py, pw, ph, 6);
        ctx.fillStyle = pillBg;
        ctx.fill();
        ctx.strokeStyle = pillBorder;
        ctx.lineWidth = 1;
        ctx.stroke();

        ctx.fillStyle = pillText;
        ctx.fillText(name, px + 5, py + 13);
      }
      ctx.restore();
    },
    [
      camera,
      themeMode,
      accentHex,
      showAxes,
      originWorld,
      showCircumSphere,
      circumSphere,
      showInSphere,
      inSphere,
      scene,
      unfoldRatio,
      unfoldedFaces,
      snap,
      showFaces,
      showHiddenDashed,
      replayStep,
      skewLinesData,
      dragFeedback,
      dragConstraint,
      mode,
      edgePreview,
      selectedFirstPt,
      inspectedVertex
    ]
  );

  // Sync Canvas Size & Repaint
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const paint = () => {
      const rect = canvas.getBoundingClientRect();
      if (rect.width <= 0 || rect.height <= 0) return;
      const dpr = Math.min(window.devicePixelRatio || 1, 2);
      const targetW = Math.round(rect.width * dpr);
      const targetH = Math.round(rect.height * dpr);
      if (canvas.width !== targetW || canvas.height !== targetH) {
        canvas.width = targetW;
        canvas.height = targetH;
      }
      const ctx = canvas.getContext('2d');
      if (!ctx) return;
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      renderSceneToCanvas(ctx, rect.width, rect.height, false);
    };
    paint();
    const ro = new ResizeObserver(paint);
    ro.observe(canvas);
    return () => ro.disconnect();
  }, [renderSceneToCanvas]);

  // ===================== HIT TESTING & POINTER INTERACTIONS =====================
  const findNearestVertex = useCallback(
    (sx: number, sy: number, width: number, height: number): string | null => {
      const maxHit = 26 * sensitivity.touchRadius;
      let bestName: string | null = null;
      let bestDist = maxHit;
      for (const [name, worldPos] of Object.entries(scene.pointRegistry)) {
        const p = projectPoint(worldPos, camera, width, height);
        const d = Math.hypot(sx - p.x, sy - p.y);
        if (d < bestDist) {
          bestDist = d;
          bestName = name;
        }
      }
      return bestName;
    },
    [scene.pointRegistry, camera, sensitivity.touchRadius]
  );

  const handlePointerDown = (e: React.PointerEvent<HTMLCanvasElement>) => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    canvas.setPointerCapture(e.pointerId);
    const rect = canvas.getBoundingClientRect();
    const sx = e.clientX - rect.left;
    const sy = e.clientY - rect.top;

    pointersRef.current.set(e.pointerId, { x: sx, y: sy });

    // 2-finger pinch start
    if (pointersRef.current.size === 2) {
      const pts = Array.from(pointersRef.current.values());
      pinchStartDistRef.current = Math.hypot(pts[0].x - pts[1].x, pts[0].y - pts[1].y);
      pinchStartRadiusRef.current = camera.radius;
      dragSessionRef.current = null;
      setDragFeedback(null);
      return;
    }

    setCameraMenuOpen(false);

    // Direct "Chấm điểm trên cạnh" mode
    if (mode === 'add_point') {
      const hit = findNearestEdgeHit3D(
        sx,
        sy,
        scene.clickableSegments,
        camera,
        rect.width,
        rect.height,
        28 * sensitivity.touchRadius,
        snapEnabled
      );
      if (hit) {
        const name = nextAutoPointName(scene.pointRegistry);
        commitSnap((prev) => ({
          ...prev,
          customPoints: [
            ...prev.customPoints,
            { id: `ep_${Date.now()}`, name, from: hit.fromName, to: hit.toName, ratio: hit.ratio }
          ]
        }));
        setEdgePreview(null);
        showToast(`Đã chấm điểm ${name} trên ${hit.fromName}${hit.toName} (${hit.snapLabel || `k=${formatMathNum(hit.ratio)}`})`);
        return;
      }
    }

    // Direct Connect Line / Vector mode
    if (mode === 'connect_dashed' || mode === 'connect_solid' || mode === 'connect_vector') {
      const hitV = findNearestVertex(sx, sy, rect.width, rect.height);
      if (hitV) {
        if (!selectedFirstPt) {
          setSelectedFirstPt(hitV);
          return;
        }
        if (selectedFirstPt !== hitV) {
          const style: LineStyle3D =
            mode === 'connect_dashed' ? 'dashed' : mode === 'connect_vector' ? 'vector_solid' : 'solid';
          const color = mode === 'connect_vector' ? '#f59e0b' : mode === 'connect_dashed' ? '#38bdf8' : '#10b981';
          commitSnap((prev) => ({
            ...prev,
            customLines: [
              ...prev.customLines,
              { id: `ln_${Date.now()}`, p1: selectedFirstPt, p2: hitV, style, color }
            ]
          }));
          showToast(`Đã nối ${selectedFirstPt}${hitV}`);
          setSelectedFirstPt(null);
          return;
        }
      }
    }

    // Drag Vertex mode
    if (mode === 'drag') {
      const hitV = findNearestVertex(sx, sy, rect.width, rect.height);
      if (hitV) {
        const startPos = scene.pointRegistry[hitV];
        const rail = findBestRailForPoint3D(hitV, scene, snap);
        const isCustomEdge = snap.customPoints.some((c) => c.name === hitV);
        const isCustomXyz = snap.customXYZPoints.some((c) => c.name === hitV);
        dragSessionRef.current = {
          type: 'vertex',
          pointerId: e.pointerId,
          startX: sx,
          startY: sy,
          lastX: sx,
          lastY: sy,
          moved: false,
          vertexName: hitV,
          startWorldPos: startPos,
          rail,
          preDragSnap: snap,
          isCustomEdgePoint: isCustomEdge,
          isCustomXyzPoint: isCustomXyz
        };
        setDragFeedback({
          vertexName: hitV,
          worldPos: startPos,
          isSnapped: false,
          snapReason: '',
          relationHint: '',
          linkedVertices: [],
          rail
        });
        return;
      }
    }

    // Default: Orbit camera
    dragSessionRef.current = {
      type: 'orbit',
      pointerId: e.pointerId,
      startX: sx,
      startY: sy,
      lastX: sx,
      lastY: sy,
      moved: false
    };
  };

  const handlePointerMove = (e: React.PointerEvent<HTMLCanvasElement>) => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const rect = canvas.getBoundingClientRect();
    const sx = e.clientX - rect.left;
    const sy = e.clientY - rect.top;

    // Live edge preview when hovering in add_point mode
    if (mode === 'add_point' && !dragSessionRef.current) {
      const hit = findNearestEdgeHit3D(
        sx,
        sy,
        scene.clickableSegments,
        camera,
        rect.width,
        rect.height,
        28 * sensitivity.touchRadius,
        snapEnabled
      );
      setEdgePreview(hit);
    }

    if (pointersRef.current.has(e.pointerId)) {
      pointersRef.current.set(e.pointerId, { x: sx, y: sy });
    }

    // 2-finger pinch zoom
    if (pointersRef.current.size === 2 && pinchStartDistRef.current) {
      const pts = Array.from(pointersRef.current.values());
      const dist = Math.hypot(pts[0].x - pts[1].x, pts[0].y - pts[1].y);
      if (dist > 10) {
        const scale = pinchStartDistRef.current / dist;
        const nextRadius = Math.max(4.5, Math.min(26, pinchStartRadiusRef.current * Math.pow(scale, sensitivity.zoom)));
        setCamera((c) => ({ ...c, radius: nextRadius }));
      }
      return;
    }

    const sess = dragSessionRef.current;
    if (!sess || sess.pointerId !== e.pointerId) return;

    const dx = sx - sess.lastX;
    const dy = sy - sess.lastY;
    sess.lastX = sx;
    sess.lastY = sy;
    if (Math.hypot(sx - sess.startX, sy - sess.startY) > 4) sess.moved = true;

    if (sess.type === 'orbit') {
      const rotSpeed = 0.0085 * sensitivity.rotation;
      setCamera((c) => ({
        ...c,
        theta: c.theta - dx * rotSpeed,
        phi: Math.max(0.08, Math.min(Math.PI - 0.08, c.phi - dy * rotSpeed))
      }));
      return;
    }

    if (sess.type === 'vertex' && sess.vertexName && sess.startWorldPos) {
      const activeConstraint: DragConstraint3D = sess.isCustomEdgePoint ? 'segment' : dragConstraint;
      const result = computeConstrainedDrag3D({
        sx,
        sy,
        cam: camera,
        width: rect.width,
        height: rect.height,
        constraint: activeConstraint,
        startPos: sess.startWorldPos,
        rail: sess.rail || null,
        dragSensitivity: sensitivity.drag,
        snapEnabled,
        shapeKey: snap.shapeKey,
        draggedVertex: sess.vertexName,
        originWorld,
        registry: scene.pointRegistry
      });

      if (sess.isCustomEdgePoint && result.ratio !== undefined) {
        const ratio = result.ratio;
        setSnap((prev) => ({
          ...prev,
          customPoints: prev.customPoints.map((cp) => (cp.name === sess.vertexName ? { ...cp, ratio } : cp))
        }));
        setDragFeedback({
          vertexName: sess.vertexName,
          worldPos: result.pos,
          isSnapped: result.snap.isSnapped,
          snapReason: result.snap.reason,
          relationHint: sess.rail ? `Trượt trên cạnh ${sess.rail.fromName}${sess.rail.toName}` : '',
          linkedVertices: [],
          rail: sess.rail || null
        });
        return;
      }

      if (sess.isCustomXyzPoint) {
        const mPos = worldToMath(result.pos, originWorld);
        setSnap((prev) => ({
          ...prev,
          customXYZPoints: prev.customXYZPoints.map((xp) =>
            xp.name === sess.vertexName ? { ...xp, x: mPos.x, y: mPos.y, z: mPos.z } : xp
          )
        }));
        setDragFeedback({
          vertexName: sess.vertexName,
          worldPos: result.pos,
          isSnapped: result.snap.isSnapped,
          snapReason: result.snap.reason,
          relationHint: 'Điểm Oxyz tự do',
          linkedVertices: [],
          rail: sess.rail || null
        });
        return;
      }

      // Base solid vertex drag with optional Khóa hình geometric relationships
      const prevPos = scene.pointRegistry[sess.vertexName] || sess.startWorldPos;
      if (keepRelations) {
        const rel = applyGeometricRelationships3D(snap.shapeKey, sess.vertexName, result.pos, prevPos, scene.pointRegistry);
        setSnap((prev) => ({
          ...prev,
          vertexOverrides: { ...(prev.vertexOverrides || {}), ...rel.updates }
        }));
        setDragFeedback({
          vertexName: sess.vertexName,
          worldPos: result.pos,
          isSnapped: result.snap.isSnapped,
          snapReason: result.snap.reason,
          relationHint: rel.relationHint,
          linkedVertices: rel.linkedVertices,
          rail: sess.rail || null
        });
      } else {
        setSnap((prev) => ({
          ...prev,
          vertexOverrides: { ...(prev.vertexOverrides || {}), [sess.vertexName!]: result.pos }
        }));
        setDragFeedback({
          vertexName: sess.vertexName,
          worldPos: result.pos,
          isSnapped: result.snap.isSnapped,
          snapReason: result.snap.reason,
          relationHint: 'Chế độ kéo tự do (không khóa hình)',
          linkedVertices: [],
          rail: sess.rail || null
        });
      }
    }
  };

  const handlePointerUp = (e: React.PointerEvent<HTMLCanvasElement>) => {
    const canvas = canvasRef.current;
    if (canvas && canvas.hasPointerCapture(e.pointerId)) {
      canvas.releasePointerCapture(e.pointerId);
    }
    pointersRef.current.delete(e.pointerId);
    if (pointersRef.current.size < 2) {
      pinchStartDistRef.current = null;
    }

    const sess = dragSessionRef.current;
    if (sess && sess.pointerId === e.pointerId) {
      if (sess.type === 'vertex' && sess.moved && sess.preDragSnap) {
        const pre = sess.preDragSnap;
        setUndoStack((u) => [...u.slice(-39), pre]);
        setRedoStack([]);
      } else if (sess.type === 'orbit' && !sess.moved && mode === 'view' && canvas) {
        // Tap a vertex in View mode to inspect its exact Oxyz coordinates!
        const rect = canvas.getBoundingClientRect();
        const hitV = findNearestVertex(e.clientX - rect.left, e.clientY - rect.top, rect.width, rect.height);
        setInspectedVertex((prev) => (hitV && prev !== hitV ? hitV : null));
      }
      dragSessionRef.current = null;
      setDragFeedback(null);
    }
  };

  const handleWheel = (e: React.WheelEvent<HTMLCanvasElement>) => {
    e.preventDefault();
    const factor = 1 + Math.sign(e.deltaY) * 0.08 * sensitivity.zoom;
    setCamera((c) => ({ ...c, radius: Math.max(4.5, Math.min(26, c.radius * factor)) }));
  };

  // ===================== EXPORTS & SAVING =====================
  const downloadFile = (content: string | Blob, filename: string) => {
    const blob = typeof content === 'string' ? new Blob([content], { type: 'text/plain;charset=utf-8' }) : content;
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  };

  const handleExportPng = () => {
    const out = document.createElement('canvas');
    out.width = 1600;
    out.height = 1200;
    const ctx = out.getContext('2d');
    if (!ctx) return;
    ctx.setTransform(1.5, 0, 0, 1.5, 0, 0);
    renderSceneToCanvas(ctx, out.width / 1.5, out.height / 1.5, transparentPng);
    out.toBlob((blob) => {
      if (blob) {
        downloadFile(blob, `tooltoan-3d-${snap.shapeKey}.png`);
        showToast(transparentPng ? 'Đã xuất ảnh PNG nền trong suốt' : 'Đã xuất ảnh PNG');
      }
    }, 'image/png');
  };

  const handleExportSvg = () => {
    const svg = exportSceneToSvg(scene, snap, camera, accentHex);
    downloadFile(svg, `tooltoan-3d-${snap.shapeKey}.svg`);
    showToast('Đã xuất tệp Vector .SVG');
  };

  const handleExportObj = () => {
    const obj = exportSceneToObj(scene, currentShape.title);
    downloadFile(obj, `tooltoan-3d-${snap.shapeKey}.obj`);
    showToast('Đã xuất mô hình 3D .OBJ');
  };

  const handleExportJson = () => {
    downloadFile(JSON.stringify(snap, null, 2), `tooltoan-3d-${snap.shapeKey}.json`);
    showToast('Đã xuất tệp dự án .JSON');
  };

  const handleImportJson = (text: string) => {
    try {
      const parsed = JSON.parse(text) as Scene3DSnapshot;
      if (parsed && typeof parsed.shapeKey === 'string') {
        commitSnap(() => ({ ...DEFAULT_SNAPSHOT, ...parsed }));
        showToast('Đã nhập dự án 3D thành công');
      }
    } catch {
      showToast('Tệp JSON không hợp lệ');
    }
  };

  const handleCopyShareLink = () => {
    const url = buildShareUrl3D(snap);
    navigator.clipboard?.writeText(url).catch(() => {});
    showToast('Đã sao chép liên kết chia sẻ 3D');
  };

  const openInspectorTab = (tab: InspectorTab3D) => {
    if (inspectorOpen && inspectorTab === tab) {
      setInspectorOpen(false);
    } else {
      setInspectorTab(tab);
      setInspectorOpen(true);
    }
  };

  // Active Mode Status Guidance Text
  const modeHintText = useMemo(() => {
    if (mode === 'drag') {
      const cLabel =
        dragConstraint === 'horizontal'
          ? 'Mặt ngang (Oxy)'
          : dragConstraint === 'vertical'
          ? 'Đường cao (Oz)'
          : 'Trên đoạn thẳng';
      return `Kéo đỉnh: ${cLabel} • ${keepRelations ? 'Khóa hình' : 'Tự do'}${snapEnabled ? ' • Hút BẬT' : ''}`;
    }
    if (mode === 'add_point') {
      return 'Chạm trực tiếp vào cạnh bất kỳ để chấm điểm (tự hút 1/4, 1/3, 1/2, 2/3, 3/4)';
    }
    if (mode === 'connect_dashed' || mode === 'connect_solid' || mode === 'connect_vector') {
      const typeLbl =
        mode === 'connect_dashed' ? 'nét đứt' : mode === 'connect_vector' ? 'vectơ' : 'nét liền';
      return selectedFirstPt
        ? `Đã chọn ${selectedFirstPt} → Chạm điểm thứ 2 để nối ${typeLbl}`
        : `Chạm 2 đỉnh lần lượt để nối ${typeLbl}`;
    }
    return null;
  }, [mode, dragConstraint, keepRelations, snapEnabled, selectedFirstPt]);

  return (
    <div className="studio-root">
      {/* FULL-SCREEN 3D CANVAS */}
      <canvas
        ref={canvasRef}
        className="studio-canvas"
        style={{ cursor: mode === 'drag' ? 'grab' : mode === 'add_point' ? 'crosshair' : 'default' }}
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={handlePointerUp}
        onPointerCancel={handlePointerUp}
        onWheel={handleWheel}
      />

      {/* TOP FLOATING HUD */}
      <div className="studio-top-hud">
        {/* Row 1: Shape Selector + Camera Presets + Export + Fullscreen */}
        <div className="studio-hud-row">
          <div className="studio-hud-group">
            <button
              type="button"
              className="glass-surface studio-pill glass-press"
              onClick={() => openInspectorTab('shapes')}
            >
              <Box size={15} color={accentHex} />
              <span style={{ maxWidth: 168, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {currentShape.title}
              </span>
              <ChevronDown size={14} style={{ opacity: 0.7 }} />
            </button>
          </div>

          <div className="studio-hud-group" style={{ position: 'relative' }}>
            {/* Camera View Presets Menu */}
            <button
              type="button"
              className="glass-surface studio-icon-pill glass-press"
              onClick={() => setCameraMenuOpen((v) => !v)}
              title="Góc nhìn Camera 3D"
              aria-label="Góc nhìn Camera 3D"
            >
              <Camera size={16} />
            </button>

            {cameraMenuOpen && (
              <div className="glass-surface studio-popover">
                <div style={{ fontSize: 11, fontWeight: 800, color: 'var(--text-secondary)', padding: '4px 10px' }}>
                  GÓC NHÌN CAMERA 3D
                </div>
                {CAMERA_VIEW_PRESETS_3D.map((preset) => (
                  <button
                    key={preset.id}
                    type="button"
                    className="studio-popover-item"
                    onClick={() => {
                      setCamera((c) => ({ ...c, theta: preset.theta, phi: preset.phi, radius: preset.radius }));
                      setCameraMenuOpen(false);
                    }}
                  >
                    <span>{preset.label}</span>
                  </button>
                ))}
                <button
                  type="button"
                  className="studio-popover-item"
                  style={{ color: accentHex }}
                  onClick={() => {
                    setCamera(DEFAULT_CAMERA);
                    setCameraMenuOpen(false);
                  }}
                >
                  <span>Đặt lại góc nhìn mặc định</span>
                  <RotateCcw size={13} />
                </button>
              </div>
            )}

            <button
              type="button"
              className="glass-surface studio-icon-pill glass-press"
              onClick={() => setExportModalOpen(true)}
              title="Lưu trữ, Chia sẻ & Xuất file"
              aria-label="Lưu trữ và xuất file"
            >
              <Save size={16} />
            </button>

            <button
              type="button"
              className="glass-surface studio-icon-pill glass-press"
              onClick={onToggleImmersive}
              title={isImmersive ? 'Thoát toàn màn hình' : 'Toàn màn hình'}
              aria-label="Toàn màn hình"
            >
              {isImmersive ? <Minimize2 size={16} /> : <Maximize2 size={16} />}
            </button>
          </div>
        </div>

        {/* Row 2: Live Metrics Micro-Pill + Undo / Redo / Reset Vertices */}
        <div className="studio-hud-row">
          <div className="studio-hud-group">
            <button
              type="button"
              className="glass-surface studio-pill glass-press mono"
              style={{ minHeight: 30, padding: '3px 11px', fontSize: 11.5 }}
              onClick={() => openInspectorTab('oxyz')}
              title="Bấm xem chi tiết công thức & tọa độ"
            >
              <span style={{ color: accentHex }}>V = {formatMathNum(scene.metrics.vol)}</span>
              <span style={{ opacity: 0.45 }}>•</span>
              <span style={{ color: '#34d399' }}>Stp = {formatMathNum(scene.metrics.sTp)}</span>
              {scene.dynamicSection && (
                <>
                  <span style={{ opacity: 0.45 }}>•</span>
                  <span style={{ color: '#fbbf24' }}>Std = {formatMathNum(scene.dynamicSection.area)}</span>
                </>
              )}
            </button>
          </div>

          <div className="studio-hud-group">
            {hasMovedVertices && (
              <button
                type="button"
                className="glass-surface studio-pill glass-press"
                style={{ minHeight: 30, padding: '3px 10px', fontSize: 11.5, color: '#fbbf24' }}
                onClick={() => {
                  commitSnap((prev) => ({ ...prev, vertexOverrides: {} }));
                  showToast('Đã khôi phục tất cả đỉnh về vị trí chuẩn');
                }}
                title="Đưa các đỉnh đã kéo về lại vị trí chuẩn"
              >
                <RotateCcw size={12} /> Đỉnh chuẩn
              </button>
            )}

            <div className="glass-surface" style={{ display: 'inline-flex', borderRadius: 999, padding: 2, gap: 2 }}>
              <button
                type="button"
                className="icon-btn"
                style={{ width: 30, height: 30, opacity: undoStack.length ? 1 : 0.35 }}
                disabled={undoStack.length === 0}
                onClick={handleUndo}
                title="Hoàn tác (Ctrl+Z)"
                aria-label="Hoàn tác"
              >
                <Undo2 size={14} />
              </button>
              <button
                type="button"
                className="icon-btn"
                style={{ width: 30, height: 30, opacity: redoStack.length ? 1 : 0.35 }}
                disabled={redoStack.length === 0}
                onClick={handleRedo}
                title="Làm lại (Ctrl+Y)"
                aria-label="Làm lại"
              >
                <Redo2 size={14} />
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* RIGHT-SIDE VERTICAL QUICK CONTROLS (Android CanvasQuickControls) */}
      <div className="glass-surface studio-right-rail">
        <button
          type="button"
          className={`studio-rail-btn ${showAxes ? 'active' : ''}`}
          onClick={() => setShowAxes((v) => !v)}
          title="Bật/tắt hệ trục Oxyz"
        >
          <Axis3D size={16} />
          <span>Oxyz</span>
        </button>
        <button
          type="button"
          className={`studio-rail-btn ${autoRotate ? 'active' : ''}`}
          onClick={() => setAutoRotate((v) => !v)}
          title="Tự động xoay khối 3D"
        >
          <RefreshCw size={15} />
          <span>Xoay</span>
        </button>
        <button
          type="button"
          className={`studio-rail-btn ${snapEnabled ? 'snap-on' : ''}`}
          onClick={() => setSnapEnabled((v) => !v)}
          title="Bật/tắt nam châm hút"
        >
          <Magnet size={15} />
          <span>Hút</span>
        </button>
        <div style={{ width: 22, height: 1, background: 'var(--border-subtle)', margin: '2px 0' }} />
        <button
          type="button"
          className="studio-rail-btn"
          onClick={() => setCamera((c) => ({ ...c, radius: Math.max(4.5, c.radius * 0.85) }))}
          title="Phóng to"
          aria-label="Phóng to"
        >
          <Plus size={16} />
        </button>
        <button
          type="button"
          className="studio-rail-btn"
          onClick={() => setCamera((c) => ({ ...c, radius: Math.min(26, c.radius * 1.18) }))}
          title="Thu nhỏ"
          aria-label="Thu nhỏ"
        >
          <Minus size={16} />
        </button>
      </div>

      {/* LIVE FLOATING DRAG / EDGE PREVIEW / VERTEX INSPECT TOOLTIP */}
      {(dragFeedback || (mode === 'add_point' && edgePreview) || (mode === 'view' && inspectedVertex && scene.pointRegistry[inspectedVertex])) && (
        <div className="glass-surface studio-live-tooltip">
          {dragFeedback ? (
            <>
              <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap', justifyContent: 'center' }}>
                <span className="studio-badge studio-badge-pink">
                  {dragConstraint === 'horizontal'
                    ? '↔ Kéo ngang Oxy'
                    : dragConstraint === 'vertical'
                    ? '↕ Kéo cao Oz'
                    : `Trên đoạn ${dragFeedback.rail ? `${dragFeedback.rail.fromName}${dragFeedback.rail.toName}` : ''}`}
                </span>
                <strong className="mono" style={{ fontSize: 13 }}>
                  {dragFeedback.vertexName} {formatCoord3D(worldToMath(dragFeedback.worldPos, originWorld))}
                </strong>
              </div>
              {dragFeedback.isSnapped && dragFeedback.snapReason && (
                <span className="studio-badge studio-badge-emerald">
                  <Magnet size={11} /> {dragFeedback.snapReason}
                </span>
              )}
              {dragFeedback.relationHint && (
                <span className="studio-badge studio-badge-sky">{dragFeedback.relationHint}</span>
              )}
            </>
          ) : mode === 'add_point' && edgePreview ? (
            <>
              <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                <span className="studio-badge studio-badge-amber">
                  Chấm trên {edgePreview.fromName}{edgePreview.toName}
                </span>
                <strong className="mono" style={{ fontSize: 12.5 }}>
                  k = {formatMathNum(edgePreview.ratio)}
                </strong>
              </div>
              {edgePreview.snapLabel && (
                <span className="studio-badge studio-badge-emerald">
                  <Magnet size={11} /> {edgePreview.snapLabel}
                </span>
              )}
            </>
          ) : inspectedVertex && scene.pointRegistry[inspectedVertex] ? (
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <strong className="mono" style={{ fontSize: 13, color: accentHex }}>
                Điểm {inspectedVertex} {formatCoord3D(worldToMath(scene.pointRegistry[inspectedVertex], originWorld))}
              </strong>
              <span style={{ fontSize: 11, color: 'var(--text-secondary)' }}>Gốc {snap.originPoint}</span>
            </div>
          ) : null}
        </div>
      )}

      {/* TOAST NOTIFICATION */}
      {toastMsg && (
        <div
          className="glass-surface"
          style={{
            position: 'absolute',
            top: 102,
            right: 64,
            zIndex: 28,
            padding: '7px 13px',
            borderRadius: 999,
            fontSize: 12,
            fontWeight: 700,
            color: '#34d399',
            borderColor: 'rgba(16,185,129,0.55)'
          }}
        >
          {toastMsg}
        </div>
      )}

      {/* ADAPTIVE INSPECTOR SHEET (SIDE DRAWER ON DESKTOP / BOTTOM SHEET ON MOBILE) */}
      <Space3DInspector
        open={inspectorOpen}
        activeTab={inspectorTab}
        onChangeTab={setInspectorTab}
        onClose={() => setInspectorOpen(false)}
        accentHex={accentHex}
        snap={snap}
        scene={scene}
        currentShape={currentShape}
        allPointNames={allPointNames}
        hasMovedVertices={hasMovedVertices}
        mode={mode}
        onChangeMode={(m) => {
          setMode(m);
          setSelectedFirstPt(null);
          setEdgePreview(null);
          if (m !== 'view') setInspectedVertex(null);
        }}
        dragConstraint={dragConstraint}
        onChangeDragConstraint={setDragConstraint}
        keepRelations={keepRelations}
        onToggleKeepRelations={() => setKeepRelations((v) => !v)}
        snapEnabled={snapEnabled}
        onToggleSnap={() => setSnapEnabled((v) => !v)}
        onResetMovedVertices={() => {
          commitSnap((prev) => ({ ...prev, vertexOverrides: {} }));
          showToast('Đã đặt lại các đỉnh về vị trí chuẩn');
        }}
        showFaces={showFaces}
        onToggleShowFaces={() => setShowFaces((v) => !v)}
        showHiddenDashed={showHiddenDashed}
        onToggleHiddenDashed={() => setShowHiddenDashed((v) => !v)}
        showAxes={showAxes}
        onToggleAxes={() => setShowAxes((v) => !v)}
        autoRotate={autoRotate}
        onToggleAutoRotate={() => setAutoRotate((v) => !v)}
        showCircumSphere={showCircumSphere}
        onToggleCircumSphere={() => setShowCircumSphere((v) => !v)}
        showInSphere={showInSphere}
        onToggleInSphere={() => setShowInSphere((v) => !v)}
        circumSphere={circumSphere}
        inSphere={inSphere}
        unfoldRatio={unfoldRatio}
        onChangeUnfoldRatio={setUnfoldRatio}
        totalConstructionSteps={totalConstructionSteps}
        replayStep={replayStep}
        isReplaying={isReplaying}
        onToggleReplay={() => {
          if (isReplaying) {
            setIsReplaying(false);
          } else {
            if (replayStep === null || replayStep >= totalConstructionSteps) setReplayStep(0);
            setIsReplaying(true);
          }
        }}
        onChangeReplayStep={(st) => {
          setIsReplaying(false);
          setReplayStep(st);
        }}
        sensitivity={sensitivity}
        onUpdateSensitivity={onUpdateSensitivity}
        onSelectShape={(key) => {
          commitSnap(() => ({ ...DEFAULT_SNAPSHOT, shapeKey: key }));
          setUnfoldRatio(0);
          setSkewOverlay(null);
          setReplayStep(null);
          setIsReplaying(false);
          setInspectedVertex(null);
        }}
        onUpdateDimensions={(patch) => setSnap((prev) => ({ ...prev, ...patch }))}
        onUpdateSection={(patch) => setSnap((prev) => ({ ...prev, ...patch }))}
        onChangeOrigin={(name) => commitSnap((prev) => ({ ...prev, originPoint: name }))}
        onAddEdgePoint={(from, to, ratio, name) =>
          commitSnap((prev) => ({
            ...prev,
            customPoints: [
              ...prev.customPoints,
              { id: `ep_${Date.now()}`, name: name || nextAutoPointName(scene.pointRegistry), from, to, ratio }
            ]
          }))
        }
        onRemoveEdgePoint={(id) =>
          commitSnap((prev) => ({ ...prev, customPoints: prev.customPoints.filter((x) => x.id !== id) }))
        }
        onAddXyzPoint={(name, x, y, z) =>
          commitSnap((prev) => ({
            ...prev,
            customXYZPoints: [...prev.customXYZPoints, { id: `xp_${Date.now()}`, name, x, y, z }]
          }))
        }
        onRemoveXyzPoint={(id) =>
          commitSnap((prev) => ({ ...prev, customXYZPoints: prev.customXYZPoints.filter((x) => x.id !== id) }))
        }
        onAddLine={(p1, p2, style, color) =>
          commitSnap((prev) => ({
            ...prev,
            customLines: [...prev.customLines, { id: `ln_${Date.now()}`, p1, p2, style, color }]
          }))
        }
        onRemoveLine={(id) =>
          commitSnap((prev) => ({ ...prev, customLines: prev.customLines.filter((x) => x.id !== id) }))
        }
        onAddPlane={(pts, color) =>
          commitSnap((prev) => ({
            ...prev,
            customPlanes: [...prev.customPlanes, { id: `pl_${Date.now()}`, pts, color }]
          }))
        }
        onRemovePlane={(id) =>
          commitSnap((prev) => ({ ...prev, customPlanes: prev.customPlanes.filter((x) => x.id !== id) }))
        }
        onSetDynamicSection={(p1, p2, p3) =>
          commitSnap((prev) => ({ ...prev, dynamicSection: { p1, p2, p3 } }))
        }
        onClearDynamicSection={() => commitSnap((prev) => ({ ...prev, dynamicSection: null }))}
        onClearAllCustom={() =>
          commitSnap((prev) => ({
            ...prev,
            customPoints: [],
            customXYZPoints: [],
            customLines: [],
            customPlanes: [],
            dynamicSection: null
          }))
        }
        skewOverlay={skewOverlay}
        onSetSkewOverlay={setSkewOverlay}
      />

      {/* BOTTOM WORKSPACE DOCK + DRAG CONSTRAINT SUB-BAR + MODE STATUS BANNER */}
      {!inspectorOpen && (
        <div className={`studio-bottom-stack ${isImmersive ? 'immersive' : ''}`}>
          {/* Active Mode Status Hint Pill */}
          {modeHintText && (
            <div className="glass-surface studio-status-banner">
              <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{modeHintText}</span>
              <button
                type="button"
                className="chip-btn active"
                style={{ minHeight: 26, padding: '2px 10px', fontSize: 11 }}
                onClick={() => {
                  setMode('view');
                  setSelectedFirstPt(null);
                  setEdgePreview(null);
                }}
              >
                <Check size={12} /> Xong
              </button>
            </div>
          )}

          {/* Drag Constraint Sub-Bar (Oxy / Cao Oz / Trên đoạn / Khóa hình / Hút) */}
          {mode === 'drag' && (
            <div className="glass-surface studio-drag-subbar">
              <button
                type="button"
                className={`studio-dock-item ${dragConstraint === 'horizontal' ? 'active-pink' : ''}`}
                style={{ minHeight: 32, padding: '4px 10px', fontSize: 11.5 }}
                onClick={() => setDragConstraint('horizontal')}
              >
                <MoveHorizontal size={13} /> Ngang Oxy
              </button>
              <button
                type="button"
                className={`studio-dock-item ${dragConstraint === 'vertical' ? 'active-pink' : ''}`}
                style={{ minHeight: 32, padding: '4px 10px', fontSize: 11.5 }}
                onClick={() => setDragConstraint('vertical')}
              >
                <MoveVertical size={13} /> Cao Oz
              </button>
              <button
                type="button"
                className={`studio-dock-item ${dragConstraint === 'segment' ? 'active-pink' : ''}`}
                style={{ minHeight: 32, padding: '4px 10px', fontSize: 11.5 }}
                onClick={() => setDragConstraint('segment')}
              >
                <Scissors size={13} /> Trên đoạn
              </button>
              <div style={{ width: 1, height: 18, background: 'var(--border-subtle)', margin: '0 2px' }} />
              <button
                type="button"
                className={`studio-dock-item ${keepRelations ? 'active' : ''}`}
                style={{ minHeight: 32, padding: '4px 10px', fontSize: 11.5 }}
                onClick={() => setKeepRelations((v) => !v)}
              >
                {keepRelations ? <Lock size={12} /> : <Unlock size={12} />}
                {keepRelations ? 'Khóa hình' : 'Tự do'}
              </button>
              <button
                type="button"
                className={`studio-dock-item ${snapEnabled ? 'active-emerald' : ''}`}
                style={{ minHeight: 32, padding: '4px 10px', fontSize: 11.5 }}
                onClick={() => setSnapEnabled((v) => !v)}
              >
                <Magnet size={12} /> Hút
              </button>
            </div>
          )}

          {/* Collapsible 3D Quick Dock */}
          <div className="glass-surface studio-dock-pill">
            {!dockCollapsed && (
              <>
                <button
                  type="button"
                  className="studio-dock-item glass-press"
                  onClick={() => openInspectorTab('shapes')}
                >
                  <Box size={15} />
                  <span>Khối</span>
                </button>
                <button
                  type="button"
                  className={`studio-dock-item glass-press ${mode === 'drag' ? 'active-pink' : ''}`}
                  onClick={() => {
                    setMode((m) => (m === 'drag' ? 'view' : 'drag'));
                    setSelectedFirstPt(null);
                    setEdgePreview(null);
                  }}
                >
                  <Move size={15} />
                  <span>Kéo đỉnh</span>
                </button>
                <button
                  type="button"
                  className={`studio-dock-item glass-press ${mode === 'add_point' ? 'active-amber' : ''}`}
                  onClick={() => {
                    setMode((m) => (m === 'add_point' ? 'view' : 'add_point'));
                    setSelectedFirstPt(null);
                    setEdgePreview(null);
                  }}
                >
                  <Crosshair size={15} />
                  <span>Chấm điểm</span>
                </button>
                <button
                  type="button"
                  className={`studio-dock-item glass-press ${
                    mode === 'connect_dashed' || mode === 'connect_solid' || mode === 'connect_vector' ? 'active' : ''
                  }`}
                  onClick={() => openInspectorTab('draw')}
                >
                  <Pencil size={15} />
                  <span>Dựng hình</span>
                </button>
                <button
                  type="button"
                  className="studio-dock-item glass-press"
                  onClick={() => openInspectorTab('oxyz')}
                >
                  <Calculator size={15} />
                  <span>Oxyz</span>
                </button>
                <button
                  type="button"
                  className="studio-dock-item glass-press"
                  onClick={() => openInspectorTab('settings')}
                >
                  <Sliders size={15} />
                  <span>Cài đặt</span>
                </button>
              </>
            )}
            <button
              type="button"
              className="studio-dock-item"
              style={{ padding: '5px 8px' }}
              onClick={() => setDockCollapsed((v) => !v)}
              title={dockCollapsed ? 'Mở thanh công cụ 3D' : 'Thu gọn thanh công cụ'}
              aria-label="Thu gọn hoặc mở thanh công cụ"
            >
              {dockCollapsed ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
              {dockCollapsed && <span>Công cụ 3D</span>}
            </button>
          </div>
        </div>
      )}

      {/* EXPORT, SHARE & SAVED SLOTS MODAL */}
      <Space3DExportModal
        open={exportModalOpen}
        onClose={() => setExportModalOpen(false)}
        shapeTitle={currentShape.title}
        transparentPng={transparentPng}
        onChangeTransparentPng={setTransparentPng}
        onExportPng={handleExportPng}
        onExportSvg={handleExportSvg}
        onExportObj={handleExportObj}
        onExportJson={handleExportJson}
        onImportJson={handleImportJson}
        onCopyShareLink={handleCopyShareLink}
        savedProjects={savedProjects}
        onSaveProject={(name) => {
          setSavedProjects((prev) => [{ id: `s3d_${Date.now()}`, name, updatedAt: Date.now(), snapshot: snap }, ...prev]);
          showToast(`Đã lưu bản vẽ "${name}"`);
        }}
        onLoadProject={(proj) => {
          commitSnap(() => ({ ...DEFAULT_SNAPSHOT, ...proj.snapshot }));
          showToast(`Đã mở "${proj.name}"`);
        }}
        onDeleteProject={(id) => setSavedProjects((prev) => prev.filter((p) => p.id !== id))}
      />
    </div>
  );
};
