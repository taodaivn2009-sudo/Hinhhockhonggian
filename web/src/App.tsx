import React, { useCallback, useEffect, useState } from 'react';
import { FloatingNavBar, ImmersiveRestorePill } from './components/FloatingNavBar';
import { Space3DWorkspace } from './components/Space3DWorkspace';
import { Plane2DWorkspace } from './components/Plane2DWorkspace';
import { DocsWorkspace } from './components/DocsWorkspace';
import { AiWorkspace } from './components/AiWorkspace';
import { SettingsWorkspace } from './components/SettingsWorkspace';
import {
  AI_PROVIDERS,
  AiDrawingPlan,
  AiProviderId,
  AiSavedProfile,
  AppSettingsState,
  DEFAULT_APP_SETTINGS,
  MathWorkspace
} from './types/app';

const SETTINGS_KEY = 'tooltoan_settings_v1';
const PROFILES_KEY = 'tooltoan_ai_profiles_v1';
const PROVIDER_KEY = 'tooltoan_ai_provider_v1';
const WORKSPACE_KEY = 'tooltoan_last_workspace_v1';

function buildDefaultProfiles(): Record<AiProviderId, AiSavedProfile> {
  const out = {} as Record<AiProviderId, AiSavedProfile>;
  for (const p of AI_PROVIDERS) {
    out[p.id] = { apiKey: '', model: p.defaultModel, baseUrl: p.defaultBaseUrl };
  }
  return out;
}

