import React, { useState } from 'react';
import {
  Activity,
  CheckCircle2,
  Download,
  ExternalLink,
  KeyRound,
  Keyboard,
  Moon,
  Palette,
  Plus,
  RefreshCw,
  Sliders,
  Sparkles,
  Sun,
  Trash2,
  XCircle
} from 'lucide-react';
import {
  AI_PROVIDERS,
  AiProviderId,
  AiSavedProfile,
  AiTokenUsage,
  AppSettingsState,
  CustomAiEndpoint,
  DEFAULT_APP_SETTINGS,
  GLASS_PRESETS,
  THEME_PRESETS
} from '../types/app';
import { fetchProviderModels, probeProviderModel } from '../services/aiClient';

type SettingsSection = 'theme' | 'glass' | 'ai' | 'models' | 'tokens' | 'shortcuts';

interface SettingsWorkspaceProps {
  settings: AppSettingsState;
  onUpdateSettings: (next: AppSettingsState) => void;
  activeProvider: AiProviderId;
  onChangeProvider: (id: AiProviderId) => void;
  profiles: Record<AiProviderId, AiSavedProfile>;
  onUpdateProfile: (id: AiProviderId, next: AiSavedProfile) => void;
  customEndpoints: CustomAiEndpoint[];
  onSaveCustomEndpoints: (next: CustomAiEndpoint[]) => void;
  tokenUsage: AiTokenUsage;
  onResetTokenUsage: () => void;
  canInstallPwa: boolean;
  onInstallPwa: () => void;
}

const ACCENT_SWATCHES = ['#38BDF8', '#10B981', '#A855F7', '#F59E0B', '#F43F5E', '#6366F1', '#06B6D4', '#EC4899'];

