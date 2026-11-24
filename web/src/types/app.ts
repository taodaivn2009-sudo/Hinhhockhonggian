export type MathWorkspace = 'space3d' | 'plane2d' | 'docs' | 'ai' | 'settings';

export type ThemeMode = 'dark' | 'light';

export type ThemePresetId =
  | 'midnight_cyan'
  | 'emerald_lab'
  | 'violet_nebula'
  | 'solar_amber'
  | 'rose_quartz'
  | 'paper_light';

export type GlassPresetId = 'clear' | 'frosted' | 'neon' | 'matte';

export interface LiquidGlassSettings {
  enabled: boolean;
  blurRadius: number;        // 0..32 px
  vibrancy: number;          // 0..1 (100%..165% saturation)
  frostGrain: number;        // 0..1 micro-grain anti-banding opacity
  translucency: number;      // 0.25..0.95
  specularRim: number;       // 0..1 top specular rim strength
  refraction: number;        // 0..1 optical edge refraction sheen
  bevelThickness: number;    // 0.5..3 px
  dispersion: number;        // 0..1 chromatic rim dispersion
  dynamicShimmer: boolean;   // animated soft specular shimmer
  highContrastText: boolean; // boost text readability over glass
  accentHex: string;
  themePreset: ThemePresetId;
  glassPreset: GlassPresetId;
}

export interface Sensitivity3D {
  rotation: number;    // 0.4..2.0
  zoom: number;        // 0.4..2.0
  drag: number;        // 0.4..2.0
  touchRadius: number; // 0.6..2.0
}

export interface Sensitivity2D {
  pan: number;         // 0.4..2.0
  zoom: number;        // 0.4..2.0
  touchRadius: number; // 0.6..2.0
}

export interface AppSettingsState {
  themeMode: ThemeMode;
  liquidGlass: LiquidGlassSettings;
  rememberWorkspace: boolean;
  sensitivity3D: Sensitivity3D;
  sensitivity2D: Sensitivity2D;
}

export type AiProviderId =
  | 'google_api_key'
  | 'openai'
  | 'openrouter'
  | 'groq'
  | 'deepseek'
  | 'anthropic'
  | 'custom_openai';

export interface AiProviderConfig {
  id: AiProviderId;
  shortTitle: string;
  fullTitle: string;
  defaultBaseUrl: string;
  defaultModel: string;
  keyPlaceholder: string;
  portalUrl: string;
  accent: string;
}

export interface AiSavedProfile {
  apiKey: string;
  model: string;
  baseUrl: string;
}

export interface CustomAiEndpoint {
  id: string;
  name: string;
  baseUrl: string;
  apiKey: string;
  model: string;
}

export interface AiTokenUsage {
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
  requestCount: number;
}

export interface AiPdfCitation {
  page: number;
  label: string;
}

export interface AiPlanEdgePoint {
  name: string;
  from: string;
  to: string;
  ratio: number;
}

export interface AiPlanXyzPoint {
  name: string;
  x: number;
  y: number;
  z: number;
}

export interface AiPlanSegment {
  p1: string;
  p2: string;
  style?: 'solid' | 'dashed' | 'vector_solid' | 'vector_dashed';
  color?: string;
}

export interface AiPlanIntegral2D {
  expr: string;
  secondExpr?: string;
  a: number;
  b: number;
}

export interface AiDrawingPlan {
  target: 'space3d' | 'plane2d';
  title: string;
  shape3dKey?: string;
  a?: number;
  b?: number;
  h?: number;
  edgePoints?: AiPlanEdgePoint[];
  xyzPoints?: AiPlanXyzPoint[];
  segments?: AiPlanSegment[];
  planes?: string[][];
  crossSection?: [string, string, string];
  showCircumSphere?: boolean;
  expressions2d?: string[];
  preset2d?: string;
  integral2d?: AiPlanIntegral2D;
  pdfCitations?: AiPdfCitation[];
}

export interface AiChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  imageDataUrl?: string;
  plan?: AiDrawingPlan | null;
  citations?: AiPdfCitation[];
  createdAt: number;
}

export interface AiChatSession {
  id: string;
  title: string;
  drawMode: boolean;
  messages: AiChatMessage[];
  updatedAt: number;
}

export const THEME_PRESETS: {
  id: ThemePresetId;
  label: string;
  mode: ThemeMode;
  accentHex: string;
  swatch: [string, string];
}[] = [
  { id: 'midnight_cyan', label: 'Xanh Khối Đêm', mode: 'dark', accentHex: '#38BDF8', swatch: ['#060d1c', '#38BDF8'] },
  { id: 'emerald_lab', label: 'Ngọc Lục Bảo', mode: 'dark', accentHex: '#10B981', swatch: ['#041512', '#10B981'] },
  { id: 'violet_nebula', label: 'Tinh Vân Tím', mode: 'dark', accentHex: '#A855F7', swatch: ['#0f0820', '#A855F7'] },
  { id: 'solar_amber', label: 'Hổ Phách Ấm', mode: 'dark', accentHex: '#F59E0B', swatch: ['#181006', '#F59E0B'] },
  { id: 'rose_quartz', label: 'Thạch Anh Hồng', mode: 'dark', accentHex: '#F43F5E', swatch: ['#1b0912', '#F43F5E'] },
  { id: 'paper_light', label: 'Giấy Sáng Sạch', mode: 'light', accentHex: '#0284C7', swatch: ['#f0f6ff', '#0284C7'] }
];

