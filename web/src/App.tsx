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
  AiTokenUsage,
  AppSettingsState,
  CustomAiEndpoint,
  DEFAULT_APP_SETTINGS,
  MathWorkspace,
  Sensitivity2D,
  Sensitivity3D
} from './types/app';
import { Scene3DSnapshot } from './engine/geo3d';
import { Geo2DSnapshot } from './engine/geo2d';
import { parseShareHashFromUrl } from './services/shareUrl';

const SETTINGS_KEY = 'tooltoan_settings_v2';
const PERF_MIGRATION_KEY = 'tooltoan_lg_perf_off_v4';
const PROFILES_KEY = 'tooltoan_ai_profiles_v1';
const PROVIDER_KEY = 'tooltoan_ai_provider_v1';
const WORKSPACE_KEY = 'tooltoan_last_workspace_v1';
const CUSTOM_ENDPOINTS_KEY = 'tooltoan_custom_endpoints_v1';
const TOKEN_USAGE_KEY = 'tooltoan_token_usage_v1';

interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

const DEFAULT_TOKEN_USAGE: AiTokenUsage = {
  promptTokens: 0,
  completionTokens: 0,
  totalTokens: 0,
  requestCount: 0
};

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
      const raw = localStorage.getItem(SETTINGS_KEY) || localStorage.getItem('tooltoan_settings_v1');
      if (!raw) {
        localStorage.setItem(PERF_MIGRATION_KEY, '1');
        return DEFAULT_APP_SETTINGS;
      }
      const parsed = JSON.parse(raw);
      const merged: AppSettingsState = {
        ...DEFAULT_APP_SETTINGS,
        ...parsed,
        liquidGlass: { ...DEFAULT_APP_SETTINGS.liquidGlass, ...(parsed.liquidGlass || {}) },
        sensitivity3D: { ...DEFAULT_APP_SETTINGS.sensitivity3D, ...(parsed.sensitivity3D || {}) },
        sensitivity2D: { ...DEFAULT_APP_SETTINGS.sensitivity2D, ...(parsed.sensitivity2D || {}) }
      };
      if (!localStorage.getItem(PERF_MIGRATION_KEY)) {
        localStorage.setItem(PERF_MIGRATION_KEY, '1');
        merged.liquidGlass = {
          ...merged.liquidGlass,
          enabled: false,
          dynamicShimmer: false
        };
      }
      return merged;
    } catch {
      return DEFAULT_APP_SETTINGS;
    }
  });

  const [activeWorkspace, setActiveWorkspace] = useState<MathWorkspace>(() => {
    const shared = parseShareHashFromUrl(window.location.hash);
    if (shared) return shared.kind;
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

  const [customEndpoints, setCustomEndpoints] = useState<CustomAiEndpoint[]>(() => {
    try {
      const raw = localStorage.getItem(CUSTOM_ENDPOINTS_KEY);
      return raw ? (JSON.parse(raw) as CustomAiEndpoint[]) : [];
    } catch {
      return [];
    }
  });
  const [activeCustomEndpointId, setActiveCustomEndpointId] = useState<string | null>(null);

  const [tokenUsage, setTokenUsage] = useState<AiTokenUsage>(() => {
    try {
      const raw = localStorage.getItem(TOKEN_USAGE_KEY);
      return raw ? { ...DEFAULT_TOKEN_USAGE, ...JSON.parse(raw) } : DEFAULT_TOKEN_USAGE;
    } catch {
      return DEFAULT_TOKEN_USAGE;
    }
  });

  const [pendingPlan, setPendingPlan] = useState<AiDrawingPlan | null>(null);
  const [prefillAiPrompt, setPrefillAiPrompt] = useState<string | null>(null);
  const [externalPdfPage, setExternalPdfPage] = useState<number | null>(null);
  const [shared3DSnapshot, setShared3DSnapshot] = useState<Scene3DSnapshot | null>(() => {
    const parsed = parseShareHashFromUrl(window.location.hash);
    return parsed?.kind === 'space3d' && parsed.snapshot3d ? parsed.snapshot3d : null;
  });
  const [shared2DSnapshot, setShared2DSnapshot] = useState<Geo2DSnapshot | null>(() => {
    const parsed = parseShareHashFromUrl(window.location.hash);
    return parsed?.kind === 'plane2d' && parsed.snapshot2d ? parsed.snapshot2d : null;
  });

  const [deferredInstallPrompt, setDeferredInstallPrompt] = useState<BeforeInstallPromptEvent | null>(null);

  // Sync Liquid Glass optical CSS variables & theme attributes live to <html>
  useEffect(() => {
    localStorage.setItem(SETTINGS_KEY, JSON.stringify(settings));
    const root = document.documentElement;
    const lg = settings.liquidGlass;
    root.setAttribute('data-theme', settings.themeMode);
    root.setAttribute('data-glass', lg.enabled ? 'on' : 'off');
    root.setAttribute('data-shimmer', lg.dynamicShimmer ? 'on' : 'off');
    root.setAttribute('data-contrast', lg.highContrastText ? 'high' : 'normal');
    root.style.setProperty('--accent', lg.accentHex);
    root.style.setProperty('--lg-blur', `${lg.blurRadius}px`);
    root.style.setProperty('--lg-sat', `${Math.round(100 + lg.vibrancy * 65)}%`);
    const bgAlpha = Math.max(0.16, Math.min(0.88, 1 - lg.translucency * 0.72));
    root.style.setProperty('--lg-bg-alpha', bgAlpha.toFixed(2));
    root.style.setProperty('--lg-grain-opacity', (lg.frostGrain * 0.14).toFixed(3));
    root.style.setProperty('--lg-bevel', `${lg.bevelThickness.toFixed(1)}px`);
    root.style.setProperty('--lg-refraction', lg.refraction.toFixed(2));
    root.style.setProperty('--lg-dispersion', lg.dispersion.toFixed(2));
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
    localStorage.setItem(CUSTOM_ENDPOINTS_KEY, JSON.stringify(customEndpoints));
  }, [customEndpoints]);

  useEffect(() => {
    localStorage.setItem(TOKEN_USAGE_KEY, JSON.stringify(tokenUsage));
  }, [tokenUsage]);

  useEffect(() => {
    if (settings.rememberWorkspace) {
      localStorage.setItem(WORKSPACE_KEY, activeWorkspace);
    }
    if (activeWorkspace !== 'space3d' && activeWorkspace !== 'plane2d' && isImmersive) {
      setIsImmersive(false);
    }
  }, [activeWorkspace, settings.rememberWorkspace, isImmersive]);

  // Listen for Share URL hash changes
  useEffect(() => {
    const onHashChange = () => {
      const parsed = parseShareHashFromUrl(window.location.hash);
      if (!parsed) return;
      if (parsed.kind === 'space3d' && parsed.snapshot3d) {
        setShared3DSnapshot(parsed.snapshot3d);
        setActiveWorkspace('space3d');
      } else if (parsed.kind === 'plane2d' && parsed.snapshot2d) {
        setShared2DSnapshot(parsed.snapshot2d);
        setActiveWorkspace('plane2d');
      }
    };
    window.addEventListener('hashchange', onHashChange);
    return () => window.removeEventListener('hashchange', onHashChange);
  }, []);

  // Capture PWA beforeinstallprompt
  useEffect(() => {
    const onBeforeInstall = (e: Event) => {
      e.preventDefault();
      setDeferredInstallPrompt(e as BeforeInstallPromptEvent);
    };
    window.addEventListener('beforeinstallprompt', onBeforeInstall);
    return () => window.removeEventListener('beforeinstallprompt', onBeforeInstall);
  }, []);

  // Global keyboard shortcuts (1-5 workspaces, F fullscreen, Esc exit immersive)
  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      const target = e.target as HTMLElement | null;
      const isTyping =
        target &&
        (target.tagName === 'INPUT' ||
          target.tagName === 'TEXTAREA' ||
          target.tagName === 'SELECT' ||
          target.isContentEditable);
      if (e.key === 'Escape' && isImmersive) {
        setIsImmersive(false);
        return;
      }
      if (isTyping || e.ctrlKey || e.metaKey || e.altKey) return;
      if (e.key === '1') setActiveWorkspace('space3d');
      else if (e.key === '2') setActiveWorkspace('plane2d');
      else if (e.key === '3') setActiveWorkspace('docs');
      else if (e.key === '4') setActiveWorkspace('ai');
      else if (e.key === '5') setActiveWorkspace('settings');
      else if (e.key.toLowerCase() === 'f' && (activeWorkspace === 'space3d' || activeWorkspace === 'plane2d')) {
        setIsImmersive((v) => !v);
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [activeWorkspace, isImmersive]);

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

  const handleOpenPdfPage = useCallback((page: number) => {
    setExternalPdfPage(page);
    setActiveWorkspace('docs');
  }, []);

  const handleRecordTokens = useCallback((promptTokens: number, completionTokens: number) => {
    setTokenUsage((prev) => ({
      promptTokens: prev.promptTokens + promptTokens,
      completionTokens: prev.completionTokens + completionTokens,
      totalTokens: prev.totalTokens + promptTokens + completionTokens,
      requestCount: prev.requestCount + 1
    }));
  }, []);

  const handleResetTokenUsage = useCallback(() => setTokenUsage(DEFAULT_TOKEN_USAGE), []);

  const handleUpdateSensitivity3D = useCallback((next: Sensitivity3D) => {
    setSettings((prev) => ({ ...prev, sensitivity3D: next }));
  }, []);

  const handleUpdateSensitivity2D = useCallback((next: Sensitivity2D) => {
    setSettings((prev) => ({ ...prev, sensitivity2D: next }));
  }, []);

  const handleInstallPwa = useCallback(async () => {
    if (!deferredInstallPrompt) return;
    await deferredInstallPrompt.prompt();
    await deferredInstallPrompt.userChoice;
    setDeferredInstallPrompt(null);
  }, [deferredInstallPrompt]);

  const handleClearPlan = useCallback(() => setPendingPlan(null), []);
  const handleClearPrefill = useCallback(() => setPrefillAiPrompt(null), []);
  const handleClearPdfPage = useCallback(() => setExternalPdfPage(null), []);
  const handleClearShared3D = useCallback(() => {
    setShared3DSnapshot(null);
    if (window.location.hash.startsWith('#share3d=')) {
      window.history.replaceState(null, '', window.location.pathname);
    }
  }, []);
  const handleClearShared2D = useCallback(() => {
    setShared2DSnapshot(null);
    if (window.location.hash.startsWith('#share2d=')) {
      window.history.replaceState(null, '', window.location.pathname);
    }
  }, []);

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
            sensitivity={settings.sensitivity3D}
            onUpdateSensitivity={handleUpdateSensitivity3D}
            externalPlan={pendingPlan}
            onPlanConsumed={handleClearPlan}
            externalSnapshot={shared3DSnapshot}
            onExternalSnapshotConsumed={handleClearShared3D}
          />
        )}

        {activeWorkspace === 'plane2d' && (
          <Plane2DWorkspace
            accentHex={settings.liquidGlass.accentHex}
            themeMode={settings.themeMode}
            isImmersive={isImmersive}
            onToggleImmersive={() => setIsImmersive((v) => !v)}
            sensitivity={settings.sensitivity2D}
            onUpdateSensitivity={handleUpdateSensitivity2D}
            externalPlan={pendingPlan}
            onPlanConsumed={handleClearPlan}
            externalSnapshot={shared2DSnapshot}
            onExternalSnapshotConsumed={handleClearShared2D}
          />
        )}

        {activeWorkspace === 'docs' && (
          <DocsWorkspace
            accentHex={settings.liquidGlass.accentHex}
            externalPage={externalPdfPage}
            onExternalPageConsumed={handleClearPdfPage}
            onAskAiAboutPage={handleAskAiAboutPage}
          />
        )}

        {activeWorkspace === 'ai' && (
          <AiWorkspace
            accentHex={settings.liquidGlass.accentHex}
            activeProvider={activeProvider}
            onChangeProvider={setActiveProvider}
            profiles={profiles}
            customEndpoints={customEndpoints}
            activeCustomEndpointId={activeCustomEndpointId}
            onSelectCustomEndpoint={setActiveCustomEndpointId}
            tokenUsage={tokenUsage}
            onRecordTokens={handleRecordTokens}
            prefillPrompt={prefillAiPrompt}
            onPrefillConsumed={handleClearPrefill}
            onOpenSettings={() => setActiveWorkspace('settings')}
            onApplyDrawingPlan={handleApplyDrawingPlan}
            onOpenPdfPage={handleOpenPdfPage}
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
            customEndpoints={customEndpoints}
            onSaveCustomEndpoints={setCustomEndpoints}
            tokenUsage={tokenUsage}
            onResetTokenUsage={handleResetTokenUsage}
            canInstallPwa={Boolean(deferredInstallPrompt)}
            onInstallPwa={handleInstallPwa}
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
