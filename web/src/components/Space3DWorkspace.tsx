import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  Compass,
  Download,
  Eye,
  EyeOff,
  Maximize2,
  Minimize2,
  RotateCcw,
  Play,
  Pause,
  Shapes,
  SlidersHorizontal,
  Upload,
  X
} from 'lucide-react';
import {
  ALL_3D_SHAPES,
  build3DShape,
  Camera3D,
  exportSceneToObj,
  exportSceneToSvg,
  projectPoint,
  SHAPE_CATEGORIES
} from '../engine/geo3d';
import { AiDrawingPlan } from '../types/app';

interface Space3DWorkspaceProps {
  accentHex: string;
  themeMode: 'dark' | 'light';
  isImmersive: boolean;
  onToggleImmersive: () => void;
  externalPlan: AiDrawingPlan | null;
  onPlanConsumed: () => void;
}

const DEFAULT_CAMERA: Camera3D = {
  theta: 0.72,
  phi: 1.12,
  radius: 10.5,
  target: { x: 0, y: 1.6, z: 0 }
};

function downloadFile(filename: string, content: string, mime: string) {
  const blob = new Blob([content], { type: mime });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

export const Space3DWorkspace: React.FC<Space3DWorkspaceProps> = ({
  accentHex,
  themeMode,
  isImmersive,
  onToggleImmersive,
  externalPlan,
  onPlanConsumed
}) => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const [shapeKey, setShapeKey] = useState<string>('pyramid4_sa');
  const [dimA, setDimA] = useState<number>(4);
  const [dimB, setDimB] = useState<number>(3);
  const [dimH, setDimH] = useState<number>(4.5);

  const [camera, setCamera] = useState<Camera3D>(DEFAULT_CAMERA);
  const [autoRotate, setAutoRotate] = useState<boolean>(false);
  const [showAxes, setShowAxes] = useState<boolean>(true);
  const [hideAnswers, setHideAnswers] = useState<boolean>(false);

  const [drawerOpen, setDrawerOpen] = useState<boolean>(false);
  const [exportOpen, setExportOpen] = useState<boolean>(false);
  const [statusBanner, setStatusBanner] = useState<string | null>(null);

  const dragRef = useRef<{ active: boolean; x: number; y: number }>({ active: false, x: 0, y: 0 });

  const currentSpec = useMemo(
    () => ALL_3D_SHAPES.find((s) => s.key === shapeKey) || ALL_3D_SHAPES[0],
    [shapeKey]
  );

  const scene = useMemo(() => build3DShape(shapeKey, dimA, dimB, dimH), [shapeKey, dimA, dimB, dimH]);

  // Consume external AI 3D drawing plan
  useEffect(() => {
    if (!externalPlan || externalPlan.target !== 'space3d') return;
    if (externalPlan.shape3dKey && ALL_3D_SHAPES.some((s) => s.key === externalPlan.shape3dKey)) {
      setShapeKey(externalPlan.shape3dKey);
    }
    if (typeof externalPlan.a === 'number' && Number.isFinite(externalPlan.a)) {
      setDimA(Math.min(10, Math.max(1, externalPlan.a)));
    }
    if (typeof externalPlan.b === 'number' && Number.isFinite(externalPlan.b)) {
      setDimB(Math.min(10, Math.max(1, externalPlan.b)));
    }
    if (typeof externalPlan.h === 'number' && Number.isFinite(externalPlan.h)) {
      setDimH(Math.min(10, Math.max(1, externalPlan.h)));
    }
    setStatusBanner(`Đã dựng mô hình từ AI: ${externalPlan.title}`);
    onPlanConsumed();
  }, [externalPlan, onPlanConsumed]);

  // Auto-clear status toast
  useEffect(() => {
    if (!statusBanner) return;
    const t = window.setTimeout(() => setStatusBanner(null), 3500);
    return () => window.clearTimeout(t);
  }, [statusBanner]);

  // Auto-rotate animation loop
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

  // Render 3D scene to Canvas
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

      // Subtle floor grid on y = 0
      const gridColor = isLight ? 'rgba(15, 23, 42, 0.11)' : 'rgba(148, 163, 184, 0.15)';
      ctx.strokeStyle = gridColor;
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

      // Oxyz axes
      if (showAxes) {
        const origin = projectPoint({ x: 0, y: 0, z: 0 }, camera, w, h);
        const axes = [
          { end: { x: 5.5, y: 0, z: 0 }, label: 'x', color: '#f43f5e' },
          { end: { x: 0, y: 5.5, z: 0 }, label: 'z', color: '#38bdf8' },
          { end: { x: 0, y: 0, z: 5.5 }, label: 'y', color: '#10b981' }
        ];
        for (const ax of axes) {
          const p = projectPoint(ax.end, camera, w, h);
          ctx.strokeStyle = ax.color;
          ctx.lineWidth = 2;
          ctx.beginPath();
          ctx.moveTo(origin.x, origin.y);
          ctx.lineTo(p.x, p.y);
          ctx.stroke();
          ctx.fillStyle = ax.color;
          ctx.font = '700 13px "JetBrains Mono", monospace';
          ctx.fillText(ax.label, p.x + 6, p.y + 4);
        }
      }

      // Project vertices
      const projected = scene.vertices.map((v) => projectPoint(v.pos, camera, w, h));

      // Painter's algorithm sort for translucent faces
      const sortedFaces = scene.faces
        .map((face) => {
          const avgDepth = face.reduce((acc, idx) => acc + projected[idx].depth, 0) / face.length;
          return { face, avgDepth };
        })
        .sort((f1, f2) => f2.avgDepth - f1.avgDepth);

      // Draw faces with glass-like shading
      for (const { face } of sortedFaces) {
        if (face.length < 3) continue;
        ctx.beginPath();
        face.forEach((vIdx, idx) => {
          const p = projected[vIdx];
          if (idx === 0) ctx.moveTo(p.x, p.y);
          else ctx.lineTo(p.x, p.y);
        });
        ctx.closePath();
        ctx.fillStyle = isLight ? `${accentHex}26` : `${accentHex}2B`;
        ctx.fill();
      }

      // Draw edges (solid + faint depth cue)
      ctx.lineCap = 'round';
      for (const edge of scene.edges) {
        const p1 = projected[edge.from];
        const p2 = projected[edge.to];
        const avgD = (p1.depth + p2.depth) / 2;
        const isBack = avgD > camera.radius + 0.55;
        ctx.save();
        ctx.strokeStyle = isLight
          ? isBack ? 'rgba(15, 23, 42, 0.42)' : '#0f172a'
          : isBack ? 'rgba(226, 232, 240, 0.44)' : '#f8fafc';
        ctx.lineWidth = isBack ? 1.5 : 2.3;
        if (isBack && !currentSpec.isRound) {
          ctx.setLineDash([6, 5]);
        }
        ctx.beginPath();
        ctx.moveTo(p1.x, p1.y);
        ctx.lineTo(p2.x, p2.y);
        ctx.stroke();
        ctx.restore();
      }

      // Draw altitude helper line for pyramids/cones
      if (shapeKey.startsWith('pyramid') || shapeKey === 'cone') {
        const apex = scene.vertices.find((v) => v.name === 'S');
        if (apex) {
          const foot = projectPoint({ x: apex.pos.x, y: 0, z: apex.pos.z }, camera, w, h);
          const top = projectPoint(apex.pos, camera, w, h);
          ctx.save();
          ctx.strokeStyle = '#f59e0b';
          ctx.lineWidth = 1.8;
          ctx.setLineDash([5, 4]);
          ctx.beginPath();
          ctx.moveTo(top.x, top.y);
          ctx.lineTo(foot.x, foot.y);
          ctx.stroke();
          ctx.restore();
        }
      }

      // Draw named vertices
      for (let i = 0; i < scene.vertices.length; i++) {
        const v = scene.vertices[i];
        if (!v.name) continue;
        const p = projected[i];
        ctx.beginPath();
        ctx.arc(p.x, p.y, 4.2, 0, Math.PI * 2);
        ctx.fillStyle = accentHex;
        ctx.fill();
        ctx.strokeStyle = '#ffffff';
        ctx.lineWidth = 1.5;
        ctx.stroke();

        ctx.font = '800 13px "Plus Jakarta Sans", sans-serif';
        ctx.fillStyle = isLight ? '#0f172a' : '#f8fafc';
        ctx.fillText(v.name, p.x + 8, p.y - 6);
      }

      ctx.restore();
    };

    render();
    window.addEventListener('resize', render);
    return () => window.removeEventListener('resize', render);
  }, [scene, camera, showAxes, accentHex, themeMode, currentSpec.isRound, shapeKey]);

  const handlePointerDown = (e: React.PointerEvent<HTMLCanvasElement>) => {
    e.currentTarget.setPointerCapture(e.pointerId);
    dragRef.current = { active: true, x: e.clientX, y: e.clientY };
    if (autoRotate) setAutoRotate(false);
  };

  const handlePointerMove = (e: React.PointerEvent<HTMLCanvasElement>) => {
    if (!dragRef.current.active) return;
    const dx = e.clientX - dragRef.current.x;
    const dy = e.clientY - dragRef.current.y;
    dragRef.current = { active: true, x: e.clientX, y: e.clientY };
    setCamera((prev) => ({
      ...prev,
      theta: prev.theta - dx * 0.0085,
      phi: Math.min(Math.PI - 0.12, Math.max(0.12, prev.phi - dy * 0.0085))
    }));
  };

  const handlePointerUp = () => {
    dragRef.current.active = false;
  };

  const handleWheel = (e: React.WheelEvent<HTMLCanvasElement>) => {
    setCamera((prev) => ({
      ...prev,
      radius: Math.min(26, Math.max(4.2, prev.radius + e.deltaY * 0.009))
    }));
  };

  const handleExportPng = () => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const a = document.createElement('a');
    a.href = canvas.toDataURL('image/png');
    a.download = `tooltoan-3d-${shapeKey}.png`;
    a.click();
    setExportOpen(false);
  };

  const handleExportSvg = () => {
    const svg = exportSceneToSvg(scene, camera, accentHex);
    downloadFile(`tooltoan-3d-${shapeKey}.svg`, svg, 'image/svg+xml;charset=utf-8');
    setExportOpen(false);
  };

  const handleExportObj = () => {
    const obj = exportSceneToObj(scene, currentSpec.title);
    downloadFile(`tooltoan-3d-${shapeKey}.obj`, obj, 'text/plain;charset=utf-8');
    setExportOpen(false);
  };

  const handleExportJson = () => {
    const payload = JSON.stringify({ shapeKey, a: dimA, b: dimB, h: dimH, camera }, null, 2);
    downloadFile(`tooltoan-3d-${shapeKey}.json`, payload, 'application/json;charset=utf-8');
    setExportOpen(false);
  };

  const handleImportJson = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      try {
        const parsed = JSON.parse(String(reader.result));
        if (parsed.shapeKey && ALL_3D_SHAPES.some((s) => s.key === parsed.shapeKey)) {
          setShapeKey(parsed.shapeKey);
        }
        if (typeof parsed.a === 'number') setDimA(parsed.a);
        if (typeof parsed.b === 'number') setDimB(parsed.b);
        if (typeof parsed.h === 'number') setDimH(parsed.h);
        if (parsed.camera) setCamera(parsed.camera);
        setStatusBanner('Đã nhập lại cấu hình hình 3D từ file JSON.');
      } catch {
        setStatusBanner('File JSON không đúng định dạng Tool Toán.');
      }
    };
    reader.readAsText(file);
    e.target.value = '';
    setExportOpen(false);
  };

  const { metrics } = scene;

  return (
    <div style={{ position: 'relative', width: '100%', height: '100%', overflow: 'hidden' }}>
      {/* Interactive Full-Screen 3D Canvas */}
      <canvas
        ref={canvasRef}
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={handlePointerUp}
        onPointerCancel={handlePointerUp}
        onWheel={handleWheel}
        style={{ width: '100%', height: '100%', display: 'block', touchAction: 'none', cursor: 'grab' }}
      />

      {/* Top Floating Header Bar */}
      {!isImmersive && (
        <header
          style={{
            position: 'absolute',
            top: 12,
            left: 12,
            right: 12,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 8,
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
              gap: 10,
              padding: '8px 14px',
              borderRadius: 999
            }}
          >
            <Shapes size={18} color={accentHex} />
            <div>
              <div style={{ fontSize: 13, fontWeight: 800, lineHeight: 1.15 }}>{currentSpec.title}</div>
              <div style={{ fontSize: 11, color: 'var(--text-secondary)' }}>{currentSpec.subtitle}</div>
            </div>
          </div>

          <div
            className="glass-surface"
            style={{
              pointerEvents: 'auto',
              display: 'flex',
              alignItems: 'center',
              gap: 4,
              padding: 4,
              borderRadius: 999
            }}
          >
            <button
              type="button"
              onClick={() => setDrawerOpen((v) => !v)}
              className={`chip-btn glass-press${drawerOpen ? ' active' : ''}`}
              title="Thư viện 15 khối & kích thước"
            >
              <SlidersHorizontal size={15} />
              <span>Hình & Thông số</span>
            </button>

            <button
              type="button"
              onClick={() => setAutoRotate((v) => !v)}
              className="icon-btn glass-press"
              title={autoRotate ? 'Dừng tự xoay' : 'Tự xoay 360°'}
              aria-label="Tự xoay"
            >
              {autoRotate ? <Pause size={17} color={accentHex} /> : <Play size={17} />}
            </button>

            <button
              type="button"
              onClick={() => setShowAxes((v) => !v)}
              className="icon-btn glass-press"
              title="Bật/tắt hệ trục Oxyz"
              aria-label="Hệ trục Oxyz"
            >
              <Compass size={17} color={showAxes ? accentHex : undefined} />
            </button>

            <button
              type="button"
              onClick={() => setExportOpen((v) => !v)}
              className="icon-btn glass-press"
              title="Xuất PNG / SVG / OBJ / JSON"
              aria-label="Xuất tệp"
            >
              <Download size={17} />
            </button>

            <button
              type="button"
              onClick={onToggleImmersive}
              className="icon-btn glass-press"
              title="Toàn màn hình"
              aria-label="Toàn màn hình"
            >
              <Maximize2 size={17} />
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
          title="Thoát toàn màn hình"
          aria-label="Thoát toàn màn hình"
        >
          <Minimize2 size={18} />
        </button>
      )}

      {/* Camera Presets & Live Metrics Pill */}
      {!isImmersive && (
        <div
          style={{
            position: 'absolute',
            left: 12,
            right: 12,
            bottom: 84,
            display: 'flex',
            flexWrap: 'wrap',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 8,
            pointerEvents: 'none',
            zIndex: 14
          }}
        >
          {/* Camera presets */}
          <div
            className="glass-surface"
            style={{
              pointerEvents: 'auto',
              display: 'inline-flex',
              alignItems: 'center',
              gap: 4,
              padding: 4,
              borderRadius: 999
            }}
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
                style={{ minHeight: 30, padding: '4px 10px' }}
              >
                {p.label}
              </button>
            ))}
            <button
              type="button"
              onClick={() => setCamera(DEFAULT_CAMERA)}
              className="icon-btn glass-press"
              style={{ width: 30, height: 30 }}
              title="Đặt lại góc nhìn"
              aria-label="Đặt lại góc nhìn"
            >
              <RotateCcw size={14} />
            </button>
          </div>

          {/* Real-time Measurements Capsule */}
          <div
            className="glass-surface"
            style={{
              pointerEvents: 'auto',
              display: 'inline-flex',
              alignItems: 'center',
              gap: 12,
              padding: '7px 14px',
              borderRadius: 999,
              fontSize: 12
            }}
          >
            <span className="mono">
              h=<b>{hideAnswers ? '•••' : metrics.realH.toFixed(2)}</b>
            </span>
            <span className="mono">
              Sđ=<b>{hideAnswers ? '•••' : metrics.sDay.toFixed(2)}</b>
            </span>
            <span className="mono">
              Stp=<b>{hideAnswers ? '•••' : metrics.sTp.toFixed(2)}</b>
            </span>
            <span className="mono" style={{ color: accentHex }}>
              V=<b>{hideAnswers ? '•••' : metrics.vol.toFixed(2)}</b>
            </span>
            <button
              type="button"
              onClick={() => setHideAnswers((v) => !v)}
              className="icon-btn glass-press"
              style={{ width: 28, height: 28 }}
              title={hideAnswers ? 'Hiện đáp án tự kiểm chứng' : 'Ẩn đáp án để tự tính'}
              aria-label="Ẩn hoặc hiện đáp án"
            >
              {hideAnswers ? <EyeOff size={15} /> : <Eye size={15} />}
            </button>
          </div>
        </div>
      )}

      {/* Status Toast */}
      {statusBanner && (
        <div
          className="glass-surface"
          style={{
            position: 'absolute',
            top: 72,
            left: '50%',
            transform: 'translateX(-50%)',
            padding: '8px 16px',
            borderRadius: 999,
            fontSize: 12,
            fontWeight: 700,
            zIndex: 25
          }}
        >
          {statusBanner}
        </div>
      )}

      {/* Export / Import Popover */}
      {exportOpen && (
        <div
          className="glass-surface"
          style={{
            position: 'absolute',
            top: 66,
            right: 12,
            width: 230,
            borderRadius: 18,
            padding: 12,
            display: 'flex',
            flexDirection: 'column',
            gap: 8,
            zIndex: 25
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontSize: 12, fontWeight: 800 }}>Xuất / Nhập mô hình</span>
            <button type="button" className="icon-btn" style={{ width: 26, height: 26 }} onClick={() => setExportOpen(false)}>
              <X size={14} />
            </button>
          </div>
          <button type="button" className="chip-btn glass-press" onClick={handleExportPng}>Tải ảnh PNG sắc nét</button>
          <button type="button" className="chip-btn glass-press" onClick={handleExportSvg}>Tải vector SVG (chèn Word/LaTeX)</button>
          <button type="button" className="chip-btn glass-press" onClick={handleExportObj}>Tải khối 3D .OBJ (Blender/In 3D)</button>
          <button type="button" className="chip-btn glass-press" onClick={handleExportJson}>Lưu dự án .JSON</button>
          <button
            type="button"
            className="chip-btn glass-press"
            onClick={() => fileInputRef.current?.click()}
          >
            <Upload size={14} />
            <span>Mở lại file .JSON</span>
          </button>
          <input ref={fileInputRef} type="file" accept=".json" style={{ display: 'none' }} onChange={handleImportJson} />
        </div>
      )}

      {/* Side Drawer: 15 Shapes + Live Sliders */}
      {drawerOpen && !isImmersive && (
        <aside
          className="glass-surface scroll-area"
          style={{
            position: 'absolute',
            top: 72,
            right: 12,
            bottom: 136,
            width: 'min(340px, calc(100vw - 24px))',
            borderRadius: 22,
            padding: 16,
            display: 'flex',
            flexDirection: 'column',
            gap: 14,
            zIndex: 22
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div style={{ fontSize: 14, fontWeight: 800 }}>Thông số & 15 Khối 3D</div>
            <button type="button" className="icon-btn" onClick={() => setDrawerOpen(false)} aria-label="Đóng bảng">
              <X size={16} />
            </button>
          </div>

          {/* Sliders */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            <label style={{ fontSize: 12, display: 'flex', flexDirection: 'column', gap: 4 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span>{currentSpec.isRound ? 'Bán kính R (theo a)' : 'Cạnh đáy a'}</span>
                <b className="mono">{dimA.toFixed(1)}</b>
              </div>
              <input
                type="range"
                className="glass-slider"
                min={1}
                max={8}
                step={0.1}
                value={dimA}
                onChange={(e) => setDimA(parseFloat(e.target.value))}
              />
            </label>

            {currentSpec.hasB && (
              <label style={{ fontSize: 12, display: 'flex', flexDirection: 'column', gap: 4 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span>Cạnh b</span>
                  <b className="mono">{dimB.toFixed(1)}</b>
                </div>
                <input
                  type="range"
                  className="glass-slider"
                  min={1}
                  max={8}
                  step={0.1}
                  value={dimB}
                  onChange={(e) => setDimB(parseFloat(e.target.value))}
                />
              </label>
            )}

            {currentSpec.hasH !== false && (
              <label style={{ fontSize: 12, display: 'flex', flexDirection: 'column', gap: 4 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span>Chiều cao h</span>
                  <b className="mono">{dimH.toFixed(1)}</b>
                </div>
                <input
                  type="range"
                  className="glass-slider"
                  min={1}
                  max={8}
                  step={0.1}
                  value={dimH}
                  onChange={(e) => setDimH(parseFloat(e.target.value))}
                />
              </label>
            )}
            <div style={{ fontSize: 11.5, color: 'var(--text-secondary)' }}>
              Công thức: <span className="mono" style={{ color: accentHex }}>{metrics.formulaNote}</span>
            </div>
          </div>

          <hr style={{ border: 'none', borderTop: '1px solid var(--border-subtle)', margin: 0 }} />

          {/* 4 Categories of 15 Shapes */}
          {SHAPE_CATEGORIES.map((cat) => (
            <div key={cat.id} style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              <div style={{ fontSize: 11.5, fontWeight: 800, color: 'var(--text-secondary)' }}>{cat.title}</div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr', gap: 6 }}>
                {ALL_3D_SHAPES.filter((s) => s.category === cat.id).map((s) => {
                  const selected = s.key === shapeKey;
                  return (
                    <button
                      key={s.key}
                      type="button"
                      onClick={() => setShapeKey(s.key)}
                      className={`glass-press`}
                      style={{
                        textAlign: 'left',
                        padding: '9px 12px',
                        borderRadius: 14,
                        cursor: 'pointer',
                        border: `1px solid ${selected ? accentHex : 'var(--border-subtle)'}`,
                        background: selected ? `${accentHex}24` : 'rgba(148,163,184,0.08)',
                        color: 'var(--text-primary)'
                      }}
                    >
                      <div style={{ fontSize: 12.5, fontWeight: 700 }}>{s.title}</div>
                      <div style={{ fontSize: 11, color: 'var(--text-secondary)' }}>{s.subtitle}</div>
                    </button>
                  );
                })}
              </div>
            </div>
          ))}
        </aside>
      )}
    </div>
  );
};
