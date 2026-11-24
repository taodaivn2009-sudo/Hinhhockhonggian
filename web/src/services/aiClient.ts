import { AI_PROVIDERS, AiDrawingPlan, AiProviderId, AiSavedProfile } from '../types/app';

const SYSTEM_PROMPT_CHAT =
  'Bạn là trợ lý Toán học THPT thông minh của ứng dụng Tool Toán. ' +
  'Hãy giải thích rõ ràng, mạch lạc từng bước bằng tiếng Việt, trình bày công thức dễ đọc.';

const SYSTEM_PROMPT_DRAW =
  'Bạn là trợ lý phân tích đề toán để dựng hình 3D hoặc đồ thị 2D cho Tool Toán. ' +
  'Hãy trả lời ngắn gọn lời giải/mô tả bằng tiếng Việt, sau đó LUÔN kèm đúng 1 khối ```json ... ``` theo mẫu:\n' +
  'Nếu là hình 3D: {"target":"space3d","title":"Tên hình","shape3dKey":"pyramid4_sa","a":4,"b":3,"h":5}\n' +
  '(shape3dKey thuộc: pyramid3, pyramid3_sa, tetra_ortho, pyramid4, pyramid4_sa, pyramid_trap, prism3, prism3_oblique, prism6, cube, box, octahedron, cylinder, cone, sphere)\n' +
  'Nếu là đồ thị/hình 2D: {"target":"plane2d","title":"Tên đồ thị","expressions2d":["x^3 - 3*x"],"preset2d":"empty"}';

export function extractDrawingPlan(text: string): AiDrawingPlan | null {
  const match = text.match(/```json\s*([\s\S]*?)```/i);
  if (!match) return null;
  try {
    const parsed = JSON.parse(match[1].trim());
    if (parsed && (parsed.target === 'space3d' || parsed.target === 'plane2d')) {
      return {
        target: parsed.target,
        title: typeof parsed.title === 'string' ? parsed.title : 'Mô hình từ AI',
        shape3dKey: typeof parsed.shape3dKey === 'string' ? parsed.shape3dKey : undefined,
        a: typeof parsed.a === 'number' ? parsed.a : undefined,
        b: typeof parsed.b === 'number' ? parsed.b : undefined,
        h: typeof parsed.h === 'number' ? parsed.h : undefined,
        expressions2d: Array.isArray(parsed.expressions2d)
          ? parsed.expressions2d.filter((x: unknown): x is string => typeof x === 'string')
          : undefined,
        preset2d: typeof parsed.preset2d === 'string' ? parsed.preset2d : undefined
      };
    }
  } catch {
    return null;
  }
  return null;
}

