import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Check,
  Download,
  Eye,
  EyeOff,
  Grid,
  Magnet,
  Maximize2,
  Minimize2,
  Minus,
  Pause,
  Play,
  Plus,
  Redo2,
  RotateCcw,
  Sigma,
  SlidersHorizontal,
  Table2,
  Trash2,
  Undo2,
  Upload,
  X
} from 'lucide-react';
import {
  angleDegrees2D,
  GraphAsymptote,
  buildPreset2DGeometry,
  detectAsymptotes,
  detectIntersections,
  detectKeyPoints,
  evaluateMathExpr,
  evaluateVerifyAnswer,
  format2DNum,
  FUNCTION_CATEGORIES,
  FUNCTION_LIBRARY,
  FunctionCategoryId,
  GEO2D_TOOLS,
  Geo2DObject,
  Geo2DPoint,
  Geo2DSnapshot,
  Geo2DToolId,
  GraphFunctionItem,
  KeyPoint2D,
  isVerifyAnswerCorrect,
  lineEquation2D,
  numericalDerivative,
  polygonAreaPerimeter2D,
  PRESET_2D_SHAPES,
  QUICK_MATH_KEYS
} from '../engine/geo2d';
import { deriveSymbolicExpr } from '../engine/geo2dAdvanced';
import { analyzeVariationTable, VariationTableResult } from '../engine/variationTable';
import { VariationTableModal } from './VariationTableModal';
import { AiDrawingPlan, Sensitivity2D } from '../types/app';

interface Plane2DWorkspaceProps {
  accentHex: string;
  themeMode: 'dark' | 'light';
  isImmersive: boolean;
  onToggleImmersive: () => void;
  sensitivity: Sensitivity2D;
  onUpdateSensitivity: (next: Sensitivity2D) => void;
  externalPlan: AiDrawingPlan | null;
  onPlanConsumed: () => void;
  externalSnapshot: Geo2DSnapshot | null;
  onExternalSnapshotConsumed: () => void;
}

type DrawerTab2D = 'functions' | 'presets' | 'objects' | 'settings';
type VerifyTarget2D = 'fn_val' | 'segment' | 'polygon_area' | 'circle_radius' | 'angle' | 'extrema';

const FN_COLORS = ['#38bdf8', '#10b981', '#f43f5e', '#f59e0b', '#a855f7', '#06b6d4'];
const AUTO_SAVE_2D_KEY = 'tooltoan_2d_autosave_v2';

const DEFAULT_2D_SNAPSHOT: Geo2DSnapshot = {
  points: [],
  objects: [],
  functions: [
    { id: 'fn-1', label: 'f₁(x)', expression: 'x^3 - 3*m*x + 1', color: FN_COLORS[0], visible: true, showDerivative: false }
  ],
  paramM: 1
};

function next2DPointName(points: Geo2DPoint[]): string {
  const used = new Set(points.map((p) => p.name));
  const letters = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';
  for (const ch of letters) {
    if (!used.has(ch)) return ch;
  }
  let idx = 1;
  while (used.has(`P${idx}`)) idx++;
  return `P${idx}`;
}

