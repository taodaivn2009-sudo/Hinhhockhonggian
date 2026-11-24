import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  Crosshair,
  Download,
  Eye,
  EyeOff,
  Sigma,
  Maximize2,
  Minimize2,
  Plus,
  RotateCcw,
  Trash2,
  X
} from 'lucide-react';
import {
  detectKeyPoints,
  evaluateMathExpr,
  FUNCTION_TEMPLATES,
  GraphFunctionItem,
  numericalDerivative,
  PRESET_2D_SHAPES
} from '../engine/geo2d';
import { AiDrawingPlan } from '../types/app';

interface Plane2DWorkspaceProps {
  accentHex: string;
  themeMode: 'dark' | 'light';
  isImmersive: boolean;
  onToggleImmersive: () => void;
  externalPlan: AiDrawingPlan | null;
  onPlanConsumed: () => void;
}

const FN_COLORS = ['#38bdf8', '#f43f5e', '#10b981', '#f59e0b', '#a855f7', '#06b6d4'];

export const Plane2DWorkspace: React.FC<Plane2DWorkspaceProps> = ({
  accentHex,
  themeMode,
  isImmersive,
  onToggleImmersive,
  externalPlan,
  onPlanConsumed
}) => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const [functions, setFunctions] = useState<GraphFunctionItem[]>([
    { id: 'fn-1', expression: 'x^3 - 3*m*x', color: FN_COLORS[0], visible: true, showDerivative: false }
  ]);
  const [paramM, setParamM] = useState<number>(1);
  const [presetId, setPresetId] = useState<string>('empty');
  const [traceMode, setTraceMode] = useState<boolean>(false);
  const [traceX, setTraceX] = useState<number>(1);
  const [showKeyPoints, setShowKeyPoints] = useState<boolean>(true);
  const [drawerOpen, setDrawerOpen] = useState<boolean>(false);
  const [banner, setBanner] = useState<string | null>(null);

  const [view, setView] = useState<{ cx: number; cy: number; scale: number }>({
    cx: 0,
    cy: 0,
    scale: 52
  });

  const dragRef = useRef<{ active: boolean; x: number; y: number }>({ active: false, x: 0, y: 0 });

  // Consume external AI 2D drawing plan
  useEffect(() => {
    if (!externalPlan || externalPlan.target !== 'plane2d') return;
    if (externalPlan.expressions2d && externalPlan.expressions2d.length > 0) {
      setFunctions(
        externalPlan.expressions2d.map((expr, i) => ({
          id: `ai-${Date.now()}-${i}`,
          expression: expr,
          color: FN_COLORS[i % FN_COLORS.length],
          visible: true,
          showDerivative: false
        }))
      );
    }
    if (externalPlan.preset2d && PRESET_2D_SHAPES.some((p) => p.id === externalPlan.preset2d)) {
      setPresetId(externalPlan.preset2d);
    }
    setBanner(`Đã vẽ từ AI: ${externalPlan.title}`);
    onPlanConsumed();
  }, [externalPlan, onPlanConsumed]);

  useEffect(() => {
    if (!banner) return;
    const t = window.setTimeout(() => setBanner(null), 3500);
    return () => window.clearTimeout(t);
  }, [banner]);

  const activePreset = useMemo(
    () => PRESET_2D_SHAPES.find((p) => p.id === presetId) || PRESET_2D_SHAPES[0],
    [presetId]
  );

  const primaryFn = functions.find((f) => f.visible && f.expression.trim().length > 0);

  // Render Oxy canvas
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
      const toScreenX = (wx: number) => w / 2 + (wx - view.cx) * view.scale;
      const toScreenY = (wy: number) => h / 2 - (wy - view.cy) * view.scale;
      const xMin = view.cx - w / (2 * view.scale);
      const xMax = view.cx + w / (2 * view.scale);
      const yMin = view.cy - h / (2 * view.scale);
      const yMax = view.cy + h / (2 * view.scale);

      // Adaptive grid spacing
      const rawStep = 70 / view.scale;
      const mag = Math.pow(10, Math.floor(Math.log10(rawStep)));
      const normStep = rawStep / mag;
      const step = normStep < 1.5 ? mag : normStep < 3.5 ? 2 * mag : normStep < 7.5 ? 5 * mag : 10 * mag;

      // Grid lines
      ctx.strokeStyle = isLight ? 'rgba(15, 23, 42, 0.09)' : 'rgba(148, 163, 184, 0.14)';
      ctx.lineWidth = 1;
      for (let gx = Math.floor(xMin / step) * step; gx <= xMax; gx += step) {
        const sx = toScreenX(gx);
        ctx.beginPath();
        ctx.moveTo(sx, 0);
        ctx.lineTo(sx, h);
        ctx.stroke();
      }
      for (let gy = Math.floor(yMin / step) * step; gy <= yMax; gy += step) {
        const sy = toScreenY(gy);
        ctx.beginPath();
        ctx.moveTo(0, sy);
        ctx.lineTo(w, sy);
        ctx.stroke();
      }

      // Main Oxy Axes
      const ox = toScreenX(0);
      const oy = toScreenY(0);
      ctx.strokeStyle = isLight ? 'rgba(15, 23, 42, 0.7)' : 'rgba(241, 245, 249, 0.65)';
      ctx.lineWidth = 1.8;
      ctx.beginPath();
      ctx.moveTo(0, oy);
      ctx.lineTo(w, oy);
      ctx.moveTo(ox, 0);
      ctx.lineTo(ox, h);
      ctx.stroke();

      // Axis ticks & numbers
      ctx.font = '600 11px "JetBrains Mono", monospace';
      ctx.fillStyle = isLight ? '#475569' : '#94a3b8';
      for (let gx = Math.floor(xMin / step) * step; gx <= xMax; gx += step) {
        if (Math.abs(gx) < step * 0.1) continue;
        const sx = toScreenX(gx);
        ctx.fillText(Number(gx.toFixed(2)).toString(), sx + 3, Math.min(h - 8, Math.max(16, oy + 14)));
      }
      for (let gy = Math.floor(yMin / step) * step; gy <= yMax; gy += step) {
        if (Math.abs(gy) < step * 0.1) continue;
        const sy = toScreenY(gy);
        ctx.fillText(Number(gy.toFixed(2)).toString(), Math.min(w - 30, Math.max(8, ox + 6)), sy - 4);
      }

      // Preset 2D shape
      if (activePreset.points.length > 0) {
        ctx.save();
        ctx.strokeStyle = accentHex;
        ctx.fillStyle = `${accentHex}22`;
        ctx.lineWidth = 2.4;
        if (activePreset.circleRadius) {
          const c = activePreset.points[0];
          ctx.beginPath();
          ctx.arc(toScreenX(c.x), toScreenY(c.y), activePreset.circleRadius * view.scale, 0, Math.PI * 2);
          ctx.fill();
          ctx.stroke();
        } else if (activePreset.points.length >= 2) {
          ctx.beginPath();
          activePreset.points.forEach((pt, idx) => {
            const sx = toScreenX(pt.x);
            const sy = toScreenY(pt.y);
            if (idx === 0) ctx.moveTo(sx, sy);
            else ctx.lineTo(sx, sy);
          });
          if (activePreset.closed) ctx.closePath();
          ctx.fill();
          ctx.stroke();
        }
        for (const pt of activePreset.points) {
          const sx = toScreenX(pt.x);
          const sy = toScreenY(pt.y);
          ctx.beginPath();
          ctx.arc(sx, sy, 4.2, 0, Math.PI * 2);
          ctx.fillStyle = accentHex;
          ctx.fill();
          ctx.font = '800 13px "Plus Jakarta Sans", sans-serif';
          ctx.fillStyle = isLight ? '#0f172a' : '#f8fafc';
          ctx.fillText(`${pt.name}(${pt.x};${pt.y})`, sx + 8, sy - 6);
        }
        ctx.restore();
      }

      // Plot all visible functions
      const sampleCount = Math.min(900, Math.max(360, Math.round(w)));
      for (const fn of functions) {
        if (!fn.visible || !fn.expression.trim()) continue;

        // Curve y = f(x)
        ctx.save();
        ctx.strokeStyle = fn.color;
        ctx.lineWidth = 2.6;
        ctx.beginPath();
        let penDown = false;
        let lastSy = 0;
        for (let i = 0; i <= sampleCount; i++) {
          const sx = (i / sampleCount) * w;
          const wx = view.cx + (sx - w / 2) / view.scale;
          const wy = evaluateMathExpr(fn.expression, wx, paramM);
          if (!Number.isFinite(wy) || Math.abs(wy) > 1e4) {
            penDown = false;
            continue;
          }
          const sy = toScreenY(wy);
          if (penDown && Math.abs(sy - lastSy) > h * 1.4) {
            penDown = false;
          }
          if (!penDown) {
            ctx.moveTo(sx, sy);
            penDown = true;
          } else {
            ctx.lineTo(sx, sy);
          }
          lastSy = sy;
        }
        ctx.stroke();
        ctx.restore();

        // Derivative y = f'(x)
        if (fn.showDerivative) {
          ctx.save();
          ctx.strokeStyle = fn.color;
          ctx.globalAlpha = 0.62;
          ctx.setLineDash([6, 5]);
          ctx.lineWidth = 1.9;
          ctx.beginPath();
          let dDown = false;
          let dLast = 0;
          for (let i = 0; i <= sampleCount; i++) {
            const sx = (i / sampleCount) * w;
            const wx = view.cx + (sx - w / 2) / view.scale;
            const wy = numericalDerivative(fn.expression, wx, paramM);
            if (!Number.isFinite(wy) || Math.abs(wy) > 1e4) {
              dDown = false;
              continue;
            }
            const sy = toScreenY(wy);
            if (dDown && Math.abs(sy - dLast) > h * 1.4) dDown = false;
            if (!dDown) {
              ctx.moveTo(sx, sy);
              dDown = true;
            } else {
              ctx.lineTo(sx, sy);
            }
            dLast = sy;
          }
          ctx.stroke();
          ctx.restore();
        }

        // Key points (roots / local max / local min)
        if (showKeyPoints) {
          const pts = detectKeyPoints(fn.expression, paramM, xMin, xMax);
          for (const kp of pts) {
            const sx = toScreenX(kp.x);
            const sy = toScreenY(kp.y);
            ctx.beginPath();
            ctx.arc(sx, sy, 4.5, 0, Math.PI * 2);
            ctx.fillStyle = kp.kind === 'root' ? '#10b981' : '#f59e0b';
            ctx.fill();
            ctx.strokeStyle = '#ffffff';
            ctx.lineWidth = 1.4;
            ctx.stroke();
          }
        }
      }

      // Trace mode crosshair & tangent line on primary function
      if (traceMode && primaryFn) {
        const ty = evaluateMathExpr(primaryFn.expression, traceX, paramM);
        const slope = numericalDerivative(primaryFn.expression, traceX, paramM);
        if (Number.isFinite(ty)) {
          const sx = toScreenX(traceX);
          const sy = toScreenY(ty);

          // Tangent line
          if (Number.isFinite(slope)) {
            const x1 = xMin;
            const y1 = ty + slope * (x1 - traceX);
            const x2 = xMax;
            const y2 = ty + slope * (x2 - traceX);
            ctx.save();
            ctx.strokeStyle = '#f59e0b';
            ctx.lineWidth = 1.6;
            ctx.setLineDash([5, 4]);
            ctx.beginPath();
            ctx.moveTo(toScreenX(x1), toScreenY(y1));
            ctx.lineTo(toScreenX(x2), toScreenY(y2));
            ctx.stroke();
            ctx.restore();
          }

          // Point marker
          ctx.beginPath();
          ctx.arc(sx, sy, 6, 0, Math.PI * 2);
          ctx.fillStyle = '#f59e0b';
          ctx.fill();
          ctx.strokeStyle = '#ffffff';
          ctx.lineWidth = 2;
          ctx.stroke();
        }
      }

      ctx.restore();
    };

    render();
    window.addEventListener('resize', render);
    return () => window.removeEventListener('resize', render);
  }, [functions, paramM, view, activePreset, traceMode, traceX, showKeyPoints, accentHex, themeMode, primaryFn]);

  const handlePointerDown = (e: React.PointerEvent<HTMLCanvasElement>) => {
    e.currentTarget.setPointerCapture(e.pointerId);
    if (traceMode) {
      const rect = e.currentTarget.getBoundingClientRect();
      const wx = view.cx + (e.clientX - rect.left - rect.width / 2) / view.scale;
      setTraceX(wx);
      dragRef.current = { active: true, x: e.clientX, y: e.clientY };
      return;
    }
    dragRef.current = { active: true, x: e.clientX, y: e.clientY };
  };

  const handlePointerMove = (e: React.PointerEvent<HTMLCanvasElement>) => {
    if (!dragRef.current.active) return;
    if (traceMode) {
      const rect = e.currentTarget.getBoundingClientRect();
      const wx = view.cx + (e.clientX - rect.left - rect.width / 2) / view.scale;
      setTraceX(wx);
      return;
    }
    const dx = e.clientX - dragRef.current.x;
    const dy = e.clientY - dragRef.current.y;
    dragRef.current = { active: true, x: e.clientX, y: e.clientY };
    setView((prev) => ({
      ...prev,
      cx: prev.cx - dx / prev.scale,
      cy: prev.cy + dy / prev.scale
    }));
  };

  const handlePointerUp = () => {
    dragRef.current.active = false;
  };

  const handleWheel = (e: React.WheelEvent<HTMLCanvasElement>) => {
    const factor = e.deltaY > 0 ? 0.9 : 1.11;
    setView((prev) => ({
      ...prev,
      scale: Math.min(260, Math.max(12, prev.scale * factor))
    }));
  };

  const addFunction = (expr = 'sin(x)') => {
    setFunctions((prev) => [
      ...prev,
      {
        id: `fn-${Date.now()}`,
        expression: expr,
        color: FN_COLORS[prev.length % FN_COLORS.length],
        visible: true,
        showDerivative: false
      }
    ]);
  };

  const exportPng = () => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const a = document.createElement('a');
    a.href = canvas.toDataURL('image/png');
    a.download = 'tooltoan-2d-oxy.png';
    a.click();
  };

  const traceY = primaryFn ? evaluateMathExpr(primaryFn.expression, traceX, paramM) : NaN;
  const traceSlope = primaryFn ? numericalDerivative(primaryFn.expression, traceX, paramM) : NaN;

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
          cursor: traceMode ? 'crosshair' : 'grab'
        }}
      />

      {/* Top Floating Bar */}
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
            <Sigma size={18} color={accentHex} />
            <div>
              <div style={{ fontSize: 13, fontWeight: 800 }}>Đồ thị & Hình phẳng Oxy</div>
              <div className="mono" style={{ fontSize: 11, color: 'var(--text-secondary)' }}>
                {primaryFn ? `y = ${primaryFn.expression}` : activePreset.title}
              </div>
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
            >
              <Plus size={15} />
              <span>Hàm số & Hình 2D</span>
            </button>
            <button
              type="button"
              onClick={() => setTraceMode((v) => !v)}
              className="icon-btn glass-press"
              title="Chế độ dò tọa độ & tiếp tuyến (Trace)"
              aria-label="Trace"
            >
              <Crosshair size={17} color={traceMode ? '#f59e0b' : undefined} />
            </button>
            <button
              type="button"
              onClick={() => setView({ cx: 0, cy: 0, scale: 52 })}
              className="icon-btn glass-press"
              title="Về gốc tọa độ O(0;0)"
              aria-label="Về gốc tọa độ"
            >
              <RotateCcw size={16} />
            </button>
            <button
              type="button"
              onClick={exportPng}
              className="icon-btn glass-press"
              title="Tải ảnh PNG"
              aria-label="Tải ảnh PNG"
            >
              <Download size={16} />
            </button>
            <button
              type="button"
              onClick={onToggleImmersive}
              className="icon-btn glass-press"
              title="Toàn màn hình"
              aria-label="Toàn màn hình"
            >
              <Maximize2 size={16} />
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

      {/* Bottom Floating Parameter m & Trace Readout */}
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
          <div
            className="glass-surface"
            style={{
              pointerEvents: 'auto',
              display: 'inline-flex',
              alignItems: 'center',
              gap: 10,
              padding: '7px 14px',
              borderRadius: 999
            }}
          >
            <span className="mono" style={{ fontSize: 12, fontWeight: 700, color: accentHex }}>
              m = {paramM.toFixed(1)}
            </span>
            <input
              type="range"
              className="glass-slider"
              style={{ width: 125 }}
              min={-5}
              max={5}
              step={0.1}
              value={paramM}
              onChange={(e) => setParamM(parseFloat(e.target.value))}
            />
          </div>

          {traceMode && primaryFn && Number.isFinite(traceY) && (
            <div
              className="glass-surface mono"
              style={{
                pointerEvents: 'auto',
                padding: '7px 14px',
                borderRadius: 999,
                fontSize: 12,
                display: 'inline-flex',
                gap: 10
              }}
            >
              <span>x={traceX.toFixed(2)}</span>
              <span>f(x)={traceY.toFixed(2)}</span>
              <span style={{ color: '#f59e0b' }}>
                f&apos;(x)={Number.isFinite(traceSlope) ? traceSlope.toFixed(2) : '—'}
              </span>
            </div>
          )}
        </div>
      )}

      {banner && (
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
          {banner}
        </div>
      )}

      {/* Side Drawer: Functions + Templates + 2D Shapes */}
      {drawerOpen && !isImmersive && (
        <aside
          className="glass-surface scroll-area"
          style={{
            position: 'absolute',
            top: 72,
            right: 12,
            bottom: 136,
            width: 'min(360px, calc(100vw - 24px))',
            borderRadius: 22,
            padding: 16,
            display: 'flex',
            flexDirection: 'column',
            gap: 12,
            zIndex: 22
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div style={{ fontSize: 14, fontWeight: 800 }}>Hàm số y = f(x) & Hình phẳng</div>
            <button type="button" className="icon-btn" onClick={() => setDrawerOpen(false)} aria-label="Đóng">
              <X size={16} />
            </button>
          </div>

          {/* Function List */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {functions.map((fn) => (
              <div
                key={fn.id}
                style={{
                  padding: 10,
                  borderRadius: 14,
                  border: `1px solid ${fn.color}66`,
                  background: 'rgba(148, 163, 184, 0.08)',
                  display: 'flex',
                  flexDirection: 'column',
                  gap: 8
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                  <span style={{ width: 10, height: 10, borderRadius: '50%', background: fn.color, flexShrink: 0 }} />
                  <input
                    type="text"
                    className="glass-input mono"
                    style={{ padding: '7px 10px', fontSize: 12.5 }}
                    value={fn.expression}
                    placeholder="VD: x^3 - 3*m*x"
                    onChange={(e) =>
                      setFunctions((prev) =>
                        prev.map((item) => (item.id === fn.id ? { ...item, expression: e.target.value } : item))
                      )
                    }
                  />
                </div>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 6 }}>
                  <button
                    type="button"
                    className={`chip-btn${fn.showDerivative ? ' active' : ''}`}
                    style={{ minHeight: 28, padding: '3px 10px', fontSize: 11 }}
                    onClick={() =>
                      setFunctions((prev) =>
                        prev.map((item) => (item.id === fn.id ? { ...item, showDerivative: !item.showDerivative } : item))
                      )
                    }
                  >
                    Vẽ đạo hàm f&apos;(x)
                  </button>
                  <div style={{ display: 'flex', gap: 4 }}>
                    <button
                      type="button"
                      className="icon-btn"
                      style={{ width: 30, height: 30 }}
                      onClick={() =>
                        setFunctions((prev) =>
                          prev.map((item) => (item.id === fn.id ? { ...item, visible: !item.visible } : item))
                        )
                      }
                      aria-label="Ẩn hiện hàm"
                    >
                      {fn.visible ? <Eye size={14} /> : <EyeOff size={14} />}
                    </button>
                    {functions.length > 1 && (
                      <button
                        type="button"
                        className="icon-btn"
                        style={{ width: 30, height: 30 }}
                        onClick={() => setFunctions((prev) => prev.filter((item) => item.id !== fn.id))}
                        aria-label="Xóa hàm"
                      >
                        <Trash2 size={14} color="#f43f5e" />
                      </button>
                    )}
                  </div>
                </div>
              </div>
            ))}

            <div style={{ display: 'flex', gap: 8 }}>
              <button type="button" className="primary-btn glass-press" style={{ flex: 1 }} onClick={() => addFunction()}>
                <Plus size={15} />
                <span>Thêm hàm y = f(x)</span>
              </button>
              <button
                type="button"
                className={`chip-btn${showKeyPoints ? ' active' : ''}`}
                onClick={() => setShowKeyPoints((v) => !v)}
              >
                Cực trị/Nghiệm
              </button>
            </div>
          </div>

          {/* Quick Function Templates */}
          <div>
            <div style={{ fontSize: 11.5, fontWeight: 800, color: 'var(--text-secondary)', marginBottom: 6 }}>
              Mẫu hàm số THPT nhanh
            </div>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
              {FUNCTION_TEMPLATES.map((tpl) => (
                <button
                  key={tpl.label}
                  type="button"
                  className="chip-btn glass-press"
                  style={{ minHeight: 30, padding: '4px 10px' }}
                  onClick={() =>
                    setFunctions((prev) =>
                      prev.length === 0
                        ? [{ id: `tpl-${Date.now()}`, expression: tpl.expr, color: FN_COLORS[0], visible: true, showDerivative: false }]
                        : prev.map((item, idx) => (idx === 0 ? { ...item, expression: tpl.expr, visible: true } : item))
                    )
                  }
                >
                  {tpl.label}
                </button>
              ))}
            </div>
          </div>

          <hr style={{ border: 'none', borderTop: '1px solid var(--border-subtle)', margin: 0 }} />

          {/* 9 Preset 2D Shapes */}
          <div>
            <div style={{ fontSize: 11.5, fontWeight: 800, color: 'var(--text-secondary)', marginBottom: 6 }}>
              Hình học phẳng mẫu (9 hình)
            </div>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
              {PRESET_2D_SHAPES.map((shape) => (
                <button
                  key={shape.id}
                  type="button"
                  className={`chip-btn glass-press${presetId === shape.id ? ' active' : ''}`}
                  style={{ minHeight: 30, padding: '4px 10px' }}
                  onClick={() => setPresetId(shape.id)}
                >
                  {shape.title}
                </button>
              ))}
            </div>
          </div>
        </aside>
      )}
    </div>
  );
};