export async function streamAiResponse(params: {
  providerId: AiProviderId;
  profile: AiSavedProfile;
  prompt: string;
  drawMode: boolean;
  imageDataUrl?: string;
  onToken: (fullText: string) => void;
}): Promise<string> {
  const spec = AI_PROVIDERS.find((p) => p.id === params.providerId) || AI_PROVIDERS[0];
  const apiKey = params.profile.apiKey.trim();
  const model = params.profile.model.trim() || spec.defaultModel;
  const baseUrl = (params.profile.baseUrl.trim() || spec.defaultBaseUrl).replace(/\/+$/, '');
  const systemPrompt = params.drawMode ? SYSTEM_PROMPT_DRAW : SYSTEM_PROMPT_CHAT;

  if (!apiKey && params.providerId !== 'custom_openai') {
    throw new Error(`Chưa nhập API Key cho ${spec.shortTitle}. Hãy vào Cài đặt AI để dán khóa.`);
  }

  // 1. Google AI Studio Gemini API (SSE via Cloudflare /api/ai-proxy)
  if (params.providerId === 'google_api_key') {
    const targetUrl = `${baseUrl}/models/${encodeURIComponent(model)}:streamGenerateContent?alt=sse&key=${encodeURIComponent(apiKey)}`;
    const parts: Record<string, unknown>[] = [{ text: params.prompt }];
    if (params.imageDataUrl) {
      const m = params.imageDataUrl.match(/^data:([^;]+);base64,(.+)$/);
      if (m) parts.push({ inlineData: { mimeType: m[1], data: m[2] } });
    }
    const body = {
      systemInstruction: { parts: [{ text: systemPrompt }] },
      contents: [{ role: 'user', parts }]
    };
    return readSseFromProxy({
      targetUrl,
      headers: { 'Content-Type': 'application/json' },
      body,
      extractChunk: (json) => {
        const candidates = json?.candidates;
        const t = candidates?.[0]?.content?.parts?.map((p: { text?: string }) => p.text || '').join('');
        return typeof t === 'string' ? t : '';
      },
      onToken: params.onToken
    });
  }

  // 2. Anthropic Claude Native Messages API (SSE via /api/ai-proxy)
  if (params.providerId === 'anthropic') {
    const targetUrl = `${baseUrl}/messages`;
    const contentBlocks: Record<string, unknown>[] = [];
    if (params.imageDataUrl) {
      const m = params.imageDataUrl.match(/^data:([^;]+);base64,(.+)$/);
      if (m) {
        contentBlocks.push({
          type: 'image',
          source: { type: 'base64', media_type: m[1], data: m[2] }
        });
      }
    }
    contentBlocks.push({ type: 'text', text: params.prompt });

    return readSseFromProxy({
      targetUrl,
      headers: {
        'Content-Type': 'application/json',
        'x-api-key': apiKey,
        'anthropic-version': '2023-06-01'
      },
      body: {
        model,
        max_tokens: 2048,
        stream: true,
        system: systemPrompt,
        messages: [{ role: 'user', content: contentBlocks }]
      },
      extractChunk: (json) => (json?.type === 'content_block_delta' ? json?.delta?.text || '' : ''),
      onToken: params.onToken
    });
  }

  // 3. OpenAI-Compatible Providers (OpenAI, OpenRouter, Groq, DeepSeek, Custom URL)
  const targetUrl = baseUrl.endsWith('/chat/completions') ? baseUrl : `${baseUrl}/chat/completions`;
  const userContent: unknown = params.imageDataUrl
    ? [
        { type: 'text', text: params.prompt },
        { type: 'image_url', image_url: { url: params.imageDataUrl } }
      ]
    : params.prompt;

  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (apiKey) headers.Authorization = `Bearer ${apiKey}`;

  return readSseFromProxy({
    targetUrl,
    headers,
    body: {
      model,
      stream: true,
      messages: [
        { role: 'system', content: systemPrompt },
        { role: 'user', content: userContent }
      ]
    },
    extractChunk: (json) => json?.choices?.[0]?.delta?.content || '',
    onToken: params.onToken
  });
}

async function readSseFromProxy(opts: {
  targetUrl: string;
  headers: Record<string, string>;
  body: unknown;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  extractChunk: (json: any) => string;
  onToken: (fullText: string) => void;
}): Promise<string> {
  const response = await fetch('/api/ai-proxy', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      targetUrl: opts.targetUrl,
      method: 'POST',
      headers: opts.headers,
      body: opts.body
    })
  });

  if (!response.ok) {
    const errText = await response.text().catch(() => '');
    throw new Error(`HTTP ${response.status}: ${errText.slice(0, 260) || 'Không thể kết nối máy chủ AI'}`);
  }

  if (!response.body) {
    const txt = await response.text();
    opts.onToken(txt);
    return txt;
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder('utf-8');
  let buffer = '';
  let fullText = '';

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split(/\r?\n/);
    buffer = lines.pop() || '';
    for (const rawLine of lines) {
      const line = rawLine.trim();
      if (!line.startsWith('data:')) continue;
      const payload = line.slice(5).trim();
      if (!payload || payload === '[DONE]') continue;
      try {
        const json = JSON.parse(payload);
        const delta = opts.extractChunk(json);
        if (delta) {
          fullText += delta;
          opts.onToken(fullText);
        }
      } catch {
        // Ignore partial non-JSON SSE keepalives
      }
    }
  }

  return fullText || 'AI không trả về nội dung văn bản.';
}