export const GLASS_PRESETS: {
  id: GlassPresetId;
  label: string;
  desc: string;
  patch: Partial<LiquidGlassSettings>;
}[] = [
  {
    id: 'clear',
    label: 'Pha lê trong',
    desc: 'Kính trong sáng, viền khúc xạ sắc nét',
    patch: { enabled: true, blurRadius: 12, vibrancy: 0.76, translucency: 0.84, frostGrain: 0.16, specularRim: 0.95, refraction: 0.82, bevelThickness: 1.5, dispersion: 0.55 }
  },
  {
    id: 'frosted',
    label: 'Kính mờ lụa',
    desc: 'Làm mờ dịu mắt, hạt mờ chống loang màu',
    patch: { enabled: true, blurRadius: 22, vibrancy: 0.65, translucency: 0.64, frostGrain: 0.42, specularRim: 0.72, refraction: 0.45, bevelThickness: 1.2, dispersion: 0.25 }
  },
  {
    id: 'neon',
    label: 'Lăng kính Neon',
    desc: 'Tán sắc viền cao, ánh phản xạ mạnh',
    patch: { enabled: true, blurRadius: 16, vibrancy: 0.96, translucency: 0.78, frostGrain: 0.22, specularRim: 1.0, refraction: 0.95, bevelThickness: 2.0, dispersion: 0.92, dynamicShimmer: true }
  },
  {
    id: 'matte',
    label: 'Phẳng tối giản',
    desc: 'Độ đục cao, tập trung tuyệt đối vào nét vẽ',
    patch: { enabled: true, blurRadius: 6, vibrancy: 0.35, translucency: 0.34, frostGrain: 0.10, specularRim: 0.30, refraction: 0.15, bevelThickness: 1.0, dispersion: 0.0, dynamicShimmer: false }
  }
];

export const DEFAULT_APP_SETTINGS: AppSettingsState = {
  themeMode: 'dark',
  liquidGlass: {
    enabled: false,
    blurRadius: 14,
    vibrancy: 0.72,
    frostGrain: 0.30,
    translucency: 0.80,
    specularRim: 0.90,
    refraction: 0.68,
    bevelThickness: 1.4,
    dispersion: 0.42,
    dynamicShimmer: false,
    highContrastText: false,
    accentHex: '#38BDF8',
    themePreset: 'midnight_cyan',
    glassPreset: 'clear'
  },
  rememberWorkspace: true,
  sensitivity3D: { rotation: 1.0, zoom: 1.0, drag: 1.0, touchRadius: 1.0 },
  sensitivity2D: { pan: 1.0, zoom: 1.0, touchRadius: 1.0 }
};

export const AI_PROVIDERS: AiProviderConfig[] = [
  {
    id: 'google_api_key',
    shortTitle: 'Google Gemini',
    fullTitle: 'Google AI Studio (Gemini API Key)',
    defaultBaseUrl: 'https://generativelanguage.googleapis.com/v1beta',
    defaultModel: 'gemini-2.5-flash',
    keyPlaceholder: 'Dán Google API Key (AIza...)',
    portalUrl: 'https://aistudio.google.com/apikey',
    accent: '#38BDF8'
  },
  {
    id: 'openai',
    shortTitle: 'OpenAI',
    fullTitle: 'OpenAI (ChatGPT API)',
    defaultBaseUrl: 'https://api.openai.com/v1',
    defaultModel: 'gpt-4o-mini',
    keyPlaceholder: 'Dán OpenAI API Key (sk-...)',
    portalUrl: 'https://platform.openai.com/api-keys',
    accent: '#10B981'
  },
  {
    id: 'openrouter',
    shortTitle: 'OpenRouter',
    fullTitle: 'OpenRouter (Đa mô hình & :free)',
    defaultBaseUrl: 'https://openrouter.ai/api/v1',
    defaultModel: 'openai/gpt-4o-mini',
    keyPlaceholder: 'Dán OpenRouter API Key (sk-or-v1-...)',
    portalUrl: 'https://openrouter.ai/keys',
    accent: '#8B5CF6'
  },
  {
    id: 'groq',
    shortTitle: 'Groq',
    fullTitle: 'Groq (Phản hồi siêu nhanh)',
    defaultBaseUrl: 'https://api.groq.com/openai/v1',
    defaultModel: 'llama-3.3-70b-versatile',
    keyPlaceholder: 'Dán Groq API Key (gsk_...)',
    portalUrl: 'https://console.groq.com/keys',
    accent: '#F59E0B'
  },
  {
    id: 'deepseek',
    shortTitle: 'DeepSeek',
    fullTitle: 'DeepSeek API',
    defaultBaseUrl: 'https://api.deepseek.com/v1',
    defaultModel: 'deepseek-chat',
    keyPlaceholder: 'Dán DeepSeek API Key (sk-...)',
    portalUrl: 'https://platform.deepseek.com/api_keys',
    accent: '#6366F1'
  },
  {
    id: 'anthropic',
    shortTitle: 'Claude',
    fullTitle: 'Anthropic Claude',
    defaultBaseUrl: 'https://api.anthropic.com/v1',
    defaultModel: 'claude-3-5-sonnet-latest',
    keyPlaceholder: 'Dán Anthropic API Key (sk-ant-...)',
    portalUrl: 'https://console.anthropic.com/settings/keys',
    accent: '#EC4899'
  },
  {
    id: 'custom_openai',
    shortTitle: 'Custom URL',
    fullTitle: 'Custom URL (Chuẩn OpenAI)',
    defaultBaseUrl: 'https://api.together.xyz/v1',
    defaultModel: 'meta-llama/Llama-3.3-70B-Instruct-Turbo',
    keyPlaceholder: 'Dán API Key (hoặc để trống nếu proxy mở)',
    portalUrl: '',
    accent: '#06B6D4'
  }
];
