export type MathWorkspace = 'space3d' | 'plane2d' | 'docs' | 'ai' | 'settings';

export type ThemeMode = 'dark' | 'light';

export interface LiquidGlassSettings {
  enabled: boolean;
  blurRadius: number;       // 0..32 px
  vibrancy: number;         // 0..1 (100%..165% saturation)
  frostGrain: number;       // 0..1 micro-grain anti-banding opacity
  translucency: number;     // 0.25..0.95
  specularRim: number;      // 0..1 top specular rim strength
  accentHex: string;
}

export interface AppSettingsState {
  themeMode: ThemeMode;
  liquidGlass: LiquidGlassSettings;
  rememberWorkspace: boolean;
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

export interface AiChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  imageDataUrl?: string;
  plan?: AiDrawingPlan | null;
  createdAt: number;
}

export interface AiDrawingPlan {
  target: 'space3d' | 'plane2d';
  title: string;
  shape3dKey?: string;
  a?: number;
  b?: number;
  h?: number;
  expressions2d?: string[];
  preset2d?: string;
}

export const DEFAULT_APP_SETTINGS: AppSettingsState = {
  themeMode: 'dark',
  liquidGlass: {
    enabled: true,
    blurRadius: 14,
    vibrancy: 0.72,
    frostGrain: 0.30,
    translucency: 0.80,
    specularRim: 0.90,
    accentHex: '#38BDF8'
  },
  rememberWorkspace: true
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