export const Plane2DWorkspace: React.FC<Plane2DWorkspaceProps> = ({
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

  const [snapshot, setSnapshot] = useState<Geo2DSnapshot>(() => {
    try {
      const raw = localStorage.getItem(AUTO_SAVE_2D_KEY);
      if (raw) return { ...DEFAULT_2D_SNAPSHOT, ...JSON.parse(raw) };
    } catch {
      // ignore
    }
    return DEFAULT_2D_SNAPSHOT;
  });

  const [undoStack, setUndoStack] = useState<Geo2DSnapshot[]>([]);
  const [redoStack, setRedoStack] = useState<Geo2DSnapshot[]>([]);

  // Active interactive tool & pending clicks
  const [activeTool, setActiveTool] = useState<Geo2DToolId>('pan');
  const [pendingPointIds, setPendingPointIds] = useState<string[]>([]);
  const [traceX, setTraceX] = useState<number>(1);
  const [playParamM, setPlayParamM] = useState<boolean>(false);

  // Item 8: Custom m bounds
  const [mMin, setMMin] = useState<number>(-5);
  const [mMax, setMMax] = useState<number>(5);

  // Display toggles
  const [showAxes, setShowAxes] = useState<boolean>(true);
  const [showGrid, setShowGrid] = useState<boolean>(true);
  const [snapToGrid, setSnapToGrid] = useState<boolean>(true);
  const [showPointCoords, setShowPointCoords] = useState<boolean>(true);
  const [showMeasurements, setShowMeasurements] = useState<boolean>(true);
  const [showKeyPoints, setShowKeyPoints] = useState<boolean>(true);
  const [showAsymptotes, setShowAsymptotes] = useState<boolean>(true);
  const [showTangent, setShowTangent] = useState<boolean>(true);

  // Export options
  const [pngTransparent, setPngTransparent] = useState<boolean>(true);
  const [exportFilename, setExportFilename] = useState<string>('');

  // Drawer & Variation Table Modal
  const [drawerOpen, setDrawerOpen] = useState<boolean>(false);
  const [activeTab, setActiveTab] = useState<DrawerTab2D>('functions');
  const [draftExpr, setDraftExpr] = useState<string>('');
  const [fnCategory, setFnCategory] = useState<FunctionCategoryId>('param');
  const [variationData, setVariationData] = useState<VariationTableResult | null>(null);
  const [toast, setToast] = useState<string | null>(null);

  // Add Point by (x; y)
  const [ptNameInput, setPtNameInput] = useState<string>('A');
  const [ptXInput, setPtXInput] = useState<string>('2');
  const [ptYInput, setPtYInput] = useState<string>('1');

  // Verify 2D Mode
  const [verifyTarget, setVerifyTarget] = useState<VerifyTarget2D>('fn_val');
  const [verifyX, setVerifyX] = useState<string>('1');
  const [verifyAns, setVerifyAns] = useState<string>('');
  const [verifyMsg, setVerifyMsg] = useState<{ ok: boolean; text: string } | null>(null);
  const [revealVerifyAns, setRevealVerifyAns] = useState<boolean>(false);

  // Camera stored in ref -> ZERO React re-renders during pan/pinch/wheel!
  const viewRef = useRef<{ cx: number; cy: number; scale: number }>({
    cx: 0,
    cy: 0,
    scale: 56
  });
  const rafRef = useRef<number | null>(null);
  const analysisCacheRef = useRef<{
    signature: string;
    xMin: number;
    xMax: number;
    scale: number;
    asymptotesByFn: Map<string, GraphAsymptote[]>;
    keyPointsByFn: Map<string, KeyPoint2D[]>;
    intersections: { x: number; y: number }[];
  } | null>(null);

  const dragRef = useRef<{
    mode: 'none' | 'pan' | 'point' | 'trace';
    pointId: string | null;
    x: number;
    y: number;
    pinchDist: number | null;
  }>({ mode: 'none', pointId: null, x: 0, y: 0, pinchDist: null });
  const pointersRef = useRef<Map<number, { x: number; y: number }>>(new Map());

  const commit = (updater: (prev: Geo2DSnapshot) => Geo2DSnapshot, recordHistory = true) => {
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
    setPendingPointIds([]);
  };

  const handleRedo = () => {
    if (redoStack.length === 0) return;
    const next = redoStack[redoStack.length - 1];
    setRedoStack((r) => r.slice(0, -1));
    setUndoStack((u) => [...u, snapshot]);
    setSnapshot(next);
    setPendingPointIds([]);
  };

  useEffect(() => {
    localStorage.setItem(AUTO_SAVE_2D_KEY, JSON.stringify(snapshot));
    setPtNameInput(next2DPointName(snapshot.points));
  }, [snapshot]);

  // Keyboard shortcuts (Ctrl+Z, Ctrl+Y, Space play/pause m)
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
        setPlayParamM((v) => !v);
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  });

  // Consume Share URL snapshot
  useEffect(() => {
    if (!externalSnapshot) return;
    commit(() => ({ ...DEFAULT_2D_SNAPSHOT, ...externalSnapshot }));
    setToast('Đã mở bài vẽ 2D từ liên kết chia sẻ!');
    onExternalSnapshotConsumed();
  }, [externalSnapshot, onExternalSnapshotConsumed]);

  // Consume external AI 2D plan
  useEffect(() => {
    if (!externalPlan || externalPlan.target !== 'plane2d') return;
    const now = Date.now();
    const exprs = externalPlan.expressions2d ? [...externalPlan.expressions2d] : [];
    const createdFns: GraphFunctionItem[] = exprs.map((expr, i) => ({
      id: `ai-${now}-${i}`,
      label: `f${i + 1}(x)`,
      expression: expr,
      color: FN_COLORS[i % FN_COLORS.length],
      visible: true,
      showDerivative: false
    }));

    commit((prev) => {
      const preset = externalPlan.preset2d ? buildPreset2DGeometry(externalPlan.preset2d) : null;
      return {
        ...prev,
        functions: createdFns.length > 0 ? createdFns : prev.functions,
        points: preset ? preset.points : prev.points,
        objects: preset ? preset.objects : prev.objects
      };
    });

    setToast(`Đã vẽ từ AI: ${externalPlan.title}`);
    onPlanConsumed();
  }, [externalPlan, onPlanConsumed]);

  useEffect(() => {
    if (!toast) return;
    const t = window.setTimeout(() => setToast(null), 3000);
    return () => window.clearTimeout(t);
  }, [toast]);

  // Parameter m animation loop (respecting custom mMin / mMax)
  useEffect(() => {
    if (!playParamM) return;
    let dir = 1;
    const lo = Math.min(mMin, mMax - 0.5);
    const hi = Math.max(mMax, lo + 0.5);
    const interval = window.setInterval(() => {
      setSnapshot((prev) => {
        let nextM = +(prev.paramM + dir * ((hi - lo) / 120)).toFixed(2);
        if (nextM >= hi) {
          nextM = hi;
          dir = -1;
        } else if (nextM <= lo) {
          nextM = lo;
          dir = 1;
        }
        return { ...prev, paramM: nextM };
      });
    }, 55);
    return () => window.clearInterval(interval);
  }, [playParamM, mMin, mMax]);

  const pointMap = useMemo(() => {
    const map = new Map<string, Geo2DPoint>();
    for (const p of snapshot.points) map.set(p.id, p);
    return map;
  }, [snapshot.points]);

  const primaryFn = snapshot.functions.find((f) => f.visible && f.expression.trim().length > 0);
  const activeToolInfo = GEO2D_TOOLS.find((t) => t.id === activeTool) || GEO2D_TOOLS[0];

  // Verify 2D expected value
  const verifyExpectedInfo = useMemo<{ value: number | null; unit: string; hint: string }>(() => {
    if (verifyTarget === 'fn_val') {
      if (!primaryFn) return { value: null, unit: '', hint: 'Cần có ít nhất 1 hàm số đang bật.' };
      const x0 = evaluateVerifyAnswer(verifyX);
      if (x0 === null) return { value: null, unit: '', hint: 'Nhập x₀ hợp lệ.' };
      const y = evaluateMathExpr(primaryFn.expression, x0, snapshot.paramM);
      return Number.isFinite(y)
        ? { value: y, unit: '', hint: `f(${format2DNum(x0)})` }
        : { value: null, unit: '', hint: 'Hàm không xác định tại x₀.' };
    }
    if (verifyTarget === 'segment') {
      const seg = [...snapshot.objects].reverse().find((o) => o.kind === 'segment');
      if (!seg || seg.kind !== 'segment') return { value: null, unit: '', hint: 'Hãy vẽ ít nhất 1 đoạn thẳng trên Oxy.' };
      const a = pointMap.get(seg.p1Id);
      const b = pointMap.get(seg.p2Id);
      if (!a || !b) return { value: null, unit: '', hint: 'Đoạn thẳng thiếu đỉnh.' };
      return { value: Math.hypot(b.x - a.x, b.y - a.y), unit: '', hint: `Độ dài ${a.name}${b.name}` };
    }
    if (verifyTarget === 'polygon_area') {
      const poly = [...snapshot.objects].reverse().find((o) => o.kind === 'polygon');
      if (!poly || poly.kind !== 'polygon') return { value: null, unit: '', hint: 'Hãy vẽ hoặc tải 1 đa giác mẫu.' };
      const pts = poly.pointIds.map((id) => pointMap.get(id)).filter((p): p is Geo2DPoint => Boolean(p));
      const { area } = polygonAreaPerimeter2D(pts);
      return { value: area, unit: '', hint: `Diện tích ${pts.map((p) => p.name).join('')}` };
    }
    if (verifyTarget === 'circle_radius') {
      const cir = [...snapshot.objects].reverse().find((o) => o.kind === 'circle');
      if (!cir || cir.kind !== 'circle') return { value: null, unit: '', hint: 'Hãy vẽ hoặc tải 1 đường tròn.' };
      const c = pointMap.get(cir.centerId);
      const e = cir.edgePointId ? pointMap.get(cir.edgePointId) : null;
      const r = c && e ? Math.hypot(e.x - c.x, e.y - c.y) : cir.fixedRadius || 0;
      return { value: r, unit: '', hint: `Bán kính R đường tròn tâm ${c?.name || 'I'}` };
    }
    if (verifyTarget === 'angle') {
      const ang = [...snapshot.objects].reverse().find((o) => o.kind === 'angle');
      if (!ang || ang.kind !== 'angle') return { value: null, unit: '°', hint: 'Hãy dùng công cụ Đo góc chọn 3 điểm.' };
      const a = pointMap.get(ang.pAId);
      const v = pointMap.get(ang.vertexId);
      const c = pointMap.get(ang.pCId);
      if (!a || !v || !c) return { value: null, unit: '°', hint: 'Thiếu đỉnh góc.' };
      return { value: angleDegrees2D(a, v, c), unit: '°', hint: `Góc ∠${a.name}${v.name}${c.name}` };
    }
    if (!primaryFn) return { value: null, unit: '', hint: 'Cần có 1 hàm số đang bật.' };
    const kps = detectKeyPoints(primaryFn.expression, snapshot.paramM, -10, 10, primaryFn.color);
    const ext = kps.find((k) => k.kind === 'max' || k.kind === 'min');
    if (!ext) return { value: null, unit: '', hint: 'Không tìm thấy cực trị trong [-10; 10].' };
    return { value: ext.y, unit: '', hint: `Giá trị cực trị tại x ≈ ${format2DNum(ext.x)}` };
  }, [verifyTarget, primaryFn, verifyX, snapshot, pointMap]);

  // Keep latest non-camera render state in ref for 60fps zero-re-render panning/zooming
  const renderStateRef = useRef({
    snapshot,
    pointMap,
    activeTool,
    traceX,
    pendingPointIds,
    showAxes,
    showGrid,
    showPointCoords,
    showMeasurements,
    showKeyPoints,
    showAsymptotes,
    showTangent,
    themeMode,
    primaryFn
  });
  renderStateRef.current = {
    snapshot,
    pointMap,
    activeTool,
    traceX,
    pendingPointIds,
    showAxes,
    showGrid,
    showPointCoords,
    showMeasurements,
    showKeyPoints,
    showAsymptotes,
    showTangent,
    themeMode,
    primaryFn
  };

  // Render full 2D canvas directly from refs (60fps + viewport-padded asymptote/extrema/intersection cache)
  const paintCanvasNow = useCallback(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const st = renderStateRef.current;
    const view = viewRef.current;

    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    const rect = canvas.getBoundingClientRect();
    const w = Math.max(300, rect.width);
    const h = Math.max(300, rect.height);
    const targetW = Math.round(w * dpr);
    const targetH = Math.round(h * dpr);
    if (canvas.width !== targetW || canvas.height !== targetH) {
      canvas.width = targetW;
      canvas.height = targetH;
    }

    ctx.save();
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, w, h);

    const isLight = st.themeMode === 'light';
    const toSX = (wx: number) => w / 2 + (wx - view.cx) * view.scale;
    const toSY = (wy: number) => h / 2 - (wy - view.cy) * view.scale;
    const xMin = view.cx - w / (2 * view.scale);
    const xMax = view.cx + w / (2 * view.scale);
    const yMin = view.cy - h / (2 * view.scale);
    const yMax = view.cy + h / (2 * view.scale);

    const rawStep = 70 / view.scale;
    const mag = Math.pow(10, Math.floor(Math.log10(rawStep)));
    const normStep = rawStep / mag;
    const step = normStep < 1.5 ? mag : normStep < 3.5 ? 2 * mag : normStep < 7.5 ? 5 * mag : 10 * mag;

    // Grid (batched in single path!)
    if (st.showGrid) {
      ctx.strokeStyle = isLight ? 'rgba(15, 23, 42, 0.09)' : 'rgba(148, 163, 184, 0.14)';
      ctx.lineWidth = 1;
      ctx.beginPath();
      for (let gx = Math.floor(xMin / step) * step; gx <= xMax; gx += step) {
        const sx = toSX(gx);
        ctx.moveTo(sx, 0);
        ctx.lineTo(sx, h);
      }
      for (let gy = Math.floor(yMin / step) * step; gy <= yMax; gy += step) {
        const sy = toSY(gy);
        ctx.moveTo(0, sy);
        ctx.lineTo(w, sy);
      }
      ctx.stroke();
    }

    // Oxy Axes
    if (st.showAxes) {
      const ox = toSX(0);
      const oy = toSY(0);
      ctx.strokeStyle = isLight ? 'rgba(15, 23, 42, 0.72)' : 'rgba(241, 245, 249, 0.65)';
      ctx.lineWidth = 1.8;
      ctx.beginPath();
      ctx.moveTo(0, oy);
      ctx.lineTo(w, oy);
      ctx.moveTo(ox, 0);
      ctx.lineTo(ox, h);
      ctx.stroke();

      ctx.font = '600 11px "JetBrains Mono", monospace';
      ctx.fillStyle = isLight ? '#475569' : '#94a3b8';
      for (let gx = Math.floor(xMin / step) * step; gx <= xMax; gx += step) {
        if (Math.abs(gx) < step * 0.1) continue;
        ctx.fillText(format2DNum(gx), toSX(gx) + 3, Math.min(h - 8, Math.max(16, oy + 14)));
      }
      for (let gy = Math.floor(yMin / step) * step; gy <= yMax; gy += step) {
        if (Math.abs(gy) < step * 0.1) continue;
        ctx.fillText(format2DNum(gy), Math.min(w - 32, Math.max(8, ox + 6)), toSY(gy) - 4);
      }
    }

    // Drawn 2D Objects
    for (const obj of st.snapshot.objects) {
      ctx.save();
      ctx.strokeStyle = obj.color;
      ctx.fillStyle = `${obj.color}24`;
      ctx.lineWidth = 2.3;

      if (obj.kind === 'polygon') {
        const pts = obj.pointIds.map((id) => st.pointMap.get(id)).filter((p): p is Geo2DPoint => Boolean(p));
        if (pts.length >= 3) {
          ctx.beginPath();
          pts.forEach((pt, idx) => {
            if (idx === 0) ctx.moveTo(toSX(pt.x), toSY(pt.y));
            else ctx.lineTo(toSX(pt.x), toSY(pt.y));
          });
          ctx.closePath();
          ctx.fill();
          ctx.stroke();
        }
      } else if (obj.kind === 'segment') {
        const a = st.pointMap.get(obj.p1Id);
        const b = st.pointMap.get(obj.p2Id);
        if (a && b) {
          if (obj.dashed) ctx.setLineDash([6, 5]);
          ctx.beginPath();
          ctx.moveTo(toSX(a.x), toSY(a.y));
          ctx.lineTo(toSX(b.x), toSY(b.y));
          ctx.stroke();
          if (st.showMeasurements) {
            const mx = (toSX(a.x) + toSX(b.x)) / 2;
            const my = (toSY(a.y) + toSY(b.y)) / 2;
            ctx.fillStyle = isLight ? '#0f172a' : '#e2e8f0';
            ctx.font = '700 11px "JetBrains Mono", monospace';
            ctx.fillText(Math.hypot(b.x - a.x, b.y - a.y).toFixed(2), mx + 5, my - 5);
          }
        }
      } else if (obj.kind === 'line') {
        const a = st.pointMap.get(obj.p1Id);
        const b = st.pointMap.get(obj.p2Id);
        if (a && b) {
          const dx = b.x - a.x;
          const dy = b.y - a.y;
          const len = Math.hypot(dx, dy);
          if (len > 1e-5) {
            const scaleExt = 400;
            ctx.beginPath();
            ctx.moveTo(toSX(a.x - (dx / len) * scaleExt), toSY(a.y - (dy / len) * scaleExt));
            ctx.lineTo(toSX(a.x + (dx / len) * scaleExt), toSY(a.y + (dy / len) * scaleExt));
            ctx.stroke();
          }
        }
      } else if (obj.kind === 'ray') {
        const a = st.pointMap.get(obj.startId);
        const b = st.pointMap.get(obj.throughId);
        if (a && b) {
          const dx = b.x - a.x;
          const dy = b.y - a.y;
          const len = Math.hypot(dx, dy);
          if (len > 1e-5) {
            ctx.beginPath();
            ctx.moveTo(toSX(a.x), toSY(a.y));
            ctx.lineTo(toSX(a.x + (dx / len) * 400), toSY(a.y + (dy / len) * 400));
            ctx.stroke();
          }
        }
      } else if (obj.kind === 'vector') {
        const a = st.pointMap.get(obj.fromId);
        const b = st.pointMap.get(obj.toId);
        if (a && b) {
          const sx1 = toSX(a.x);
          const sy1 = toSY(a.y);
          const sx2 = toSX(b.x);
          const sy2 = toSY(b.y);
          ctx.beginPath();
          ctx.moveTo(sx1, sy1);
          ctx.lineTo(sx2, sy2);
          ctx.stroke();
          const ang = Math.atan2(sy2 - sy1, sx2 - sx1);
          ctx.fillStyle = obj.color;
          ctx.beginPath();
          ctx.moveTo(sx2, sy2);
          ctx.lineTo(sx2 - 12 * Math.cos(ang - 0.4), sy2 - 12 * Math.sin(ang - 0.4));
          ctx.lineTo(sx2 - 12 * Math.cos(ang + 0.4), sy2 - 12 * Math.sin(ang + 0.4));
          ctx.closePath();
          ctx.fill();
        }
      } else if (obj.kind === 'circle') {
        const c = st.pointMap.get(obj.centerId);
        const e = obj.edgePointId ? st.pointMap.get(obj.edgePointId) : null;
        if (c) {
          const r = e ? Math.hypot(e.x - c.x, e.y - c.y) : obj.fixedRadius || 2;
          ctx.beginPath();
          ctx.arc(toSX(c.x), toSY(c.y), r * view.scale, 0, Math.PI * 2);
          ctx.fill();
          ctx.stroke();
        }
      } else if (obj.kind === 'angle') {
        const a = st.pointMap.get(obj.pAId);
        const v = st.pointMap.get(obj.vertexId);
        const c = st.pointMap.get(obj.pCId);
        if (a && v && c) {
          const vx = toSX(v.x);
          const vy = toSY(v.y);
          const a1 = Math.atan2(toSY(a.y) - vy, toSX(a.x) - vx);
          const a2 = Math.atan2(toSY(c.y) - vy, toSX(c.x) - vx);
          let diff = a2 - a1;
          while (diff > Math.PI) diff -= Math.PI * 2;
          while (diff < -Math.PI) diff += Math.PI * 2;
          ctx.beginPath();
          ctx.arc(vx, vy, 24, a1, a1 + diff, diff < 0);
          ctx.stroke();
          const deg = angleDegrees2D(a, v, c);
          const midA = a1 + diff * 0.5;
          ctx.fillStyle = '#fbbf24';
          ctx.font = '800 11.5px "JetBrains Mono", monospace';
          ctx.fillText(`${deg.toFixed(1)}°`, vx + 30 * Math.cos(midA) - 10, vy + 30 * Math.sin(midA) + 4);
        }
      }
      ctx.restore();
    }

    // Functions, Derivatives & Cached Asymptotes / Key Points / Intersections
    const visibleFns = st.snapshot.functions.filter((f) => (f.visible || f.showDerivative) && f.expression.trim());
    const sampleCount = Math.min(720, Math.max(300, Math.round(w * 0.85)));

    // Resolve viewport-padded analysis cache (eliminates per-frame root-finding jank while panning!)
    const fnsVisibleOnly = visibleFns.filter((f) => f.visible);
    const cacheSig = `${st.snapshot.paramM.toFixed(3)}|${st.showAsymptotes ? 1 : 0}|${st.showKeyPoints ? 1 : 0}|${fnsVisibleOnly
      .map((f) => `${f.id}:${f.expression}`)
      .join(';')}`;

    let cache = analysisCacheRef.current;
    const scaleRatio = cache ? view.scale / cache.scale : 99;
    const cacheValid =
      cache !== null &&
      cache.signature === cacheSig &&
      xMin >= cache.xMin &&
      xMax <= cache.xMax &&
      scaleRatio >= 0.6 &&
      scaleRatio <= 1.65;

    if (!cache || !cacheValid) {
      const pad = (xMax - xMin) * 0.55;
      const cMin = xMin - pad;
      const cMax = xMax + pad;
      const asymptotesByFn = new Map<string, GraphAsymptote[]>();
      const keyPointsByFn = new Map<string, KeyPoint2D[]>();
      const intersections: { x: number; y: number }[] = [];

      for (const fn of fnsVisibleOnly) {
        if (st.showAsymptotes) {
          asymptotesByFn.set(fn.id, detectAsymptotes(fn.expression, st.snapshot.paramM, cMin, cMax, '#fbbf24'));
        }
        if (st.showKeyPoints) {
          keyPointsByFn.set(fn.id, detectKeyPoints(fn.expression, st.snapshot.paramM, cMin, cMax, fn.color));
        }
      }
      if (st.showKeyPoints && fnsVisibleOnly.length >= 2) {
        for (let i = 0; i < fnsVisibleOnly.length; i++) {
          for (let j = i + 1; j < fnsVisibleOnly.length; j++) {
            const inters = detectIntersections(
              fnsVisibleOnly[i].expression,
              fnsVisibleOnly[j].expression,
              st.snapshot.paramM,
              cMin,
              cMax
            );
            for (const pt of inters) intersections.push(pt);
          }
        }
      }

      cache = {
        signature: cacheSig,
        xMin: cMin,
        xMax: cMax,
        scale: view.scale,
        asymptotesByFn,
        keyPointsByFn,
        intersections
      };
      analysisCacheRef.current = cache;
    }

    for (const fn of visibleFns) {
      if (st.showAsymptotes && fn.visible) {
        const asyms = cache.asymptotesByFn.get(fn.id) || [];
        if (asyms.length > 0) {
          ctx.save();
          ctx.strokeStyle = 'rgba(251, 191, 36, 0.72)';
          ctx.setLineDash([6, 5]);
          ctx.lineWidth = 1.5;
          for (const a of asyms) {
            ctx.beginPath();
            if (a.kind === 'vertical') {
              const sx = toSX(a.x);
              ctx.moveTo(sx, 0);
              ctx.lineTo(sx, h);
            } else if (a.kind === 'horizontal') {
              const sy = toSY(a.y);
              ctx.moveTo(0, sy);
              ctx.lineTo(w, sy);
            } else {
              ctx.moveTo(toSX(xMin), toSY(a.a * xMin + a.b));
              ctx.lineTo(toSX(xMax), toSY(a.a * xMax + a.b));
            }
            ctx.stroke();
          }
          ctx.restore();
        }
      }

      if (fn.visible) {
        ctx.save();
        ctx.strokeStyle = fn.color;
        ctx.lineWidth = 2.6;
        ctx.beginPath();
        let down = false;
        let lastSy = 0;
        for (let i = 0; i <= sampleCount; i++) {
          const sx = (i / sampleCount) * w;
          const wx = view.cx + (sx - w / 2) / view.scale;
          const wy = evaluateMathExpr(fn.expression, wx, st.snapshot.paramM);
          if (!Number.isFinite(wy) || Math.abs(wy) > 1e4) {
            down = false;
            continue;
          }
          const sy = toSY(wy);
          if (down && Math.abs(sy - lastSy) > h * 1.35) down = false;
          if (!down) {
            ctx.moveTo(sx, sy);
            down = true;
          } else {
            ctx.lineTo(sx, sy);
          }
          lastSy = sy;
        }
        ctx.stroke();
        ctx.restore();
      }

      if (fn.showDerivative) {
        ctx.save();
        ctx.strokeStyle = fn.color;
        ctx.globalAlpha = 0.65;
        ctx.setLineDash([6, 5]);
        ctx.lineWidth = 1.9;
        ctx.beginPath();
        let down = false;
        let lastSy = 0;
        for (let i = 0; i <= sampleCount; i++) {
          const sx = (i / sampleCount) * w;
          const wx = view.cx + (sx - w / 2) / view.scale;
          const wy = numericalDerivative(fn.expression, wx, st.snapshot.paramM);
          if (!Number.isFinite(wy) || Math.abs(wy) > 1e4) {
            down = false;
            continue;
          }
          const sy = toSY(wy);
          if (down && Math.abs(sy - lastSy) > h * 1.35) down = false;
          if (!down) {
            ctx.moveTo(sx, sy);
            down = true;
          } else {
            ctx.lineTo(sx, sy);
          }
          lastSy = sy;
        }
        ctx.stroke();
        ctx.restore();
      }

      if (st.showKeyPoints && fn.visible) {
        const pts = cache.keyPointsByFn.get(fn.id) || [];
        for (const kp of pts) {
          if (kp.x < xMin || kp.x > xMax) continue;
          const sx = toSX(kp.x);
          const sy = toSY(kp.y);
          ctx.beginPath();
          ctx.arc(sx, sy, 4.3, 0, Math.PI * 2);
          ctx.fillStyle =
            kp.kind === 'root' || kp.kind === 'y_intercept'
              ? '#10b981'
              : kp.kind === 'inflection'
                ? '#a855f7'
                : '#fbbf24';
          ctx.fill();
          ctx.strokeStyle = '#ffffff';
          ctx.lineWidth = 1.3;
          ctx.stroke();
        }
      }
    }

    if (st.showKeyPoints && cache.intersections.length > 0) {
      for (const pt of cache.intersections) {
        if (pt.x < xMin || pt.x > xMax) continue;
        ctx.beginPath();
        ctx.arc(toSX(pt.x), toSY(pt.y), 5, 0, Math.PI * 2);
        ctx.fillStyle = '#f43f5e';
        ctx.fill();
        ctx.strokeStyle = '#ffffff';
        ctx.lineWidth = 1.6;
        ctx.stroke();
      }
    }

    // Draw interactive Points on top
    ctx.font = '800 12.5px "Plus Jakarta Sans", sans-serif';
    for (const pt of st.snapshot.points) {
      const sx = toSX(pt.x);
      const sy = toSY(pt.y);
      const isPending = st.pendingPointIds.includes(pt.id);
      ctx.beginPath();
      ctx.arc(sx, sy, isPending ? 6.5 : 4.8, 0, Math.PI * 2);
      ctx.fillStyle = isPending ? '#fbbf24' : pt.color;
      ctx.fill();
      ctx.strokeStyle = '#ffffff';
      ctx.lineWidth = 1.8;
      ctx.stroke();

      ctx.fillStyle = isLight ? '#0f172a' : '#f8fafc';
      const lbl = st.showPointCoords ? `${pt.name}(${format2DNum(pt.x)};${format2DNum(pt.y)})` : pt.name;
      ctx.fillText(lbl, sx + 8, sy - 6);
    }

    // Trace marker & tangent line
    if (st.activeTool === 'trace' && st.primaryFn) {
      const ty = evaluateMathExpr(st.primaryFn.expression, st.traceX, st.snapshot.paramM);
      const slope = numericalDerivative(st.primaryFn.expression, st.traceX, st.snapshot.paramM);
      if (Number.isFinite(ty)) {
        const sx = toSX(st.traceX);
        const sy = toSY(ty);
        if (st.showTangent && Number.isFinite(slope)) {
          ctx.save();
          ctx.strokeStyle = '#f59e0b';
          ctx.lineWidth = 1.8;
          ctx.setLineDash([6, 4]);
          ctx.beginPath();
          ctx.moveTo(toSX(xMin), toSY(ty + slope * (xMin - st.traceX)));
          ctx.lineTo(toSX(xMax), toSY(ty + slope * (xMax - st.traceX)));
          ctx.stroke();
          ctx.restore();
        }
        ctx.beginPath();
        ctx.arc(sx, sy, 6.2, 0, Math.PI * 2);
        ctx.fillStyle = '#f59e0b';
        ctx.fill();
        ctx.strokeStyle = '#ffffff';
        ctx.lineWidth = 2;
        ctx.stroke();
      }
    }

    ctx.restore();
  }, []);

  const scheduleRender = useCallback(() => {
    if (rafRef.current !== null) return;
    rafRef.current = requestAnimationFrame(() => {
      rafRef.current = null;
      paintCanvasNow();
    });
  }, [paintCanvasNow]);

  // Attach ResizeObserver ONCE on mount (zero teardown/re-bind on pan or zoom)
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    paintCanvasNow();
    const ro = new ResizeObserver(() => scheduleRender());
    ro.observe(canvas);
    return () => {
      ro.disconnect();
      if (rafRef.current !== null) {
        cancelAnimationFrame(rafRef.current);
        rafRef.current = null;
      }
    };
  }, [paintCanvasNow, scheduleRender]);

  // Repaint when React scene/display state changes
  useEffect(() => {
    scheduleRender();
  }, [
    scheduleRender,
    snapshot,
    pointMap,
    activeTool,
    traceX,
    pendingPointIds,
    showAxes,
    showGrid,
    showPointCoords,
    showMeasurements,
    showKeyPoints,
    showAsymptotes,
    showTangent,
    themeMode,
    primaryFn
  ]);

  // Coordinate helpers reading viewRef directly
  const screenToWorld = (clientX: number, clientY: number) => {
    const canvas = canvasRef.current;
    if (!canvas) return { x: 0, y: 0 };
    const rect = canvas.getBoundingClientRect();
    const view = viewRef.current;
    const wx = view.cx + (clientX - rect.left - rect.width / 2) / view.scale;
    const wy = view.cy - (clientY - rect.top - rect.height / 2) / view.scale;
    return { x: wx, y: wy };
  };

  const findHitPoint = (wx: number, wy: number): Geo2DPoint | null => {
    const hitTol = (18 * sensitivity.touchRadius) / viewRef.current.scale;
    let best: Geo2DPoint | null = null;
    let bestD = hitTol;
    for (const pt of snapshot.points) {
      const d = Math.hypot(pt.x - wx, pt.y - wy);
      if (d < bestD) {
        best = pt;
        bestD = d;
      }
    }
    return best;
  };

  const snapCoord = (v: number) => (snapToGrid ? Math.round(v * 2) / 2 : Math.round(v * 100) / 100);

  const handleCanvasToolTap = (wx: number, wy: number) => {
    const existing = findHitPoint(wx, wy);

    if (activeTool === 'point') {
      const name = next2DPointName(snapshot.points);
      commit((p) => ({
        ...p,
        points: [...p.points, { id: `pt_${Date.now()}`, name, x: snapCoord(wx), y: snapCoord(wy), color: accentHex }]
      }));
      return;
    }

    let targetId = existing?.id;
    let newlyCreatedPt: Geo2DPoint | null = null;
    if (!targetId) {
      const name = next2DPointName(snapshot.points);
      newlyCreatedPt = { id: `pt_${Date.now()}_${Math.random().toString(36).slice(2, 5)}`, name, x: snapCoord(wx), y: snapCoord(wy), color: accentHex };
      targetId = newlyCreatedPt.id;
    }

    if (activeTool === 'polygon') {
      if (pendingPointIds.length >= 3 && existing && existing.id === pendingPointIds[0]) {
        const polyIds = [...pendingPointIds];
        commit((p) => ({
          ...p,
          objects: [...p.objects, { id: `poly_${Date.now()}`, kind: 'polygon', pointIds: polyIds, color: '#6366f1' }]
        }));
        setPendingPointIds([]);
        setToast('Đã khép kín đa giác!');
        return;
      }
      if (newlyCreatedPt) {
        const ptToAdd = newlyCreatedPt;
        commit((p) => ({ ...p, points: [...p.points, ptToAdd] }));
      }
      setPendingPointIds((prev) => [...prev, targetId!]);
      return;
    }

    const nextPending = [...pendingPointIds, targetId];
    const needed = activeTool === 'angle' ? 3 : 2;

    if (nextPending.length < needed) {
      if (newlyCreatedPt) {
        const ptToAdd = newlyCreatedPt;
        commit((p) => ({ ...p, points: [...p.points, ptToAdd] }));
      }
      setPendingPointIds(nextPending);
      return;
    }

    const newObj: Geo2DObject | null =
      activeTool === 'segment'
        ? { id: `seg_${Date.now()}`, kind: 'segment', p1Id: nextPending[0], p2Id: nextPending[1], color: '#f8fafc' }
        : activeTool === 'line'
          ? { id: `lin_${Date.now()}`, kind: 'line', p1Id: nextPending[0], p2Id: nextPending[1], color: '#38bdf8' }
          : activeTool === 'ray'
            ? { id: `ray_${Date.now()}`, kind: 'ray', startId: nextPending[0], throughId: nextPending[1], color: '#a855f7' }
            : activeTool === 'vector'
              ? { id: `vec_${Date.now()}`, kind: 'vector', fromId: nextPending[0], toId: nextPending[1], color: '#f43f5e' }
              : activeTool === 'circle'
                ? { id: `cir_${Date.now()}`, kind: 'circle', centerId: nextPending[0], edgePointId: nextPending[1], color: '#10b981' }
                : activeTool === 'angle'
                  ? { id: `ang_${Date.now()}`, kind: 'angle', pAId: nextPending[0], vertexId: nextPending[1], pCId: nextPending[2], color: '#fbbf24' }
                  : null;

    commit((p) => ({
      ...p,
      points: newlyCreatedPt ? [...p.points, newlyCreatedPt] : p.points,
      objects: newObj ? [...p.objects, newObj] : p.objects
    }));
    setPendingPointIds([]);
  };

  const handlePointerDown = (e: React.PointerEvent<HTMLCanvasElement>) => {
    e.currentTarget.setPointerCapture(e.pointerId);
    pointersRef.current.set(e.pointerId, { x: e.clientX, y: e.clientY });

    if (pointersRef.current.size === 2) {
      const pts = Array.from(pointersRef.current.values());
      dragRef.current = {
        mode: 'none',
        pointId: null,
        x: e.clientX,
        y: e.clientY,
        pinchDist: Math.hypot(pts[1].x - pts[0].x, pts[1].y - pts[0].y)
      };
      return;
    }

    const { x: wx, y: wy } = screenToWorld(e.clientX, e.clientY);
    if (activeTool === 'trace') {
      setTraceX(wx);
      dragRef.current = { mode: 'trace', pointId: null, x: e.clientX, y: e.clientY, pinchDist: null };
      return;
    }

    if (activeTool === 'pan') {
      const hit = findHitPoint(wx, wy);
      if (hit) {
        dragRef.current = { mode: 'point', pointId: hit.id, x: e.clientX, y: e.clientY, pinchDist: null };
      } else {
        dragRef.current = { mode: 'pan', pointId: null, x: e.clientX, y: e.clientY, pinchDist: null };
      }
      return;
    }

    handleCanvasToolTap(wx, wy);
  };

  const handlePointerMove = (e: React.PointerEvent<HTMLCanvasElement>) => {
    if (!pointersRef.current.has(e.pointerId)) return;
    pointersRef.current.set(e.pointerId, { x: e.clientX, y: e.clientY });

    if (pointersRef.current.size === 2) {
      const pts = Array.from(pointersRef.current.values());
      const dist = Math.hypot(pts[1].x - pts[0].x, pts[1].y - pts[0].y);
      if (dragRef.current.pinchDist && dist > 10) {
        const rawFactor = dist / dragRef.current.pinchDist;
        const factor = 1 + (rawFactor - 1) * sensitivity.zoom;
        viewRef.current.scale = Math.min(2000, Math.max(4, viewRef.current.scale * factor));
        scheduleRender();
      }
      dragRef.current.pinchDist = dist;
      return;
    }

    if (dragRef.current.mode === 'trace') {
      const { x: wx } = screenToWorld(e.clientX, e.clientY);
      setTraceX(wx);
      return;
    }

    if (dragRef.current.mode === 'point' && dragRef.current.pointId) {
      const { x: wx, y: wy } = screenToWorld(e.clientX, e.clientY);
      const id = dragRef.current.pointId;
      commit(
        (p) => ({
          ...p,
          points: p.points.map((pt) => (pt.id === id ? { ...pt, x: snapCoord(wx), y: snapCoord(wy) } : pt))
        }),
        false
      );
      return;
    }

    if (dragRef.current.mode === 'pan') {
      const dx = (e.clientX - dragRef.current.x) * sensitivity.pan;
      const dy = (e.clientY - dragRef.current.y) * sensitivity.pan;
      dragRef.current.x = e.clientX;
      dragRef.current.y = e.clientY;
      viewRef.current.cx -= dx / viewRef.current.scale;
      viewRef.current.cy += dy / viewRef.current.scale;
      scheduleRender();
    }
  };

  const handlePointerUp = (e: React.PointerEvent<HTMLCanvasElement>) => {
    pointersRef.current.delete(e.pointerId);
    if (pointersRef.current.size === 0) {
      dragRef.current.mode = 'none';
      dragRef.current.pointId = null;
      dragRef.current.pinchDist = null;
    }
  };

  const handleWheel = (e: React.WheelEvent<HTMLCanvasElement>) => {
    const stepFactor = 1 + 0.1 * sensitivity.zoom;
    const factor = e.deltaY > 0 ? 1 / stepFactor : stepFactor;
    viewRef.current.scale = Math.min(2000, Math.max(4, viewRef.current.scale * factor));
    scheduleRender();
  };

  const addFunctionExpr = (expr: string) => {
    const cleaned = expr.trim();
    if (!cleaned) return;
    commit((p) => ({
      ...p,
      functions: [
        ...p.functions,
        {
          id: `fn-${Date.now()}`,
          label: `f${p.functions.length + 1}(x)`,
          expression: cleaned,
          color: FN_COLORS[p.functions.length % FN_COLORS.length],
          visible: true,
          showDerivative: false
        }
      ]
    }));
  };

  const getBaseFilename = () => exportFilename.trim().replace(/[^a-zA-Z0-9_-]/g, '_') || 'tooltoan-2d-oxy';

  const exportPng = () => {
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
  };

  const exportJson = () => {
    const blob = new Blob([JSON.stringify({ snapshot, view: viewRef.current }, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${getBaseFilename()}.json`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const importJson = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      try {
        const parsed = JSON.parse(String(reader.result));
        if (parsed.snapshot) commit(() => parsed.snapshot);
        if (parsed.view) {
          viewRef.current = { ...viewRef.current, ...parsed.view };
          scheduleRender();
        }
        setToast('Đã nhập bài vẽ 2D từ JSON.');
      } catch {
        setToast('File JSON 2D không hợp lệ.');
      }
    };
    reader.readAsText(file);
    e.target.value = '';
  };

  const traceY = primaryFn ? evaluateMathExpr(primaryFn.expression, traceX, snapshot.paramM) : NaN;
  const traceSlope = primaryFn ? numericalDerivative(primaryFn.expression, traceX, snapshot.paramM) : NaN;

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
          cursor: activeTool === 'pan' ? 'grab' : 'crosshair'
        }}
      />

      {/* Top Floating Bar */}
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
            style={{ pointerEvents: 'auto', display: 'flex', alignItems: 'center', gap: 8, padding: '6px 12px', borderRadius: 999 }}
          >
            <Sigma size={17} color={accentHex} />
            <div>
              <div style={{ fontSize: 12.5, fontWeight: 800 }}>Đồ thị & Hình phẳng Oxy</div>
              <div className="mono" style={{ fontSize: 10.5, color: 'var(--text-secondary)' }}>
                {activeToolInfo.label} {primaryFn ? `• y=${primaryFn.expression}` : ''}
              </div>
            </div>
          </div>

          <div
            className="glass-surface"
            style={{ pointerEvents: 'auto', display: 'flex', alignItems: 'center', gap: 3, padding: 4, borderRadius: 999 }}
          >
            <button
              type="button"
              onClick={() => setDrawerOpen((v) => !v)}
              className={`chip-btn glass-press${drawerOpen ? ' active' : ''}`}
              style={{ minHeight: 34, padding: '4px 11px' }}
            >
              <SlidersHorizontal size={14} />
              <span>Công cụ 2D</span>
            </button>
            <button
              type="button"
              onClick={handleUndo}
              disabled={undoStack.length === 0}
              className="icon-btn glass-press"
              style={{ width: 34, height: 34, opacity: undoStack.length ? 1 : 0.35 }}
              title="Hoàn tác (Ctrl+Z)"
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
            >
              <Redo2 size={15} />
            </button>
            <button
              type="button"
              onClick={() => {
                viewRef.current = { cx: 0, cy: 0, scale: 56 };
                scheduleRender();
              }}
              className="icon-btn glass-press"
              style={{ width: 34, height: 34 }}
              title="Về gốc tọa độ"
            >
              <RotateCcw size={15} />
            </button>
            <button
              type="button"
              onClick={exportPng}
              className="icon-btn glass-press"
              style={{ width: 34, height: 34 }}
              title="Tải ảnh PNG"
            >
              <Download size={15} />
            </button>
            <button
              type="button"
              onClick={onToggleImmersive}
              className="icon-btn glass-press"
              style={{ width: 34, height: 34 }}
              title="Toàn màn hình (F)"
            >
              <Maximize2 size={15} />
            </button>
          </div>
        </header>
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

      {/* Bottom 10-Tool Strip + Parameter m (Custom bounds + Play/Pause) + Trace / Integral Readout */}
      {!isImmersive && (
        <div
          style={{
            position: 'absolute',
            left: 10,
            right: 10,
            bottom: 80,
            display: 'flex',
            flexDirection: 'column',
            gap: 6,
            pointerEvents: 'none',
            zIndex: 14
          }}
        >
          <div style={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: 'center', gap: 6 }}>
            <div
              className="glass-surface"
              style={{ pointerEvents: 'auto', display: 'inline-flex', alignItems: 'center', gap: 8, padding: '5px 12px', borderRadius: 999 }}
            >
              <button
                type="button"
                className="icon-btn"
                style={{ width: 26, height: 26 }}
                onClick={() => setPlayParamM((v) => !v)}
                title="Chạy tự động tham số m (Space)"
              >
                {playParamM ? <Pause size={13} color={accentHex} /> : <Play size={13} />}
              </button>
              <span className="mono" style={{ fontSize: 11.5, fontWeight: 700, color: accentHex }}>
                m={snapshot.paramM.toFixed(1)}
              </span>
              <input
                type="range"
                className="glass-slider"
                style={{ width: 105 }}
                min={mMin}
                max={mMax}
                step={0.1}
                value={Math.min(mMax, Math.max(mMin, snapshot.paramM))}
                onChange={(e) => setSnapshot((p) => ({ ...p, paramM: parseFloat(e.target.value) }))}
              />
            </div>

            {activeTool === 'trace' && primaryFn && Number.isFinite(traceY) && (
              <div
                className="glass-surface mono"
                style={{ pointerEvents: 'auto', padding: '6px 12px', borderRadius: 999, fontSize: 11.5, display: 'inline-flex', gap: 8 }}
              >
                <span>x={traceX.toFixed(2)}</span>
                <span>f(x)={traceY.toFixed(2)}</span>
                <span style={{ color: '#fbbf24' }}>f&apos;(x)={Number.isFinite(traceSlope) ? traceSlope.toFixed(2) : '—'}</span>
              </div>
            )}
          </div>

          <div
            className="glass-surface"
            style={{
              pointerEvents: 'auto',
              borderRadius: 999,
              padding: '4px 6px',
              display: 'flex',
              alignItems: 'center',
              gap: 4,
              overflowX: 'auto'
            }}
          >
            {GEO2D_TOOLS.map((tool) => (
              <button
                key={tool.id}
                type="button"
                onClick={() => {
                  setActiveTool(tool.id);
                  setPendingPointIds([]);
                }}
                className={`chip-btn glass-press${activeTool === tool.id ? ' active' : ''}`}
                style={{ minHeight: 30, padding: '3px 10px', fontSize: 11, flexShrink: 0 }}
              >
                {tool.label}
              </button>
            ))}
          </div>
        </div>
      )}

      {toast && (
        <div
          className="glass-surface"
          style={{
            position: 'absolute',
            top: 66,
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

      {/* Right-Side Quick Controls Rail (matching 3D Studio HUD) */}
      {!isImmersive && (
        <div className="glass-surface studio-right-rail">
          <button
            type="button"
            className={`studio-rail-btn ${showAxes ? 'active' : ''}`}
            onClick={() => setShowAxes((v) => !v)}
            title="Bật/tắt trục tọa độ Oxy"
          >
            <Sigma size={15} />
            <span>Oxy</span>
          </button>
          <button
            type="button"
            className={`studio-rail-btn ${showGrid ? 'active' : ''}`}
            onClick={() => setShowGrid((v) => !v)}
            title="Bật/tắt lưới tọa độ"
          >
            <Grid size={15} />
            <span>Lưới</span>
          </button>
          <button
            type="button"
            className={`studio-rail-btn ${snapToGrid ? 'snap-on' : ''}`}
            onClick={() => setSnapToGrid((v) => !v)}
            title="Bật/tắt hút lưới 0.5"
          >
            <Magnet size={15} />
            <span>Hút</span>
          </button>
          <div style={{ width: 22, height: 1, background: 'var(--border-subtle)', margin: '2px 0' }} />
          <button
            type="button"
            className="studio-rail-btn"
            onClick={() => {
              viewRef.current.scale = Math.min(2000, viewRef.current.scale * 1.22);
              scheduleRender();
            }}
            title="Phóng to"
            aria-label="Phóng to"
          >
            <Plus size={16} />
          </button>
          <button
            type="button"
            className="studio-rail-btn"
            onClick={() => {
              viewRef.current.scale = Math.max(4, viewRef.current.scale * 0.82);
              scheduleRender();
            }}
            title="Thu nhỏ"
            aria-label="Thu nhỏ"
          >
            <Minus size={16} />
          </button>
        </div>
      )}

      {/* 4-Tab 2D Responsive Inspector (Side panel on desktop / Bottom sheet on mobile) */}
      {drawerOpen && !isImmersive && (
        <aside className="glass-surface studio-inspector-panel">
          <div className="studio-inspector-handle" />
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
                { id: 'functions' as const, label: 'Hàm số' },
                { id: 'presets' as const, label: 'Hình mẫu' },
                { id: 'objects' as const, label: 'Đo đạc' },
                { id: 'settings' as const, label: 'Cài đặt' }
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
            {/* TAB 1: FUNCTIONS + SYMBOLIC f'(x) + INTEGRAL + VALUE TABLE + 31 TEMPLATES */}
            {activeTab === 'functions' && (
              <>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                  <div style={{ display: 'flex', gap: 6 }}>
                    <input
                      type="text"
                      className="glass-input mono"
                      style={{ fontSize: 12.5, padding: '8px 10px' }}
                      placeholder="y = x^3 - 3*m*x + 1, |x-1|, log(3; x)..."
                      value={draftExpr}
                      onChange={(e) => setDraftExpr(e.target.value)}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter' && draftExpr.trim()) {
                          addFunctionExpr(draftExpr);
                          setDraftExpr('');
                        }
                      }}
                    />
                    <button
                      type="button"
                      className="primary-btn"
                      style={{ minHeight: 38, padding: '0 12px' }}
                      onClick={() => {
                        addFunctionExpr(draftExpr);
                        setDraftExpr('');
                      }}
                    >
                      <Plus size={15} />
                      <span>Vẽ</span>
                    </button>
                  </div>

                  <div style={{ display: 'flex', gap: 4, overflowX: 'auto', paddingBottom: 2 }}>
                    {QUICK_MATH_KEYS.map((k) => (
                      <button
                        key={k}
                        type="button"
                        className="chip-btn mono"
                        style={{ minHeight: 28, padding: '2px 8px', fontSize: 11, flexShrink: 0 }}
                        onClick={() => setDraftExpr((prev) => prev + k)}
                      >
                        {k.trim()}
                      </button>
                    ))}
                  </div>
                </div>

                {/* Active Functions with Symbolic f'(x) (Item 7), BBT & Casio Value Table (Item 20) */}
                <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                  <div style={{ fontSize: 11.5, fontWeight: 800, color: 'var(--text-secondary)' }}>
                    Danh sách hàm số ({snapshot.functions.length})
                  </div>
                  {snapshot.functions.map((fn) => {
                    const symbolicDeriv = deriveSymbolicExpr(fn.expression);
                    return (
                      <div
                        key={fn.id}
                        style={{
                          padding: 9,
                          borderRadius: 14,
                          border: `1px solid ${fn.color}66`,
                          background: 'rgba(148,163,184,0.08)',
                          display: 'flex',
                          flexDirection: 'column',
                          gap: 6
                        }}
                      >
                        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                          <button
                            type="button"
                            onClick={() => {
                              const nextCol = FN_COLORS[(FN_COLORS.indexOf(fn.color) + 1) % FN_COLORS.length];
                              commit((p) => ({
                                ...p,
                                functions: p.functions.map((item) => (item.id === fn.id ? { ...item, color: nextCol } : item))
                              }));
                            }}
                            style={{ width: 16, height: 16, borderRadius: '50%', background: fn.color, border: '1.5px solid #fff', cursor: 'pointer', flexShrink: 0 }}
                            title="Đổi màu"
                          />
                          <input
                            type="text"
                            className="glass-input mono"
                            style={{ padding: '6px 8px', fontSize: 12 }}
                            value={fn.expression}
                            onChange={(e) =>
                              commit(
                                (p) => ({
                                  ...p,
                                  functions: p.functions.map((item) => (item.id === fn.id ? { ...item, expression: e.target.value } : item))
                                }),
                                false
                              )
                            }
                          />
                        </div>

                        {/* Item 7: Symbolic Derivative Formula */}
                        {symbolicDeriv && (
                          <div
                            className="mono"
                            style={{
                              fontSize: 11,
                              color: '#fbbf24',
                              padding: '4px 8px',
                              borderRadius: 8,
                              background: 'rgba(2,6,23,0.34)',
                              overflowX: 'auto',
                              whiteSpace: 'nowrap'
                            }}
                          >
                            f&apos;(x) = {symbolicDeriv}
                          </div>
                        )}

                        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 5 }}>
                          <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
                            <button
                              type="button"
                              className={`chip-btn${fn.showDerivative ? ' active' : ''}`}
                              style={{ minHeight: 26, padding: '2px 8px', fontSize: 10.5 }}
                              onClick={() =>
                                commit((p) => ({
                                  ...p,
                                  functions: p.functions.map((item) => (item.id === fn.id ? { ...item, showDerivative: !item.showDerivative } : item))
                                }))
                              }
                            >
                              Vẽ f&apos;(x)
                            </button>
                            <button
                              type="button"
                              className="chip-btn active"
                              style={{ minHeight: 26, padding: '2px 8px', fontSize: 10.5 }}
                              onClick={() => {
                                const res = analyzeVariationTable(fn.expression, snapshot.paramM);
                                if (res) setVariationData(res);
                                else setToast('Không thể phân tích bảng biến thiên cho biểu thức này.');
                              }}
                            >
                              <Table2 size={12} />
                              <span>BBT</span>
                            </button>
                          </div>

                          <div style={{ display: 'flex', gap: 3 }}>
                            <button
                              type="button"
                              className="icon-btn"
                              style={{ width: 28, height: 28 }}
                              onClick={() =>
                                commit((p) => ({
                                  ...p,
                                  functions: p.functions.map((item) => (item.id === fn.id ? { ...item, visible: !item.visible } : item))
                                }))
                              }
                            >
                              {fn.visible ? <Eye size={14} /> : <EyeOff size={14} />}
                            </button>
                            <button
                              type="button"
                              className="icon-btn"
                              style={{ width: 28, height: 28 }}
                              onClick={() =>
                                commit((p) => ({ ...p, functions: p.functions.filter((item) => item.id !== fn.id) }))
                              }
                            >
                              <Trash2 size={14} color="#f43f5e" />
                            </button>
                          </div>
                        </div>
                      </div>
                    );
                  })}
                </div>

                {/* 31-Template Categorized Library */}
                <div style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
                  <div style={{ fontSize: 11.5, fontWeight: 800, color: '#10b981' }}>
                    Thư viện 31 hàm số THPT (6 nhóm)
                  </div>
                  <div style={{ display: 'flex', gap: 5, overflowX: 'auto', paddingBottom: 2 }}>
                    {FUNCTION_CATEGORIES.map((c) => (
                      <button
                        key={c.id}
                        type="button"
                        className={`chip-btn${fnCategory === c.id ? ' active' : ''}`}
                        style={{ minHeight: 28, padding: '2px 9px', fontSize: 11, flexShrink: 0 }}
                        onClick={() => setFnCategory(c.id)}
                      >
                        {c.title}
                      </button>
                    ))}
                  </div>
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 6 }}>
                    {FUNCTION_LIBRARY.filter((t) => t.category === fnCategory).map((tpl) => (
                      <button
                        key={tpl.expr}
                        type="button"
                        className="glass-press"
                        onClick={() => addFunctionExpr(tpl.expr)}
                        style={{
                          textAlign: 'left',
                          padding: '7px 9px',
                          borderRadius: 11,
                          border: '1px solid var(--border-subtle)',
                          background: 'rgba(2,6,23,0.35)',
                          color: 'var(--text-primary)',
                          cursor: 'pointer'
                        }}
                      >
                        <div className="mono" style={{ fontSize: 11.5, fontWeight: 700, color: accentHex }}>y = {tpl.expr}</div>
                        <div style={{ fontSize: 10, color: 'var(--text-secondary)', marginTop: 2 }}>{tpl.note}</div>
                      </button>
                    ))}
                  </div>
                </div>
              </>
            )}

            {/* TAB 2: 9 PRESETS & ADD POINT (X;Y) */}
            {activeTab === 'presets' && (
              <>
                <div style={{ padding: 11, borderRadius: 15, border: '1px solid var(--border-subtle)', background: 'rgba(148,163,184,0.08)', display: 'flex', flexDirection: 'column', gap: 8 }}>
                  <div style={{ fontSize: 11.5, fontWeight: 800, color: accentHex }}>CHẤM ĐIỂM THEO TỌA ĐỘ (x; y)</div>
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 6 }}>
                    <input className="glass-input" style={{ padding: '7px', fontSize: 12 }} value={ptNameInput} maxLength={3} onChange={(e) => setPtNameInput(e.target.value)} placeholder="Tên" />
                    <input className="glass-input mono" style={{ padding: '7px', fontSize: 12 }} value={ptXInput} onChange={(e) => setPtXInput(e.target.value)} placeholder="x" />
                    <input className="glass-input mono" style={{ padding: '7px', fontSize: 12 }} value={ptYInput} onChange={(e) => setPtYInput(e.target.value)} placeholder="y" />
                  </div>
                  <button
                    type="button"
                    className="primary-btn"
                    style={{ minHeight: 36, fontSize: 12 }}
                    onClick={() => {
                      const x = parseFloat(ptXInput.replace(',', '.')) || 0;
                      const y = parseFloat(ptYInput.replace(',', '.')) || 0;
                      const name = ptNameInput.trim() || next2DPointName(snapshot.points);
                      commit((p) => ({
                        ...p,
                        points: [...p.points, { id: `pt_${Date.now()}`, name, x, y, color: accentHex }]
                      }));
                      setToast(`Đã đặt điểm ${name}(${x}; ${y})`);
                    }}
                  >
                    + Thêm điểm trên Oxy
                  </button>
                </div>

                <div style={{ fontSize: 11.5, fontWeight: 800, color: 'var(--text-secondary)' }}>9 Hình phẳng mẫu chuẩn</div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                  {PRESET_2D_SHAPES.map((pr) => (
                    <button
                      key={pr.id}
                      type="button"
                      className="glass-press"
                      onClick={() => {
                        const built = buildPreset2DGeometry(pr.id);
                        commit((p) => ({ ...p, points: built.points, objects: built.objects }));
                        setToast(`Đã tải ${pr.title}`);
                      }}
                      style={{
                        textAlign: 'left',
                        padding: '9px 11px',
                        borderRadius: 13,
                        border: '1px solid var(--border-subtle)',
                        background: 'rgba(148,163,184,0.08)',
                        color: 'var(--text-primary)',
                        cursor: 'pointer'
                      }}
                    >
                      <div style={{ fontSize: 12.5, fontWeight: 700 }}>{pr.title}</div>
                      <div style={{ fontSize: 11, color: 'var(--text-secondary)' }}>{pr.subtitle}</div>
                    </button>
                  ))}
                </div>
              </>
            )}

            {/* TAB 3: OBJECTS, MEASUREMENTS & FULL VERIFY 2D (Item 9) */}
            {activeTab === 'objects' && (
              <>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ fontSize: 12, fontWeight: 800 }}>
                    Điểm ({snapshot.points.length}) & Hình ({snapshot.objects.length})
                  </span>
                  {(snapshot.points.length > 0 || snapshot.objects.length > 0) && (
                    <button
                      type="button"
                      className="chip-btn"
                      style={{ minHeight: 26, fontSize: 11, color: '#f43f5e' }}
                      onClick={() => commit((p) => ({ ...p, points: [], objects: [] }))}
                    >
                      Xóa hình 2D
                    </button>
                  )}
                </div>

                {snapshot.objects.length === 0 && snapshot.points.length === 0 ? (
                  <div style={{ fontSize: 12, color: 'var(--text-secondary)', textAlign: 'center', padding: 12 }}>
                    Chọn 1 trong 10 công cụ ở thanh dưới hoặc tải hình mẫu để vẽ và tự động đo đạc!
                  </div>
                ) : (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                    {snapshot.objects.map((obj) => {
                      let desc = '';
                      if (obj.kind === 'segment') {
                        const a = pointMap.get(obj.p1Id);
                        const b = pointMap.get(obj.p2Id);
                        desc = a && b ? `Đoạn ${a.name}${b.name} = ${Math.hypot(b.x - a.x, b.y - a.y).toFixed(2)}` : 'Đoạn thẳng';
                      } else if (obj.kind === 'line') {
                        const a = pointMap.get(obj.p1Id);
                        const b = pointMap.get(obj.p2Id);
                        desc = a && b ? `Đường ${a.name}${b.name}: ${lineEquation2D(a, b)}` : 'Đường thẳng';
                      } else if (obj.kind === 'ray') {
                        const a = pointMap.get(obj.startId);
                        const b = pointMap.get(obj.throughId);
                        desc = a && b ? `Tia ${a.name}${b.name}` : 'Tia';
                      } else if (obj.kind === 'vector') {
                        const a = pointMap.get(obj.fromId);
                        const b = pointMap.get(obj.toId);
                        desc =
                          a && b
                            ? `Vectơ ${a.name}${b.name}→ = (${format2DNum(b.x - a.x)}; ${format2DNum(b.y - a.y)}), |u|=${Math.hypot(b.x - a.x, b.y - a.y).toFixed(2)}`
                            : 'Vectơ';
                      } else if (obj.kind === 'circle') {
                        const c = pointMap.get(obj.centerId);
                        const e = obj.edgePointId ? pointMap.get(obj.edgePointId) : null;
                        const r = c && e ? Math.hypot(e.x - c.x, e.y - c.y) : obj.fixedRadius || 0;
                        desc = `Đường tròn (${c?.name || 'I'}; R=${r.toFixed(2)}) • S=${(Math.PI * r * r).toFixed(2)}`;
                      } else if (obj.kind === 'polygon') {
                        const pts = obj.pointIds.map((id) => pointMap.get(id)).filter((p): p is Geo2DPoint => Boolean(p));
                        const { area, perimeter } = polygonAreaPerimeter2D(pts);
                        desc = `Đa giác ${pts.map((p) => p.name).join('')} • P=${perimeter.toFixed(2)} • S=${area.toFixed(2)}`;
                      } else if (obj.kind === 'angle') {
                        const a = pointMap.get(obj.pAId);
                        const v = pointMap.get(obj.vertexId);
                        const c = pointMap.get(obj.pCId);
                        desc = a && v && c ? `∠${a.name}${v.name}${c.name} = ${angleDegrees2D(a, v, c).toFixed(1)}°` : 'Góc';
                      }
                      return (
                        <div
                          key={obj.id}
                          className="mono"
                          style={{
                            display: 'flex',
                            justifyContent: 'space-between',
                            alignItems: 'center',
                            gap: 6,
                            padding: '7px 9px',
                            borderRadius: 11,
                            background: 'rgba(2,6,23,0.38)',
                            border: '1px solid var(--border-subtle)',
                            fontSize: 11.5
                          }}
                        >
                          <span>{desc}</span>
                          <button
                            type="button"
                            className="icon-btn"
                            style={{ width: 24, height: 24 }}
                            onClick={() => commit((p) => ({ ...p, objects: p.objects.filter((o) => o.id !== obj.id) }))}
                          >
                            <X size={13} color="#f43f5e" />
                          </button>
                        </div>
                      );
                    })}
                  </div>
                )}

                {/* Item 9: Full 7-target Verify Mode 2D */}
                <div style={{ padding: 11, borderRadius: 15, border: '1px solid rgba(16,185,129,0.55)', background: 'rgba(16,185,129,0.09)', display: 'flex', flexDirection: 'column', gap: 8 }}>
                  <div style={{ fontSize: 11.5, fontWeight: 800, color: '#10b981' }}>KIỂM TRA ĐÁP ÁN 2D TOÀN DIỆN</div>
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4 }}>
                    {[
                      { id: 'fn_val' as const, label: 'f(x₀)' },
                      { id: 'extrema' as const, label: 'Cực trị' },
                      { id: 'segment' as const, label: 'Độ dài đoạn' },
                      { id: 'polygon_area' as const, label: 'S đa giác' },
                      { id: 'circle_radius' as const, label: 'Bán kính R' },
                      { id: 'angle' as const, label: 'Số đo góc' }
                    ].map((t) => (
                      <button
                        key={t.id}
                        type="button"
                        className={`chip-btn${verifyTarget === t.id ? ' active' : ''}`}
                        style={{ minHeight: 26, padding: '2px 8px', fontSize: 10.5 }}
                        onClick={() => {
                          setVerifyTarget(t.id);
                          setVerifyMsg(null);
                          setRevealVerifyAns(false);
                        }}
                      >
                        {t.label}
                      </button>
                    ))}
                  </div>

                  <div style={{ fontSize: 11, color: 'var(--text-secondary)' }}>{verifyExpectedInfo.hint}</div>

                  {verifyExpectedInfo.value !== null && (
                    <>
                      <div style={{ display: 'grid', gridTemplateColumns: verifyTarget === 'fn_val' ? '0.65fr 1.35fr auto' : '1fr auto', gap: 6 }}>
                        {verifyTarget === 'fn_val' && (
                          <input className="glass-input mono" style={{ padding: '7px', fontSize: 12 }} value={verifyX} onChange={(e) => setVerifyX(e.target.value)} placeholder="x₀" />
                        )}
                        <input
                          className="glass-input mono"
                          style={{ padding: '7px', fontSize: 12 }}
                          value={verifyAns}
                          onChange={(e) => {
                            setVerifyAns(e.target.value);
                            setVerifyMsg(null);
                          }}
                          placeholder="Nhập số hoặc biểu thức..."
                        />
                        <button
                          type="button"
                          className="primary-btn"
                          style={{ minHeight: 34, padding: '0 10px', background: '#10b981' }}
                          onClick={() => {
                            const ans = evaluateVerifyAnswer(verifyAns);
                            if (ans === null) {
                              setVerifyMsg({ ok: false, text: 'Biểu thức không hợp lệ.' });
                              return;
                            }
                            const exp = verifyExpectedInfo.value!;
                            if (isVerifyAnswerCorrect(exp, ans)) {
                              setVerifyMsg({ ok: true, text: `Chính xác! Kết quả khớp (${exp.toFixed(2)}${verifyExpectedInfo.unit}).` });
                            } else {
                              setVerifyMsg({ ok: false, text: `Chưa đúng — đáp án ${ans > exp ? 'nhỏ hơn' : 'lớn hơn'} số bạn nhập.` });
                            }
                          }}
                        >
                          <Check size={14} />
                        </button>
                      </div>
                      {verifyMsg && (
                        <div style={{ fontSize: 11.5, fontWeight: 700, color: verifyMsg.ok ? '#10b981' : '#f43f5e' }}>
                          {verifyMsg.text}
                        </div>
                      )}
                      <button
                        type="button"
                        className="chip-btn"
                        style={{ alignSelf: 'flex-start', minHeight: 26, fontSize: 10.5 }}
                        onClick={() => setRevealVerifyAns((v) => !v)}
                      >
                        {revealVerifyAns
                          ? `Đáp án chuẩn: ${verifyExpectedInfo.value.toFixed(3)}${verifyExpectedInfo.unit}`
                          : 'Xem đáp án chuẩn'}
                      </button>
                    </>
                  )}
                </div>
              </>
            )}

            {/* TAB 4: OXY DISPLAY, CUSTOM m BOUNDS, 2D SENSITIVITY & EXPORT/SHARE */}
            {activeTab === 'settings' && (
              <>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                  {[
                    { label: 'Hệ trục Oxy', val: showAxes, set: setShowAxes },
                    { label: 'Lưới tọa độ', val: showGrid, set: setShowGrid },
                    { label: 'Bắt lưới (Snap)', val: snapToGrid, set: setSnapToGrid },
                    { label: 'Hiện tọa độ điểm', val: showPointCoords, set: setShowPointCoords },
                    { label: 'Hiện số đo trên hình', val: showMeasurements, set: setShowMeasurements },
                    { label: 'Cực trị / Nghiệm / Giao điểm', val: showKeyPoints, set: setShowKeyPoints },
                    { label: 'Đường tiệm cận (TCĐ/TCN/TCX)', val: showAsymptotes, set: setShowAsymptotes },
                    { label: 'Tiếp tuyến khi Trace', val: showTangent, set: setShowTangent }
                  ].map((opt) => (
                    <button
                      key={opt.label}
                      type="button"
                      className={`chip-btn${opt.val ? ' active' : ''}`}
                      style={{ minHeight: 30, fontSize: 11 }}
                      onClick={() => opt.set(!opt.val)}
                    >
                      {opt.label}
                    </button>
                  ))}
                </div>

                {/* Item 8: Custom Parameter m bounds */}
                <div style={{ padding: 10, borderRadius: 14, border: '1px solid var(--border-subtle)', background: 'rgba(148,163,184,0.08)', display: 'flex', flexDirection: 'column', gap: 7 }}>
                  <div style={{ fontSize: 11.5, fontWeight: 800, color: accentHex }}>MIỀN GIÁ TRỊ THAM SỐ m</div>
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 6 }}>
                    <label style={{ fontSize: 11, display: 'flex', flexDirection: 'column', gap: 3 }}>
                      <span>m nhỏ nhất (m_min)</span>
                      <input
                        type="number"
                        className="glass-input mono"
                        style={{ padding: '6px 8px', fontSize: 12 }}
                        value={mMin}
                        onChange={(e) => setMMin(parseFloat(e.target.value) || -5)}
                      />
                    </label>
                    <label style={{ fontSize: 11, display: 'flex', flexDirection: 'column', gap: 3 }}>
                      <span>m lớn nhất (m_max)</span>
                      <input
                        type="number"
                        className="glass-input mono"
                        style={{ padding: '6px 8px', fontSize: 12 }}
                        value={mMax}
                        onChange={(e) => setMMax(parseFloat(e.target.value) || 5)}
                      />
                    </label>
                  </div>
                </div>

                {/* Item 8: 2D Sensitivity Sliders */}
                <div style={{ padding: 10, borderRadius: 14, border: '1px solid var(--border-subtle)', background: 'rgba(148,163,184,0.08)', display: 'flex', flexDirection: 'column', gap: 7 }}>
                  <div style={{ fontSize: 11.5, fontWeight: 800, color: '#10b981' }}>ĐỘ NHẠY TƯƠNG TÁC OXY</div>
                  {[
                    { key: 'pan' as const, label: 'Độ nhạy kéo (Pan)', min: 0.4, max: 2.0 },
                    { key: 'zoom' as const, label: 'Độ nhạy phóng to/thu nhỏ', min: 0.4, max: 2.0 },
                    { key: 'touchRadius' as const, label: 'Bán kính bắt điểm', min: 0.6, max: 2.0 }
                  ].map((s) => (
                    <label key={s.key} style={{ display: 'flex', flexDirection: 'column', gap: 3, fontSize: 11.5 }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                        <span>{s.label}</span>
                        <b className="mono">{sensitivity[s.key].toFixed(2)}×</b>
                      </div>
                      <input
                        type="range"
                        className="glass-slider"
                        min={s.min}
                        max={s.max}
                        step={0.05}
                        value={sensitivity[s.key]}
                        onChange={(e) => onUpdateSensitivity({ ...sensitivity, [s.key]: parseFloat(e.target.value) })}
                      />
                    </label>
                  ))}
                </div>

                <hr style={{ border: 'none', borderTop: '1px solid var(--border-subtle)', margin: 0 }} />

                <div style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
                  <input
                    type="text"
                    className="glass-input"
                    style={{ padding: '7px 9px', fontSize: 12 }}
                    placeholder="Tên file xuất (mặc định: tooltoan-2d-oxy)"
                    value={exportFilename}
                    onChange={(e) => setExportFilename(e.target.value)}
                  />
                  <button
                    type="button"
                    className={`chip-btn${pngTransparent ? ' active' : ''}`}
                    style={{ minHeight: 30, fontSize: 11 }}
                    onClick={() => setPngTransparent((v) => !v)}
                  >
                    {pngTransparent ? '✓ PNG nền trong suốt' : 'PNG có nền hiện tại'}
                  </button>
                  <button type="button" className="chip-btn glass-press" onClick={exportPng}>
                    <Download size={14} />
                    <span>Tải ảnh mặt phẳng Oxy (.PNG)</span>
                  </button>
                  <button type="button" className="chip-btn glass-press" onClick={exportJson}>
                    <Download size={14} />
                    <span>Lưu toàn bộ bài vẽ 2D (.JSON)</span>
                  </button>
                  <button type="button" className="chip-btn glass-press" onClick={() => fileInputRef.current?.click()}>
                    <Upload size={14} />
                    <span>Mở lại file bài vẽ 2D (.JSON)</span>
                  </button>
                  <input ref={fileInputRef} type="file" accept=".json" style={{ display: 'none' }} onChange={importJson} />
                </div>
              </>
            )}
          </div>
        </aside>
      )}

      {/* Automatic Vietnamese Variation Table Modal */}
      <VariationTableModal
        data={variationData}
        paramM={snapshot.paramM}
        accentHex={accentHex}
        onClose={() => setVariationData(null)}
      />
    </div>
  );
};
