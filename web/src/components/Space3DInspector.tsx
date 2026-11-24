import React, { useMemo, useState } from 'react';
import {
  ArrowRight,
  Axis3D,
  Box,
  Calculator,
  Check,
  CheckCircle2,
  Compass,
  Crosshair,
  Layers,
  Lock,
  Magnet,
  Move,
  MoveHorizontal,
  MoveVertical,
  Pause,
  Pencil,
  Play,
  Plus,
  RotateCcw,
  Scissors,
  Sliders,
  Sparkles,
  Trash2,
  Unlock,
  X,
  XCircle
} from 'lucide-react';
import {
  ALL_3D_SHAPES,
  Built3DScene,
  LineStyle3D,
  SHAPE_CATEGORIES,
  Scene3DSnapshot,
  ShapeSpec,
  analyzeLineAndPlane,
  build3DSceneFromSnapshot,
  formatMathNum,
  nextAutoPointName,
  worldToMath
} from '../engine/geo3d';
import {
  buildShapeThumbnailEdges,
  formatCoord3D
} from '../engine/geo3dAdvanced';
import {
  DragConstraint3D,
  InteractionMode3D,
  SPECIAL_EDGE_RATIOS
} from '../engine/geo3dDragSnap';
import { Sensitivity3D } from '../types/app';

export type InspectorTab3D = 'shapes' | 'settings' | 'draw' | 'oxyz';

const DRAW_COLORS = ['#38bdf8', '#f59e0b', '#10b981', '#ec4899', '#a855f7', '#f43f5e'];

interface Space3DInspectorProps {
  open: boolean;
  activeTab: InspectorTab3D;
  onChangeTab: (tab: InspectorTab3D) => void;
  onClose: () => void;
  accentHex: string;

  snap: Scene3DSnapshot;
  scene: Built3DScene;
  currentShape: ShapeSpec;
  allPointNames: string[];
  hasMovedVertices: boolean;

  // Mode & Drag Controls
  mode: InteractionMode3D;
  onChangeMode: (m: InteractionMode3D) => void;
  dragConstraint: DragConstraint3D;
  onChangeDragConstraint: (c: DragConstraint3D) => void;
  keepRelations: boolean;
  onToggleKeepRelations: () => void;
  snapEnabled: boolean;
  onToggleSnap: () => void;
  onResetMovedVertices: () => void;

  // Display & Visual toggles
  showFaces: boolean;
  onToggleShowFaces: () => void;
  showHiddenDashed: boolean;
  onToggleHiddenDashed: () => void;
  showAxes: boolean;
  onToggleAxes: () => void;
  autoRotate: boolean;
  onToggleAutoRotate: () => void;

  // Replay
  totalConstructionSteps: number;
  replayStep: number | null;
  isReplaying: boolean;
  onToggleReplay: () => void;
  onChangeReplayStep: (step: number | null) => void;

  // Sensitivity
  sensitivity: Sensitivity3D;
  onUpdateSensitivity: (next: Sensitivity3D) => void;

  // Snapshot Mutators
  onSelectShape: (key: string) => void;
  onUpdateDimensions: (patch: Partial<Pick<Scene3DSnapshot, 'a' | 'b' | 'h'>>) => void;
  onUpdateSection: (patch: Partial<Pick<Scene3DSnapshot, 'showSection' | 'secRatio'>>) => void;
  onChangeOrigin: (name: string) => void;
  onAddEdgePoint: (from: string, to: string, ratio: number, name?: string) => void;
  onRemoveEdgePoint: (id: string) => void;
  onAddXyzPoint: (name: string, x: number, y: number, z: number) => void;
  onRemoveXyzPoint: (id: string) => void;
  onAddLine: (p1: string, p2: string, style: LineStyle3D, color: string) => void;
  onRemoveLine: (id: string) => void;
  onAddPlane: (pts: string[], color: string) => void;
  onRemovePlane: (id: string) => void;
  onSetDynamicSection: (p1: string, p2: string, p3: string) => void;
  onClearDynamicSection: () => void;
  onClearAllCustom: () => void;
}