export const App: React.FC = () => {
  const [settings, setSettings] = useState<AppSettingsState>(() => {
    try {
      const raw = localStorage.getItem(SETTINGS_KEY);
      if (!raw) return DEFAULT_APP_SETTINGS;
      const parsed = JSON.parse(raw);
      return {
        ...DEFAULT_APP_SETTINGS,
        ...parsed,
        liquidGlass: { ...DEFAULT_APP_SETTINGS.liquidGlass, ...(parsed.liquidGlass || {}) }
      };
    } catch {
      return DEFAULT_APP_SETTINGS;
    }
  });

  const [activeWorkspace, setActiveWorkspace] = useState<MathWorkspace>(() => {
    const saved = localStorage.getItem(WORKSPACE_KEY) as MathWorkspace | null;
    if (saved && ['space3d', 'plane2d', 'docs', 'ai', 'settings'].includes(saved)) {
      return saved;
    }
    return 'space3d';
  });

  const [isImmersive, setIsImmersive] = useState<boolean>(false);

  const [activeProvider, setActiveProvider] = useState<AiProviderId>(() => {
    const saved = localStorage.getItem(PROVIDER_KEY) as AiProviderId | null;
    return saved && AI_PROVIDERS.some((p) => p.id === saved) ? saved : 'google_api_key';
  });

  const [profiles, setProfiles] = useState<Record<AiProviderId, AiSavedProfile>>(() => {
    const defaults = buildDefaultProfiles();
    try {
      const raw = localStorage.getItem(PROFILES_KEY);
      if (!raw) return defaults;
      const parsed = JSON.parse(raw);
      for (const p of AI_PROVIDERS) {
        if (parsed[p.id]) {
          defaults[p.id] = {
            apiKey: typeof parsed[p.id].apiKey === 'string' ? parsed[p.id].apiKey : '',
            model: typeof parsed[p.id].model === 'string' ? parsed[p.id].model : p.defaultModel,
            baseUrl: typeof parsed[p.id].baseUrl === 'string' ? parsed[p.id].baseUrl : p.defaultBaseUrl
          };
        }
      }
      return defaults;
    } catch {
      return defaults;
    }
  });

  const [pendingPlan, setPendingPlan] = useState<AiDrawingPlan | null>(null);
  const [prefillAiPrompt, setPrefillAiPrompt] = useState<string | null>(null);

  // Sync Liquid Glass CSS variables & theme attributes live to <html>
  useEffect(() => {
    localStorage.setItem(SETTINGS_KEY, JSON.stringify(settings));
    const root = document.documentElement;
    const lg = settings.liquidGlass;
    root.setAttribute('data-theme', settings.themeMode);
    root.setAttribute('data-glass', lg.enabled ? 'on' : 'off');
    root.style.setProperty('--accent', lg.accentHex);
    root.style.setProperty('--lg-blur', `${lg.blurRadius}px`);
    root.style.setProperty('--lg-sat', `${Math.round(100 + lg.vibrancy * 65)}%`);
    const bgAlpha = Math.max(0.18, Math.min(0.86, 1 - lg.translucency * 0.72));
    root.style.setProperty('--lg-bg-alpha', bgAlpha.toFixed(2));
    root.style.setProperty('--lg-grain-opacity', (lg.frostGrain * 0.14).toFixed(3));
    const isLight = settings.themeMode === 'light';
    root.style.setProperty(
      '--lg-rim-top',
      isLight
        ? `rgba(255, 255, 255, ${(0.45 + lg.specularRim * 0.5).toFixed(2)})`
        : `rgba(224, 247, 255, ${(0.20 + lg.specularRim * 0.55).toFixed(2)})`
    );
    root.style.setProperty(
      '--lg-gloss-alpha',
      (isLight ? 0.16 + lg.specularRim * 0.24 : 0.08 + lg.specularRim * 0.16).toFixed(2)
    );
  }, [settings]);

  useEffect(() => {
    localStorage.setItem(PROFILES_KEY, JSON.stringify(profiles));
  }, [profiles]);

  useEffect(() => {
    localStorage.setItem(PROVIDER_KEY, activeProvider);
  }, [activeProvider]);

  useEffect(() => {
    if (settings.rememberWorkspace) {
      localStorage.setItem(WORKSPACE_KEY, activeWorkspace);
    }
    if (activeWorkspace !== 'space3d' && activeWorkspace !== 'plane2d' && isImmersive) {
      setIsImmersive(false);
    }
  }, [activeWorkspace, settings.rememberWorkspace, isImmersive]);

  const handleUpdateProfile = useCallback((id: AiProviderId, next: AiSavedProfile) => {
    setProfiles((prev) => ({ ...prev, [id]: next }));
  }, []);

  const handleAskAiAboutPage = useCallback((prompt: string) => {
    setPrefillAiPrompt(prompt);
    setActiveWorkspace('ai');
  }, []);

  const handleApplyDrawingPlan = useCallback((plan: AiDrawingPlan) => {
    setPendingPlan(plan);
    setActiveWorkspace(plan.target);
  }, []);

  const handleClearPlan = useCallback(() => setPendingPlan(null), []);
  const handleClearPrefill = useCallback(() => setPrefillAiPrompt(null), []);

  return (
    <div style={{ position: 'relative', width: '100%', height: '100%', overflow: 'hidden' }}>
      <div className="aurora-bg" aria-hidden="true" />

      <main style={{ position: 'relative', zIndex: 1, width: '100%', height: '100%' }}>
        {activeWorkspace === 'space3d' && (
          <Space3DWorkspace
            accentHex={settings.liquidGlass.accentHex}
            themeMode={settings.themeMode}
            isImmersive={isImmersive}
            onToggleImmersive={() => setIsImmersive((v) => !v)}
            externalPlan={pendingPlan}
            onPlanConsumed={handleClearPlan}
          />
        )}

        {activeWorkspace === 'plane2d' && (
          <Plane2DWorkspace
            accentHex={settings.liquidGlass.accentHex}
            themeMode={settings.themeMode}
            isImmersive={isImmersive}
            onToggleImmersive={() => setIsImmersive((v) => !v)}
            externalPlan={pendingPlan}
            onPlanConsumed={handleClearPlan}
          />
        )}

        {activeWorkspace === 'docs' && (
          <DocsWorkspace
            accentHex={settings.liquidGlass.accentHex}
            onAskAiAboutPage={handleAskAiAboutPage}
          />
        )}

        {activeWorkspace === 'ai' && (
          <AiWorkspace
            accentHex={settings.liquidGlass.accentHex}
            activeProvider={activeProvider}
            onChangeProvider={setActiveProvider}
            profiles={profiles}
            prefillPrompt={prefillAiPrompt}
            onPrefillConsumed={handleClearPrefill}
            onOpenSettings={() => setActiveWorkspace('settings')}
            onApplyDrawingPlan={handleApplyDrawingPlan}
          />
        )}

        {activeWorkspace === 'settings' && (
          <SettingsWorkspace
            settings={settings}
            onUpdateSettings={setSettings}
            activeProvider={activeProvider}
            onChangeProvider={setActiveProvider}
            profiles={profiles}
            onUpdateProfile={handleUpdateProfile}
          />
        )}
      </main>

      {isImmersive ? (
        <ImmersiveRestorePill onRestore={() => setIsImmersive(false)} />
      ) : (
        <FloatingNavBar active={activeWorkspace} onSelect={setActiveWorkspace} />
      )}
    </div>
  );
};

export default App;
