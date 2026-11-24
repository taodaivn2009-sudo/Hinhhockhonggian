import React from 'react';
import { Table2, X } from 'lucide-react';
import { VariationTableResult } from '../engine/variationTable';

interface VariationTableModalProps {
  data: VariationTableResult | null;
  paramM: number;
  accentHex: string;
  onClose: () => void;
}

export const VariationTableModal: React.FC<VariationTableModalProps> = ({
  data,
  paramM,
  accentHex,
  onClose
}) => {
  if (!data) return null;

  const hasM = /\bm\b/i.test(data.expression);

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        zIndex: 60,
        background: 'rgba(2, 6, 23, 0.65)',
        backdropFilter: 'blur(6px)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: 14
      }}
      onClick={onClose}
    >
      <div
        className="glass-surface scroll-area"
        style={{
          width: 'min(760px, 100%)',
          maxHeight: '88vh',
          borderRadius: 24,
          padding: 18,
          display: 'flex',
          flexDirection: 'column',
          gap: 14
        }}
        onClick={(e) => e.stopPropagation()}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 10 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <Table2 size={18} color={accentHex} />
            <div>
              <div style={{ fontSize: 15, fontWeight: 800 }}>Bảng Biến Thiên Tự Động</div>
              <div className="mono" style={{ fontSize: 12, color: accentHex }}>
                y = {data.expression} {hasM ? `(với m = ${paramM.toFixed(1)})` : ''}
              </div>
            </div>
          </div>
          <button type="button" className="icon-btn" onClick={onClose} aria-label="Đóng bảng biến thiên">
            <X size={18} />
          </button>
        </div>

        <div style={{ fontSize: 12.5, color: 'var(--text-secondary)' }}>
          <b>Tập xác định:</b> <span className="mono" style={{ color: 'var(--text-primary)' }}>{data.domainText}</span>
        </div>

        {/* Classic Vietnamese Variation Table Grid */}
        <div style={{ overflowX: 'auto', borderRadius: 14, border: '1px solid var(--border-subtle)' }}>
          <table
            style={{
              width: '100%',
              minWidth: Math.max(420, data.nodes.length * 92),
              borderCollapse: 'collapse',
              textAlign: 'center',
              fontSize: 13
            }}
          >
            <tbody>
              {/* Row x */}
              <tr style={{ borderBottom: '1px solid var(--border-subtle)', background: 'rgba(148,163,184,0.10)' }}>
                <td className="mono" style={{ width: 52, padding: 10, fontWeight: 800, borderRight: '1px solid var(--border-subtle)' }}>
                  x
                </td>
                {data.nodes.map((node, idx) => (
                  <React.Fragment key={`x-${idx}`}>
                    <td className="mono" style={{ padding: '10px 6px', fontWeight: 700 }}>{node.xLabel}</td>
                    {idx < data.intervals.length && <td style={{ padding: '10px 6px' }} />}
                  </React.Fragment>
                ))}
              </tr>

              {/* Row y' */}
              <tr style={{ borderBottom: '1px solid var(--border-subtle)' }}>
                <td className="mono" style={{ padding: 10, fontWeight: 800, borderRight: '1px solid var(--border-subtle)' }}>
                  y&apos;
                </td>
                {data.nodes.map((node, idx) => (
                  <React.Fragment key={`yp-${idx}`}>
                    <td
                      className="mono"
                      style={{
                        padding: '10px 4px',
                        fontWeight: 800,
                        color: node.yPrimeLabel === '||' ? '#f43f5e' : '#fbbf24'
                      }}
                    >
                      {node.yPrimeLabel}
                    </td>
                    {idx < data.intervals.length && (
                      <td
                        className="mono"
                        style={{
                          padding: '10px 6px',
                          fontWeight: 800,
                          fontSize: 15,
                          color:
                            data.intervals[idx].monotony === 'inc'
                              ? '#10b981'
                              : data.intervals[idx].monotony === 'dec'
                                ? '#f43f5e'
                                : 'var(--text-secondary)'
                        }}
                      >
                        {data.intervals[idx].signLabel}
                      </td>
                    )}
                  </React.Fragment>
                ))}
              </tr>

              {/* Row y */}
              <tr>
                <td className="mono" style={{ padding: 14, fontWeight: 800, borderRight: '1px solid var(--border-subtle)' }}>
                  y
                </td>
                {data.nodes.map((node, idx) => (
                  <React.Fragment key={`y-${idx}`}>
                    <td className="mono" style={{ padding: '14px 4px', fontWeight: 700, verticalAlign: 'middle' }}>
                      {node.kind === 'discontinuity' ? (
                        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 4 }}>
                          <span style={{ fontSize: 11 }}>{node.yLeftLabel}</span>
                          <span style={{ color: '#f43f5e', fontWeight: 900 }}>||</span>
                          <span style={{ fontSize: 11 }}>{node.yRightLabel}</span>
                        </div>
                      ) : (
                        <span>{node.yCenterLabel}</span>
                      )}
                    </td>
                    {idx < data.intervals.length && (
                      <td style={{ padding: '14px 6px', fontSize: 22, fontWeight: 800 }}>
                        {data.intervals[idx].monotony === 'inc' ? (
                          <span style={{ color: '#10b981' }}>↗</span>
                        ) : data.intervals[idx].monotony === 'dec' ? (
                          <span style={{ color: '#f43f5e' }}>↘</span>
                        ) : data.intervals[idx].monotony === 'const' ? (
                          <span style={{ color: accentHex }}>→</span>
                        ) : (
                          <span style={{ fontSize: 13, color: 'var(--text-secondary)' }}>///</span>
                        )}
                      </td>
                    )}
                  </React.Fragment>
                ))}
              </tr>
            </tbody>
          </table>
        </div>

        {/* Summary Cards */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(210px, 1fr))', gap: 10 }}>
          <div style={{ padding: 10, borderRadius: 14, background: 'rgba(148,163,184,0.08)', border: '1px solid var(--border-subtle)' }}>
            <div style={{ fontSize: 11.5, fontWeight: 800, color: '#fbbf24', marginBottom: 4 }}>Đường tiệm cận</div>
            {data.asymptotes.length === 0 ? (
              <div style={{ fontSize: 12, color: 'var(--text-secondary)' }}>Không có tiệm cận</div>
            ) : (
              data.asymptotes.map((a) => (
                <div key={a} className="mono" style={{ fontSize: 12 }}>{a}</div>
              ))
            )}
          </div>

          <div style={{ padding: 10, borderRadius: 14, background: 'rgba(148,163,184,0.08)', border: '1px solid var(--border-subtle)' }}>
            <div style={{ fontSize: 11.5, fontWeight: 800, color: '#10b981', marginBottom: 4 }}>Cực trị & Điểm uốn</div>
            {data.extrema.length === 0 && data.inflections.length === 0 ? (
              <div style={{ fontSize: 12, color: 'var(--text-secondary)' }}>Không có cực trị trong vùng khảo sát</div>
            ) : (
              <>
                {data.extrema.map((e) => (
                  <div key={e} className="mono" style={{ fontSize: 12 }}>{e}</div>
                ))}
                {data.inflections.map((inf) => (
                  <div key={inf} className="mono" style={{ fontSize: 12, color: accentHex }}>{inf}</div>
                ))}
              </>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