export const Space3DInspector: React.FC<Space3DInspectorProps> = (props) => {
  const {
    open,
    activeTab,
    onChangeTab,
    onClose,
    accentHex,
    snap,
    scene,
    currentShape,
    allPointNames,
    hasMovedVertices,
    mode,
    onChangeMode,
    dragConstraint,
    onChangeDragConstraint,
    keepRelations,
    onToggleKeepRelations,
    snapEnabled,
    onToggleSnap,
    onResetMovedVertices,
    showFaces,
    onToggleShowFaces,
    showHiddenDashed,
    onToggleHiddenDashed,
    showAxes,
    onToggleAxes,
    autoRotate,
    onToggleAutoRotate,
    totalConstructionSteps,
    replayStep,
    isReplaying,
    onToggleReplay,
    onChangeReplayStep,
    sensitivity,
    onUpdateSensitivity,
    onSelectShape,
    onUpdateDimensions,
    onUpdateSection,
    onChangeOrigin,
    onAddEdgePoint,
    onRemoveEdgePoint,
    onAddXyzPoint,
    onRemoveXyzPoint,
    onAddLine,
    onRemoveLine,
    onAddPlane,
    onRemovePlane,
    onSetDynamicSection,
    onClearDynamicSection,
    onClearAllCustom
  } = props;

  // Sub-tab inside Oxyz & Formulas
  const [oxyzSubTab, setOxyzSubTab] = useState<'oxyz' | 'formulas'>('oxyz');
  const [shapeCatFilter, setShapeCatFilter] = useState<string>('all');

  // Draw forms state
  const [epFrom, setEpFrom] = useState<string>(allPointNames[0] || 'A');
  const [epTo, setEpTo] = useState<string>(allPointNames[1] || 'B');
  const [epRatio, setEpRatio] = useState<number>(0.5);
  const [epName, setEpName] = useState<string>('');

  const [lnP1, setLnP1] = useState<string>(allPointNames[0] || 'A');
  const [lnP2, setLnP2] = useState<string>(allPointNames[1] || 'S');
  const [lnStyle, setLnStyle] = useState<LineStyle3D>('solid');
  const [lnColor, setLnColor] = useState<string>(DRAW_COLORS[0]);

  const [planeSelectedPts, setPlaneSelectedPts] = useState<string[]>([]);
  const [planeColor, setPlaneColor] = useState<string>(DRAW_COLORS[1]);

  const [secP1, setSecP1] = useState<string>(allPointNames[0] || 'A');
  const [secP2, setSecP2] = useState<string>(allPointNames[1] || 'B');
  const [secP3, setSecP3] = useState<string>(allPointNames[2] || 'S');

  // Oxyz calculator states
  const [calcLineA, setCalcLineA] = useState<string>(allPointNames[0] || 'S');
  const [calcLineB, setCalcLineB] = useState<string>(allPointNames[1] || 'A');
  const [calcP1, setCalcP1] = useState<string>(allPointNames[0] || 'A');
  const [calcP2, setCalcP2] = useState<string>(allPointNames[1] || 'B');
  const [calcP3, setCalcP3] = useState<string>(allPointNames[2] || 'C');

  const [xyzName, setXyzName] = useState<string>('');
  const [xyzX, setXyzX] = useState<string>('1');
  const [xyzY, setXyzY] = useState<string>('1');
  const [xyzZ, setXyzZ] = useState<string>('2');

  // Verify Mode state
  const [verifyMetric, setVerifyMetric] = useState<'vol' | 'sDay' | 'sXq' | 'sTp'>('vol');
  const [verifyInput, setVerifyInput] = useState<string>('');
  const [verifyResult, setVerifyResult] = useState<{ ok: boolean; expected: number; userVal: number } | null>(null);

  // Precompute mini thumbnails for the 15 shapes once
  const shapeThumbs = useMemo(() => {
    const map: Record<string, { x1: number; y1: number; x2: number; y2: number }[]> = {};
    for (const s of ALL_3D_SHAPES) {
      const miniScene = build3DSceneFromSnapshot({
        shapeKey: s.key,
        a: 3.4,
        b: 2.8,
        h: 3.8,
        originPoint: 'A',
        customPoints: [],
        customXYZPoints: [],
        customLines: [],
        customPlanes: [],
        dynamicSection: null
      });
      map[s.key] = buildShapeThumbnailEdges(miniScene, 40);
    }
    return map;
  }, []);

  // Safe selected point helper
  const safePt = (val: string, fallbackIdx: number) =>
    allPointNames.includes(val) ? val : allPointNames[Math.min(fallbackIdx, Math.max(0, allPointNames.length - 1))] || '';

  const sEpFrom = safePt(epFrom, 0);
  const sEpTo = safePt(epTo, 1);
  const sLnP1 = safePt(lnP1, 0);
  const sLnP2 = safePt(lnP2, 1);
  const sSecP1 = safePt(secP1, 0);
  const sSecP2 = safePt(secP2, 1);
  const sSecP3 = safePt(secP3, 2);

  const sCalcA = safePt(calcLineA, 0);
  const sCalcB = safePt(calcLineB, 1);
  const sCalcP1 = safePt(calcP1, 0);
  const sCalcP2 = safePt(calcP2, 1);
  const sCalcP3 = safePt(calcP3, 2);

  const originWorld = scene.pointRegistry[snap.originPoint] || { x: 0, y: 0, z: 0 };

  const linePlaneResult = useMemo(() => {
    const a = scene.pointRegistry[sCalcA];
    const b = scene.pointRegistry[sCalcB];
    const p1 = scene.pointRegistry[sCalcP1];
    const p2 = scene.pointRegistry[sCalcP2];
    const p3 = scene.pointRegistry[sCalcP3];
    if (!a || !b || !p1 || !p2 || !p3) return null;
    return analyzeLineAndPlane(a, b, p1, p2, p3, originWorld);
  }, [scene.pointRegistry, sCalcA, sCalcB, sCalcP1, sCalcP2, sCalcP3, originWorld]);

  if (!open) return null;

  const isRound = Boolean(currentShape.isRound);

  const handleVerifyCheck = () => {
    const normalized = verifyInput.trim().replace(',', '.');
    const userVal = parseFloat(normalized);
    if (!Number.isFinite(userVal)) return;
    let expected = scene.metrics.vol;
    if (verifyMetric === 'sDay') expected = scene.metrics.sDay;
    else if (verifyMetric === 'sXq') expected = scene.metrics.sXq;
    else if (verifyMetric === 'sTp') expected = scene.metrics.sTp;
    const relErr = Math.abs(expected) < 1e-4 ? Math.abs(userVal) : Math.abs(userVal - expected) / Math.abs(expected);
    setVerifyResult({ ok: relErr < 0.025, expected, userVal });
  };

  return (
    <aside className="glass-surface studio-inspector-panel" aria-label="Bảng điều khiển 3D">
      <div className="studio-inspector-handle" />
      <div className="studio-inspector-header">
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, minWidth: 0 }}>
          <Box size={16} color={accentHex} />
          <div style={{ minWidth: 0 }}>
            <div style={{ fontWeight: 800, fontSize: 13.5, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
              {currentShape.title}
            </div>
            <div style={{ fontSize: 11, color: 'var(--text-secondary)' }}>{currentShape.subtitle}</div>
          </div>
        </div>
        <button type="button" className="icon-btn" onClick={onClose} aria-label="Đóng bảng">
          <X size={17} />
        </button>
      </div>

      <div className="studio-inspector-tabs">
        <button
          type="button"
          className={`studio-inspector-tab ${activeTab === 'shapes' ? 'active' : ''}`}
          onClick={() => onChangeTab('shapes')}
        >
          <Box size={15} />
          <span>Khối hình</span>
        </button>
        <button
          type="button"
          className={`studio-inspector-tab ${activeTab === 'settings' ? 'active' : ''}`}
          onClick={() => onChangeTab('settings')}
        >
          <Sliders size={15} />
          <span>Cài đặt</span>
        </button>
        <button
          type="button"
          className={`studio-inspector-tab ${activeTab === 'draw' ? 'active' : ''}`}
          onClick={() => onChangeTab('draw')}
        >
          <Pencil size={15} />
          <span>Dựng hình</span>
        </button>
        <button
          type="button"
          className={`studio-inspector-tab ${activeTab === 'oxyz' ? 'active' : ''}`}
          onClick={() => onChangeTab('oxyz')}
        >
          <Calculator size={15} />
          <span>Oxyz & CT</span>
        </button>
      </div>

      <div className="studio-inspector-body scroll-area">
        {/* ================= TAB 1: 15 SHAPES ================= */}
        {activeTab === 'shapes' && (
          <>
            <div style={{ display: 'flex', gap: 5, flexWrap: 'wrap' }}>
              <button
                type="button"
                className={`chip-btn ${shapeCatFilter === 'all' ? 'active' : ''}`}
                style={{ minHeight: 30, padding: '3px 10px', fontSize: 11.5 }}
                onClick={() => setShapeCatFilter('all')}
              >
                Tất cả (15)
              </button>
              {SHAPE_CATEGORIES.map((cat) => (
                <button
                  key={cat.id}
                  type="button"
                  className={`chip-btn ${shapeCatFilter === cat.id ? 'active' : ''}`}
                  style={{ minHeight: 30, padding: '3px 10px', fontSize: 11.5 }}
                  onClick={() => setShapeCatFilter(cat.id)}
                >
                  {cat.title.replace(/^\d+\.\s*/, '')}
                </button>
              ))}
            </div>

            {SHAPE_CATEGORIES.filter((c) => shapeCatFilter === 'all' || shapeCatFilter === c.id).map((cat) => (
              <div key={cat.id} style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                <div className="studio-section-title">{cat.title}</div>
                {ALL_3D_SHAPES.filter((s) => s.category === cat.id).map((s) => {
                  const isActive = snap.shapeKey === s.key;
                  const edges = shapeThumbs[s.key] || [];
                  return (
                    <button
                      key={s.key}
                      type="button"
                      className="glass-press"
                      onClick={() => onSelectShape(s.key)}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 10,
                        padding: '8px 10px',
                        borderRadius: 14,
                        border: `1.2px solid ${isActive ? accentHex : 'var(--border-subtle)'}`,
                        background: isActive ? `color-mix(in srgb, ${accentHex} 16%, transparent)` : 'rgba(148,163,184,0.08)',
                        color: 'var(--text-primary)',
                        textAlign: 'left',
                        cursor: 'pointer'
                      }}
                    >
                      <svg
                        width="40"
                        height="40"
                        viewBox="0 0 40 40"
                        style={{
                          flexShrink: 0,
                          borderRadius: 10,
                          background: 'rgba(2, 6, 23, 0.42)',
                          border: '1px solid var(--border-subtle)'
                        }}
                      >
                        {edges.map((e, idx) => (
                          <line
                            key={idx}
                            x1={e.x1}
                            y1={e.y1}
                            x2={e.x2}
                            y2={e.y2}
                            stroke={isActive ? accentHex : '#cbd5e1'}
                            strokeWidth={1.3}
                            strokeLinecap="round"
                          />
                        ))}
                      </svg>
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <div style={{ fontSize: 13, fontWeight: 700 }}>{s.title}</div>
                        <div style={{ fontSize: 11, color: 'var(--text-secondary)' }}>{s.subtitle}</div>
                      </div>
                      {isActive && <Check size={16} color={accentHex} />}
                    </button>
                  );
                })}
              </div>
            ))}
          </>
        )}

        {/* ================= TAB 2: SETTINGS & DIMENSIONS ================= */}
        {activeTab === 'settings' && (
          <>
            {/* Dimensions */}
            <div className="studio-section-card">
              <div className="studio-section-title">
                <span>Kích thước hình học</span>
              </div>
              <div>
                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12.5, marginBottom: 4 }}>
                  <span>{isRound ? 'Bán kính / Đường kính (a)' : 'Cạnh đáy (a)'}</span>
                  <strong className="mono" style={{ color: accentHex }}>{formatMathNum(snap.a)}</strong>
                </div>
                <input
                  type="range"
                  className="glass-slider"
                  min={1.0}
                  max={7.5}
                  step={0.1}
                  value={snap.a}
                  onChange={(e) => onUpdateDimensions({ a: parseFloat(e.target.value) })}
                />
              </div>

              {currentShape.hasB && (
                <div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12.5, marginBottom: 4 }}>
                    <span>Chiều rộng (b)</span>
                    <strong className="mono" style={{ color: accentHex }}>{formatMathNum(snap.b)}</strong>
                  </div>
                  <input
                    type="range"
                    className="glass-slider"
                    min={1.0}
                    max={7.5}
                    step={0.1}
                    value={snap.b}
                    onChange={(e) => onUpdateDimensions({ b: parseFloat(e.target.value) })}
                  />
                </div>
              )}

              {currentShape.hasH !== false && (
                <div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12.5, marginBottom: 4 }}>
                    <span>Chiều cao (h)</span>
                    <strong className="mono" style={{ color: accentHex }}>{formatMathNum(snap.h)}</strong>
                  </div>
                  <input
                    type="range"
                    className="glass-slider"
                    min={1.0}
                    max={8.0}
                    step={0.1}
                    value={snap.h}
                    onChange={(e) => onUpdateDimensions({ h: parseFloat(e.target.value) })}
                  />
                </div>
              )}
            </div>

            {/* Vertex Dragging, Constraints & Magnetic Snap */}
            <div className="studio-section-card">
              <div className="studio-section-title">
                <span>Kéo Đỉnh, Ràng Buộc & Nam Châm Hút</span>
                {hasMovedVertices && (
                  <button
                    type="button"
                    className="chip-btn"
                    style={{ minHeight: 24, padding: '2px 8px', fontSize: 10.5 }}
                    onClick={onResetMovedVertices}
                  >
                    <RotateCcw size={11} /> Đỉnh chuẩn
                  </button>
                )}
              </div>

              <button
                type="button"
                className={`chip-btn glass-press ${mode === 'drag' ? 'active' : ''}`}
                style={{
                  width: '100%',
                  borderColor: mode === 'drag' ? '#ec4899' : undefined,
                  color: mode === 'drag' ? '#f472b6' : undefined,
                  background: mode === 'drag' ? 'rgba(236,72,153,0.18)' : undefined
                }}
                onClick={() => onChangeMode(mode === 'drag' ? 'view' : 'drag')}
              >
                <Move size={14} />
                {mode === 'drag' ? 'Đang bật Kéo Đỉnh (Bấm để tắt)' : 'Bật chế độ Kéo Đỉnh 3D'}
              </button>

              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 5 }}>
                <button
                  type="button"
                  className={`chip-btn ${dragConstraint === 'horizontal' ? 'active' : ''}`}
                  style={{ minHeight: 32, padding: '4px 6px', fontSize: 11 }}
                  onClick={() => {
                    onChangeDragConstraint('horizontal');
                    if (mode !== 'drag') onChangeMode('drag');
                  }}
                >
                  <MoveHorizontal size={12} /> Oxy
                </button>
                <button
                  type="button"
                  className={`chip-btn ${dragConstraint === 'vertical' ? 'active' : ''}`}
                  style={{ minHeight: 32, padding: '4px 6px', fontSize: 11 }}
                  onClick={() => {
                    onChangeDragConstraint('vertical');
                    if (mode !== 'drag') onChangeMode('drag');
                  }}
                >
                  <MoveVertical size={12} /> Cao Oz
                </button>
                <button
                  type="button"
                  className={`chip-btn ${dragConstraint === 'segment' ? 'active' : ''}`}
                  style={{ minHeight: 32, padding: '4px 6px', fontSize: 11 }}
                  onClick={() => {
                    onChangeDragConstraint('segment');
                    if (mode !== 'drag') onChangeMode('drag');
                  }}
                >
                  <Scissors size={12} /> Đoạn
                </button>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 6 }}>
                <button
                  type="button"
                  className={`chip-btn ${keepRelations ? 'active' : ''}`}
                  style={{ minHeight: 32, fontSize: 11.5 }}
                  onClick={onToggleKeepRelations}
                >
                  {keepRelations ? <Lock size={13} /> : <Unlock size={13} />}
                  {keepRelations ? 'Khóa hình' : 'Kéo tự do'}
                </button>
                <button
                  type="button"
                  className={`chip-btn ${snapEnabled ? 'active' : ''}`}
                  style={{
                    minHeight: 32,
                    fontSize: 11.5,
                    borderColor: snapEnabled ? '#10b981' : undefined,
                    color: snapEnabled ? '#34d399' : undefined,
                    background: snapEnabled ? 'rgba(16,185,129,0.18)' : undefined
                  }}
                  onClick={onToggleSnap}
                >
                  <Magnet size={13} />
                  {snapEnabled ? 'Hút: BẬT' : 'Hút: TẮT'}
                </button>
              </div>
            </div>

            {/* SGK Display Switches */}
            <div className="studio-section-card">
              <div className="studio-section-title">
                <span>Hiển thị chuẩn SGK</span>
              </div>
              <label className="studio-switch-row">
                <span>Tô màu bề mặt khối 3D</span>
                <input type="checkbox" checked={showFaces} onChange={onToggleShowFaces} />
              </label>
              <label className="studio-switch-row">
                <span>Tự động nét đứt cạnh khuất</span>
                <input type="checkbox" checked={showHiddenDashed} onChange={onToggleHiddenDashed} />
              </label>
              <label className="studio-switch-row">
                <span>Hiển thị hệ trục tọa độ Oxyz</span>
                <input type="checkbox" checked={showAxes} onChange={onToggleAxes} />
              </label>
              <label className="studio-switch-row">
                <span>Tự động xoay không gian 3D</span>
                <input type="checkbox" checked={autoRotate} onChange={onToggleAutoRotate} />
              </label>
            </div>

            {/* Horizontal Cross-Section */}
            <div className="studio-section-card">
              <div className="studio-section-title">
                <span>Mặt cắt ngang song song đáy</span>
              </div>
              <label className="studio-switch-row">
                <span>Bật mặt phẳng cắt ngang</span>
                <input
                  type="checkbox"
                  checked={Boolean(snap.showSection)}
                  onChange={(e) => onUpdateSection({ showSection: e.target.checked })}
                />
              </label>
              {snap.showSection && (
                <div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, marginBottom: 4 }}>
                    <span>Tỉ lệ chiều cao k = h&apos;/h</span>
                    <strong className="mono" style={{ color: '#f59e0b' }}>
                      {Math.round((snap.secRatio ?? 0.5) * 100)}%
                    </strong>
                  </div>
                  <input
                    type="range"
                    className="glass-slider"
                    min={0.15}
                    max={0.85}
                    step={0.01}
                    value={snap.secRatio ?? 0.5}
                    onChange={(e) => onUpdateSection({ secRatio: parseFloat(e.target.value) })}
                  />
                </div>
              )}
            </div>

            {/* Step-by-Step Construction Replay */}
            {totalConstructionSteps > 0 && (
              <div className="studio-section-card">
                <div className="studio-section-title">
                  <span>Phát lại các bước dựng hình</span>
                  <span className="mono">
                    {replayStep === null ? `${totalConstructionSteps}/${totalConstructionSteps}` : `${replayStep}/${totalConstructionSteps}`}
                  </span>
                </div>
                <div style={{ display: 'flex', gap: 6 }}>
                  <button type="button" className="primary-btn glass-press" style={{ flex: 1, minHeight: 34 }} onClick={onToggleReplay}>
                    {isReplaying ? <Pause size={14} /> : <Play size={14} />}
                    {isReplaying ? 'Tạm dừng' : 'Phát lại'}
                  </button>
                  {replayStep !== null && (
                    <button type="button" className="chip-btn" style={{ minHeight: 34 }} onClick={() => onChangeReplayStep(null)}>
                      Hiện đủ
                    </button>
                  )}
                </div>
                <input
                  type="range"
                  className="glass-slider"
                  min={0}
                  max={totalConstructionSteps}
                  step={1}
                  value={replayStep ?? totalConstructionSteps}
                  onChange={(e) => onChangeReplayStep(parseInt(e.target.value, 10))}
                />
              </div>
            )}

            {/* 4-Way 3D Sensitivity */}
            <div className="studio-section-card">
              <div className="studio-section-title">
                <span>Độ nhạy thao tác 3D</span>
              </div>
              {(
                [
                  ['rotation', 'Xoay không gian', 0.4, 2.0],
                  ['zoom', 'Thu phóng', 0.4, 2.0],
                  ['drag', 'Kéo đỉnh', 0.4, 2.0],
                  ['touchRadius', 'Vùng bắt điểm', 0.6, 2.0]
                ] as const
              ).map(([field, label, min, max]) => (
                <div key={field}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, marginBottom: 3 }}>
                    <span>{label}</span>
                    <span className="mono">{sensitivity[field].toFixed(1)}×</span>
                  </div>
                  <input
                    type="range"
                    className="glass-slider"
                    min={min}
                    max={max}
                    step={0.1}
                    value={sensitivity[field]}
                    onChange={(e) => onUpdateSensitivity({ ...sensitivity, [field]: parseFloat(e.target.value) })}
                  />
                </div>
              ))}
            </div>
          </>
        )}

        {/* ================= TAB 3: DRAW & CROSS-SECTION ================= */}
        {activeTab === 'draw' && (
          <>
            {/* Quick Direct Canvas Draw Bar (2x2 matching Android QuickDrawBar) */}
            <div className="studio-section-card">
              <div className="studio-section-title">
                <span>Vẽ nhanh trực tiếp trên hình 3D</span>
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 6 }}>
                <button
                  type="button"
                  className={`chip-btn glass-press ${mode === 'add_point' ? 'active' : ''}`}
                  style={{
                    borderColor: mode === 'add_point' ? '#f59e0b' : undefined,
                    color: mode === 'add_point' ? '#fbbf24' : undefined,
                    background: mode === 'add_point' ? 'rgba(245,158,11,0.18)' : undefined
                  }}
                  onClick={() => onChangeMode(mode === 'add_point' ? 'view' : 'add_point')}
                >
                  <Crosshair size={13} /> Chấm điểm
                </button>
                <button
                  type="button"
                  className={`chip-btn glass-press ${mode === 'connect_dashed' ? 'active' : ''}`}
                  onClick={() => onChangeMode(mode === 'connect_dashed' ? 'view' : 'connect_dashed')}
                >
                  <Pencil size={13} /> Nối nét đứt
                </button>
                <button
                  type="button"
                  className={`chip-btn glass-press ${mode === 'connect_solid' ? 'active' : ''}`}
                  onClick={() => onChangeMode(mode === 'connect_solid' ? 'view' : 'connect_solid')}
                >
                  <Pencil size={13} /> Nối nét liền
                </button>
                <button
                  type="button"
                  className={`chip-btn glass-press ${mode === 'connect_vector' ? 'active' : ''}`}
                  onClick={() => onChangeMode(mode === 'connect_vector' ? 'view' : 'connect_vector')}
                >
                  <ArrowRight size={13} /> Vẽ Vectơ
                </button>
              </div>
            </div>

            {/* Add Edge Point Form */}
            <div className="studio-section-card">
              <div className="studio-section-title">
                <span>Thêm điểm chia tỉ lệ trên cạnh</span>
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 74px', gap: 6 }}>
                <select className="glass-select" value={sEpFrom} onChange={(e) => setEpFrom(e.target.value)}>
                  {allPointNames.map((n) => (
                    <option key={n} value={n}>{n}</option>
                  ))}
                </select>
                <select className="glass-select" value={sEpTo} onChange={(e) => setEpTo(e.target.value)}>
                  {allPointNames.map((n) => (
                    <option key={n} value={n}>{n}</option>
                  ))}
                </select>
                <input
                  className="glass-input"
                  placeholder={nextAutoPointName(scene.pointRegistry)}
                  value={epName}
                  onChange={(e) => setEpName(e.target.value.slice(0, 4))}
                />
              </div>
              <div>
                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, marginBottom: 4 }}>
                  <span>Tỉ lệ k = {sEpFrom}M / {sEpFrom}{sEpTo}</span>
                  <strong className="mono">{formatMathNum(epRatio)}</strong>
                </div>
                <input
                  type="range"
                  className="glass-slider"
                  min={0.05}
                  max={0.95}
                  step={0.01}
                  value={epRatio}
                  onChange={(e) => setEpRatio(parseFloat(e.target.value))}
                />
                <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap', marginTop: 6 }}>
                  {SPECIAL_EDGE_RATIOS.map((sp) => (
                    <button
                      key={sp.label}
                      type="button"
                      className={`chip-btn ${Math.abs(epRatio - sp.value) < 0.02 ? 'active' : ''}`}
                      style={{ minHeight: 26, padding: '2px 8px', fontSize: 11 }}
                      onClick={() => setEpRatio(sp.value)}
                    >
                      {sp.value === 0.5 ? '1/2' : sp.label}
                    </button>
                  ))}
                </div>
              </div>
              <button
                type="button"
                className="primary-btn glass-press"
                style={{ minHeight: 36 }}
                onClick={() => {
                  if (sEpFrom && sEpTo && sEpFrom !== sEpTo) {
                    onAddEdgePoint(sEpFrom, sEpTo, epRatio, epName.trim() || undefined);
                    setEpName('');
                  }
                }}
              >
                <Plus size={15} /> Thêm điểm trên {sEpFrom}{sEpTo}
              </button>
            </div>

            {/* Connect Line / Vector Form */}
            <div className="studio-section-card">
              <div className="studio-section-title">
                <span>Nối đoạn thẳng / Vectơ</span>
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 6 }}>
                <select className="glass-select" value={sLnP1} onChange={(e) => setLnP1(e.target.value)}>
                  {allPointNames.map((n) => (
                    <option key={n} value={n}>{n}</option>
                  ))}
                </select>
                <select className="glass-select" value={sLnP2} onChange={(e) => setLnP2(e.target.value)}>
                  {allPointNames.map((n) => (
                    <option key={n} value={n}>{n}</option>
                  ))}
                </select>
              </div>
              <select className="glass-select" value={lnStyle} onChange={(e) => setLnStyle(e.target.value as LineStyle3D)}>
                <option value="solid">Nét liền (Tự động khuất)</option>
                <option value="dashed">Nét đứt (Đường phụ)</option>
                <option value="vector_solid">Vectơ nét liền (→)</option>
                <option value="vector_dashed">Vectơ nét đứt (⇢)</option>
              </select>
              <div style={{ display: 'flex', alignItems: 'center', gap: 7 }}>
                {DRAW_COLORS.map((c) => (
                  <button
                    key={c}
                    type="button"
                    onClick={() => setLnColor(c)}
                    style={{
                      width: 24,
                      height: 24,
                      borderRadius: 999,
                      background: c,
                      border: lnColor === c ? '2.5px solid #fff' : '1px solid rgba(255,255,255,0.25)',
                      cursor: 'pointer'
                    }}
                    aria-label={`Màu ${c}`}
                  />
                ))}
              </div>
              <button
                type="button"
                className="primary-btn glass-press"
                style={{ minHeight: 36 }}
                onClick={() => sLnP1 && sLnP2 && sLnP1 !== sLnP2 && onAddLine(sLnP1, sLnP2, lnStyle, lnColor)}
              >
                <Plus size={15} /> Nối {sLnP1} → {sLnP2}
              </button>
            </div>

            {/* Dynamic 3-Point Cross-Section */}
            <div className="studio-section-card">
              <div className="studio-section-title">
                <span>Thiết diện động qua 3 điểm</span>
                {scene.dynamicSection && (
                  <span className="mono" style={{ color: '#34d399' }}>
                    S = {formatMathNum(scene.dynamicSection.area)}
                  </span>
                )}
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 5 }}>
                {[
                  [sSecP1, setSecP1],
                  [sSecP2, setSecP2],
                  [sSecP3, setSecP3]
                ].map(([val, setter], idx) => (
                  <select
                    key={idx}
                    className="glass-select"
                    value={val as string}
                    onChange={(e) => (setter as React.Dispatch<React.SetStateAction<string>>)(e.target.value)}
                  >
                    {allPointNames.map((n) => (
                      <option key={n} value={n}>{n}</option>
                    ))}
                  </select>
                ))}
              </div>
              <div style={{ display: 'flex', gap: 6 }}>
                <button
                  type="button"
                  className="primary-btn glass-press"
                  style={{ flex: 1, minHeight: 34 }}
                  onClick={() => onSetDynamicSection(sSecP1, sSecP2, sSecP3)}
                >
                  <Layers size={14} /> Cắt ({sSecP1}{sSecP2}{sSecP3})
                </button>
                {snap.dynamicSection && (
                  <button type="button" className="chip-btn" style={{ minHeight: 34 }} onClick={onClearDynamicSection}>
                    Tắt
                  </button>
                )}
              </div>
            </div>

            {/* Fill Custom Plane */}
            <div className="studio-section-card">
              <div className="studio-section-title">
                <span>Tô màu mặt phẳng phụ (≥ 3 điểm)</span>
              </div>
              <div style={{ display: 'flex', gap: 5, flexWrap: 'wrap' }}>
                {allPointNames.map((name) => {
                  const picked = planeSelectedPts.includes(name);
                  return (
                    <button
                      key={name}
                      type="button"
                      className={`chip-btn ${picked ? 'active' : ''}`}
                      style={{ minHeight: 28, padding: '2px 9px', fontSize: 11.5 }}
                      onClick={() =>
                        setPlaneSelectedPts((prev) =>
                          prev.includes(name) ? prev.filter((x) => x !== name) : [...prev, name]
                        )
                      }
                    >
                      {name}
                    </button>
                  );
                })}
              </div>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}>
                <div style={{ display: 'flex', gap: 6 }}>
                  {DRAW_COLORS.map((c) => (
                    <button
                      key={c}
                      type="button"
                      onClick={() => setPlaneColor(c)}
                      style={{
                        width: 22,
                        height: 22,
                        borderRadius: 999,
                        background: c,
                        border: planeColor === c ? '2px solid #fff' : '1px solid rgba(255,255,255,0.25)',
                        cursor: 'pointer'
                      }}
                      aria-label={`Màu mặt phẳng ${c}`}
                    />
                  ))}
                </div>
                <button
                  type="button"
                  className="chip-btn active"
                  disabled={planeSelectedPts.length < 3}
                  onClick={() => {
                    if (planeSelectedPts.length >= 3) {
                      onAddPlane(planeSelectedPts, planeColor);
                      setPlaneSelectedPts([]);
                    }
                  }}
                >
                  <Plus size={13} /> Tô ({planeSelectedPts.join('') || '...'})
                </button>
              </div>
            </div>

            {/* Constructed Elements List */}
            {(snap.customPoints.length > 0 ||
              snap.customXYZPoints.length > 0 ||
              snap.customLines.length > 0 ||
              snap.customPlanes.length > 0) && (
              <div className="studio-section-card">
                <div className="studio-section-title">
                  <span>Đã dựng ({snap.customPoints.length + snap.customXYZPoints.length + snap.customLines.length + snap.customPlanes.length})</span>
                  <button
                    type="button"
                    className="chip-btn"
                    style={{ minHeight: 24, padding: '2px 8px', fontSize: 10.5, color: '#f43f5e' }}
                    onClick={onClearAllCustom}
                  >
                    Xóa tất cả
                  </button>
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 5 }}>
                  {snap.customPoints.map((cp) => (
                    <div key={cp.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: 12 }}>
                      <span>
                        Điểm <strong>{cp.name}</strong> ∈ {cp.from}{cp.to} (k={formatMathNum(cp.ratio)})
                      </span>
                      <button type="button" className="icon-btn" style={{ width: 26, height: 26, color: '#f43f5e' }} onClick={() => onRemoveEdgePoint(cp.id)}>
                        <Trash2 size={13} />
                      </button>
                    </div>
                  ))}
                  {snap.customXYZPoints.map((xp) => (
                    <div key={xp.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: 12 }}>
                      <span>
                        Oxyz <strong>{xp.name}</strong>({formatMathNum(xp.x)}; {formatMathNum(xp.y)}; {formatMathNum(xp.z)})
                      </span>
                      <button type="button" className="icon-btn" style={{ width: 26, height: 26, color: '#f43f5e' }} onClick={() => onRemoveXyzPoint(xp.id)}>
                        <Trash2 size={13} />
                      </button>
                    </div>
                  ))}
                  {snap.customLines.map((cl) => (
                    <div key={cl.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: 12 }}>
                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                        <span style={{ width: 9, height: 9, borderRadius: 99, background: cl.color }} />
                        Đoạn <strong>{cl.p1}{cl.p2}</strong> ({cl.style})
                      </span>
                      <button type="button" className="icon-btn" style={{ width: 26, height: 26, color: '#f43f5e' }} onClick={() => onRemoveLine(cl.id)}>
                        <Trash2 size={13} />
                      </button>
                    </div>
                  ))}
                  {snap.customPlanes.map((pl) => (
                    <div key={pl.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: 12 }}>
                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                        <span style={{ width: 9, height: 9, borderRadius: 3, background: pl.color }} />
                        Mặt phẳng <strong>({pl.pts.join('')})</strong>
                      </span>
                      <button type="button" className="icon-btn" style={{ width: 26, height: 26, color: '#f43f5e' }} onClick={() => onRemovePlane(pl.id)}>
                        <Trash2 size={13} />
                      </button>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </>
        )}

        {/* ================= TAB 4: OXYZ & FORMULAS (2 SUB-TABS) ================= */}
        {activeTab === 'oxyz' && (
          <>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 6 }}>
              <button
                type="button"
                className={`chip-btn ${oxyzSubTab === 'oxyz' ? 'active' : ''}`}
                onClick={() => setOxyzSubTab('oxyz')}
              >
                <Axis3D size={14} /> Hệ Oxyz & Khoảng cách
              </button>
              <button
                type="button"
                className={`chip-btn ${oxyzSubTab === 'formulas' ? 'active' : ''}`}
                onClick={() => setOxyzSubTab('formulas')}
              >
                <Sparkles size={14} /> Công thức & Kiểm chứng
              </button>
            </div>

            {oxyzSubTab === 'oxyz' ? (
              <>
                {/* Origin selector */}
                <div className="studio-section-card">
                  <div className="studio-section-title">
                    <span>Chọn gốc tọa độ O(0; 0; 0)</span>
                  </div>
                  <div style={{ display: 'flex', gap: 5, flexWrap: 'wrap' }}>
                    {allPointNames.map((n) => (
                      <button
                        key={n}
                        type="button"
                        className={`chip-btn ${snap.originPoint === n ? 'active' : ''}`}
                        style={{ minHeight: 28, padding: '2px 10px', fontSize: 11.5 }}
                        onClick={() => onChangeOrigin(n)}
                      >
                        {n}
                      </button>
                    ))}
                  </div>
                </div>

                {/* Line & Plane Analyzer */}
                <div className="studio-section-card">
                  <div className="studio-section-title">
                    <span>Khoảng cách & Góc: Đường thẳng – Mặt phẳng</span>
                  </div>
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 5 }}>
                    <select className="glass-select" value={sCalcA} onChange={(e) => setCalcLineA(e.target.value)}>
                      {allPointNames.map((n) => <option key={n} value={n}>Đường: {n}</option>)}
                    </select>
                    <select className="glass-select" value={sCalcB} onChange={(e) => setCalcLineB(e.target.value)}>
                      {allPointNames.map((n) => <option key={n} value={n}>đến {n}</option>)}
                    </select>
                  </div>
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 5 }}>
                    {[
                      [sCalcP1, setCalcP1],
                      [sCalcP2, setCalcP2],
                      [sCalcP3, setCalcP3]
                    ].map(([v, setV], i) => (
                      <select
                        key={i}
                        className="glass-select"
                        value={v as string}
                        onChange={(e) => (setV as React.Dispatch<React.SetStateAction<string>>)(e.target.value)}
                      >
                        {allPointNames.map((n) => <option key={n} value={n}>(P): {n}</option>)}
                      </select>
                    ))}
                  </div>
                  {linePlaneResult ? (
                    <div className="mono" style={{ fontSize: 12, display: 'flex', flexDirection: 'column', gap: 4 }}>
                      <div>(P): <strong style={{ color: accentHex }}>{linePlaneResult.planeEquation}</strong></div>
                      <div>Độ dài {sCalcA}{sCalcB} = <strong>{formatMathNum(linePlaneResult.lineLength)}</strong></div>
                      <div>d({sCalcA}, (P)) = <strong>{formatMathNum(linePlaneResult.distA)}</strong> • d({sCalcB}, (P)) = <strong>{formatMathNum(linePlaneResult.distB)}</strong></div>
                      <div>∠({sCalcA}{sCalcB}, (P)) = <strong style={{ color: '#fbbf24' }}>{linePlaneResult.angleDegrees.toFixed(1)}°</strong></div>
                    </div>
                  ) : (
                    <div style={{ fontSize: 12, color: 'var(--text-secondary)' }}>Chọn 3 điểm không thẳng hàng cho (P).</div>
                  )}
                </div>

                {/* Add XYZ Point */}
                <div className="studio-section-card">
                  <div className="studio-section-title">
                    <span>Thêm điểm tọa độ Oxyz</span>
                  </div>
                  <div style={{ display: 'grid', gridTemplateColumns: '64px 1fr 1fr 1fr', gap: 5 }}>
                    <input
                      className="glass-input"
                      placeholder={nextAutoPointName(scene.pointRegistry)}
                      value={xyzName}
                      onChange={(e) => setXyzName(e.target.value.slice(0, 4))}
                    />
                    <input className="glass-input" type="number" step="0.5" value={xyzX} onChange={(e) => setXyzX(e.target.value)} placeholder="x" />
                    <input className="glass-input" type="number" step="0.5" value={xyzY} onChange={(e) => setXyzY(e.target.value)} placeholder="y" />
                    <input className="glass-input" type="number" step="0.5" value={xyzZ} onChange={(e) => setXyzZ(e.target.value)} placeholder="z" />
                  </div>
                  <button
                    type="button"
                    className="primary-btn glass-press"
                    style={{ minHeight: 34 }}
                    onClick={() => {
                      const x = parseFloat(xyzX);
                      const y = parseFloat(xyzY);
                      const z = parseFloat(xyzZ);
                      if (Number.isFinite(x) && Number.isFinite(y) && Number.isFinite(z)) {
                        onAddXyzPoint(xyzName.trim() || nextAutoPointName(scene.pointRegistry), x, y, z);
                        setXyzName('');
                      }
                    }}
                  >
                    <Plus size={14} /> Thêm điểm Oxyz
                  </button>
                </div>

                {/* Coordinates Table */}
                <div className="studio-section-card">
                  <div className="studio-section-title">
                    <span>Bảng tọa độ đỉnh (Gốc {snap.originPoint})</span>
                  </div>
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 5 }}>
                    {allPointNames.map((n) => {
                      const m = worldToMath(scene.pointRegistry[n], originWorld);
                      return (
                        <div
                          key={n}
                          className="mono"
                          style={{
                            padding: '5px 8px',
                            borderRadius: 10,
                            background: n === snap.originPoint ? 'rgba(244,63,94,0.16)' : 'rgba(148,163,184,0.08)',
                            border: '1px solid var(--border-subtle)',
                            fontSize: 11.5
                          }}
                        >
                          <strong>{n}</strong> {formatCoord3D(m)}
                        </div>
                      );
                    })}
                  </div>
                </div>
              </>
            ) : (
              <>
                {/* 4 KPI Cards */}
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 7 }}>
                  {[
                    { label: 'Thể tích (V)', value: formatMathNum(scene.metrics.vol), color: accentHex },
                    { label: 'Diện tích TP', value: formatMathNum(scene.metrics.sTp), color: '#10b981' },
                    { label: 'Diện tích đáy', value: formatMathNum(scene.metrics.sDay), color: '#f59e0b' },
                    { label: 'Diện tích XQ', value: formatMathNum(scene.metrics.sXq), color: '#a855f7' }
                  ].map((kpi) => (
                    <div
                      key={kpi.label}
                      className="studio-section-card"
                      style={{ gap: 2, borderColor: `color-mix(in srgb, ${kpi.color} 42%, transparent)` }}
                    >
                      <span style={{ fontSize: 11, color: 'var(--text-secondary)' }}>{kpi.label}</span>
                      <strong className="mono" style={{ fontSize: 17, color: kpi.color }}>{kpi.value}</strong>
                    </div>
                  ))}
                </div>

                {/* SGK Formulas */}
                <div className="studio-section-card">
                  <div className="studio-section-title">
                    <span>Công thức chuẩn SGK</span>
                  </div>
                  {scene.metrics.dimensionRows.map((row) => (
                    <div key={row.label} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12.5 }}>
                      <span style={{ color: 'var(--text-secondary)' }}>{row.label}</span>
                      <strong className="mono">{row.value}</strong>
                    </div>
                  ))}
                  <div style={{ borderTop: '1px solid var(--border-subtle)', paddingTop: 7, display: 'flex', flexDirection: 'column', gap: 4, fontSize: 12.5 }}>
                    {scene.metrics.formulaLines.map((line, i) => (
                      <div key={i} className="mono">{line}</div>
                    ))}
                  </div>
                </div>

                {/* Verify Mode */}
                <div className="studio-section-card">
                  <div className="studio-section-title">
                    <span>Kiểm chứng đáp án tự giải</span>
                    <Compass size={13} />
                  </div>
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 6 }}>
                    <select
                      className="glass-select"
                      value={verifyMetric}
                      onChange={(e) => {
                        setVerifyMetric(e.target.value as typeof verifyMetric);
                        setVerifyResult(null);
                      }}
                    >
                      <option value="vol">Thể tích V</option>
                      <option value="sDay">Diện tích đáy Sđáy</option>
                      <option value="sXq">Diện tích XQ</option>
                      <option value="sTp">Diện tích TP</option>
                    </select>
                    <input
                      className="glass-input"
                      placeholder="Nhập kết quả..."
                      value={verifyInput}
                      onChange={(e) => setVerifyInput(e.target.value)}
                      onKeyDown={(e) => e.key === 'Enter' && handleVerifyCheck()}
                    />
                  </div>
                  <button type="button" className="primary-btn glass-press" style={{ minHeight: 34 }} onClick={handleVerifyCheck}>
                    Kiểm tra kết quả
                  </button>
                  {verifyResult && (
                    <div
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 8,
                        padding: '8px 10px',
                        borderRadius: 12,
                        background: verifyResult.ok ? 'rgba(16,185,129,0.18)' : 'rgba(244,63,94,0.18)',
                        color: verifyResult.ok ? '#34d399' : '#fb7185',
                        fontSize: 12.5,
                        fontWeight: 700
                      }}
                    >
                      {verifyResult.ok ? <CheckCircle2 size={16} /> : <XCircle size={16} />}
                      <span>
                        {verifyResult.ok
                          ? `Chính xác! Đáp án ≈ ${formatMathNum(verifyResult.expected)}`
                          : `Chưa khớp (Sai số > 2.5%). Đáp án đúng ≈ ${formatMathNum(verifyResult.expected)}`}
                      </span>
                    </div>
                  )}
                </div>
              </>
            )}
          </>
        )}
      </div>
    </aside>
  );
};
