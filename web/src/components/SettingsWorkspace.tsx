import React, { useState } from 'react';
import {
  Check,
  ExternalLink,
  KeyRound,
  Moon,
  Palette,
  RotateCcw,
  Sparkles,
  Sun
} from 'lucide-react';
import {
  AI_PROVIDERS,
  AiProviderId,
  AiSavedProfile,
  AppSettingsState,
  DEFAULT_APP_SETTINGS
} from '../types/app';

interface SettingsWorkspaceProps {
  settings: AppSettingsState;
  onUpdateSettings: (next: AppSettingsState) => void;
  activeProvider: AiProviderId;
  onChangeProvider: (id: AiProviderId) => void;
  profiles: Record<AiProviderId, AiSavedProfile>;
  onUpdateProfile: (id: AiProviderId, next: AiSavedProfile) => void;
}

const ACCENT_PRESETS = ['#38BDF8', '#6366F1', '#10B981', '#F59E0B', '#F43F5E', '#A855F7', '#06B6D4'];

export const SettingsWorkspace: React.FC<SettingsWorkspaceProps> = ({
  settings,
  onUpdateSettings,
  activeProvider,
  onChangeProvider,
  profiles,
  onUpdateProfile
}) => {
  const [savedNotice, setSavedNotice] = useState<string | null>(null);
  const lg = settings.liquidGlass;
  const providerSpec = AI_PROVIDERS.find((p) => p.id === activeProvider) || AI_PROVIDERS[0];
  const currentProfile = profiles[activeProvider];

  const updateGlass = (patch: Partial<AppSettingsState['liquidGlass']>) => {
    onUpdateSettings({
      ...settings,
      liquidGlass: { ...settings.liquidGlass, ...patch }
    });
  };

  const notify = (msg: string) => {
    setSavedNotice(msg);
    window.setTimeout(() => setSavedNotice(null), 2400);
  };

  return (
    <div
      className="scroll-area"
      style={{
        width: '100%',
        height: '100%',
        padding: '14px 14px 96px 14px'
      }}
    >
      <div
        style={{
          maxWidth: 780,
          margin: '0 auto',
          display: 'flex',
          flexDirection: 'column',
          gap: 14
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div>
            <h1 style={{ margin: 0, fontSize: 20, fontWeight: 800 }}>Cài đặt Tool Toán</h1>
            <div style={{ fontSize: 12, color: 'var(--text-secondary)', marginTop: 2 }}>
              Tùy biến giao diện kính lỏng quang học & kết nối API AI cá nhân
            </div>
          </div>
          {savedNotice && (
            <span
              className="chip-btn active"
              style={{ pointerEvents: 'none' }}
            >
              <Check size={14} />
              <span>{savedNotice}</span>
            </span>
          )}
        </div>

        {/* 1. Theme & Accent */}
        <section className="glass-surface" style={{ borderRadius: 22, padding: 16, display: 'flex', flexDirection: 'column', gap: 12 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 14, fontWeight: 800 }}>
            <Palette size={17} color={lg.accentHex} />
            <span>Giao diện & Màu chủ đạo</span>
          </div>

          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            <button
              type="button"
              className={`chip-btn glass-press${settings.themeMode === 'dark' ? ' active' : ''}`}
              onClick={() => onUpdateSettings({ ...settings, themeMode: 'dark' })}
            >
              <Moon size={14} />
              <span>Chế độ Tối</span>
            </button>
            <button
              type="button"
              className={`chip-btn glass-press${settings.themeMode === 'light' ? ' active' : ''}`}
              onClick={() => onUpdateSettings({ ...settings, themeMode: 'light' })}
            >
              <Sun size={14} />
              <span>Chế độ Sáng</span>
            </button>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
            {ACCENT_PRESETS.map((hex) => {
              const selected = lg.accentHex.toUpperCase() === hex.toUpperCase();
              return (
                <button
                  key={hex}
                  type="button"
                  onClick={() => updateGlass({ accentHex: hex })}
                  aria-label={`Chọn màu ${hex}`}
                  style={{
                    width: 32,
                    height: 32,
                    borderRadius: '50%',
                    background: hex,
                    border: selected ? '3px solid #ffffff' : '1px solid rgba(255,255,255,0.3)',
                    boxShadow: selected ? `0 0 14px ${hex}` : 'none',
                    cursor: 'pointer'
                  }}
                />
              );
            })}
            <input
              type="color"
              value={lg.accentHex}
              onChange={(e) => updateGlass({ accentHex: e.target.value })}
              style={{
                width: 34,
                height: 32,
                padding: 0,
                border: 'none',
                background: 'transparent',
                cursor: 'pointer'
              }}
              aria-label="Chọn màu tùy chỉnh"
            />
          </div>
        </section>

        {/* 2. Liquid Glass Studio */}
        <section className="glass-surface" style={{ borderRadius: 22, padding: 16, display: 'flex', flexDirection: 'column', gap: 12 }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 14, fontWeight: 800 }}>
              <Sparkles size={17} color={lg.accentHex} />
              <span>Liquid Glass Studio</span>
            </div>

            <div style={{ display: 'flex', gap: 6 }}>
              <button
                type="button"
                className={`chip-btn glass-press${lg.enabled ? ' active' : ''}`}
                onClick={() => updateGlass({ enabled: !lg.enabled })}
              >
                {lg.enabled ? 'Đang bật' : 'Đã tắt'}
              </button>
              <button
                type="button"
                className="icon-btn glass-press"
                title="Khôi phục mặc định"
                onClick={() => {
                  onUpdateSettings({ ...settings, liquidGlass: DEFAULT_APP_SETTINGS.liquidGlass });
                  notify('Đã khôi phục thông số kính lỏng mặc định');
                }}
              >
                <RotateCcw size={15} />
              </button>
            </div>
          </div>

          {/* Interactive Live Preview Card */}
          <div
            style={{
              position: 'relative',
              borderRadius: 18,
              padding: 18,
              overflow: 'hidden',
              background: `linear-gradient(125deg, ${lg.accentHex}66, #6366f180 52%, #ec489966)`
            }}
          >
            <div
              className="glass-surface"
              style={{
                borderRadius: 16,
                padding: '12px 16px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between'
              }}
            >
              <div>
                <div style={{ fontSize: 13, fontWeight: 800 }}>Bề mặt kính lỏng trực tiếp</div>
                <div className="mono" style={{ fontSize: 11, color: 'var(--text-secondary)' }}>
                  Blur {lg.blurRadius}px · Sat {Math.round(100 + lg.vibrancy * 65)}% · Rim {Math.round(lg.specularRim * 100)}%
                </div>
              </div>
              <span className="chip-btn active" style={{ pointerEvents: 'none', minHeight: 28 }}>
                Live
              </span>
            </div>
          </div>

          {/* Sliders */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 12 }}>
            <label style={{ fontSize: 12, display: 'flex', flexDirection: 'column', gap: 5 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span>Độ mờ kính (Blur)</span>
                <b className="mono">{lg.blurRadius}px</b>
              </div>
              <input
                type="range"
                className="glass-slider"
                min={0}
                max={32}
                step={1}
                value={lg.blurRadius}
                onChange={(e) => updateGlass({ blurRadius: parseInt(e.target.value, 10) })}
              />
            </label>

            <label style={{ fontSize: 12, display: 'flex', flexDirection: 'column', gap: 5 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span>Độ rực nền (Vibrancy)</span>
                <b className="mono">{Math.round(lg.vibrancy * 100)}%</b>
              </div>
              <input
                type="range"
                className="glass-slider"
                min={0}
                max={1}
                step={0.05}
                value={lg.vibrancy}
                onChange={(e) => updateGlass({ vibrancy: parseFloat(e.target.value) })}
              />
            </label>

            <label style={{ fontSize: 12, display: 'flex', flexDirection: 'column', gap: 5 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span>Hạt sương mịn (Frost Grain)</span>
                <b className="mono">{Math.round(lg.frostGrain * 100)}%</b>
              </div>
              <input
                type="range"
                className="glass-slider"
                min={0}
                max={1}
                step={0.05}
                value={lg.frostGrain}
                onChange={(e) => updateGlass({ frostGrain: parseFloat(e.target.value) })}
              />
            </label>

            <label style={{ fontSize: 12, display: 'flex', flexDirection: 'column', gap: 5 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span>Độ trong suốt (Translucency)</span>
                <b className="mono">{Math.round(lg.translucency * 100)}%</b>
              </div>
              <input
                type="range"
                className="glass-slider"
                min={0.25}
                max={0.95}
                step={0.05}
                value={lg.translucency}
                onChange={(e) => updateGlass({ translucency: parseFloat(e.target.value) })}
              />
            </label>

            <label style={{ fontSize: 12, display: 'flex', flexDirection: 'column', gap: 5 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span>Viền sáng đỉnh (Specular Rim)</span>
                <b className="mono">{Math.round(lg.specularRim * 100)}%</b>
              </div>
              <input
                type="range"
                className="glass-slider"
                min={0}
                max={1}
                step={0.05}
                value={lg.specularRim}
                onChange={(e) => updateGlass({ specularRim: parseFloat(e.target.value) })}
              />
            </label>
          </div>
        </section>

        {/* 3. AI Provider API Keys */}
        <section className="glass-surface" style={{ borderRadius: 22, padding: 16, display: 'flex', flexDirection: 'column', gap: 12 }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 8 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 14, fontWeight: 800 }}>
              <KeyRound size={17} color={lg.accentHex} />
              <span>Quản lý API Key Trợ lý AI (7 nguồn)</span>
            </div>
            {providerSpec.portalUrl && (
              <a
                href={providerSpec.portalUrl}
                target="_blank"
                rel="noreferrer"
                className="chip-btn"
                style={{ textDecoration: 'none' }}
              >
                <span>Lấy khóa {providerSpec.shortTitle}</span>
                <ExternalLink size={13} />
              </a>
            )}
          </div>

          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
            {AI_PROVIDERS.map((p) => (
              <button
                key={p.id}
                type="button"
                className={`chip-btn glass-press${activeProvider === p.id ? ' active' : ''}`}
                onClick={() => onChangeProvider(p.id)}
              >
                {p.shortTitle}
              </button>
            ))}
          </div>

          <div style={{ fontSize: 12, color: 'var(--text-secondary)' }}>
            {providerSpec.fullTitle} — Khóa được lưu cục bộ trong <span className="mono">localStorage</span> trình duyệt và chuyển tiếp an toàn qua <span className="mono">/api/ai-proxy</span>.
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            <label style={{ fontSize: 12, display: 'flex', flexDirection: 'column', gap: 4 }}>
              <span>API Key</span>
              <input
                type="password"
                className="glass-input mono"
                placeholder={providerSpec.keyPlaceholder}
                value={currentProfile.apiKey}
                onChange={(e) =>
                  onUpdateProfile(activeProvider, { ...currentProfile, apiKey: e.target.value })
                }
              />
            </label>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 10 }}>
              <label style={{ fontSize: 12, display: 'flex', flexDirection: 'column', gap: 4 }}>
                <span>Model ID</span>
                <input
                  type="text"
                  className="glass-input mono"
                  placeholder={providerSpec.defaultModel}
                  value={currentProfile.model}
                  onChange={(e) =>
                    onUpdateProfile(activeProvider, { ...currentProfile, model: e.target.value })
                  }
                />
              </label>

              <label style={{ fontSize: 12, display: 'flex', flexDirection: 'column', gap: 4 }}>
                <span>Base URL</span>
                <input
                  type="url"
                  className="glass-input mono"
                  placeholder={providerSpec.defaultBaseUrl}
                  value={currentProfile.baseUrl}
                  onChange={(e) =>
                    onUpdateProfile(activeProvider, { ...currentProfile, baseUrl: e.target.value })
                  }
                />
              </label>
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
              <button
                type="button"
                className="primary-btn glass-press"
                onClick={() => notify(`Đã lưu cấu hình ${providerSpec.shortTitle}`)}
              >
                <Check size={15} />
                <span>Xác nhận cấu hình AI</span>
              </button>
            </div>
          </div>
        </section>
      </div>
    </div>
  );
};
