import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  Check,
  Compass,
  Download,
  Eye,
  EyeOff,
  FolderOpen,
  Gauge,
  Globe,
  Hand,
  Layers,
  Link2,
  Maximize2,
  Minimize2,
  MousePointerClick,
  Move,
  Pause,
  Pencil,
  Play,
  Redo2,
  RotateCcw,
  Save,
  Shapes,
  SkipBack,
  SkipForward,
  SlidersHorizontal,
  Trash2,
  Undo2,
  Upload,
  X
} from 'lucide-react';
import {
  ALL_3D_SHAPES,
  analyzeLineAndPlane,
  build3DSceneFromSnapshot,
  Camera3D,
  dist3,
  dot3,
  exportSceneToObj,
  exportSceneToSvg,
  formatMathNum,
  LineStyle3D,
  nextAutoPointName,
  projectPoint,
  Scene3DSnapshot,
  SHAPE_CATEGORIES,
  sub3,
  Vec3,
  worldToMath
} from '../engine/geo3d';
import {
  buildOccluderFaces,
  buildShapeThumbnailEdges,
  buildUnfoldedFaces3D,
  computeCircumSphere,
  computeDihedralAngle,
  computeInSphere,
  computeSkewLinesDistance,
  formatCoord3D,
  getCameraWorldPosition,
  getRoundBodyHint,
  isSegmentOccludedBySolid,
  SphereResult3D
} from '../engine/geo3dAdvanced';
import { evaluateVerifyAnswer, isVerifyAnswerCorrect } from '../engine/geo2d';
import { buildShareUrl3D } from '../services/shareUrl';
import { AiDrawingPlan, Sensitivity3D } from '../types/app';

interface Space3DWorkspaceProps {
  accentHex: string;
  themeMode: 'dark' | 'light';
  isImmersive: boolean;
  onToggleImmersive: () => void;
  sensitivity: Sensitivity3D;
  onUpdateSensitivity: (next: Sensitivity3D) => void;
  externalPlan: AiDrawingPlan | null;
  onPlanConsumed: () => void;
  externalSnapshot: Scene3DSnapshot | null;
  onExternalSnapshotConsumed: () => void;
}

type DrawerTab3D = 'shapes' | 'draw' | 'coords' | 'formulas';
type InteractionMode3D = 'view' | 'drag' | 'connect_dashed' | 'connect_solid' | 'connect_vector';
type VerifyTarget3D = 'vol' | 'stp' | 'sday' | 'h' | 'dist_ab' | 'angle_lp' | 'dist_ap' | 'dyn_sec' | 'circum_r' | 'skew_d' | 'dihedral';

interface SavedSlot3D {
  id: string;
  name: string;
  savedAt: string;
  snapshot: Scene3DSnapshot;
}

const DEFAULT_CAMERA: Camera3D = {
  theta: 0.72,
  phi: 1.12,
  radius: 10.5,
  target: { x: 0, y: 1.6, z: 0 }
};

const DEFAULT_SNAPSHOT: Scene3DSnapshot = {
  shapeKey: 'pyramid4_sa',
  a: 4,
  b: 3,
  h: 4.5,
  originPoint: 'A',
  customPoints: [],
  customXYZPoints: [],
  customLines: [],
  customPlanes: [],
  dynamicSection: null,
  vertexOverrides: {}
};

const AUTO_SAVE_KEY = 'tooltoan_3d_autosave_v2';
const SLOTS_KEY = 'tooltoan_3d_slots_v2';

const LINE_COLORS = [
  { hex: '#38bdf8', label: 'Xanh dương' },
  { hex: '#f43f5e', label: 'Đỏ hồng' },
  { hex: '#10b981', label: 'Xanh lá' },
  { hex: '#fbbf24', label: 'Vàng cam' },
  { hex: '#e879f9', label: 'Tím hồng' }
];