export const SettingsWorkspace: React.FC<SettingsWorkspaceProps> = ({
  settings,
  onUpdateSettings,
  activeProvider,
  onChangeProvider,
  profiles,
  onUpdateProfile,
  customEndpoints,
  onSaveCustomEndpoints,
  tokenUsage,
  onResetTokenUsage,
  canInstallPwa,
  onInstallPwa
}) => {
  const [section, setSection] = useState<SettingsSection>('theme');
  const [fetchedModels, setFetchedModels] = useState<string[]>([]);
  const [fetchingModels, setFetchingModels] = useState<boolean>(false);
  const [modelError, setModelError] = useState<string | null>(null);
  const [probing, setProbing] = useState<boolean>(false);
  const [probeResult, setProbeResult] = useState<{ ok: boolean; latencyMs: number; message: string } | null>(null);

  // New custom endpoint form
  const [ceName, setCeName] = useState<string>('');
  const [ceUrl, setCeUrl] = useState<string>('https://api.together.xyz/v1');
  const [ceKey, setCeKey] = useState<string>('');
  const [ceModel, setCeModel] = useState<string>('meta-llama/Llama-3.3-70B-Instruct-Turbo');

  const lg = settings.liquidGlass;
  const activeProfile = profiles[activeProvider];
  const activeSpec = AI_PROVIDERS.find((p) => p.id === activeProvider) || AI_PROVIDERS[0];

  const patchGlass = (patch: Partial<typeof lg>) => {
    onUpdateSettings({ ...settings, liquidGlass: { ...lg, ...patch } });
  };

  const handleFetchModels = async () => {
    setFetchingModels(true);
    setModelError(null);
    try {
      const list = await fetchProviderModels(activeProvider, activeProfile);
      setFetchedModels(list);
      if (list.length === 0) setModelError('Máy chủ không trả về model nào.');
    } catch (e) {
      setModelError(e instanceof Error ? e.message : 'Lỗi tải danh sách model');
    } finally {
      setFetchingModels(false);
    }
  };

  const handleProbe = async () => {
    setProbing(true);
    setProbeResult(null);
    const res = await probeProviderModel(activeProvider, activeProfile);
    setProbeResult(res);
    setProbing(false);
  };

  const handleAddCustomEndpoint = () => {
    if (!ceName.trim() || !ceUrl.trim() || !ceModel.trim()) return;
    const item: CustomAiEndpoint = {
      id: `ce_${Date.now()}`,
      name: ceName.trim(),
      baseUrl: ceUrl.trim(),
      apiKey: ceKey.trim(),
      model: ceModel.trim()
    };
    onSaveCustomEndpoints([...customEndpoints, item]);
    setCeName('');
    setCeKey('');
  };

  const navItems: { id: SettingsSection; label: string; icon: React.ReactNode }[] = [
    { id: 'theme', label: '1. Chủ đề & Màu', icon: <Palette size={15} /> },
    { id: 'glass', label: '2. Liquid Glass', icon: <Sparkles size={15} /> },
    { id: 'ai', label: '3. API Key & Endpoints', icon: <KeyRound size={15} /> },
    { id: 'models', label: '4. Model & Test Key', icon: <Activity size={15} /> },
    { id: 'tokens', label: '5. Token & Độ nhạy', icon: <Sliders size={15} /> },
    { id: 'shortcuts', label: '6. Phím tắt & PWA', icon: <Keyboard size={15} /> }
  ];

  return (
    <div style={{ width: '100%', height: '100%', display: 'flex', flexDirection: 'column', padding: '10px 12px 78px', gap: 10 }}>
      {/* 6-Section Hub Nav */}
      <div className="glass-surface" style={{ borderRadius: 18, padding: '8px 10px', display: 'flex', gap: 6, overflowX: 'auto' }}>
        {navItems.map((item) => (
          <button
            key={item.id}
            className={`chip-btn glass-press ${section === item.id ? 'active' : ''}`}
            onClick={() => setSection(item.id)}
          >
            {item.icon} {item.label}
          </button>
        ))}
      </div>

      {/* Main Content Card */}
      <div className="glass-surface scroll-area" style={{ flex: 1, borderRadius: 20, padding: 16 }}>
        <div style={{ maxWidth: 760, margin: '0 auto', display: 'flex', flexDirection: 'column', gap: 16 }}>
          {/* SECTION 1: THEME & PRESETS */}
          {section === 'theme' && (
            <>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 8 }}>
                <div>
                  <div style={{ fontSize: 16, fontWeight: 800 }}>Giao diện & Bộ Preset Chủ đề</div>
                  <div style={{ fontSize: 12, color: 'var(--text-secondary)' }}>Đồng bộ sắc thái Sáng/Tối, màu nhấn và độ tương phản</div>
                </div>
                <div style={{ display: 'flex', gap: 6 }}>
                  <button
                    className={`chip-btn ${settings.themeMode === 'dark' ? 'active' : ''}`}
                    onClick={() => onUpdateSettings({ ...settings, themeMode: 'dark' })}
                  >
                    <Moon size={14} /> Nền Tối
                  </button>
                  <button
                    className={`chip-btn ${settings.themeMode === 'light' ? 'active' : ''}`}
                    onClick={() => onUpdateSettings({ ...settings, themeMode: 'light' })}
                  >
                    <Sun size={14} /> Nền Sáng
                  </button>
                </div>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(150px, 1fr))', gap: 10 }}>
                {THEME_PRESETS.map((preset) => (
                  <button
                    key={preset.id}
                    onClick={() =>
                      onUpdateSettings({
                        ...settings,
                        themeMode: preset.mode,
                        liquidGlass: { ...lg, accentHex: preset.accentHex, themePreset: preset.id }
                      })
                    }
                    className="glass-press"
                    style={{
                      padding: 12,
                      borderRadius: 14,
                      border: `1.5px solid ${lg.themePreset === preset.id ? preset.accentHex : 'var(--border-subtle)'}`,
                      background: lg.themePreset === preset.id ? `${preset.accentHex}20` : 'rgba(148,163,184,0.08)',
                      color: 'var(--text-primary)',
                      cursor: 'pointer',
                      textAlign: 'left',
                      display: 'flex',
                      flexDirection: 'column',
                      gap: 8
                    }}
                  >
                    <div style={{ display: 'flex', gap: 6 }}>
                      <span style={{ width: 22, height: 22, borderRadius: 99, background: preset.swatch[0], border: '1px solid rgba(255,255,255,0.3)' }} />
                      <span style={{ width: 22, height: 22, borderRadius: 99, background: preset.swatch[1] }} />
                    </div>
                    <div style={{ fontSize: 12.5, fontWeight: 700 }}>{preset.label}</div>
                  </button>
                ))}
              </div>

              <div>
                <div style={{ fontSize: 12.5, fontWeight: 700, marginBottom: 8 }}>Màu nhấn tùy chỉnh</div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                  {ACCENT_SWATCHES.map((hex) => (
                    <button
                      key={hex}
                      onClick={() => patchGlass({ accentHex: hex })}
                      style={{
                        width: 32,
                        height: 32,
                        borderRadius: 999,
                        background: hex,
                        border: lg.accentHex.toLowerCase() === hex.toLowerCase() ? '3px solid #fff' : '1px solid rgba(255,255,255,0.25)',
                        cursor: 'pointer'
                      }}
                    />
                  ))}
                  <input
                    type="color"
                    value={lg.accentHex}
                    onChange={(e) => patchGlass({ accentHex: e.target.value })}
                    style={{ width: 38, height: 32, border: 'none', background: 'transparent', cursor: 'pointer' }}
                  />
                </div>
              </div>

              <label style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 12px', borderRadius: 12, background: 'rgba(148,163,184,0.08)' }}>
                <div>
                  <div style={{ fontSize: 13, fontWeight: 700 }}>Chữ tương phản cao (High Contrast)</div>
                  <div style={{ fontSize: 11.5, color: 'var(--text-secondary)' }}>Tăng độ rõ nét chữ trên nền kính mờ</div>
                </div>
                <input
                  type="checkbox"
                  checked={lg.highContrastText}
                  onChange={(e) => patchGlass({ highContrastText: e.target.checked })}
                />
              </label>

              <label style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 12px', borderRadius: 12, background: 'rgba(148,163,184,0.08)' }}>
                <div>
                  <div style={{ fontSize: 13, fontWeight: 700 }}>Nhớ không gian làm việc gần nhất</div>
                  <div style={{ fontSize: 11.5, color: 'var(--text-secondary)' }}>Tự động mở lại tab 3D / 2D / Sổ tay / AI cũ</div>
                </div>
                <input
                  type="checkbox"
                  checked={settings.rememberWorkspace}
                  onChange={(e) => onUpdateSettings({ ...settings, rememberWorkspace: e.target.checked })}
                />
              </label>
            </>
          )}

          {/* SECTION 2: LIQUID GLASS STUDIO */}
          {section === 'glass' && (
            <>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div>
                  <div style={{ fontSize: 16, fontWeight: 800 }}>Liquid Glass Studio</div>
                  <div style={{ fontSize: 12, color: 'var(--text-secondary)' }}>Tinh chỉnh quang học kính lỏng, khúc xạ, vát cạnh và tán sắc</div>
                </div>
                <button
                  className={`chip-btn ${lg.enabled ? 'active' : ''}`}
                  onClick={() => patchGlass({ enabled: !lg.enabled })}
                >
                  {lg.enabled ? 'Đang bật Kính lỏng' : 'Đã tắt Kính lỏng'}
                </button>
              </div>

              {/* Glass Presets */}
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(160px, 1fr))', gap: 8 }}>
                {GLASS_PRESETS.map((gp) => (
                  <button
                    key={gp.id}
                    className="glass-press"
                    onClick={() => patchGlass({ ...gp.patch, glassPreset: gp.id })}
                    style={{
                      padding: 10,
                      borderRadius: 12,
                      border: `1.5px solid ${lg.glassPreset === gp.id ? lg.accentHex : 'var(--border-subtle)'}`,
                      background: lg.glassPreset === gp.id ? `${lg.accentHex}20` : 'rgba(148,163,184,0.08)',
                      color: 'var(--text-primary)',
                      textAlign: 'left',
                      cursor: 'pointer'
                    }}
                  >
                    <div style={{ fontSize: 12.5, fontWeight: 800 }}>{gp.label}</div>
                    <div style={{ fontSize: 11, color: 'var(--text-secondary)', marginTop: 2 }}>{gp.desc}</div>
                  </button>
                ))}
              </div>

              {/* Optical Sliders */}
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: 12 }}>
                {[
                  { label: 'Độ mờ kính (Blur)', val: `${lg.blurRadius}px`, min: 0, max: 32, step: 1, num: lg.blurRadius, onChange: (v: number) => patchGlass({ blurRadius: v }) },
                  { label: 'Độ trong suốt (Translucency)', val: `${Math.round(lg.translucency * 100)}%`, min: 0.25, max: 0.95, step: 0.02, num: lg.translucency, onChange: (v: number) => patchGlass({ translucency: v }) },
                  { label: 'Độ rực màu (Vibrancy)', val: `${Math.round(lg.vibrancy * 100)}%`, min: 0, max: 1, step: 0.02, num: lg.vibrancy, onChange: (v: number) => patchGlass({ vibrancy: v }) },
                  { label: 'Hạt mờ chống loang (Frost Grain)', val: `${Math.round(lg.frostGrain * 100)}%`, min: 0, max: 1, step: 0.02, num: lg.frostGrain, onChange: (v: number) => patchGlass({ frostGrain: v }) },
                  { label: 'Ánh viền trên (Specular Rim)', val: `${Math.round(lg.specularRim * 100)}%`, min: 0, max: 1, step: 0.02, num: lg.specularRim, onChange: (v: number) => patchGlass({ specularRim: v }) },
                  { label: 'Độ khúc xạ ánh sáng (Refraction)', val: `${Math.round(lg.refraction * 100)}%`, min: 0, max: 1, step: 0.02, num: lg.refraction, onChange: (v: number) => patchGlass({ refraction: v }) },
                  { label: 'Độ dày vát cạnh (Bevel)', val: `${lg.bevelThickness.toFixed(1)}px`, min: 0.5, max: 3, step: 0.1, num: lg.bevelThickness, onChange: (v: number) => patchGlass({ bevelThickness: v }) },
                  { label: 'Tán sắc viền màu (Dispersion)', val: `${Math.round(lg.dispersion * 100)}%`, min: 0, max: 1, step: 0.02, num: lg.dispersion, onChange: (v: number) => patchGlass({ dispersion: v }) }
                ].map((sl) => (
                  <div key={sl.label} style={{ padding: '10px 12px', borderRadius: 12, background: 'rgba(148,163,184,0.08)' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, marginBottom: 6 }}>
                      <span style={{ fontWeight: 600 }}>{sl.label}</span>
                      <span className="mono" style={{ color: lg.accentHex }}>{sl.val}</span>
                    </div>
                    <input
                      type="range"
                      className="glass-slider"
                      min={sl.min}
                      max={sl.max}
                      step={sl.step}
                      value={sl.num}
                      onChange={(e) => sl.onChange(parseFloat(e.target.value))}
                    />
                  </div>
                ))}
              </div>

              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 10 }}>
                <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, fontWeight: 600 }}>
                  <input
                    type="checkbox"
                    checked={lg.dynamicShimmer}
                    onChange={(e) => patchGlass({ dynamicShimmer: e.target.checked })}
                  />
                  Ánh lấp lánh động trên viền kính (Dynamic Shimmer)
                </label>
                <button
                  className="chip-btn"
                  onClick={() => patchGlass(DEFAULT_APP_SETTINGS.liquidGlass)}
                >
                  Khôi phục kính mặc định
                </button>
              </div>
            </>
          )}

          {/* SECTION 3: AI PROVIDERS & MULTIPLE CUSTOM ENDPOINTS */}
          {section === 'ai' && (
            <>
              <div>
                <div style={{ fontSize: 16, fontWeight: 800 }}>Kết nối AI & Quản lý API Key</div>
                <div style={{ fontSize: 12, color: 'var(--text-secondary)' }}>Khóa API được lưu cục bộ ngay trên trình duyệt của bạn và đi qua proxy bảo mật /api/ai-proxy</div>
              </div>

              <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                {AI_PROVIDERS.map((p) => (
                  <button
                    key={p.id}
                    className={`chip-btn ${activeProvider === p.id ? 'active' : ''}`}
                    onClick={() => onChangeProvider(p.id)}
                  >
                    {p.shortTitle}
                    {profiles[p.id].apiKey ? ' ✓' : ''}
                  </button>
                ))}
              </div>

              <div style={{ padding: 14, borderRadius: 14, background: 'rgba(148,163,184,0.08)', display: 'flex', flexDirection: 'column', gap: 10 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 6 }}>
                  <span style={{ fontSize: 13.5, fontWeight: 800 }}>{activeSpec.fullTitle}</span>
                  {activeSpec.portalUrl && (
                    <a
                      href={activeSpec.portalUrl}
                      target="_blank"
                      rel="noreferrer"
                      className="chip-btn"
                      style={{ textDecoration: 'none', minHeight: 28, padding: '2px 10px', fontSize: 11.5 }}
                    >
                      Lấy Key <ExternalLink size={12} />
                    </a>
                  )}
                </div>

                <div>
                  <div style={{ fontSize: 11.5, color: 'var(--text-secondary)', marginBottom: 4 }}>API Key</div>
                  <input
                    type="password"
                    className="glass-input mono"
                    placeholder={activeSpec.keyPlaceholder}
                    value={activeProfile.apiKey}
                    onChange={(e) => onUpdateProfile(activeProvider, { ...activeProfile, apiKey: e.target.value })}
                  />
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
                  <div>
                    <div style={{ fontSize: 11.5, color: 'var(--text-secondary)', marginBottom: 4 }}>Model ID</div>
                    <input
                      className="glass-input mono"
                      value={activeProfile.model}
                      onChange={(e) => onUpdateProfile(activeProvider, { ...activeProfile, model: e.target.value })}
                    />
                  </div>
                  <div>
                    <div style={{ fontSize: 11.5, color: 'var(--text-secondary)', marginBottom: 4 }}>Base URL</div>
                    <input
                      className="glass-input mono"
                      value={activeProfile.baseUrl}
                      onChange={(e) => onUpdateProfile(activeProvider, { ...activeProfile, baseUrl: e.target.value })}
                    />
                  </div>
                </div>
              </div>

              {/* Multiple Custom OpenAI Endpoints */}
              <div style={{ padding: 14, borderRadius: 14, border: '1px solid var(--border-subtle)', display: 'flex', flexDirection: 'column', gap: 10 }}>
                <div style={{ fontSize: 14, fontWeight: 800 }}>Danh sách Custom Endpoints chuẩn OpenAI ({customEndpoints.length})</div>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
                  <input className="glass-input" placeholder="Tên gợi nhớ (VD: Together Llama)" value={ceName} onChange={(e) => setCeName(e.target.value)} />
                  <input className="glass-input mono" placeholder="Model ID" value={ceModel} onChange={(e) => setCeModel(e.target.value)} />
                  <input className="glass-input mono" placeholder="Base URL (https://.../v1)" value={ceUrl} onChange={(e) => setCeUrl(e.target.value)} />
                  <input type="password" className="glass-input mono" placeholder="API Key (nếu có)" value={ceKey} onChange={(e) => setCeKey(e.target.value)} />
                </div>
                <button className="primary-btn glass-press" style={{ alignSelf: 'flex-start' }} onClick={handleAddCustomEndpoint}>
                  <Plus size={15} /> Thêm Custom Endpoint
                </button>

                {customEndpoints.map((ce) => (
                  <div
                    key={ce.id}
                    style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                      padding: '8px 10px',
                      borderRadius: 10,
                      background: 'rgba(148,163,184,0.08)',
                      gap: 8
                    }}
                  >
                    <div style={{ minWidth: 0 }}>
                      <div style={{ fontSize: 12.5, fontWeight: 700 }}>{ce.name} · <span className="mono">{ce.model}</span></div>
                      <div className="mono" style={{ fontSize: 11, color: 'var(--text-secondary)', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                        {ce.baseUrl}
                      </div>
                    </div>
                    <button
                      className="icon-btn"
                      onClick={() => onSaveCustomEndpoints(customEndpoints.filter((x) => x.id !== ce.id))}
                    >
                      <Trash2 size={15} />
                    </button>
                  </div>
                ))}
              </div>
            </>
          )}

          {/* SECTION 4: AUTO-FETCH /MODELS & PROBE KEY */}
          {section === 'models' && (
            <>
              <div>
                <div style={{ fontSize: 16, fontWeight: 800 }}>Đồng bộ Model (/models) & Kiểm tra Key</div>
                <div style={{ fontSize: 12, color: 'var(--text-secondary)' }}>
                  Đang kiểm tra nhà cung cấp: <b>{activeSpec.shortTitle}</b> · Model hiện tại: <span className="mono">{activeProfile.model}</span>
                </div>
              </div>

              <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                <button className="primary-btn glass-press" onClick={handleProbe} disabled={probing}>
                  <Activity size={15} /> {probing ? 'Đang kiểm tra...' : 'Kiểm tra API Key & Model (Test Key)'}
                </button>
                <button className="chip-btn active glass-press" onClick={handleFetchModels} disabled={fetchingModels}>
                  <RefreshCw size={14} /> {fetchingModels ? 'Đang tải...' : 'Tải danh sách Model (/models)'}
                </button>
              </div>

              {probeResult && (
                <div
                  style={{
                    padding: '12px 14px',
                    borderRadius: 14,
                    border: `1px solid ${probeResult.ok ? '#10b981' : '#f43f5e'}`,
                    background: probeResult.ok ? 'rgba(16,185,129,0.14)' : 'rgba(244,63,94,0.14)',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 10,
                    fontSize: 13
                  }}
                >
                  {probeResult.ok ? <CheckCircle2 size={18} color="#10b981" /> : <XCircle size={18} color="#f43f5e" />}
                  <span>{probeResult.message}</span>
                </div>
              )}

              {modelError && <div style={{ fontSize: 12.5, color: '#f43f5e' }}>{modelError}</div>}

              {fetchedModels.length > 0 && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                  <div style={{ fontSize: 12.5, fontWeight: 700 }}>
                    Chọn Model ({fetchedModels.length} mô hình khả dụng):
                  </div>
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, maxHeight: 300, overflowY: 'auto' }}>
                    {fetchedModels.map((m) => (
                      <button
                        key={m}
                        className={`chip-btn mono ${activeProfile.model === m ? 'active' : ''}`}
                        style={{ fontSize: 11.5 }}
                        onClick={() => onUpdateProfile(activeProvider, { ...activeProfile, model: m })}
                      >
                        {m}
                      </button>
                    ))}
                  </div>
                </div>
              )}
            </>
          )}

          {/* SECTION 5: TOKEN TRACKER & 3D/2D SENSITIVITY */}
          {section === 'tokens' && (
            <>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div>
                  <div style={{ fontSize: 16, fontWeight: 800 }}>Thống kê Token AI & Độ nhạy điều khiển</div>
                  <div style={{ fontSize: 12, color: 'var(--text-secondary)' }}>Theo dõi mức tiêu thụ Token và cân chỉnh tốc độ thao tác 3D / 2D</div>
                </div>
                <button className="chip-btn" onClick={onResetTokenUsage}>Đặt lại bộ đếm</button>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: 10 }}>
                {[
                  { label: 'Tổng Token', val: tokenUsage.totalTokens.toLocaleString(), color: lg.accentHex },
                  { label: 'Token Gửi (Prompt)', val: tokenUsage.promptTokens.toLocaleString(), color: '#10b981' },
                  { label: 'Token Nhận (Reply)', val: tokenUsage.completionTokens.toLocaleString(), color: '#a855f7' },
                  { label: 'Số lượt hỏi AI', val: String(tokenUsage.requestCount), color: '#f59e0b' }
                ].map((card) => (
                  <div key={card.label} style={{ padding: 12, borderRadius: 14, background: 'rgba(148,163,184,0.08)', border: '1px solid var(--border-subtle)' }}>
                    <div style={{ fontSize: 11.5, color: 'var(--text-secondary)' }}>{card.label}</div>
                    <div className="mono" style={{ fontSize: 20, fontWeight: 800, color: card.color, marginTop: 4 }}>{card.val}</div>
                  </div>
                ))}
              </div>

              {/* 3D & 2D Sensitivity */}
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: 12 }}>
                <div style={{ padding: 12, borderRadius: 14, background: 'rgba(148,163,184,0.08)', display: 'flex', flexDirection: 'column', gap: 10 }}>
                  <div style={{ fontSize: 13.5, fontWeight: 800 }}>Độ nhạy Không gian 3D</div>
                  {[
                    { key: 'rotation' as const, label: 'Độ nhạy xoay camera' },
                    { key: 'zoom' as const, label: 'Độ nhạy thu phóng' },
                    { key: 'drag' as const, label: 'Độ nhạy kéo đỉnh/điểm' },
                    { key: 'touchRadius' as const, label: 'Vùng bắt điểm chạm' }
                  ].map((s) => (
                    <div key={s.key}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12 }}>
                        <span>{s.label}</span>
                        <span className="mono">{settings.sensitivity3D[s.key].toFixed(1)}×</span>
                      </div>
                      <input
                        type="range"
                        className="glass-slider"
                        min={0.4}
                        max={2.0}
                        step={0.1}
                        value={settings.sensitivity3D[s.key]}
                        onChange={(e) =>
                          onUpdateSettings({
                            ...settings,
                            sensitivity3D: { ...settings.sensitivity3D, [s.key]: parseFloat(e.target.value) }
                          })
                        }
                      />
                    </div>
                  ))}
                </div>

                <div style={{ padding: 12, borderRadius: 14, background: 'rgba(148,163,184,0.08)', display: 'flex', flexDirection: 'column', gap: 10 }}>
                  <div style={{ fontSize: 13.5, fontWeight: 800 }}>Độ nhạy Mặt phẳng 2D (Oxy)</div>
                  {[
                    { key: 'pan' as const, label: 'Độ nhạy kéo hệ trục Oxy' },
                    { key: 'zoom' as const, label: 'Độ nhạy thu phóng đồ thị' },
                    { key: 'touchRadius' as const, label: 'Vùng chạm điểm 2D' }
                  ].map((s) => (
                    <div key={s.key}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12 }}>
                        <span>{s.label}</span>
                        <span className="mono">{settings.sensitivity2D[s.key].toFixed(1)}×</span>
                      </div>
                      <input
                        type="range"
                        className="glass-slider"
                        min={0.4}
                        max={2.0}
                        step={0.1}
                        value={settings.sensitivity2D[s.key]}
                        onChange={(e) =>
                          onUpdateSettings({
                            ...settings,
                            sensitivity2D: { ...settings.sensitivity2D, [s.key]: parseFloat(e.target.value) }
                          })
                        }
                      />
                    </div>
                  ))}
                </div>
              </div>
            </>
          )}

          {/* SECTION 6: KEYBOARD SHORTCUTS & OFFLINE PWA */}
          {section === 'shortcuts' && (
            <>
              <div>
                <div style={{ fontSize: 16, fontWeight: 800 }}>Phím tắt máy tính & Cài đặt PWA Offline</div>
                <div style={{ fontSize: 12, color: 'var(--text-secondary)' }}>Cài Tool Toán ra màn hình chính để chạy độc lập kể cả khi không có mạng</div>
              </div>

              <div style={{ padding: 14, borderRadius: 14, background: `${lg.accentHex}16`, border: `1px solid ${lg.accentHex}44`, display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 10 }}>
                <div>
                  <div style={{ fontSize: 13.5, fontWeight: 800 }}>Ứng dụng Web Offline (PWA)</div>
                  <div style={{ fontSize: 12, color: 'var(--text-secondary)' }}>
                    {canInstallPwa
                      ? 'Trình duyệt hỗ trợ cài đặt trực tiếp Tool Toán lên màn hình chính.'
                      : 'Service Worker tự động lưu bộ nhớ đệm offline. Trên Chrome/Edge/Safari, chọn "Thêm vào màn hình chính" hoặc biểu tượng Cài đặt trên thanh địa chỉ.'}
                  </div>
                </div>
                {canInstallPwa && (
                  <button className="primary-btn glass-press" onClick={onInstallPwa}>
                    <Download size={15} /> Cài đặt Ứng dụng
                  </button>
                )}
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 8 }}>
                {[
                  { key: 'Ctrl + Z', desc: 'Hoàn tác bước vẽ (Undo) trên 3D & 2D' },
                  { key: 'Ctrl + Y', desc: 'Làm lại bước vẽ (Redo) trên 3D & 2D' },
                  { key: 'Space', desc: 'Phát / Dừng hoạt ảnh tham số m (2D) hoặc Tự xoay (3D)' },
                  { key: 'F', desc: 'Bật / Tắt chế độ Toàn màn hình (Immersive)' },
                  { key: 'Esc', desc: 'Thoát toàn màn hình / Hủy chọn điểm đang nối' },
                  { key: '1 – 5', desc: 'Chuyển nhanh giữa 5 không gian (3D, 2D, Sổ tay, AI, Cài đặt)' }
                ].map((sc) => (
                  <div key={sc.key} style={{ padding: '10px 12px', borderRadius: 12, background: 'rgba(148,163,184,0.08)', display: 'flex', alignItems: 'center', gap: 10 }}>
                    <kbd className="mono" style={{ padding: '3px 8px', borderRadius: 8, background: 'rgba(2,6,23,0.45)', border: '1px solid var(--border-subtle)', fontSize: 11.5, fontWeight: 700, whiteSpace: 'nowrap' }}>
                      {sc.key}
                    </kbd>
                    <span style={{ fontSize: 12, color: 'var(--text-secondary)' }}>{sc.desc}</span>
                  </div>
                ))}
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
};