function downloadFile(filename: string, content: string, mime: string) {
  const blob = new Blob([content], { type: mime });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

function drawSphereOverlay(
  ctx: CanvasRenderingContext2D,
  sphere: SphereResult3D,
  camera: Camera3D,
  w: number,
  h: number,
  strokeColor: string,
  labelPrefix: string
) {
  const { centerWorld: c, radius: r } = sphere;
  const steps = 48;

  const drawCircle = (ptAt: (ang: number) => Vec3, dashed = false) => {
    ctx.save();
    ctx.strokeStyle = strokeColor;
    ctx.lineWidth = dashed ? 1.2 : 1.7;
    if (dashed) ctx.setLineDash([5, 4]);
    ctx.beginPath();
    for (let i = 0; i <= steps; i++) {
      const ang = (i / steps) * Math.PI * 2;
      const p = projectPoint(ptAt(ang), camera, w, h);
      if (i === 0) ctx.moveTo(p.x, p.y);
      else ctx.lineTo(p.x, p.y);
    }
    ctx.stroke();
    ctx.restore();
  };

  // Equator + 2 orthogonal meridians + silhouette ring
  drawCircle((ang) => ({ x: c.x + r * Math.cos(ang), y: c.y, z: c.z + r * Math.sin(ang) }), false);
  drawCircle((ang) => ({ x: c.x + r * Math.cos(ang), y: c.y + r * Math.sin(ang), z: c.z }), true);
  drawCircle((ang) => ({ x: c.x, y: c.y + r * Math.sin(ang), z: c.z + r * Math.cos(ang) }), true);

  // Center I marker + label
  const cp = projectPoint(c, camera, w, h);
  ctx.save();
  ctx.beginPath();
  ctx.arc(cp.x, cp.y, 4.5, 0, Math.PI * 2);
  ctx.fillStyle = strokeColor;
  ctx.fill();
  ctx.strokeStyle = '#ffffff';
  ctx.lineWidth = 1.4;
  ctx.stroke();
  ctx.font = '800 11.5px "JetBrains Mono", monospace';
  ctx.fillStyle = strokeColor;
  ctx.fillText(`${labelPrefix} (R=${r.toFixed(2)})`, cp.x + 8, cp.y - 6);
  ctx.restore();
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
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const [snapshot, setSnapshot] = useState<Scene3DSnapshot>(() => {
    try {
      const raw = localStorage.getItem(AUTO_SAVE_KEY);
      if (raw) return { ...DEFAULT_SNAPSHOT, ...JSON.parse(raw) };
    } catch {
      // ignore
    }
    return DEFAULT_SNAPSHOT;
  });

  const [undoStack, setUndoStack] = useState<Scene3DSnapshot[]>([]);
  const [redoStack, setRedoStack] = useState<Scene3DSnapshot[]>([]);

  const [savedSlots, setSavedSlots] = useState<SavedSlot3D[]>(() => {
    try {
      const raw = localStorage.getItem(SLOTS_KEY);
      return raw ? JSON.parse(raw) : [];
    } catch {
      return [];
    }
  });
  const [slotNameInput, setSlotNameInput] = useState<string>('');

  // Display, camera & interactive mode
  const [camera, setCamera] = useState<Camera3D>(DEFAULT_CAMERA);
  const [autoRotate, setAutoRotate] = useState<boolean>(false);
  const [showAxes, setShowAxes] = useState<boolean>(true);
  const [showGrid, setShowGrid] = useState<boolean>(true);
  const [showDashed, setShowDashed] = useState<boolean>(true);
  const [showAltitude, setShowAltitude] = useState<boolean>(true);
  const [showLabels, setShowLabels] = useState<boolean>(true);
  const [showCoordsOnLabels, setShowCoordsOnLabels] = useState<boolean>(false);
  const [hideAnswers, setHideAnswers] = useState<boolean>(false);

  // Item 1 & 2: Direct canvas interaction modes
  const [interactionMode, setInteractionMode] = useState<InteractionMode3D>('view');
  const [pendingConnectFrom, setPendingConnectFrom] = useState<string | null>(null);
  const [draggingPointName, setDraggingPointName] = useState<string | null>(null);

  // Item 19: Circumscribed/Inscribed Sphere, Unfolding, Dihedral, Skew Lines
  const [showCircumSphere, setShowCircumSphere] = useState<boolean>(false);
  const [showInSphere, setShowInSphere] = useState<boolean>(false);
  const [unfoldRatio, setUnfoldRatio] = useState<number>(0);
  const [showSkewPerp, setShowSkewPerp] = useState<boolean>(false);

  // Drawer, Sensitivity Popover & Export Modal
  const [drawerOpen, setDrawerOpen] = useState<boolean>(false);
  const [activeTab, setActiveTab] = useState<DrawerTab3D>('shapes');
  const [exportOpen, setExportOpen] = useState<boolean>(false);
  const [sensOpen, setSensOpen] = useState<boolean>(false);
  const [pngTransparent, setPngTransparent] = useState<boolean>(true);
  const [exportFilename, setExportFilename] = useState<string>('');
  const [toast, setToast] = useState<string | null>(null);

  // Replay mode
  const [replayActive, setReplayActive] = useState<boolean>(false);
  const [replayStep, setReplayStep] = useState<number>(0);
  const [replayPlaying, setReplayPlaying] = useState<boolean>(false);

  // Precomputed mini SVG wireframe thumbnails for all 15 shapes (Item 6)
  const shapeThumbnails = useMemo(() => {
    const map: Record<string, { x1: number; y1: number; x2: number; y2: number }[]> = {};
    for (const s of ALL_3D_SHAPES) {
      const miniScene = build3DSceneFromSnapshot({
        ...DEFAULT_SNAPSHOT,
        shapeKey: s.key,
        a: 3.6,
        b: 2.8,
        h: 4.0
      });
      map[s.key] = buildShapeThumbnailEdges(miniScene, 40);
    }
    return map;
  }, []);

  const totalCustomSteps =
    snapshot.customXYZPoints.length +
    snapshot.customPoints.length +
    snapshot.customLines.length +
    snapshot.customPlanes.length +
    (snapshot.dynamicSection ? 1 : 0);

  const effectiveSnapshot = useMemo<Scene3DSnapshot>(() => {
    if (!replayActive) return snapshot;
    let rem = replayStep;
    const xyz = snapshot.customXYZPoints.slice(0, Math.min(rem, snapshot.customXYZPoints.length));
    rem = Math.max(0, rem - snapshot.customXYZPoints.length);
    const pts = snapshot.customPoints.slice(0, Math.min(rem, snapshot.customPoints.length));
    rem = Math.max(0, rem - snapshot.customPoints.length);
    const lines = snapshot.customLines.slice(0, Math.min(rem, snapshot.customLines.length));
    rem = Math.max(0, rem - snapshot.customLines.length);
    const planes = snapshot.customPlanes.slice(0, Math.min(rem, snapshot.customPlanes.length));
    rem = Math.max(0, rem - snapshot.customPlanes.length);
    const sec = rem > 0 ? snapshot.dynamicSection : null;
    return {
      ...snapshot,
      customXYZPoints: xyz,
      customPoints: pts,
      customLines: lines,
      customPlanes: planes,
      dynamicSection: sec
    };
  }, [snapshot, replayActive, replayStep]);

  const scene = useMemo(() => build3DSceneFromSnapshot(effectiveSnapshot), [effectiveSnapshot]);
  const fullScene = useMemo(() => build3DSceneFromSnapshot(snapshot), [snapshot]);
  const pointNames = useMemo(() => Object.keys(fullScene.pointRegistry), [fullScene.pointRegistry]);

  // Draw pane local state
  const [ptName, setPtName] = useState<string>('M');
  const [ptFrom, setPtFrom] = useState<string>('S');
  const [ptTo, setPtTo] = useState<string>('A');
  const [ptRatio, setPtRatio] = useState<number>(0.5);

  const [lineP1, setLineP1] = useState<string>('S');
  const [lineP2, setLineP2] = useState<string>('C');
  const [lineStyle, setLineStyle] = useState<LineStyle3D>('dashed');
  const [lineColor, setLineColor] = useState<string>('#38bdf8');

  const [pl1, setPl1] = useState<string>('S');
  const [pl2, setPl2] = useState<string>('A');
  const [pl3, setPl3] = useState<string>('C');
  const [pl4, setPl4] = useState<string>('');

  const [sec1, setSec1] = useState<string>('S');
  const [sec2, setSec2] = useState<string>('A');
  const [sec3, setSec3] = useState<string>('C');

  // Coords pane local state
  const [calcA, setCalcA] = useState<string>('S');
  const [calcB, setCalcB] = useState<string>('A');
  const [calcP1, setCalcP1] = useState<string>('A');
  const [calcP2, setCalcP2] = useState<string>('B');
  const [calcP3, setCalcP3] = useState<string>('C');

  // Second plane Q (for Dihedral angle) & Second line C-D (for Skew lines)
  const [calcQ1, setCalcQ1] = useState<string>('S');
  const [calcQ2, setCalcQ2] = useState<string>('A');
  const [calcQ3, setCalcQ3] = useState<string>('B');
  const [skewC, setSkewC] = useState<string>('B');
  const [skewD, setSkewD] = useState<string>('C');

  const [xyzName, setXyzName] = useState<string>('K');
  const [xyzX, setXyzX] = useState<string>('1');
  const [xyzY, setXyzY] = useState<string>('1');
  const [xyzZ, setXyzZ] = useState<string>('2');

  // Verify Mode state
  const [verifyTarget, setVerifyTarget] = useState<VerifyTarget3D>('vol');
  const [verifyInput, setVerifyInput] = useState<string>('');
  const [verifyFeedback, setVerifyFeedback] = useState<{ ok: boolean; msg: string } | null>(null);
  const [revealVerifyAns, setRevealVerifyAns] = useState<boolean>(false);

  const dragRef = useRef<{
    mode: 'none' | 'orbit' | 'drag_point';
    pointName: string | null;
    x: number;
    y: number;
    pinchDist: number | null;
  }>({
    mode: 'none',
    pointName: null,
    x: 0,
    y: 0,
    pinchDist: null
  });
  const activePointersRef = useRef<Map<number, { x: number; y: number }>>(new Map());

  const showToast = (msg: string) => setToast(msg);

  const commitSnapshot = (updater: (prev: Scene3DSnapshot) => Scene3DSnapshot, recordHistory = true) => {
    setReplayActive(false);
    setReplayPlaying(false);
    setSnapshot((prev) => {
      const next = updater(prev);
      if (recordHistory) {
        setUndoStack((u) => [...u.slice(-39), prev]);
        setRedoStack([]);
      }
      return next;
    });
  };

  const handleUndo = () => {
    if (undoStack.length === 0) return;
    const prev = undoStack[undoStack.length - 1];
    setUndoStack((u) => u.slice(0, -1));
    setRedoStack((r) => [...r, snapshot]);
    setSnapshot(prev);
    setReplayActive(false);
  };

  const handleRedo = () => {
    if (redoStack.length === 0) return;
    const next = redoStack[redoStack.length - 1];
    setRedoStack((r) => r.slice(0, -1));
    setUndoStack((u) => [...u, snapshot]);
    setSnapshot(next);
    setReplayActive(false);
  };

  // Auto-save snapshot & slots
  useEffect(() => {
    localStorage.setItem(AUTO_SAVE_KEY, JSON.stringify(snapshot));
  }, [snapshot]);

  useEffect(() => {
    localStorage.setItem(SLOTS_KEY, JSON.stringify(savedSlots));
  }, [savedSlots]);

  // Keyboard shortcuts (Ctrl+Z, Ctrl+Y, Space auto-rotate)
  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      const t = e.target as HTMLElement | null;
      const typing = t && (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.tagName === 'SELECT');
      if (typing) return;
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'z' && !e.shiftKey) {
        e.preventDefault();
        handleUndo();
      } else if ((e.ctrlKey || e.metaKey) && (e.key.toLowerCase() === 'y' || (e.shiftKey && e.key.toLowerCase() === 'z'))) {
        e.preventDefault();
        handleRedo();
      } else if (e.code === 'Space') {
        e.preventDefault();
        setAutoRotate((v) => !v);
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  });

  // Keep dropdown selections valid when shape changes
  useEffect(() => {
    if (pointNames.length === 0) return;
    const pick = (cur: string, idx: number) => (pointNames.includes(cur) ? cur : pointNames[idx % pointNames.length]);
    setPtName(nextAutoPointName(fullScene.pointRegistry));
    setXyzName(nextAutoPointName(fullScene.pointRegistry));
    setPtFrom((v) => pick(v, 0));
    setPtTo((v) => pick(v, 1));
    setLineP1((v) => pick(v, 0));
    setLineP2((v) => pick(v, 1));
    setPl1((v) => pick(v, 0));
    setPl2((v) => pick(v, 1));
    setPl3((v) => pick(v, 2));
    setSec1((v) => pick(v, 0));
    setSec2((v) => pick(v, 1));
    setSec3((v) => pick(v, 2));
    setCalcA((v) => pick(v, 0));
    setCalcB((v) => pick(v, 1));
    setCalcP1((v) => pick(v, 0));
    setCalcP2((v) => pick(v, 1));
    setCalcP3((v) => pick(v, 2));
    setCalcQ1((v) => pick(v, 0));
    setCalcQ2((v) => pick(v, 1));
    setCalcQ3((v) => pick(v, 3));
    setSkewC((v) => pick(v, 2));
    setSkewD((v) => pick(v, 3));
  }, [pointNames, fullScene.pointRegistry]);

  // Consume external Share URL snapshot
  useEffect(() => {
    if (!externalSnapshot) return;
    commitSnapshot(() => ({ ...DEFAULT_SNAPSHOT, ...externalSnapshot }));
    setUnfoldRatio(0);
    showToast('Đã mở mô hình 3D từ liên kết chia sẻ!');
    onExternalSnapshotConsumed();
  }, [externalSnapshot, onExternalSnapshotConsumed]);

  // Consume rich external AI plan (Item 17)
  useEffect(() => {
    if (!externalPlan || externalPlan.target !== 'space3d') return;
    const now = Date.now();
    commitSnapshot((prev) => ({
      ...DEFAULT_SNAPSHOT,
      shapeKey:
        externalPlan.shape3dKey && ALL_3D_SHAPES.some((s) => s.key === externalPlan.shape3dKey)
          ? externalPlan.shape3dKey
          : prev.shapeKey,
      a: typeof externalPlan.a === 'number' && Number.isFinite(externalPlan.a) ? Math.min(10, Math.max(1, externalPlan.a)) : prev.a,
      b: typeof externalPlan.b === 'number' && Number.isFinite(externalPlan.b) ? Math.min(10, Math.max(1, externalPlan.b)) : prev.b,
      h: typeof externalPlan.h === 'number' && Number.isFinite(externalPlan.h) ? Math.min(10, Math.max(1, externalPlan.h)) : prev.h,
      customPoints: (externalPlan.edgePoints || []).map((ep, i) => ({
        id: `ai_ep_${now}_${i}`,
        name: ep.name,
        from: ep.from,
        to: ep.to,
        ratio: Math.max(0.05, Math.min(0.95, ep.ratio))
      })),
      customXYZPoints: (externalPlan.xyzPoints || []).map((xp, i) => ({
        id: `ai_xyz_${now}_${i}`,
        name: xp.name,
        x: xp.x,
        y: xp.y,
        z: xp.z
      })),
      customLines: (externalPlan.segments || []).map((seg, i) => ({
        id: `ai_ln_${now}_${i}`,
        p1: seg.p1,
        p2: seg.p2,
        style: seg.style || 'dashed',
        color: seg.color || '#38bdf8'
      })),
      customPlanes: (externalPlan.planes || []).map((pts, i) => ({
        id: `ai_pl_${now}_${i}`,
        pts,
        color: '#10b981'
      })),
      dynamicSection: externalPlan.crossSection
        ? { p1: externalPlan.crossSection[0], p2: externalPlan.crossSection[1], p3: externalPlan.crossSection[2] }
        : null,
      vertexOverrides: {}
    }));
    if (externalPlan.showCircumSphere) setShowCircumSphere(true);
    setUnfoldRatio(0);
    showToast(`Đã dựng trọn bộ mô hình AI: ${externalPlan.title}`);
    onPlanConsumed();
  }, [externalPlan, onPlanConsumed]);

  useEffect(() => {
    if (!toast) return;
    const t = window.setTimeout(() => setToast(null), 3000);
    return () => window.clearTimeout(t);
  }, [toast]);

  // Auto-rotate loop
  useEffect(() => {
    if (!autoRotate) return;
    let raf = 0;
    const tick = () => {
      setCamera((prev) => ({ ...prev, theta: prev.theta + 0.008 }));
      raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [autoRotate]);

  // Replay timer
  useEffect(() => {
    if (!replayActive || !replayPlaying) return;
    if (replayStep >= totalCustomSteps) {
      setReplayPlaying(false);
      return;
    }
    const timer = window.setTimeout(() => {
      setReplayStep((s) => Math.min(totalCustomSteps, s + 1));
    }, 950);
    return () => window.clearTimeout(timer);
  }, [replayActive, replayPlaying, replayStep, totalCustomSteps]);

  const currentSpec = useMemo(
    () => ALL_3D_SHAPES.find((s) => s.key === snapshot.shapeKey) || ALL_3D_SHAPES[0],
    [snapshot.shapeKey]
  );

  const originWorld = useMemo(
    () => scene.pointRegistry[snapshot.originPoint] || { x: 0, y: 0, z: 0 },
    [scene.pointRegistry, snapshot.originPoint]
  );

  // Item 19 calculations
  const circumSphere = useMemo(
    () => computeCircumSphere(fullScene, snapshot.shapeKey, snapshot.a, snapshot.b, snapshot.h, originWorld),
    [fullScene, snapshot.shapeKey, snapshot.a, snapshot.b, snapshot.h, originWorld]
  );
  const inSphere = useMemo(
    () => computeInSphere(fullScene, snapshot.shapeKey, snapshot.a, snapshot.b, snapshot.h, originWorld),
    [fullScene, snapshot.shapeKey, snapshot.a, snapshot.b, snapshot.h, originWorld]
  );

  const vCalcA = fullScene.pointRegistry[calcA];
  const vCalcB = fullScene.pointRegistry[calcB];
  const vP1 = fullScene.pointRegistry[calcP1];
  const vP2 = fullScene.pointRegistry[calcP2];
  const vP3 = fullScene.pointRegistry[calcP3];
  const vQ1 = fullScene.pointRegistry[calcQ1];
  const vQ2 = fullScene.pointRegistry[calcQ2];
  const vQ3 = fullScene.pointRegistry[calcQ3];
  const vSkewC = fullScene.pointRegistry[skewC];
  const vSkewD = fullScene.pointRegistry[skewD];

  const linePlaneAnalysis =
    vCalcA && vCalcB && vP1 && vP2 && vP3
      ? analyzeLineAndPlane(vCalcA, vCalcB, vP1, vP2, vP3, originWorld)
      : null;

  const dihedralAnalysis =
    vP1 && vP2 && vP3 && vQ1 && vQ2 && vQ3
      ? computeDihedralAngle(vP1, vP2, vP3, vQ1, vQ2, vQ3, originWorld)
      : null;

  const skewAnalysis =
    vCalcA && vCalcB && vSkewC && vSkewD
      ? computeSkewLinesDistance(vCalcA, vCalcB, vSkewC, vSkewD)
      : null;

  const occluderFaces = useMemo(() => buildOccluderFaces(scene), [scene]);
  const roundBodyHint = useMemo(
    () => getRoundBodyHint(snapshot.shapeKey, snapshot.a, snapshot.h),
    [snapshot.shapeKey, snapshot.a, snapshot.h]
  );

  const unfoldedFaces = useMemo(
    () => (unfoldRatio > 0.005 ? buildUnfoldedFaces3D(scene, snapshot.shapeKey, unfoldRatio) : null),
    [scene, snapshot.shapeKey, unfoldRatio]
  );

  // Render full 3D canvas
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const render = () => {
      const dpr = Math.min(window.devicePixelRatio || 1, 2);
      const rect = canvas.getBoundingClientRect();
      const w = Math.max(300, rect.width);
      const h = Math.max(300, rect.height);
      if (canvas.width !== Math.round(w * dpr) || canvas.height !== Math.round(h * dpr)) {
        canvas.width = Math.round(w * dpr);
        canvas.height = Math.round(h * dpr);
      }

      ctx.save();
      ctx.scale(dpr, dpr);
      ctx.clearRect(0, 0, w, h);

      const isLight = themeMode === 'light';
      const camPos = getCameraWorldPosition(camera);

      // Floor grid
      if (showGrid) {
        ctx.strokeStyle = isLight ? 'rgba(15, 23, 42, 0.11)' : 'rgba(148, 163, 184, 0.14)';
        ctx.lineWidth = 1;
        for (let g = -5; g <= 5; g++) {
          const a1 = projectPoint({ x: g, y: 0, z: -5 }, camera, w, h);
          const a2 = projectPoint({ x: g, y: 0, z: 5 }, camera, w, h);
          ctx.beginPath();
          ctx.moveTo(a1.x, a1.y);
          ctx.lineTo(a2.x, a2.y);
          ctx.stroke();

          const b1 = projectPoint({ x: -5, y: 0, z: g }, camera, w, h);
          const b2 = projectPoint({ x: 5, y: 0, z: g }, camera, w, h);
          ctx.beginPath();
          ctx.moveTo(b1.x, b1.y);
          ctx.lineTo(b2.x, b2.y);
          ctx.stroke();
        }
      }

      // Oxyz axes rooted at selected originPoint
      if (showAxes) {
        const oProj = projectPoint(originWorld, camera, w, h);
        const axes = [
          { end: { x: originWorld.x + 5, y: originWorld.y, z: originWorld.z }, label: 'x', color: '#f43f5e' },
          { end: { x: originWorld.x, y: originWorld.y, z: originWorld.z + 5 }, label: 'y', color: '#10b981' },
          { end: { x: originWorld.x, y: originWorld.y + 5, z: originWorld.z }, label: 'z', color: '#38bdf8' }
        ];
        for (const ax of axes) {
          const p = projectPoint(ax.end, camera, w, h);
          ctx.strokeStyle = ax.color;
          ctx.lineWidth = 2;
          ctx.beginPath();
          ctx.moveTo(oProj.x, oProj.y);
          ctx.lineTo(p.x, p.y);
          ctx.stroke();
          ctx.fillStyle = ax.color;
          ctx.font = '700 13px "JetBrains Mono", monospace';
          ctx.fillText(ax.label, p.x + 6, p.y + 4);
        }
      }

      // If Polyhedron Net Unfolding is active, draw unfolded net faces!
      if (unfoldedFaces && unfoldedFaces.length > 0) {
        const sortedUnf = unfoldedFaces
          .map((uf) => {
            const pts2 = uf.points.map((pt) => projectPoint(pt, camera, w, h));
            const avgD = pts2.reduce((acc, p) => acc + p.depth, 0) / pts2.length;
            return { uf, pts2, avgD };
          })
          .sort((a1, a2) => a2.avgD - a1.avgD);

        for (const { uf, pts2 } of sortedUnf) {
          ctx.save();
          ctx.beginPath();
          pts2.forEach((p, idx) => {
            if (idx === 0) ctx.moveTo(p.x, p.y);
            else ctx.lineTo(p.x, p.y);
          });
          ctx.closePath();
          ctx.fillStyle = uf.isBase ? `${accentHex}55` : `${accentHex}2C`;
          ctx.fill();
          ctx.strokeStyle = isLight ? '#0f172a' : '#f8fafc';
          ctx.lineWidth = uf.isBase ? 2.4 : 1.9;
          ctx.stroke();
          ctx.restore();
        }
      } else {
        const projected = scene.vertices.map((v) => projectPoint(v.pos, camera, w, h));

        // Solid translucent faces (sorted back-to-front)
        const sortedFaces = scene.faces
          .map((face) => ({
            face,
            avgDepth: face.reduce((acc, idx) => acc + projected[idx].depth, 0) / face.length
          }))
          .sort((f1, f2) => f2.avgDepth - f1.avgDepth);

        for (const { face } of sortedFaces) {
          if (face.length < 3) continue;
          ctx.beginPath();
          face.forEach((vIdx, idx) => {
            const p = projected[vIdx];
            if (idx === 0) ctx.moveTo(p.x, p.y);
            else ctx.lineTo(p.x, p.y);
          });
          ctx.closePath();
          ctx.fillStyle = isLight ? `${accentHex}24` : `${accentHex}2A`;
          ctx.fill();
        }

        // Auxiliary custom planes
        for (const pl of effectiveSnapshot.customPlanes) {
          const coords = pl.pts.map((name) => scene.pointRegistry[name]).filter(Boolean);
          if (coords.length < 3) continue;
          ctx.save();
          ctx.beginPath();
          coords.forEach((pt, idx) => {
            const p = projectPoint(pt, camera, w, h);
            if (idx === 0) ctx.moveTo(p.x, p.y);
            else ctx.lineTo(p.x, p.y);
          });
          ctx.closePath();
          ctx.fillStyle = 'rgba(16, 185, 129, 0.28)';
          ctx.fill();
          ctx.strokeStyle = '#10b981';
          ctx.lineWidth = 1.8;
          ctx.stroke();
          ctx.restore();
        }

        // Dynamic 3-point cross-section polygon
        if (scene.dynamicSection && scene.dynamicSection.polygon.length >= 3) {
          ctx.save();
          ctx.beginPath();
          scene.dynamicSection.polygon.forEach((pt, idx) => {
            const p = projectPoint(pt, camera, w, h);
            if (idx === 0) ctx.moveTo(p.x, p.y);
            else ctx.lineTo(p.x, p.y);
          });
          ctx.closePath();
          ctx.fillStyle = 'rgba(249, 115, 22, 0.34)';
          ctx.fill();
          ctx.strokeStyle = '#fb923c';
          ctx.lineWidth = 2.8;
          ctx.stroke();
          ctx.restore();
        }

        // Base edges with REAL 3D Solid Occlusion (Item 3)
        ctx.lineCap = 'round';
        for (const edge of scene.edges) {
          const v1 = scene.vertices[edge.from].pos;
          const v2 = scene.vertices[edge.to].pos;
          const p1 = projected[edge.from];
          const p2 = projected[edge.to];
          const occluded =
            showDashed && isSegmentOccludedBySolid(v1, v2, camPos, occluderFaces, roundBodyHint);
          ctx.save();
          ctx.strokeStyle = isLight
            ? occluded ? 'rgba(15, 23, 42, 0.44)' : '#0f172a'
            : occluded ? 'rgba(226, 232, 240, 0.46)' : '#f8fafc';
          ctx.lineWidth = occluded ? 1.5 : 2.3;
          if (occluded) {
            ctx.setLineDash([6, 5]);
          }
          ctx.beginPath();
          ctx.moveTo(p1.x, p1.y);
          ctx.lineTo(p2.x, p2.y);
          ctx.stroke();
          ctx.restore();
        }

        // Altitude helper line
        if (showAltitude && (snapshot.shapeKey.startsWith('pyramid') || snapshot.shapeKey === 'cone')) {
          const apex = scene.pointRegistry.S;
          if (apex) {
            const foot = projectPoint({ x: apex.x, y: 0, z: apex.z }, camera, w, h);
            const top = projectPoint(apex, camera, w, h);
            ctx.save();
            ctx.strokeStyle = '#f59e0b';
            ctx.lineWidth = 1.9;
            ctx.setLineDash([5, 4]);
            ctx.beginPath();
            ctx.moveTo(top.x, top.y);
            ctx.lineTo(foot.x, foot.y);
            ctx.stroke();
            ctx.restore();
          }
        }

        // Custom segments & vectors (with solid occlusion auto-dashing)
        for (const cl of effectiveSnapshot.customLines) {
          const v1 = scene.pointRegistry[cl.p1];
          const v2 = scene.pointRegistry[cl.p2];
          if (!v1 || !v2) continue;
          const a = projectPoint(v1, camera, w, h);
          const b = projectPoint(v2, camera, w, h);
          const isOccluded =
            showDashed &&
            (cl.style === 'solid' || cl.style === 'vector_solid') &&
            isSegmentOccludedBySolid(v1, v2, camPos, occluderFaces, roundBodyHint);
          ctx.save();
          ctx.strokeStyle = cl.color;
          ctx.fillStyle = cl.color;
          ctx.lineWidth = 2.6;
          if (cl.style === 'dashed' || cl.style === 'vector_dashed' || isOccluded) {
            ctx.setLineDash([7, 5]);
            if (isOccluded) ctx.globalAlpha = 0.72;
          }
          ctx.beginPath();
          ctx.moveTo(a.x, a.y);
          ctx.lineTo(b.x, b.y);
          ctx.stroke();
          ctx.setLineDash([]);

          if (cl.style === 'vector_solid' || cl.style === 'vector_dashed') {
            const ang = Math.atan2(b.y - a.y, b.x - a.x);
            const head = 12;
            ctx.beginPath();
            ctx.moveTo(b.x, b.y);
            ctx.lineTo(b.x - head * Math.cos(ang - 0.42), b.y - head * Math.sin(ang - 0.42));
            ctx.lineTo(b.x - head * Math.cos(ang + 0.42), b.y - head * Math.sin(ang + 0.42));
            ctx.closePath();
            ctx.fill();
          }
          ctx.restore();
        }

        // Skew lines common perpendicular H1-H2 overlay (Item 19)
        if (showSkewPerp && skewAnalysis && !skewAnalysis.isIntersecting) {
          const h1 = projectPoint(skewAnalysis.closestOnLine1, camera, w, h);
          const h2 = projectPoint(skewAnalysis.closestOnLine2, camera, w, h);
          ctx.save();
          ctx.strokeStyle = '#f43f5e';
          ctx.lineWidth = 3;
          ctx.beginPath();
          ctx.moveTo(h1.x, h1.y);
          ctx.lineTo(h2.x, h2.y);
          ctx.stroke();
          for (const [pt, lbl] of [
            [h1, 'H₁'],
            [h2, 'H₂']
          ] as const) {
            ctx.beginPath();
            ctx.arc(pt.x, pt.y, 4.5, 0, Math.PI * 2);
            ctx.fillStyle = '#f43f5e';
            ctx.fill();
            ctx.strokeStyle = '#ffffff';
            ctx.lineWidth = 1.4;
            ctx.stroke();
            ctx.font = '800 11px "JetBrains Mono", monospace';
            ctx.fillStyle = '#f43f5e';
            ctx.fillText(lbl, pt.x + 7, pt.y - 5);
          }
          ctx.restore();
        }

        // Circumscribed & Inscribed Sphere overlays (Item 19)
        if (showCircumSphere && circumSphere) {
          drawSphereOverlay(ctx, circumSphere, camera, w, h, '#38bdf8', 'I_ngoại');
        }
        if (showInSphere && inSphere) {
          drawSphereOverlay(ctx, inSphere, camera, w, h, '#10b981', 'I_nội');
        }

        // Named points + optional Oxyz coordinates
        for (const [name, pos] of Object.entries(scene.pointRegistry)) {
          const p = projectPoint(pos, camera, w, h);
          const isOrigin = name === snapshot.originPoint;
          const isCustom =
            effectiveSnapshot.customPoints.some((cp) => cp.name === name) ||
            effectiveSnapshot.customXYZPoints.some((cp) => cp.name === name);
          const isSelectedConnect = pendingConnectFrom === name || draggingPointName === name;

          if (isSelectedConnect) {
            ctx.beginPath();
            ctx.arc(p.x, p.y, 11, 0, Math.PI * 2);
            ctx.fillStyle = 'rgba(251, 191, 36, 0.32)';
            ctx.fill();
          }

          ctx.beginPath();
          ctx.arc(p.x, p.y, isSelectedConnect ? 6.5 : isCustom || isOrigin ? 5 : 4, 0, Math.PI * 2);
          ctx.fillStyle = isSelectedConnect ? '#fbbf24' : isOrigin ? '#f43f5e' : isCustom ? '#fbbf24' : accentHex;
          ctx.fill();
          ctx.strokeStyle = '#ffffff';
          ctx.lineWidth = 1.5;
          ctx.stroke();

          if (showLabels) {
            const mCoord = worldToMath(pos, originWorld);
            const labelText = showCoordsOnLabels
              ? `${name}(${formatMathNum(mCoord.x)};${formatMathNum(mCoord.y)};${formatMathNum(mCoord.z)})`
              : name;
            ctx.font = '800 12.5px "Plus Jakarta Sans", sans-serif';
            ctx.fillStyle = isLight ? '#0f172a' : '#f8fafc';
            ctx.fillText(labelText, p.x + 8, p.y - 6);
          }
        }
      }

      ctx.restore();
    };

    render();
    window.addEventListener('resize', render);
    return () => window.removeEventListener('resize', render);
  }, [
    scene,
    effectiveSnapshot,
    camera,
    showAxes,
    showGrid,
    showDashed,
    showAltitude,
    showLabels,
    showCoordsOnLabels,
    accentHex,
    themeMode,
    snapshot.shapeKey,
    snapshot.originPoint,
    originWorld,
    occluderFaces,
    roundBodyHint,
    unfoldedFaces,
    showCircumSphere,
    circumSphere,
    showInSphere,
    inSphere,
    showSkewPerp,
    skewAnalysis,
    pendingConnectFrom,
    draggingPointName
  ]);

  // Hit-test 3D projected points on canvas using sensitivity.touchRadius
  const hitTestPoint3D = (clientX: number, clientY: number): string | null => {
    const canvas = canvasRef.current;
    if (!canvas) return null;
    const rect = canvas.getBoundingClientRect();
    const lx = clientX - rect.left;
    const ly = clientY - rect.top;
    const maxR = 24 * sensitivity.touchRadius;
    let bestName: string | null = null;
    let bestDist = maxR;
    for (const [name, pos] of Object.entries(scene.pointRegistry)) {
      const p = projectPoint(pos, camera, rect.width, rect.height);
      const d = Math.hypot(p.x - lx, p.y - ly);
      if (d < bestDist) {
        bestDist = d;
        bestName = name;
      }
    }
    return bestName;
  };

  // Pointer orbit, direct 3D vertex/point drag (Item 1), tap-to-connect (Item 2), & pinch zoom
  const handlePointerDown = (e: React.PointerEvent<HTMLCanvasElement>) => {
    e.currentTarget.setPointerCapture(e.pointerId);
    activePointersRef.current.set(e.pointerId, { x: e.clientX, y: e.clientY });
    if (autoRotate) setAutoRotate(false);

    if (activePointersRef.current.size === 2) {
      const pts = Array.from(activePointersRef.current.values());
      dragRef.current = {
        mode: 'none',
        pointName: null,
        x: e.clientX,
        y: e.clientY,
        pinchDist: Math.hypot(pts[1].x - pts[0].x, pts[1].y - pts[0].y)
      };
      return;
    }

    // Tap-2-points connect mode
    if (interactionMode.startsWith('connect_')) {
      const hit = hitTestPoint3D(e.clientX, e.clientY);
      if (hit) {
        if (!pendingConnectFrom) {
          setPendingConnectFrom(hit);
          showToast(`Đã chọn điểm đầu ${hit} — chạm điểm thứ 2 để nối`);
        } else if (pendingConnectFrom === hit) {
          setPendingConnectFrom(null);
        } else {
          const style: LineStyle3D =
            interactionMode === 'connect_solid'
              ? 'solid'
              : interactionMode === 'connect_vector'
                ? 'vector_solid'
                : 'dashed';
          handleAddLine(pendingConnectFrom, hit, style, lineColor);
          setPendingConnectFrom(null);
        }
        return;
      }
      // Fall through to orbit if user drags empty space
    }

    // Direct 3D vertex/point drag mode
    if (interactionMode === 'drag' && unfoldRatio <= 0.005) {
      const hit = hitTestPoint3D(e.clientX, e.clientY);
      if (hit) {
        setDraggingPointName(hit);
        dragRef.current = { mode: 'drag_point', pointName: hit, x: e.clientX, y: e.clientY, pinchDist: null };
        return;
      }
    }

    dragRef.current = { mode: 'orbit', pointName: null, x: e.clientX, y: e.clientY, pinchDist: null };
  };

  const handlePointerMove = (e: React.PointerEvent<HTMLCanvasElement>) => {
    if (!activePointersRef.current.has(e.pointerId)) return;
    activePointersRef.current.set(e.pointerId, { x: e.clientX, y: e.clientY });

    if (activePointersRef.current.size === 2) {
      const pts = Array.from(activePointersRef.current.values());
      const dist = Math.hypot(pts[1].x - pts[0].x, pts[1].y - pts[0].y);
      if (dragRef.current.pinchDist !== null && dist > 5) {
        const delta = dragRef.current.pinchDist - dist;
        setCamera((prev) => ({
          ...prev,
          radius: Math.min(26, Math.max(4.2, prev.radius + delta * 0.025 * sensitivity.zoom))
        }));
      }
      dragRef.current.pinchDist = dist;
      return;
    }

    const dx = e.clientX - dragRef.current.x;
    const dy = e.clientY - dragRef.current.y;
    dragRef.current.x = e.clientX;
    dragRef.current.y = e.clientY;

    // Direct 3D point dragging in camera view plane
    if (dragRef.current.mode === 'drag_point' && dragRef.current.pointName) {
      const name = dragRef.current.pointName;
      const moveScale = (camera.radius * 0.0032) * sensitivity.drag;
      // Camera right & up vectors in world space
      const right: Vec3 = { x: Math.cos(camera.theta), y: 0, z: -Math.sin(camera.theta) };
      const up: Vec3 = {
        x: -Math.cos(camera.phi) * Math.sin(camera.theta),
        y: Math.sin(camera.phi),
        z: -Math.cos(camera.phi) * Math.cos(camera.theta)
      };
      const deltaWorld: Vec3 = {
        x: (right.x * dx - up.x * dy) * moveScale,
        y: (right.y * dx - up.y * dy) * moveScale,
        z: (right.z * dx - up.z * dy) * moveScale
      };

      const edgePt = snapshot.customPoints.find((cp) => cp.name === name);
      if (edgePt) {
        const pFrom = fullScene.pointRegistry[edgePt.from];
        const pTo = fullScene.pointRegistry[edgePt.to];
        if (pFrom && pTo) {
          const seg = sub3(pTo, pFrom);
          const segLenSq = dot3(seg, seg);
          if (segLenSq > 1e-5) {
            const dRatio = dot3(deltaWorld, seg) / segLenSq;
            const nextRatio = Math.max(0.05, Math.min(0.95, edgePt.ratio + dRatio));
            commitSnapshot(
              (prev) => ({
                ...prev,
                customPoints: prev.customPoints.map((cp) => (cp.id === edgePt.id ? { ...cp, ratio: nextRatio } : cp))
              }),
              false
            );
          }
        }
        return;
      }

      const xyzPt = snapshot.customXYZPoints.find((xp) => xp.name === name);
      if (xyzPt) {
        commitSnapshot(
          (prev) => ({
            ...prev,
            customXYZPoints: prev.customXYZPoints.map((xp) =>
              xp.id === xyzPt.id
                ? {
                    ...xp,
                    x: +(xp.x + deltaWorld.x).toFixed(2),
                    y: +(xp.y + deltaWorld.z).toFixed(2),
                    z: +(xp.z + deltaWorld.y).toFixed(2)
                  }
                : xp
            )
          }),
          false
        );
        return;
      }

      const curPos = fullScene.pointRegistry[name];
      if (curPos) {
        const nextPos: Vec3 = {
          x: +(curPos.x + deltaWorld.x).toFixed(3),
          y: +(curPos.y + deltaWorld.y).toFixed(3),
          z: +(curPos.z + deltaWorld.z).toFixed(3)
        };
        commitSnapshot(
          (prev) => ({
            ...prev,
            vertexOverrides: { ...(prev.vertexOverrides || {}), [name]: nextPos }
          }),
          false
        );
      }
      return;
    }

    if (dragRef.current.mode !== 'orbit') return;
    setCamera((prev) => ({
      ...prev,
      theta: prev.theta - dx * 0.0085 * sensitivity.rotation,
      phi: Math.min(Math.PI - 0.12, Math.max(0.12, prev.phi - dy * 0.0085 * sensitivity.rotation))
    }));
  };

  const handlePointerUp = (e: React.PointerEvent<HTMLCanvasElement>) => {
    activePointersRef.current.delete(e.pointerId);
    if (activePointersRef.current.size < 2) dragRef.current.pinchDist = null;
    if (activePointersRef.current.size === 0) {
      dragRef.current.mode = 'none';
      dragRef.current.pointName = null;
      setDraggingPointName(null);
    }
  };

  const handleWheel = (e: React.WheelEvent<HTMLCanvasElement>) => {
    setCamera((prev) => ({
      ...prev,
      radius: Math.min(26, Math.max(4.2, prev.radius + e.deltaY * 0.009 * sensitivity.zoom))
    }));
  };

  const verifyExpected: number | null = useMemo(() => {
    const m = fullScene.metrics;
    switch (verifyTarget) {
      case 'vol': return m.vol;
      case 'stp': return m.sTp;
      case 'sday': return m.sDay;
      case 'h': return m.realH;
      case 'dist_ab': return vCalcA && vCalcB ? dist3(vCalcA, vCalcB) : null;
      case 'angle_lp': return linePlaneAnalysis ? linePlaneAnalysis.angleDegrees : null;
      case 'dist_ap': return linePlaneAnalysis ? linePlaneAnalysis.distA : null;
      case 'dyn_sec': return fullScene.dynamicSection ? fullScene.dynamicSection.area : null;
      case 'circum_r': return circumSphere ? circumSphere.radius : null;
      case 'skew_d': return skewAnalysis ? skewAnalysis.distance : null;
      case 'dihedral': return dihedralAnalysis ? dihedralAnalysis.angleDegrees : null;
    }
  }, [verifyTarget, fullScene, vCalcA, vCalcB, linePlaneAnalysis, circumSphere, skewAnalysis, dihedralAnalysis]);

  const handleAddEdgePoint = () => {
    const name = ptName.trim();
    if (!name) return showToast('Vui lòng nhập tên điểm.');
    if (ptFrom === ptTo) return showToast('Hai điểm đầu cuối phải khác nhau.');
    if (fullScene.pointRegistry[name]) return showToast(`Điểm ${name} đã tồn tại.`);
    commitSnapshot((prev) => ({
      ...prev,
      customPoints: [...prev.customPoints, { id: `ep_${Date.now()}`, name, from: ptFrom, to: ptTo, ratio: ptRatio }]
    }));
    showToast(`Đã thêm điểm ${name} trên đoạn ${ptFrom}${ptTo}`);
  };

  const handleAddLine = (p1 = lineP1, p2 = lineP2, style = lineStyle, color = lineColor) => {
    if (p1 === p2) return showToast('Hai điểm phải khác nhau.');
    commitSnapshot((prev) => ({
      ...prev,
      customLines: [...prev.customLines, { id: `ln_${Date.now()}`, p1, p2, style, color }]
    }));
    showToast(`Đã kẻ ${style.startsWith('vector') ? 'vectơ' : 'đoạn'} ${p1}${p2}`);
  };

  const handleAddPlane = () => {
    const pts = [pl1, pl2, pl3, ...(pl4 ? [pl4] : [])];
    if (new Set(pts).size < 3) return showToast('Cần chọn ít nhất 3 điểm phân biệt.');
    commitSnapshot((prev) => ({
      ...prev,
      customPlanes: [...prev.customPlanes, { id: `pl_${Date.now()}`, pts, color: '#10b981' }]
    }));
    showToast(`Đã tô mặt phẳng (${pts.join('')})`);
  };

  const handleCutSection = () => {
    if (new Set([sec1, sec2, sec3]).size < 3) return showToast('Chọn 3 điểm phân biệt để cắt thiết diện.');
    commitSnapshot((prev) => ({
      ...prev,
      dynamicSection: { p1: sec1, p2: sec2, p3: sec3 }
    }));
    showToast(`Đã cắt thiết diện động (${sec1}${sec2}${sec3})`);
  };

  const handleAddXYZ = () => {
    const name = xyzName.trim();
    if (!name) return showToast('Nhập tên điểm.');
    if (fullScene.pointRegistry[name]) return showToast(`Điểm ${name} đã tồn tại.`);
    const x = parseFloat(xyzX.replace(',', '.')) || 0;
    const y = parseFloat(xyzY.replace(',', '.')) || 0;
    const z = parseFloat(xyzZ.replace(',', '.')) || 0;
    commitSnapshot((prev) => ({
      ...prev,
      customXYZPoints: [...prev.customXYZPoints, { id: `xyz_${Date.now()}`, name, x, y, z }]
    }));
    showToast(`Đã chấm điểm ${name}(${x}; ${y}; ${z})`);
  };

  const handleSaveSlot = () => {
    const label = slotNameInput.trim() || `${currentSpec.title} (${new Date().toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })})`;
    const item: SavedSlot3D = {
      id: `slot_${Date.now()}`,
      name: label,
      savedAt: new Date().toLocaleString('vi-VN'),
      snapshot
    };
    setSavedSlots((prev) => [item, ...prev.slice(0, 14)]);
    setSlotNameInput('');
    showToast(`Đã lưu bài vẽ "${label}"`);
  };

  const getBaseFilename = () => {
    const cleaned = exportFilename.trim().replace(/[^a-zA-Z0-9_-]/g, '_');
    return cleaned || `tooltoan-3d-${snapshot.shapeKey}`;
  };

  const handleExportPng = () => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    let dataUrl: string;
    if (pngTransparent) {
      dataUrl = canvas.toDataURL('image/png');
    } else {
      const off = document.createElement('canvas');
      off.width = canvas.width;
      off.height = canvas.height;
      const octx = off.getContext('2d');
      if (octx) {
        octx.fillStyle = themeMode === 'light' ? '#f0f6ff' : '#060d1c';
        octx.fillRect(0, 0, off.width, off.height);
        octx.drawImage(canvas, 0, 0);
      }
      dataUrl = off.toDataURL('image/png');
    }
    const a = document.createElement('a');
    a.href = dataUrl;
    a.download = `${getBaseFilename()}.png`;
    a.click();
    setExportOpen(false);
  };

  const handleExportSvg = () => {
    downloadFile(`${getBaseFilename()}.svg`, exportSceneToSvg(fullScene, snapshot, camera, accentHex), 'image/svg+xml;charset=utf-8');
    setExportOpen(false);
  };

  const handleExportObj = () => {
    downloadFile(`${getBaseFilename()}.obj`, exportSceneToObj(fullScene, currentSpec.title), 'text/plain;charset=utf-8');
    setExportOpen(false);
  };

  const handleExportJson = () => {
    downloadFile(`${getBaseFilename()}.json`, JSON.stringify({ snapshot, camera }, null, 2), 'application/json;charset=utf-8');
    setExportOpen(false);
  };

  const handleCopyShareLink = async () => {
    try {
      const url = buildShareUrl3D(snapshot);
      await navigator.clipboard.writeText(url);
      showToast('Đã sao chép liên kết chia sẻ mô hình 3D!');
    } catch {
      showToast('Không thể sao chép liên kết.');
    }
  };

  const handleImportJson = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      try {
        const parsed = JSON.parse(String(reader.result));
        const loaded: Scene3DSnapshot = parsed.snapshot ? { ...DEFAULT_SNAPSHOT, ...parsed.snapshot } : { ...DEFAULT_SNAPSHOT, ...parsed };
        commitSnapshot(() => loaded);
        if (parsed.camera) setCamera(parsed.camera);
        showToast('Đã nhập bài vẽ 3D từ file JSON.');
      } catch {
        showToast('File JSON không hợp lệ.');
      }
    };
    reader.readAsText(file);
    e.target.value = '';
    setExportOpen(false);
  };

  const { metrics } = fullScene;
  const hasVertexOverrides = Boolean(snapshot.vertexOverrides && Object.keys(snapshot.vertexOverrides).length > 0);
  const canUnfold = !currentSpec.isRound;

  return (
    <div style={{ position: 'relative', width: '100%', height: '100%', overflow: 'hidden' }}>
      <canvas
        ref={canvasRef}
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={handlePointerUp}
        onPointerCancel={handlePointerUp}
        onWheel={handleWheel}
        style={{
          width: '100%',
          height: '100%',
          display: 'block',
          touchAction: 'none',
          cursor: interactionMode === 'view' ? 'grab' : interactionMode === 'drag' ? 'move' : 'crosshair'
        }}
      />

      {/* Top Header */}
      {!isImmersive && (
        <header
          style={{
            position: 'absolute',
            top: 10,
            left: 10,
            right: 10,
            display: 'flex',
            flexWrap: 'wrap',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 6,
            pointerEvents: 'none',
            zIndex: 15
          }}
        >
          <div
            className="glass-surface"
            style={{
              pointerEvents: 'auto',
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              padding: '6px 12px',
              borderRadius: 999
            }}
          >
            <Shapes size={17} color={accentHex} />
            <div>
              <div style={{ fontSize: 12.5, fontWeight: 800, lineHeight: 1.15 }}>{currentSpec.title}</div>
              <div style={{ fontSize: 10.5, color: 'var(--text-secondary)' }}>{currentSpec.subtitle}</div>
            </div>
          </div>

          <div
            className="glass-surface"
            style={{
              pointerEvents: 'auto',
              display: 'flex',
              alignItems: 'center',
              gap: 3,
              padding: 4,
              borderRadius: 999
            }}
          >
            <button
              type="button"
              onClick={() => setDrawerOpen((v) => !v)}
              className={`chip-btn glass-press${drawerOpen ? ' active' : ''}`}
              style={{ minHeight: 34, padding: '4px 11px' }}
            >
              <SlidersHorizontal size={14} />
              <span>Công cụ 3D</span>
            </button>

            <button
              type="button"
              onClick={handleUndo}
              disabled={undoStack.length === 0}
              className="icon-btn glass-press"
              style={{ width: 34, height: 34, opacity: undoStack.length ? 1 : 0.35 }}
              title="Hoàn tác (Ctrl+Z)"
              aria-label="Hoàn tác"
            >
              <Undo2 size={15} />
            </button>
            <button
              type="button"
              onClick={handleRedo}
              disabled={redoStack.length === 0}
              className="icon-btn glass-press"
              style={{ width: 34, height: 34, opacity: redoStack.length ? 1 : 0.35 }}
              title="Làm lại (Ctrl+Y)"
              aria-label="Làm lại"
            >
              <Redo2 size={15} />
            </button>

            <button
              type="button"
              onClick={() => setAutoRotate((v) => !v)}
              className="icon-btn glass-press"
              style={{ width: 34, height: 34 }}
              title="Tự xoay 360° (Space)"
              aria-label="Tự xoay"
            >
              {autoRotate ? <Pause size={15} color={accentHex} /> : <Play size={15} />}
            </button>

            <button
              type="button"
              onClick={() => setSensOpen((v) => !v)}
              className="icon-btn glass-press"
              style={{ width: 34, height: 34 }}
              title="Độ nhạy xoay / kéo / chạm 3D"
              aria-label="Độ nhạy 3D"
            >
              <Gauge size={15} color={sensOpen ? accentHex : undefined} />
            </button>

            <button
              type="button"
              onClick={() => setShowAxes((v) => !v)}
              className="icon-btn glass-press"
              style={{ width: 34, height: 34 }}
              title="Bật/tắt hệ trục Oxyz"
              aria-label="Oxyz"
            >
              <Compass size={15} color={showAxes ? accentHex : undefined} />
            </button>

            <button
              type="button"
              onClick={() => setExportOpen((v) => !v)}
              className="icon-btn glass-press"
              style={{ width: 34, height: 34 }}
              title="Lưu, Chia sẻ Link & Xuất PNG / SVG / OBJ / JSON"
              aria-label="Lưu và xuất file"
            >
              <Download size={15} />
            </button>

            <button
              type="button"
              onClick={onToggleImmersive}
              className="icon-btn glass-press"
              style={{ width: 34, height: 34 }}
              title="Toàn màn hình (F)"
              aria-label="Toàn màn hình"
            >
              <Maximize2 size={15} />
            </button>
          </div>
        </header>
      )}

      {/* Direct Canvas Interaction Mode Strip (Items 1 & 2) */}
      {!isImmersive && (
        <div
          className="glass-surface"
          style={{
            position: 'absolute',
            top: 62,
            left: 10,
            display: 'inline-flex',
            alignItems: 'center',
            gap: 4,
            padding: '4px 6px',
            borderRadius: 999,
            zIndex: 15,
            maxWidth: 'calc(100vw - 20px)',
            overflowX: 'auto'
          }}
        >
          {[
            { id: 'view' as const, label: 'Xoay 3D', icon: <Hand size={12} /> },
            { id: 'drag' as const, label: 'Kéo điểm', icon: <Move size={12} /> },
            { id: 'connect_dashed' as const, label: 'Nối nét đứt', icon: <MousePointerClick size={12} /> },
            { id: 'connect_solid' as const, label: 'Nối nét liền', icon: <Pencil size={12} /> },
            { id: 'connect_vector' as const, label: 'Nối vectơ →', icon: <Pencil size={12} /> }
          ].map((m) => (
            <button
              key={m.id}
              type="button"
              onClick={() => {
                setInteractionMode(m.id);
                setPendingConnectFrom(null);
              }}
              className={`chip-btn glass-press${interactionMode === m.id ? ' active' : ''}`}
              style={{ minHeight: 28, padding: '2px 9px', fontSize: 11, flexShrink: 0 }}
            >
              {m.icon}
              <span>{m.label}</span>
            </button>
          ))}
          {hasVertexOverrides && (
            <button
              type="button"
              className="chip-btn"
              style={{ minHeight: 28, padding: '2px 9px', fontSize: 11, color: '#fbbf24', flexShrink: 0 }}
              onClick={() => {
                commitSnapshot((p) => ({ ...p, vertexOverrides: {} }));
                showToast('Đã khôi phục toàn bộ đỉnh gốc.');
              }}
            >
              Khôi phục đỉnh
            </button>
          )}
        </div>
      )}

      {/* 3D Sensitivity Popover (Item 4) */}
      {sensOpen && !isImmersive && (
        <div
          className="glass-surface"
          style={{
            position: 'absolute',
            top: 62,
            right: 54,
            width: 'min(265px, calc(100vw - 20px))',
            borderRadius: 18,
            padding: 13,
            display: 'flex',
            flexDirection: 'column',
            gap: 9,
            zIndex: 27
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontSize: 12.5, fontWeight: 800 }}>Độ nhạy tương tác 3D</span>
            <button type="button" className="icon-btn" style={{ width: 24, height: 24 }} onClick={() => setSensOpen(false)}>
              <X size={13} />
            </button>
          </div>
          {[
            { key: 'rotation' as const, label: 'Độ nhạy xoay', min: 0.4, max: 2.0 },
            { key: 'zoom' as const, label: 'Độ nhạy zoom', min: 0.4, max: 2.0 },
            { key: 'drag' as const, label: 'Độ nhạy kéo đỉnh', min: 0.4, max: 2.0 },
            { key: 'touchRadius' as const, label: 'Vùng bắt điểm', min: 0.6, max: 2.0 }
          ].map((item) => (
            <label key={item.key} style={{ display: 'flex', flexDirection: 'column', gap: 3, fontSize: 11.5 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span>{item.label}</span>
                <b className="mono">{sensitivity[item.key].toFixed(2)}×</b>
              </div>
              <input
                type="range"
                className="glass-slider"
                min={item.min}
                max={item.max}
                step={0.05}
                value={sensitivity[item.key]}
                onChange={(e) =>
                  onUpdateSensitivity({ ...sensitivity, [item.key]: parseFloat(e.target.value) })
                }
              />
            </label>
          ))}
          <button
            type="button"
            className="chip-btn"
            style={{ minHeight: 28, fontSize: 11 }}
            onClick={() => onUpdateSensitivity({ rotation: 1, zoom: 1, drag: 1, touchRadius: 1 })}
          >
            Đặt lại mặc định (1.0×)
          </button>
        </div>
      )}

      {isImmersive && (
        <button
          type="button"
          onClick={onToggleImmersive}
          className="glass-surface icon-btn glass-press"
          style={{ position: 'absolute', top: 14, right: 14, zIndex: 20 }}
          aria-label="Thoát toàn màn hình"
        >
          <Minimize2 size={18} />
        </button>
      )}

      {/* Replay Step Bar */}
      {replayActive && !isImmersive && (
        <div
          className="glass-surface"
          style={{
            position: 'absolute',
            top: 108,
            left: '50%',
            transform: 'translateX(-50%)',
            padding: '6px 12px',
            borderRadius: 999,
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            zIndex: 24
          }}
        >
          <span className="mono" style={{ fontSize: 12, fontWeight: 700, color: accentHex }}>
            Bước {replayStep}/{totalCustomSteps}
          </span>
          <button type="button" className="icon-btn" style={{ width: 28, height: 28 }} onClick={() => setReplayStep((s) => Math.max(0, s - 1))}>
            <SkipBack size={14} />
          </button>
          <button type="button" className="icon-btn" style={{ width: 28, height: 28 }} onClick={() => setReplayPlaying((v) => !v)}>
            {replayPlaying ? <Pause size={14} /> : <Play size={14} />}
          </button>
          <button type="button" className="icon-btn" style={{ width: 28, height: 28 }} onClick={() => setReplayStep((s) => Math.min(totalCustomSteps, s + 1))}>
            <SkipForward size={14} />
          </button>
          <button
            type="button"
            className="chip-btn"
            style={{ minHeight: 26, padding: '2px 9px', fontSize: 11 }}
            onClick={() => {
              setReplayActive(false);
              setReplayPlaying(false);
            }}
          >
            Thoát
          </button>
        </div>
      )}

      {/* Camera Presets & Live Metrics Pill */}
      {!isImmersive && (
        <div
          style={{
            position: 'absolute',
            left: 10,
            right: 10,
            bottom: 82,
            display: 'flex',
            flexWrap: 'wrap',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 6,
            pointerEvents: 'none',
            zIndex: 14
          }}
        >
          <div
            className="glass-surface"
            style={{ pointerEvents: 'auto', display: 'inline-flex', alignItems: 'center', gap: 3, padding: 4, borderRadius: 999 }}
          >
            {[
              { label: '3D', cam: DEFAULT_CAMERA },
              { label: 'Trước', cam: { ...DEFAULT_CAMERA, theta: 0, phi: Math.PI / 2 } },
              { label: 'Trên', cam: { ...DEFAULT_CAMERA, theta: 0.01, phi: 0.14 } },
              { label: 'Bên', cam: { ...DEFAULT_CAMERA, theta: Math.PI / 2, phi: Math.PI / 2 } }
            ].map((p) => (
              <button
                key={p.label}
                type="button"
                onClick={() => setCamera(p.cam)}
                className="chip-btn glass-press"
                style={{ minHeight: 28, padding: '3px 9px', fontSize: 11 }}
              >
                {p.label}
              </button>
            ))}
            <button
              type="button"
              onClick={() => setCamera(DEFAULT_CAMERA)}
              className="icon-btn glass-press"
              style={{ width: 28, height: 28 }}
              title="Đặt lại góc nhìn"
            >
              <RotateCcw size={13} />
            </button>
          </div>

          <div
            className="glass-surface"
            style={{
              pointerEvents: 'auto',
              display: 'inline-flex',
              alignItems: 'center',
              gap: 10,
              padding: '6px 12px',
              borderRadius: 999,
              fontSize: 11.5
            }}
          >
            <span className="mono">h=<b>{hideAnswers ? '???' : metrics.realH.toFixed(2)}</b></span>
            <span className="mono">Sđ=<b>{hideAnswers ? '???' : metrics.sDay.toFixed(2)}</b></span>
            <span className="mono">Stp=<b>{hideAnswers ? '???' : metrics.sTp.toFixed(2)}</b></span>
            <span className="mono" style={{ color: accentHex }}>V=<b>{hideAnswers ? '???' : metrics.vol.toFixed(2)}</b></span>
            <button
              type="button"
              onClick={() => setHideAnswers((v) => !v)}
              className="icon-btn glass-press"
              style={{ width: 26, height: 26 }}
              title={hideAnswers ? 'Hiện đáp án' : 'Ẩn đáp án để tự giải'}
            >
              {hideAnswers ? <EyeOff size={14} color="#fbbf24" /> : <Eye size={14} />}
            </button>
          </div>
        </div>
      )}

      {toast && (
        <div
          className="glass-surface"
          style={{
            position: 'absolute',
            top: 108,
            left: '50%',
            transform: 'translateX(-50%)',
            padding: '7px 14px',
            borderRadius: 999,
            fontSize: 12,
            fontWeight: 700,
            zIndex: 28
          }}
        >
          {toast}
        </div>
      )}

      {/* Save / Share Link / Export / Saved Slots Modal (Items 5 & 21) */}
      {exportOpen && (
        <div
          className="glass-surface scroll-area"
          style={{
            position: 'absolute',
            top: 62,
            right: 10,
            maxHeight: 'calc(100% - 160px)',
            width: 'min(315px, calc(100vw - 20px))',
            borderRadius: 20,
            padding: 14,
            display: 'flex',
            flexDirection: 'column',
            gap: 10,
            zIndex: 26
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontSize: 13, fontWeight: 800 }}>Lưu, Chia sẻ & Xuất 3D</span>
            <button type="button" className="icon-btn" style={{ width: 26, height: 26 }} onClick={() => setExportOpen(false)}>
              <X size={14} />
            </button>
          </div>

          <button type="button" className="primary-btn" style={{ minHeight: 38, fontSize: 12.5 }} onClick={handleCopyShareLink}>
            <Link2 size={14} />
            <span>Sao chép Link chia sẻ 3D</span>
          </button>

          <input
            type="text"
            className="glass-input"
            style={{ padding: '7px 9px', fontSize: 12 }}
            placeholder={`Tên file xuất (mặc định: tooltoan-3d-${snapshot.shapeKey})`}
            value={exportFilename}
            onChange={(e) => setExportFilename(e.target.value)}
          />

          <button
            type="button"
            className={`chip-btn${pngTransparent ? ' active' : ''}`}
            style={{ minHeight: 30, fontSize: 11.5 }}
            onClick={() => setPngTransparent((v) => !v)}
          >
            {pngTransparent ? '✓ Xuất PNG nền trong suốt (chèn slide/Word)' : 'Xuất PNG có màu nền hiện tại'}
          </button>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            <button type="button" className="chip-btn glass-press" onClick={handleExportPng}>Tải ảnh .PNG sắc nét</button>
            <button type="button" className="chip-btn glass-press" onClick={handleExportSvg}>Tải vector .SVG (Word / LaTeX)</button>
            <button type="button" className="chip-btn glass-press" onClick={handleExportObj}>Tải khối 3D .OBJ (Blender / In 3D)</button>
            <button type="button" className="chip-btn glass-press" onClick={handleExportJson}>Xuất tệp dự án .JSON</button>
            <button type="button" className="chip-btn glass-press" onClick={() => fileInputRef.current?.click()}>
              <Upload size={14} />
              <span>Mở tệp dự án .JSON</span>
            </button>
            <input ref={fileInputRef} type="file" accept=".json" style={{ display: 'none' }} onChange={handleImportJson} />
          </div>

          <hr style={{ border: 'none', borderTop: '1px solid var(--border-subtle)', margin: 0 }} />

          <div style={{ fontSize: 12, fontWeight: 800 }}>Kho bài vẽ đã lưu ({savedSlots.length})</div>
          <div style={{ display: 'flex', gap: 6 }}>
            <input
              type="text"
              className="glass-input"
              style={{ padding: '7px 9px', fontSize: 12 }}
              placeholder="Tên bài vẽ..."
              value={slotNameInput}
              onChange={(e) => setSlotNameInput(e.target.value)}
            />
            <button type="button" className="primary-btn" style={{ minHeight: 34, padding: '0 10px' }} onClick={handleSaveSlot}>
              <Save size={14} />
            </button>
          </div>

          {savedSlots.map((slot) => (
            <div
              key={slot.id}
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                gap: 6,
                padding: '7px 9px',
                borderRadius: 12,
                background: 'rgba(148,163,184,0.1)',
                border: '1px solid var(--border-subtle)'
              }}
            >
              <button
                type="button"
                onClick={() => {
                  commitSnapshot(() => slot.snapshot);
                  setExportOpen(false);
                  showToast(`Đã mở "${slot.name}"`);
                }}
                style={{ flex: 1, textAlign: 'left', background: 'none', border: 'none', color: 'var(--text-primary)', cursor: 'pointer' }}
              >
                <div style={{ fontSize: 12, fontWeight: 700, display: 'flex', alignItems: 'center', gap: 5 }}>
                  <FolderOpen size={13} color={accentHex} />
                  <span>{slot.name}</span>
                </div>
                <div style={{ fontSize: 10, color: 'var(--text-secondary)' }}>{slot.savedAt}</div>
              </button>
              <button
                type="button"
                className="icon-btn"
                style={{ width: 26, height: 26 }}
                onClick={() => setSavedSlots((prev) => prev.filter((s) => s.id !== slot.id))}
              >
                <Trash2 size={13} color="#f43f5e" />
              </button>
            </div>
          ))}
        </div>
      )}

      {/* Full 4-Tab 3D Tool Drawer */}
      {drawerOpen && !isImmersive && (
        <aside
          className="glass-surface"
          style={{
            position: 'absolute',
            top: 108,
            right: 10,
            bottom: 132,
            width: 'min(380px, calc(100vw - 20px))',
            borderRadius: 22,
            display: 'flex',
            flexDirection: 'column',
            zIndex: 22
          }}
        >
          {/* 4 Drawer Tabs */}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              padding: '10px 10px 8px 10px',
              borderBottom: '1px solid var(--border-subtle)',
              gap: 4
            }}
          >
            <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
              {[
                { id: 'shapes' as const, label: 'Hình 3D' },
                { id: 'draw' as const, label: 'Kẻ hình' },
                { id: 'coords' as const, label: 'Oxyz' },
                { id: 'formulas' as const, label: 'Công thức' }
              ].map((t) => (
                <button
                  key={t.id}
                  type="button"
                  onClick={() => setActiveTab(t.id)}
                  className={`chip-btn glass-press${activeTab === t.id ? ' active' : ''}`}
                  style={{ minHeight: 30, padding: '4px 10px', fontSize: 11.5 }}
                >
                  {t.label}
                </button>
              ))}
            </div>
            <button type="button" className="icon-btn" style={{ width: 28, height: 28 }} onClick={() => setDrawerOpen(false)}>
              <X size={15} />
            </button>
          </div>

          <div className="scroll-area" style={{ flex: 1, padding: 14, display: 'flex', flexDirection: 'column', gap: 12 }}>
            {/* TAB 1: SHAPES, SPHERES, UNFOLDING & DISPLAY */}
            {activeTab === 'shapes' && (
              <>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 9 }}>
                  <label style={{ fontSize: 12, display: 'flex', flexDirection: 'column', gap: 4 }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                      <span>{currentSpec.isRound ? 'Bán kính R (theo a)' : 'Cạnh đáy a'}</span>
                      <b className="mono">{snapshot.a.toFixed(1)}</b>
                    </div>
                    <input
                      type="range"
                      className="glass-slider"
                      min={1}
                      max={8}
                      step={0.1}
                      value={snapshot.a}
                      onChange={(e) => commitSnapshot((p) => ({ ...p, a: parseFloat(e.target.value) }), false)}
                    />
                  </label>

                  {currentSpec.hasB && (
                    <label style={{ fontSize: 12, display: 'flex', flexDirection: 'column', gap: 4 }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                        <span>Cạnh b</span>
                        <b className="mono">{snapshot.b.toFixed(1)}</b>
                      </div>
                      <input
                        type="range"
                        className="glass-slider"
                        min={1}
                        max={8}
                        step={0.1}
                        value={snapshot.b}
                        onChange={(e) => commitSnapshot((p) => ({ ...p, b: parseFloat(e.target.value) }), false)}
                      />
                    </label>
                  )}

                  {currentSpec.hasH !== false && (
                    <label style={{ fontSize: 12, display: 'flex', flexDirection: 'column', gap: 4 }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                        <span>Chiều cao h</span>
                        <b className="mono">{snapshot.h.toFixed(1)}</b>
                      </div>
                      <input
                        type="range"
                        className="glass-slider"
                        min={1}
                        max={8}
                        step={0.1}
                        value={snapshot.h}
                        onChange={(e) => commitSnapshot((p) => ({ ...p, h: parseFloat(e.target.value) }), false)}
                      />
                    </label>
                  )}

                  {/* Polyhedron Net Unfolding Slider (Item 19) */}
                  {canUnfold && (
                    <label style={{ fontSize: 12, display: 'flex', flexDirection: 'column', gap: 4, padding: '8px 10px', borderRadius: 12, background: 'rgba(148,163,184,0.08)', border: '1px solid var(--border-subtle)' }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                        <span style={{ fontWeight: 700, color: accentHex }}>Trải phẳng đa diện (Net)</span>
                        <b className="mono">{Math.round(unfoldRatio * 100)}%</b>
                      </div>
                      <input
                        type="range"
                        className="glass-slider"
                        min={0}
                        max={1}
                        step={0.01}
                        value={unfoldRatio}
                        onChange={(e) => setUnfoldRatio(parseFloat(e.target.value))}
                      />
                    </label>
                  )}
                </div>

                {/* Display + Sphere Toggles (Item 19) */}
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                  {[
                    { label: 'Nét khuất 3D', val: showDashed, set: setShowDashed },
                    { label: 'Đường cao', val: showAltitude, set: setShowAltitude },
                    { label: 'Nhãn điểm', val: showLabels, set: setShowLabels },
                    { label: 'Lưới nền', val: showGrid, set: setShowGrid },
                    { label: 'Hệ trục Oxyz', val: showAxes, set: setShowAxes },
                    { label: 'Tọa độ nhãn', val: showCoordsOnLabels, set: setShowCoordsOnLabels },
                    { label: 'Mặt cầu ngoại tiếp', val: showCircumSphere, set: setShowCircumSphere },
                    { label: 'Mặt cầu nội tiếp', val: showInSphere, set: setShowInSphere }
                  ].map((opt) => (
                    <button
                      key={opt.label}
                      type="button"
                      className={`chip-btn${opt.val ? ' active' : ''}`}
                      style={{ minHeight: 28, padding: '3px 9px', fontSize: 11 }}
                      onClick={() => opt.set(!opt.val)}
                    >
                      {opt.label}
                    </button>
                  ))}
                </div>

                {/* Live Circumscribed / Inscribed Sphere Card (Item 19) */}
                {(showCircumSphere || showInSphere) && (
                  <div style={{ padding: 10, borderRadius: 14, border: '1px solid rgba(56,189,248,0.45)', background: 'rgba(56,189,248,0.09)', display: 'flex', flexDirection: 'column', gap: 6 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 11.5, fontWeight: 800, color: accentHex }}>
                      <Globe size={13} />
                      <span>THÔNG SỐ MẶT CẦU NỘI / NGOẠI TIẾP</span>
                    </div>
                    {showCircumSphere && circumSphere && (
                      <div className="mono" style={{ fontSize: 11.2, padding: 8, borderRadius: 10, background: 'rgba(2,6,23,0.42)', display: 'flex', flexDirection: 'column', gap: 3 }}>
                        <div style={{ fontWeight: 800, color: '#38bdf8' }}>Mặt cầu ngoại tiếp ({circumSphere.formulaHint})</div>
                        <div>Tâm I = {formatCoord3D(circumSphere.centerMath)}</div>
                        <div>R = <b>{hideAnswers ? '???' : circumSphere.radius.toFixed(3)}</b> • S = <b>{hideAnswers ? '???' : circumSphere.surfaceArea.toFixed(2)}</b> • V = <b>{hideAnswers ? '???' : circumSphere.volume.toFixed(2)}</b></div>
                      </div>
                    )}
                    {showInSphere && inSphere && (
                      <div className="mono" style={{ fontSize: 11.2, padding: 8, borderRadius: 10, background: 'rgba(2,6,23,0.42)', display: 'flex', flexDirection: 'column', gap: 3 }}>
                        <div style={{ fontWeight: 800, color: '#10b981' }}>Mặt cầu nội tiếp ({inSphere.formulaHint})</div>
                        <div>Tâm I = {formatCoord3D(inSphere.centerMath)}</div>
                        <div>r = <b>{hideAnswers ? '???' : inSphere.radius.toFixed(3)}</b> • S = <b>{hideAnswers ? '???' : inSphere.surfaceArea.toFixed(2)}</b> • V = <b>{hideAnswers ? '???' : inSphere.volume.toFixed(2)}</b></div>
                      </div>
                    )}
                  </div>
                )}

                <hr style={{ border: 'none', borderTop: '1px solid var(--border-subtle)', margin: 0 }} />

                {/* 15 Shapes with Mini SVG Wireframe Thumbnails (Item 6) */}
                {SHAPE_CATEGORIES.map((cat) => (
                  <div key={cat.id} style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                    <div style={{ fontSize: 11.5, fontWeight: 800, color: 'var(--text-secondary)' }}>{cat.title}</div>
                    {ALL_3D_SHAPES.filter((s) => s.category === cat.id).map((s) => {
                      const selected = s.key === snapshot.shapeKey;
                      const thumbEdges = shapeThumbnails[s.key] || [];
                      return (
                        <button
                          key={s.key}
                          type="button"
                          onClick={() => {
                            setUnfoldRatio(0);
                            commitSnapshot((p) => ({
                              ...p,
                              shapeKey: s.key,
                              customPoints: [],
                              customXYZPoints: [],
                              customLines: [],
                              customPlanes: [],
                              dynamicSection: null,
                              vertexOverrides: {}
                            }));
                          }}
                          className="glass-press"
                          style={{
                            textAlign: 'left',
                            padding: '7px 10px',
                            borderRadius: 13,
                            cursor: 'pointer',
                            border: `1px solid ${selected ? accentHex : 'var(--border-subtle)'}`,
                            background: selected ? `${accentHex}22` : 'rgba(148,163,184,0.08)',
                            color: 'var(--text-primary)',
                            display: 'flex',
                            alignItems: 'center',
                            gap: 10
                          }}
                        >
                          <svg
                            width={40}
                            height={40}
                            viewBox="0 0 40 40"
                            style={{
                              flexShrink: 0,
                              borderRadius: 9,
                              background: 'rgba(2,6,23,0.34)',
                              border: `1px solid ${selected ? `${accentHex}88` : 'var(--border-subtle)'}`
                            }}
                          >
                            {thumbEdges.map((e, idx) => (
                              <line
                                key={idx}
                                x1={e.x1}
                                y1={e.y1}
                                x2={e.x2}
                                y2={e.y2}
                                stroke={selected ? accentHex : 'currentColor'}
                                strokeOpacity={selected ? 0.95 : 0.72}
                                strokeWidth={1.15}
                                strokeLinecap="round"
                              />
                            ))}
                          </svg>
                          <div style={{ minWidth: 0 }}>
                            <div style={{ fontSize: 12.5, fontWeight: 700 }}>{s.title}</div>
                            <div style={{ fontSize: 11, color: 'var(--text-secondary)' }}>{s.subtitle}</div>
                          </div>
                        </button>
                      );
                    })}
                  </div>
                ))}
              </>
            )}

            {/* TAB 2: DRAW, DYNAMIC SECTION & REPLAY */}
            {activeTab === 'draw' && (
              <>
                <div style={{ padding: 11, borderRadius: 15, border: '1px solid var(--border-subtle)', background: 'rgba(148,163,184,0.07)', display: 'flex', flexDirection: 'column', gap: 8 }}>
                  <div style={{ fontSize: 11.5, fontWeight: 800, color: '#fbbf24' }}>1. LẤY ĐIỂM TRÊN ĐOẠN THẲNG</div>
                  <div style={{ display: 'grid', gridTemplateColumns: '0.8fr 1fr 1fr', gap: 6 }}>
                    <input className="glass-input" style={{ padding: '7px 8px', fontSize: 12 }} value={ptName} maxLength={3} onChange={(e) => setPtName(e.target.value)} placeholder="Tên" />
                    <select className="glass-select" style={{ padding: '7px 8px', fontSize: 12 }} value={ptFrom} onChange={(e) => setPtFrom(e.target.value)}>
                      {pointNames.map((n) => <option key={n} value={n} style={{ color: '#0f172a' }}>{n}</option>)}
                    </select>
                    <select className="glass-select" style={{ padding: '7px 8px', fontSize: 12 }} value={ptTo} onChange={(e) => setPtTo(e.target.value)}>
                      {pointNames.map((n) => <option key={n} value={n} style={{ color: '#0f172a' }}>{n}</option>)}
                    </select>
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11.5 }}>
                    <span>Tỉ lệ vị trí (k):</span>
                    <b className="mono">{ptRatio.toFixed(2)}</b>
                  </div>
                  <input type="range" className="glass-slider" min={0.05} max={0.95} step={0.01} value={ptRatio} onChange={(e) => setPtRatio(parseFloat(e.target.value))} />
                  <div style={{ display: 'flex', gap: 5 }}>
                    <button type="button" className="chip-btn" style={{ flex: 1, minHeight: 28, fontSize: 11 }} onClick={() => setPtRatio(1 / 3)}>k = 1/3</button>
                    <button type="button" className="chip-btn" style={{ flex: 1, minHeight: 28, fontSize: 11 }} onClick={() => setPtRatio(0.5)}>Trung điểm</button>
                    <button type="button" className="chip-btn" style={{ flex: 1, minHeight: 28, fontSize: 11 }} onClick={() => setPtRatio(2 / 3)}>k = 2/3</button>
                  </div>
                  <button type="button" className="primary-btn" style={{ minHeight: 36, fontSize: 12 }} onClick={handleAddEdgePoint}>
                    + Thêm điểm mới
                  </button>
                </div>

                <div style={{ padding: 11, borderRadius: 15, border: '1px solid var(--border-subtle)', background: 'rgba(148,163,184,0.07)', display: 'flex', flexDirection: 'column', gap: 8 }}>
                  <div style={{ fontSize: 11.5, fontWeight: 800, color: accentHex }}>2. KẺ ĐOẠN THẲNG / VECTƠ</div>
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 6 }}>
                    <select className="glass-select" style={{ padding: '7px 8px', fontSize: 12 }} value={lineP1} onChange={(e) => setLineP1(e.target.value)}>
                      {pointNames.map((n) => <option key={n} value={n} style={{ color: '#0f172a' }}>Đầu: {n}</option>)}
                    </select>
                    <select className="glass-select" style={{ padding: '7px 8px', fontSize: 12 }} value={lineP2} onChange={(e) => setLineP2(e.target.value)}>
                      {pointNames.map((n) => <option key={n} value={n} style={{ color: '#0f172a' }}>Cuối: {n}</option>)}
                    </select>
                  </div>
                  <div style={{ display: 'grid', gridTemplateColumns: '1.2fr 1fr', gap: 6 }}>
                    <select className="glass-select" style={{ padding: '7px 8px', fontSize: 12 }} value={lineStyle} onChange={(e) => setLineStyle(e.target.value as LineStyle3D)}>
                      <option value="solid" style={{ color: '#0f172a' }}>Đoạn nét liền</option>
                      <option value="dashed" style={{ color: '#0f172a' }}>Đoạn nét đứt</option>
                      <option value="vector_solid" style={{ color: '#0f172a' }}>Vectơ liền →</option>
                      <option value="vector_dashed" style={{ color: '#0f172a' }}>Vectơ đứt →</option>
                    </select>
                    <select className="glass-select" style={{ padding: '7px 8px', fontSize: 12 }} value={lineColor} onChange={(e) => setLineColor(e.target.value)}>
                      {LINE_COLORS.map((c) => <option key={c.hex} value={c.hex} style={{ color: '#0f172a' }}>{c.label}</option>)}
                    </select>
                  </div>
                  <button type="button" className="primary-btn" style={{ minHeight: 36, fontSize: 12 }} onClick={() => handleAddLine()}>
                    <Pencil size={13} />
                    <span>Vẽ đoạn thẳng / Vectơ</span>
                  </button>
                </div>

                <div style={{ padding: 11, borderRadius: 15, border: '1px solid var(--border-subtle)', background: 'rgba(148,163,184,0.07)', display: 'flex', flexDirection: 'column', gap: 8 }}>
                  <div style={{ fontSize: 11.5, fontWeight: 800, color: '#10b981' }}>3. TÔ MẶT PHẲNG PHỤ (3–4 ĐIỂM)</div>
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 6 }}>
                    <select className="glass-select" style={{ padding: '7px 8px', fontSize: 12 }} value={pl1} onChange={(e) => setPl1(e.target.value)}>
                      {pointNames.map((n) => <option key={n} value={n} style={{ color: '#0f172a' }}>Đ1: {n}</option>)}
                    </select>
                    <select className="glass-select" style={{ padding: '7px 8px', fontSize: 12 }} value={pl2} onChange={(e) => setPl2(e.target.value)}>
                      {pointNames.map((n) => <option key={n} value={n} style={{ color: '#0f172a' }}>Đ2: {n}</option>)}
                    </select>
                    <select className="glass-select" style={{ padding: '7px 8px', fontSize: 12 }} value={pl3} onChange={(e) => setPl3(e.target.value)}>
                      {pointNames.map((n) => <option key={n} value={n} style={{ color: '#0f172a' }}>Đ3: {n}</option>)}
                    </select>
                    <select className="glass-select" style={{ padding: '7px 8px', fontSize: 12 }} value={pl4} onChange={(e) => setPl4(e.target.value)}>
                      <option value="" style={{ color: '#0f172a' }}>-- 3 điểm --</option>
                      {pointNames.map((n) => <option key={n} value={n} style={{ color: '#0f172a' }}>Đ4: {n}</option>)}
                    </select>
                  </div>
                  <button type="button" className="chip-btn active" style={{ minHeight: 36 }} onClick={handleAddPlane}>
                    <Layers size={14} />
                    <span>+ Tô mặt phẳng</span>
                  </button>
                </div>

                <div style={{ padding: 11, borderRadius: 15, border: '1px solid rgba(249,115,22,0.55)', background: 'rgba(249,115,22,0.1)', display: 'flex', flexDirection: 'column', gap: 8 }}>
                  <div style={{ fontSize: 11.5, fontWeight: 800, color: '#fb923c' }}>4. DỰNG THIẾT DIỆN ĐỘNG QUA 3 ĐIỂM</div>
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 5 }}>
                    <select className="glass-select" style={{ padding: '6px', fontSize: 12 }} value={sec1} onChange={(e) => setSec1(e.target.value)}>
                      {pointNames.map((n) => <option key={n} value={n} style={{ color: '#0f172a' }}>{n}</option>)}
                    </select>
                    <select className="glass-select" style={{ padding: '6px', fontSize: 12 }} value={sec2} onChange={(e) => setSec2(e.target.value)}>
                      {pointNames.map((n) => <option key={n} value={n} style={{ color: '#0f172a' }}>{n}</option>)}
                    </select>
                    <select className="glass-select" style={{ padding: '6px', fontSize: 12 }} value={sec3} onChange={(e) => setSec3(e.target.value)}>
                      {pointNames.map((n) => <option key={n} value={n} style={{ color: '#0f172a' }}>{n}</option>)}
                    </select>
                  </div>
                  <div style={{ display: 'flex', gap: 6 }}>
                    <button
                      type="button"
                      className="primary-btn"
                      style={{ flex: 1, minHeight: 36, fontSize: 12, background: 'linear-gradient(135deg, #ea580c, #f97316)' }}
                      onClick={handleCutSection}
                    >
                      Cắt thiết diện động
                    </button>
                    {snapshot.dynamicSection && (
                      <button type="button" className="chip-btn" onClick={() => commitSnapshot((p) => ({ ...p, dynamicSection: null }))}>
                        Xóa
                      </button>
                    )}
                  </div>
                  {fullScene.dynamicSection && snapshot.dynamicSection && (
                    <div className="mono" style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, padding: '6px 9px', borderRadius: 10, background: 'rgba(2,6,23,0.45)' }}>
                      <span>({snapshot.dynamicSection.p1}{snapshot.dynamicSection.p2}{snapshot.dynamicSection.p3}) • {fullScene.dynamicSection.polygon.length} cạnh</span>
                      <b style={{ color: '#fbbf24' }}>S = {hideAnswers ? '???' : fullScene.dynamicSection.area.toFixed(2)}</b>
                    </div>
                  )}
                </div>

                {/* Custom Elements + Replay */}
                <div style={{ padding: 11, borderRadius: 15, border: '1px solid var(--border-subtle)', background: 'rgba(148,163,184,0.07)', display: 'flex', flexDirection: 'column', gap: 7 }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <span style={{ fontSize: 11.5, fontWeight: 800 }}>Yếu tố đã dựng ({totalCustomSteps})</span>
                    {totalCustomSteps > 0 && (
                      <div style={{ display: 'flex', gap: 6 }}>
                        <button
                          type="button"
                          className="chip-btn active"
                          style={{ minHeight: 26, padding: '2px 9px', fontSize: 11 }}
                          onClick={() => {
                            setReplayActive(true);
                            setReplayStep(0);
                            setReplayPlaying(true);
                          }}
                        >
                          Chiếu từng bước
                        </button>
                        <button
                          type="button"
                          className="chip-btn"
                          style={{ minHeight: 26, padding: '2px 9px', fontSize: 11, color: '#f43f5e' }}
                          onClick={() =>
                            commitSnapshot((p) => ({
                              ...p,
                              customPoints: [],
                              customXYZPoints: [],
                              customLines: [],
                              customPlanes: [],
                              dynamicSection: null
                            }))
                          }
                        >
                          Xóa hết
                        </button>
                      </div>
                    )}
                  </div>
                  {totalCustomSteps === 0 ? (
                    <div style={{ fontSize: 11.5, color: 'var(--text-secondary)', textAlign: 'center', padding: 6 }}>
                      Chưa có yếu tố kẻ thêm (hoặc chọn chế độ Nối điểm ngay trên hình 3D).
                    </div>
                  ) : (
                    <>
                      {snapshot.customPoints.map((cp) => (
                        <div key={cp.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: 12 }}>
                          <span>Điểm <b>{cp.name}</b> ∈ {cp.from}{cp.to} (k={cp.ratio.toFixed(2)})</span>
                          <button type="button" className="icon-btn" style={{ width: 24, height: 24 }} onClick={() => commitSnapshot((p) => ({ ...p, customPoints: p.customPoints.filter((i) => i.id !== cp.id) }))}>
                            <X size={13} color="#f43f5e" />
                          </button>
                        </div>
                      ))}
                      {snapshot.customXYZPoints.map((cp) => (
                        <div key={cp.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: 12 }}>
                          <span className="mono">Điểm <b>{cp.name}</b>({cp.x};{cp.y};{cp.z})</span>
                          <button type="button" className="icon-btn" style={{ width: 24, height: 24 }} onClick={() => commitSnapshot((p) => ({ ...p, customXYZPoints: p.customXYZPoints.filter((i) => i.id !== cp.id) }))}>
                            <X size={13} color="#f43f5e" />
                          </button>
                        </div>
                      ))}
                      {snapshot.customLines.map((cl) => (
                        <div key={cl.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: 12 }}>
                          <span style={{ color: cl.color }}>{cl.style.startsWith('vector') ? 'Vectơ' : 'Đoạn'} <b>{cl.p1}{cl.p2}</b></span>
                          <button type="button" className="icon-btn" style={{ width: 24, height: 24 }} onClick={() => commitSnapshot((p) => ({ ...p, customLines: p.customLines.filter((i) => i.id !== cl.id) }))}>
                            <X size={13} color="#f43f5e" />
                          </button>
                        </div>
                      ))}
                      {snapshot.customPlanes.map((pl) => (
                        <div key={pl.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: 12 }}>
                          <span style={{ color: '#10b981' }}>Mặt phẳng ({pl.pts.join('')})</span>
                          <button type="button" className="icon-btn" style={{ width: 24, height: 24 }} onClick={() => commitSnapshot((p) => ({ ...p, customPlanes: p.customPlanes.filter((i) => i.id !== pl.id) }))}>
                            <X size={13} color="#f43f5e" />
                          </button>
                        </div>
                      ))}
                    </>
                  )}
                </div>
              </>
            )}

            {/* TAB 3: OXYZ COORDINATES, DIHEDRAL ANGLE & SKEW LINES */}
            {activeTab === 'coords' && (
              <>
                <div style={{ padding: 11, borderRadius: 15, border: '1px solid var(--border-subtle)', background: 'rgba(148,163,184,0.07)', display: 'flex', flexDirection: 'column', gap: 8 }}>
                  <div style={{ fontSize: 11.5, fontWeight: 800, color: '#f43f5e' }}>1. CHỌN GỐC TỌA ĐỘ (0; 0; 0)</div>
                  <select
                    className="glass-select"
                    style={{ padding: '7px 9px', fontSize: 12 }}
                    value={snapshot.originPoint}
                    onChange={(e) => commitSnapshot((p) => ({ ...p, originPoint: e.target.value }))}
                  >
                    {pointNames.map((n) => (
                      <option key={n} value={n} style={{ color: '#0f172a' }}>Điểm {n} (0; 0; 0)</option>
                    ))}
                  </select>
                  <button
                    type="button"
                    className={`chip-btn${showCoordsOnLabels ? ' active' : ''}`}
                    style={{ minHeight: 30 }}
                    onClick={() => setShowCoordsOnLabels((v) => !v)}
                  >
                    {showCoordsOnLabels ? 'Đang hiện (x;y;z) trên nhãn điểm' : 'Hiện tọa độ trên nhãn điểm 3D'}
                  </button>
                </div>

                {/* Point & Vector Calculator */}
                <div style={{ padding: 11, borderRadius: 15, border: '1px solid var(--border-subtle)', background: 'rgba(148,163,184,0.07)', display: 'flex', flexDirection: 'column', gap: 8 }}>
                  <div style={{ fontSize: 11.5, fontWeight: 800, color: accentHex }}>2. TỌA ĐỘ ĐIỂM, ĐƯỜNG AB & VECTƠ</div>
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 6 }}>
                    <select className="glass-select" style={{ padding: '7px', fontSize: 12 }} value={calcA} onChange={(e) => setCalcA(e.target.value)}>
                      {pointNames.map((n) => <option key={n} value={n} style={{ color: '#0f172a' }}>Điểm A: {n}</option>)}
                    </select>
                    <select className="glass-select" style={{ padding: '7px', fontSize: 12 }} value={calcB} onChange={(e) => setCalcB(e.target.value)}>
                      {pointNames.map((n) => <option key={n} value={n} style={{ color: '#0f172a' }}>Điểm B: {n}</option>)}
                    </select>
                  </div>
                  {vCalcA && vCalcB && (() => {
                    const cA = worldToMath(vCalcA, originWorld);
                    const cB = worldToMath(vCalcB, originWorld);
                    const vx = cB.x - cA.x;
                    const vy = cB.y - cA.y;
                    const vz = cB.z - cA.z;
                    const len = Math.hypot(vx, vy, vz);
                    return (
                      <div className="mono" style={{ fontSize: 11.5, padding: 9, borderRadius: 11, background: 'rgba(2,6,23,0.42)', display: 'flex', flexDirection: 'column', gap: 4 }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between' }}><span>{calcA}:</span><b>{formatCoord3D(cA)}</b></div>
                        <div style={{ display: 'flex', justifyContent: 'space-between' }}><span>{calcB}:</span><b>{formatCoord3D(cB)}</b></div>
                        <div style={{ display: 'flex', justifyContent: 'space-between', color: '#fbbf24' }}><span>Vectơ {calcA}{calcB}→:</span><b>({formatMathNum(vx)}; {formatMathNum(vy)}; {formatMathNum(vz)})</b></div>
                        <div style={{ display: 'flex', justifyContent: 'space-between', color: '#10b981' }}><span>Độ dài |{calcA}{calcB}|:</span><b>{hideAnswers ? '???' : len.toFixed(2)}</b></div>
                      </div>
                    );
                  })()}
                  <button type="button" className="primary-btn" style={{ minHeight: 34, fontSize: 12 }} onClick={() => handleAddLine(calcA, calcB, 'vector_solid', '#f43f5e')}>
                    Vẽ Vectơ {calcA}{calcB}→ lên hình 3D
                  </button>
                </div>

                {/* Line-Plane Distance & Angle */}
                <div style={{ padding: 11, borderRadius: 15, border: '1px solid rgba(168,85,247,0.45)', background: 'rgba(168,85,247,0.08)', display: 'flex', flexDirection: 'column', gap: 8 }}>
                  <div style={{ fontSize: 11.5, fontWeight: 800, color: '#c084fc' }}>3. KHOẢNG CÁCH & GÓC VỚI MẶT PHẲNG (P)</div>
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 5 }}>
                    <select className="glass-select" style={{ padding: '6px', fontSize: 12 }} value={calcP1} onChange={(e) => setCalcP1(e.target.value)}>
                      {pointNames.map((n) => <option key={n} value={n} style={{ color: '#0f172a' }}>{n}</option>)}
                    </select>
                    <select className="glass-select" style={{ padding: '6px', fontSize: 12 }} value={calcP2} onChange={(e) => setCalcP2(e.target.value)}>
                      {pointNames.map((n) => <option key={n} value={n} style={{ color: '#0f172a' }}>{n}</option>)}
                    </select>
                    <select className="glass-select" style={{ padding: '6px', fontSize: 12 }} value={calcP3} onChange={(e) => setCalcP3(e.target.value)}>
                      {pointNames.map((n) => <option key={n} value={n} style={{ color: '#0f172a' }}>{n}</option>)}
                    </select>
                  </div>
                  {linePlaneAnalysis ? (
                    <div className="mono" style={{ fontSize: 11.5, padding: 9, borderRadius: 11, background: 'rgba(2,6,23,0.42)', display: 'flex', flexDirection: 'column', gap: 4 }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between' }}><span>PT ({calcP1}{calcP2}{calcP3}):</span><b>{linePlaneAnalysis.planeEquation}</b></div>
                      <div style={{ display: 'flex', justifyContent: 'space-between', color: accentHex }}><span>d({calcA}, (P)):</span><b>{hideAnswers ? '???' : linePlaneAnalysis.distA.toFixed(2)}</b></div>
                      <div style={{ display: 'flex', justifyContent: 'space-between', color: accentHex }}><span>d({calcB}, (P)):</span><b>{hideAnswers ? '???' : linePlaneAnalysis.distB.toFixed(2)}</b></div>
                      <div style={{ display: 'flex', justifyContent: 'space-between', color: '#fbbf24' }}><span>Góc ({calcA}{calcB}, (P)):</span><b>{hideAnswers ? '???°' : `${linePlaneAnalysis.angleDegrees.toFixed(1)}°`}</b></div>
                    </div>
                  ) : (
                    <div style={{ fontSize: 11.5, color: '#fbbf24' }}>Chọn 3 điểm không thẳng hàng để lập mặt phẳng (P).</div>
                  )}
                </div>

                {/* Item 19: Dihedral Angle between (P) and (Q) */}
                <div style={{ padding: 11, borderRadius: 15, border: '1px solid rgba(56,189,248,0.45)', background: 'rgba(56,189,248,0.08)', display: 'flex', flexDirection: 'column', gap: 8 }}>
                  <div style={{ fontSize: 11.5, fontWeight: 800, color: '#38bdf8' }}>4. GÓC NHỊ DIỆN GIỮA (P) VÀ (Q)</div>
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 5 }}>
                    <select className="glass-select" style={{ padding: '6px', fontSize: 12 }} value={calcQ1} onChange={(e) => setCalcQ1(e.target.value)}>
                      {pointNames.map((n) => <option key={n} value={n} style={{ color: '#0f172a' }}>Q1: {n}</option>)}
                    </select>
                    <select className="glass-select" style={{ padding: '6px', fontSize: 12 }} value={calcQ2} onChange={(e) => setCalcQ2(e.target.value)}>
                      {pointNames.map((n) => <option key={n} value={n} style={{ color: '#0f172a' }}>Q2: {n}</option>)}
                    </select>
                    <select className="glass-select" style={{ padding: '6px', fontSize: 12 }} value={calcQ3} onChange={(e) => setCalcQ3(e.target.value)}>
                      {pointNames.map((n) => <option key={n} value={n} style={{ color: '#0f172a' }}>Q3: {n}</option>)}
                    </select>
                  </div>
                  {dihedralAnalysis ? (
                    <div className="mono" style={{ fontSize: 11.5, padding: 9, borderRadius: 11, background: 'rgba(2,6,23,0.42)', display: 'flex', flexDirection: 'column', gap: 4 }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between' }}><span>PT (Q):</span><b>{dihedralAnalysis.plane2Eq}</b></div>
                      <div style={{ display: 'flex', justifyContent: 'space-between', color: '#38bdf8' }}>
                        <span>Góc ((P), (Q)):</span>
                        <b>{hideAnswers ? '???°' : `${dihedralAnalysis.angleDegrees.toFixed(2)}°`}</b>
                      </div>
                      <div style={{ display: 'flex', justifyContent: 'space-between', color: 'var(--text-secondary)' }}>
                        <span>cos φ:</span>
                        <b>{hideAnswers ? '???' : dihedralAnalysis.cosPhi.toFixed(4)}</b>
                      </div>
                    </div>
                  ) : (
                    <div style={{ fontSize: 11.5, color: '#fbbf24' }}>Chọn 3 điểm không thẳng hàng cho cả (P) và (Q).</div>
                  )}
                </div>

                {/* Item 19: Distance between 2 Skew Lines d(AB, CD) + Common Perpendicular */}
                <div style={{ padding: 11, borderRadius: 15, border: '1px solid rgba(244,63,94,0.45)', background: 'rgba(244,63,94,0.08)', display: 'flex', flexDirection: 'column', gap: 8 }}>
                  <div style={{ fontSize: 11.5, fontWeight: 800, color: '#fb7185' }}>5. KHOẢNG CÁCH 2 ĐƯỜNG CHÉO NHAU</div>
                  <div style={{ fontSize: 11, color: 'var(--text-secondary)' }}>
                    Đường 1: <b>{calcA}{calcB}</b> — Chọn đường 2:
                  </div>
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 6 }}>
                    <select className="glass-select" style={{ padding: '6px', fontSize: 12 }} value={skewC} onChange={(e) => setSkewC(e.target.value)}>
                      {pointNames.map((n) => <option key={n} value={n} style={{ color: '#0f172a' }}>Điểm C: {n}</option>)}
                    </select>
                    <select className="glass-select" style={{ padding: '6px', fontSize: 12 }} value={skewD} onChange={(e) => setSkewD(e.target.value)}>
                      {pointNames.map((n) => <option key={n} value={n} style={{ color: '#0f172a' }}>Điểm D: {n}</option>)}
                    </select>
                  </div>
                  {skewAnalysis ? (
                    <>
                      <div className="mono" style={{ fontSize: 11.5, padding: 9, borderRadius: 11, background: 'rgba(2,6,23,0.42)', display: 'flex', flexDirection: 'column', gap: 4 }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', color: '#fb7185' }}>
                          <span>d({calcA}{calcB}, {skewC}{skewD}):</span>
                          <b>{hideAnswers ? '???' : skewAnalysis.distance.toFixed(3)}</b>
                        </div>
                        <div style={{ fontSize: 10.5, color: 'var(--text-secondary)' }}>
                          Vị trí: {skewAnalysis.isIntersecting ? 'Cắt nhau / Trùng nhau' : skewAnalysis.isParallel ? 'Song song' : 'Chéo nhau trong không gian'}
                        </div>
                      </div>
                      <button
                        type="button"
                        className={`chip-btn${showSkewPerp ? ' active' : ''}`}
                        style={{ minHeight: 32 }}
                        onClick={() => setShowSkewPerp((v) => !v)}
                      >
                        {showSkewPerp ? '✓ Đang vẽ đoạn vuông góc chung H₁H₂' : 'Vẽ đoạn vuông góc chung H₁H₂ lên hình 3D'}
                      </button>
                    </>
                  ) : (
                    <div style={{ fontSize: 11.5, color: '#fbbf24' }}>Chọn 2 đường thẳng hợp lệ (A≠B, C≠D).</div>
                  )}
                </div>

                {/* Add point by (x;y;z) */}
                <div style={{ padding: 11, borderRadius: 15, border: '1px solid var(--border-subtle)', background: 'rgba(148,163,184,0.07)', display: 'flex', flexDirection: 'column', gap: 8 }}>
                  <div style={{ fontSize: 11.5, fontWeight: 800, color: '#fbbf24' }}>6. CHẤM ĐIỂM THEO TỌA ĐỘ (x; y; z)</div>
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr 1fr', gap: 5 }}>
                    <input className="glass-input" style={{ padding: '6px', fontSize: 12 }} value={xyzName} maxLength={3} onChange={(e) => setXyzName(e.target.value)} placeholder="Tên" />
                    <input className="glass-input mono" style={{ padding: '6px', fontSize: 12 }} value={xyzX} onChange={(e) => setXyzX(e.target.value)} placeholder="x" />
                    <input className="glass-input mono" style={{ padding: '6px', fontSize: 12 }} value={xyzY} onChange={(e) => setXyzY(e.target.value)} placeholder="y" />
                    <input className="glass-input mono" style={{ padding: '6px', fontSize: 12 }} value={xyzZ} onChange={(e) => setXyzZ(e.target.value)} placeholder="z" />
                  </div>
                  <button type="button" className="primary-btn" style={{ minHeight: 34, fontSize: 12 }} onClick={handleAddXYZ}>
                    + Thêm điểm (x; y; z)
                  </button>
                </div>

                {/* Full Coordinates Table */}
                <div style={{ padding: 10, borderRadius: 15, border: '1px solid var(--border-subtle)', background: 'rgba(2,6,23,0.35)' }}>
                  <div style={{ fontSize: 11.5, fontWeight: 800, color: '#10b981', marginBottom: 6 }}>7. BẢNG TỌA ĐỘ CÁC ĐIỂM</div>
                  <table className="mono" style={{ width: '100%', fontSize: 11.5, borderCollapse: 'collapse', textAlign: 'center' }}>
                    <thead>
                      <tr style={{ borderBottom: '1px solid var(--border-subtle)' }}>
                        <th style={{ padding: 4 }}>Điểm</th>
                        <th style={{ color: '#f43f5e' }}>x</th>
                        <th style={{ color: '#10b981' }}>y</th>
                        <th style={{ color: '#38bdf8' }}>z</th>
                      </tr>
                    </thead>
                    <tbody>
                      {Object.entries(fullScene.pointRegistry).map(([name, pos]) => {
                        const c = worldToMath(pos, originWorld);
                        return (
                          <tr key={name} style={{ borderBottom: '1px solid rgba(148,163,184,0.12)' }}>
                            <td style={{ padding: 4, fontWeight: 700, color: name === snapshot.originPoint ? '#f43f5e' : undefined }}>{name}</td>
                            <td>{formatMathNum(c.x)}</td>
                            <td>{formatMathNum(c.y)}</td>
                            <td>{formatMathNum(c.z)}</td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              </>
            )}

            {/* TAB 4: FORMULAS & VERIFY MODE */}
            {activeTab === 'formulas' && (
              <>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 7 }}>
                  {[
                    { label: 'Diện tích đáy (Sđ)', val: metrics.sDay, color: accentHex },
                    { label: 'DT xung quanh (Sxq)', val: metrics.sXq, color: accentHex },
                    { label: 'DT toàn phần (Stp)', val: metrics.sTp, color: accentHex },
                    { label: 'Thể tích thực (V)', val: metrics.vol, color: '#fbbf24' }
                  ].map((card) => (
                    <div key={card.label} style={{ padding: 10, borderRadius: 14, background: 'rgba(2,6,23,0.42)', border: '1px solid var(--border-subtle)' }}>
                      <div style={{ fontSize: 10.5, color: 'var(--text-secondary)' }}>{card.label}</div>
                      <div className="mono" style={{ fontSize: 16, fontWeight: 800, color: card.color, marginTop: 2 }}>
                        {hideAnswers ? '???' : card.val.toFixed(2)}
                      </div>
                    </div>
                  ))}
                </div>

                <div style={{ padding: 10, borderRadius: 14, background: 'rgba(148,163,184,0.08)', border: '1px solid var(--border-subtle)', display: 'flex', flexDirection: 'column', gap: 5 }}>
                  {metrics.dimensionRows.map((row) => (
                    <div key={row.label} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12 }}>
                      <span style={{ color: 'var(--text-secondary)' }}>{row.label}</span>
                      <b className="mono">{hideAnswers ? '???' : row.value}</b>
                    </div>
                  ))}
                  {circumSphere && (
                    <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, color: '#38bdf8' }}>
                      <span>Bán kính MC ngoại tiếp (R)</span>
                      <b className="mono">{hideAnswers ? '???' : circumSphere.radius.toFixed(2)}</b>
                    </div>
                  )}
                  {inSphere && (
                    <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, color: '#10b981' }}>
                      <span>Bán kính MC nội tiếp (r)</span>
                      <b className="mono">{hideAnswers ? '???' : inSphere.radius.toFixed(2)}</b>
                    </div>
                  )}
                  <hr style={{ border: 'none', borderTop: '1px solid var(--border-subtle)', margin: '4px 0' }} />
                  {metrics.formulaLines.map((line) => (
                    <div key={line} className="mono" style={{ fontSize: 11.5 }}>{line}</div>
                  ))}
                </div>

                {/* Verify Mode Card */}
                <div style={{ padding: 12, borderRadius: 16, border: '1px solid rgba(16,185,129,0.55)', background: 'rgba(16,185,129,0.09)', display: 'flex', flexDirection: 'column', gap: 8 }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <span style={{ fontSize: 12, fontWeight: 800, color: '#10b981' }}>CHẾ ĐỘ KIỂM TRA ĐÁP ÁN</span>
                    <button
                      type="button"
                      className={`chip-btn${hideAnswers ? ' active' : ''}`}
                      style={{ minHeight: 26, padding: '2px 9px', fontSize: 10.5 }}
                      onClick={() => setHideAnswers((v) => !v)}
                    >
                      {hideAnswers ? 'Đang ẩn KQ' : 'Ẩn đáp án'}
                    </button>
                  </div>
                  <div style={{ fontSize: 11, color: 'var(--text-secondary)' }}>
                    Nhập số hoặc biểu thức (<span className="mono">sqrt(3)/2</span>, <span className="mono">4*pi/3</span>) để tự chấm điểm.
                  </div>
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 5 }}>
                    {[
                      { id: 'vol' as const, label: 'Thể tích V' },
                      { id: 'stp' as const, label: 'Stp' },
                      { id: 'sday' as const, label: 'S đáy' },
                      { id: 'h' as const, label: 'Chiều cao h' },
                      { id: 'circum_r' as const, label: 'R ngoại tiếp' },
                      { id: 'dist_ab' as const, label: `Đoạn ${calcA}${calcB}` },
                      { id: 'angle_lp' as const, label: 'Góc (AB,(P))' },
                      { id: 'dist_ap' as const, label: `d(${calcA},(P))` },
                      { id: 'dihedral' as const, label: 'Góc ((P),(Q))' },
                      { id: 'skew_d' as const, label: `d(${calcA}${calcB},${skewC}${skewD})` },
                      { id: 'dyn_sec' as const, label: 'S thiết diện' }
                    ].map((t) => (
                      <button
                        key={t.id}
                        type="button"
                        className={`chip-btn${verifyTarget === t.id ? ' active' : ''}`}
                        style={{ minHeight: 26, padding: '2px 8px', fontSize: 10.5 }}
                        onClick={() => {
                          setVerifyTarget(t.id);
                          setVerifyFeedback(null);
                          setRevealVerifyAns(false);
                        }}
                      >
                        {t.label}
                      </button>
                    ))}
                  </div>

                  {verifyExpected === null ? (
                    <div style={{ fontSize: 11.5, color: '#fbbf24' }}>
                      Cần chọn đủ điểm/mặt phẳng (hoặc cắt thiết diện động) trước khi kiểm tra mục này.
                    </div>
                  ) : (
                    <>
                      <div style={{ display: 'flex', gap: 6 }}>
                        <input
                          type="text"
                          className="glass-input mono"
                          style={{ padding: '8px 10px', fontSize: 12 }}
                          placeholder="VD: 12.5 hoặc 4*sqrt(3)"
                          value={verifyInput}
                          onChange={(e) => {
                            setVerifyInput(e.target.value);
                            setVerifyFeedback(null);
                          }}
                        />
                        <button
                          type="button"
                          className="primary-btn"
                          style={{ minHeight: 38, padding: '0 12px', background: '#10b981' }}
                          onClick={() => {
                            const val = evaluateVerifyAnswer(verifyInput);
                            if (val === null) {
                              setVerifyFeedback({ ok: false, msg: 'Biểu thức không hợp lệ!' });
                            } else if (isVerifyAnswerCorrect(verifyExpected, val)) {
                              setVerifyFeedback({ ok: true, msg: `Chính xác! (${val.toFixed(2)}) khớp với mô hình.` });
                            } else {
                              setVerifyFeedback({
                                ok: false,
                                msg: `Chưa đúng — số bạn nhập ${val > verifyExpected ? 'lớn hơn' : 'nhỏ hơn'} đáp án chuẩn.`
                              });
                            }
                          }}
                        >
                          <Check size={15} />
                        </button>
                      </div>
                      {verifyFeedback && (
                        <div style={{ fontSize: 11.5, fontWeight: 700, color: verifyFeedback.ok ? '#10b981' : '#f43f5e' }}>
                          {verifyFeedback.msg}
                        </div>
                      )}
                      <button
                        type="button"
                        className="chip-btn"
                        style={{ alignSelf: 'flex-start', minHeight: 28, fontSize: 11 }}
                        onClick={() => setRevealVerifyAns((v) => !v)}
                      >
                        {revealVerifyAns
                          ? `Đáp án chuẩn: ${verifyExpected.toFixed(2)}${verifyTarget === 'angle_lp' || verifyTarget === 'dihedral' ? '°' : ''}`
                          : 'Xem đáp án chuẩn'}
                      </button>
                    </>
                  )}
                </div>
              </>
            )}
          </div>
        </aside>
      )}
    </div>
  );
};
