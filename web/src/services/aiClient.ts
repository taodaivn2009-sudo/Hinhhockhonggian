import {
  AI_PROVIDERS,
  AiChatMessage,
  AiDrawingPlan,
  AiPdfCitation,
  AiProviderId,
  AiSavedProfile
} from '../types/app';

const SYSTEM_PROMPT_CHAT =
  'Bạn là trợ lý Toán học THPT thông minh của ứng dụng Tool Toán. ' +
  'Hãy giải thích rõ ràng, mạch lạc từng bước bằng tiếng Việt. ' +
  'LUÔN bọc các công thức toán học bằng LaTeX chuẩn ($...$ cho công thức cùng dòng, $$...$$ cho công thức khối). ' +
  'Nếu kiến thức liên quan trực tiếp đến chương trong Sổ tay Toán 10-11-12 (388 trang), hãy ghi kèm trích dẫn dạng [Tr. SỐ_TRANG: Tên chủ đề] (ví dụ [Tr. 214: Góc & Góc nhị diện], [Tr. 326: Tích phân Diện tích & Thể tích]).';

const SYSTEM_PROMPT_DRAW =
  'Bạn là trợ lý phân tích đề toán để dựng hình 3D hoặc đồ thị 2D cho Tool Toán. ' +
  'Trả lời lời giải ngắn gọn chuẩn LaTeX ($...$, $$...$$) bằng tiếng Việt, sau đó LUÔN kèm đúng 1 khối ```json ... ``` theo mẫu mở rộng:\n' +
  'Nếu là hình 3D:\n' +
  '{"target":"space3d","title":"Tên hình","shape3dKey":"pyramid4_sa","a":4,"b":3,"h":5,' +
  '"edgePoints":[{"name":"M","from":"S","to":"C","ratio":0.5}],' +
  '"xyzPoints":[{"name":"K","x":1,"y":2,"z":1}],' +
  '"segments":[{"p1":"A","p2":"M","style":"dashed","color":"#f59e0b"}],' +
  '"planes":[["A","M","B"]],' +
  '"crossSection":["A","M","N"],' +
  '"showCircumSphere":false,' +
  '"pdfCitations":[{"page":203,"label":"Thiết diện hình không gian"}]}\n' +
  '(shape3dKey thuộc: pyramid3, pyramid3_sa, tetra_ortho, pyramid4, pyramid4_sa, pyramid_trap, prism3, prism3_oblique, prism6, cube, box, octahedron, cylinder, cone, sphere)\n' +
  'Nếu là đồ thị/hình 2D:\n' +
  '{"target":"plane2d","title":"Tên đồ thị","expressions2d":["x^3 - 3*x"],"preset2d":"empty",' +
  '"integral2d":{"expr":"x^2","secondExpr":"x","a":0,"b":1},' +
  '"pdfCitations":[{"page":326,"label":"Ứng dụng tích phân"}]}';

export function extractPdfCitations(text: string): AiPdfCitation[] {
  const out: AiPdfCitation[] = [];
  const seen = new Set<number>();
  const re = /\[(?:Tr\.?|Trang)\s*(\d{1,3})(?:\s*[:\-–]\s*([^\]]+))?\]/gi;
  let m: RegExpExecArray | null;
  while ((m = re.exec(text)) !== null) {
    const page = parseInt(m[1], 10);
    if (page >= 1 && page <= 388 && !seen.has(page)) {
      seen.add(page);
      out.push({ page, label: (m[2] || `Sổ tay Toán · Tr. ${page}`).trim() });
    }
  }
  return out;
}

export function extractDrawingPlan(text: string): AiDrawingPlan | null {
  const match = text.match(/```json\s*([\s\S]*?)```/i);
  if (!match) return null;
  try {
    const parsed = JSON.parse(match[1].trim());
    if (parsed && (parsed.target === 'space3d' || parsed.target === 'plane2d')) {
      const pdfCitations: AiPdfCitation[] = Array.isArray(parsed.pdfCitations)
        ? parsed.pdfCitations
            .filter((c: { page?: unknown }) => typeof c?.page === 'number' && c.page >= 1 && c.page <= 388)
            .map((c: { page: number; label?: unknown }) => ({
              page: Math.round(c.page),
              label: typeof c.label === 'string' ? c.label : `Trang ${Math.round(c.page)}`
            }))
        : [];

      const crossSection: [string, string, string] | undefined =
        Array.isArray(parsed.crossSection) &&
        parsed.crossSection.length === 3 &&
        parsed.crossSection.every((s: unknown) => typeof s === 'string')
          ? [parsed.crossSection[0], parsed.crossSection[1], parsed.crossSection[2]]
          : undefined;

      return {
        target: parsed.target,
        title: typeof parsed.title === 'string' ? parsed.title : 'Mô hình từ AI',
        shape3dKey: typeof parsed.shape3dKey === 'string' ? parsed.shape3dKey : undefined,
        a: typeof parsed.a === 'number' ? parsed.a : undefined,
        b: typeof parsed.b === 'number' ? parsed.b : undefined,
        h: typeof parsed.h === 'number' ? parsed.h : undefined,
        edgePoints: Array.isArray(parsed.edgePoints) ? parsed.edgePoints : undefined,
        xyzPoints: Array.isArray(parsed.xyzPoints) ? parsed.xyzPoints : undefined,
        segments: Array.isArray(parsed.segments) ? parsed.segments : undefined,
        planes: Array.isArray(parsed.planes) ? parsed.planes : undefined,
        crossSection,
        showCircumSphere: typeof parsed.showCircumSphere === 'boolean' ? parsed.showCircumSphere : undefined,
        expressions2d: Array.isArray(parsed.expressions2d)
          ? parsed.expressions2d.filter((x: unknown): x is string => typeof x === 'string')
          : undefined,
        preset2d: typeof parsed.preset2d === 'string' ? parsed.preset2d : undefined,
        integral2d:
          parsed.integral2d && typeof parsed.integral2d.expr === 'string'
            ? {
                expr: parsed.integral2d.expr,
                secondExpr: typeof parsed.integral2d.secondExpr === 'string' ? parsed.integral2d.secondExpr : undefined,
                a: typeof parsed.integral2d.a === 'number' ? parsed.integral2d.a : 0,
                b: typeof parsed.integral2d.b === 'number' ? parsed.integral2d.b : 2
              }
            : undefined,
        pdfCitations
      };
    }
  } catch {
    return null;
  }
  return null;
}

export interface StreamAiResult {
  text: string;
  promptTokens: number;
  completionTokens: number;
}

export async function streamAiResponse(params: {
  providerId: AiProviderId;
  profile: AiSavedProfile;
  history: AiChatMessage[];
  prompt: string;
  drawMode: boolean;
  imageDataUrl?: string;
  signal?: AbortSignal;
  onToken: (fullText: string) => void;
}): Promise<StreamAiResult> {
  const spec = AI_PROVIDERS.find((p) => p.id === params.providerId) || AI_PROVIDERS[0];
  const apiKey = params.profile.apiKey.trim();
  const model = params.profile.model.trim() || spec.defaultModel;
  const baseUrl = (params.profile.baseUrl.trim() || spec.defaultBaseUrl).replace(/\/+$/, '');
  const systemPrompt = params.drawMode ? SYSTEM_PROMPT_DRAW : SYSTEM_PROMPT_CHAT;

  if (!apiKey && params.providerId !== 'custom_openai') {
    throw new Error(`Chưa nhập API Key cho ${spec.shortTitle}. Hãy vào Cài đặt AI để dán khóa.`);
  }

  // Keep last 8 messages for coherent multi-turn context without blowing token limits
  const recentHistory = params.history.slice(-8);
  const approxPromptChars =
    systemPrompt.length +
    recentHistory.reduce((acc, m) => acc + m.content.length, 0) +
    params.prompt.length;

  // 1. Google AI Studio Gemini API
  if (params.providerId === 'google_api_key') {
    const targetUrl = `${baseUrl}/models/${encodeURIComponent(model)}:streamGenerateContent?alt=sse&key=${encodeURIComponent(apiKey)}`;
    const contents: Record<string, unknown>[] = recentHistory.map((m) => ({
      role: m.role === 'assistant' ? 'model' : 'user',
      parts: [{ text: m.content }]
    }));
    const currentParts: Record<string, unknown>[] = [{ text: params.prompt }];
    if (params.imageDataUrl) {
      const m = params.imageDataUrl.match(/^data:([^;]+);base64,(.+)$/);
      if (m) currentParts.push({ inlineData: { mimeType: m[1], data: m[2] } });
    }
    contents.push({ role: 'user', parts: currentParts });

    const text = await readSseFromProxy({
      targetUrl,
      headers: { 'Content-Type': 'application/json' },
      body: { systemInstruction: { parts: [{ text: systemPrompt }] }, contents },
      signal: params.signal,
      extractChunk: (json) => {
        const t = json?.candidates?.[0]?.content?.parts?.map((p: { text?: string }) => p.text || '').join('');
        return typeof t === 'string' ? t : '';
      },
      onToken: params.onToken
    });
    return {
      text,
      promptTokens: Math.ceil(approxPromptChars / 4),
      completionTokens: Math.ceil(text.length / 4)
    };
  }

  // 2. Anthropic Claude Native Messages API
  if (params.providerId === 'anthropic') {
    const targetUrl = `${baseUrl}/messages`;
    const messages: Record<string, unknown>[] = recentHistory.map((m) => ({
      role: m.role,
      content: m.content
    }));
    const contentBlocks: Record<string, unknown>[] = [];
    if (params.imageDataUrl) {
      const m = params.imageDataUrl.match(/^data:([^;]+);base64,(.+)$/);
      if (m) {
        contentBlocks.push({ type: 'image', source: { type: 'base64', media_type: m[1], data: m[2] } });
      }
    }
    contentBlocks.push({ type: 'text', text: params.prompt });
    messages.push({ role: 'user', content: contentBlocks });

    const text = await readSseFromProxy({
      targetUrl,
      headers: {
        'Content-Type': 'application/json',
        'x-api-key': apiKey,
        'anthropic-version': '2023-06-01'
      },
      body: { model, max_tokens: 2048, stream: true, system: systemPrompt, messages },
      signal: params.signal,
      extractChunk: (json) => (json?.type === 'content_block_delta' ? json?.delta?.text || '' : ''),
      onToken: params.onToken
    });
    return {
      text,
      promptTokens: Math.ceil(approxPromptChars / 4),
      completionTokens: Math.ceil(text.length / 4)
    };
  }

  // 3. OpenAI-Compatible Providers
  const targetUrl = baseUrl.endsWith('/chat/completions') ? baseUrl : `${baseUrl}/chat/completions`;
  const userContent: unknown = params.imageDataUrl
    ? [
        { type: 'text', text: params.prompt },
        { type: 'image_url', image_url: { url: params.imageDataUrl } }
      ]
    : params.prompt;

  const messages: Record<string, unknown>[] = [
    { role: 'system', content: systemPrompt },
    ...recentHistory.map((m) => ({ role: m.role, content: m.content })),
    { role: 'user', content: userContent }
  ];

  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (apiKey) headers.Authorization = `Bearer ${apiKey}`;

  const text = await readSseFromProxy({
    targetUrl,
    headers,
    body: { model, stream: true, messages },
    signal: params.signal,
    extractChunk: (json) => json?.choices?.[0]?.delta?.content || '',
    onToken: params.onToken
  });
  return {
    text,
    promptTokens: Math.ceil(approxPromptChars / 4),
    completionTokens: Math.ceil(text.length / 4)
  };
}

/**
 * Fetches available models from the selected provider via /api/ai-proxy.
 */
export async function fetchProviderModels(
  providerId: AiProviderId,
  profile: AiSavedProfile
): Promise<string[]> {
  const spec = AI_PROVIDERS.find((p) => p.id === providerId) || AI_PROVIDERS[0];
  const apiKey = profile.apiKey.trim();
  const baseUrl = (profile.baseUrl.trim() || spec.defaultBaseUrl).replace(/\/+$/, '');

  if (providerId === 'anthropic') {
    return [
      'claude-3-7-sonnet-latest',
      'claude-3-5-sonnet-latest',
      'claude-3-5-haiku-latest',
      'claude-3-opus-latest'
    ];
  }

  let targetUrl = `${baseUrl}/models`;
  const headers: Record<string, string> = {};
  if (providerId === 'google_api_key') {
    if (!apiKey) throw new Error('Cần nhập Google API Key để tải danh sách model.');
    targetUrl = `${baseUrl}/models?key=${encodeURIComponent(apiKey)}`;
  } else if (apiKey) {
    headers.Authorization = `Bearer ${apiKey}`;
  }

  const res = await fetch('/api/ai-proxy', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ targetUrl, method: 'GET', headers })
  });
  if (!res.ok) {
    const errText = await res.text().catch(() => '');
    throw new Error(`Không tải được danh sách model (HTTP ${res.status}): ${errText.slice(0, 140)}`);
  }
  const data = await res.json();
  if (providerId === 'google_api_key' && Array.isArray(data?.models)) {
    return (data.models as { name?: string; supportedGenerationMethods?: string[] }[])
      .filter((m) => m.name && (!m.supportedGenerationMethods || m.supportedGenerationMethods.includes('generateContent')))
      .map((m) => (m.name || '').replace(/^models\//, ''))
      .slice(0, 40);
  }
  if (Array.isArray(data?.data)) {
    return (data.data as { id?: string }[])
      .map((m) => m.id || '')
      .filter(Boolean)
      .sort()
      .slice(0, 60);
  }
  return [];
}

/**
 * Probes an API Key + Model with a minimal ping request and returns status + latency in ms.
 */
export async function probeProviderModel(
  providerId: AiProviderId,
  profile: AiSavedProfile
): Promise<{ ok: boolean; latencyMs: number; message: string }> {
  const start = performance.now();
  try {
    const result = await streamAiResponse({
      providerId,
      profile,
      history: [],
      prompt: 'Trả lời ngắn gọn đúng 1 chữ: OK',
      drawMode: false,
      onToken: () => {}
    });
    const latencyMs = Math.round(performance.now() - start);
    return {
      ok: true,
      latencyMs,
      message: `Kết nối thành công (${latencyMs}ms) · Phản hồi: ${result.text.slice(0, 40)}`
    };
  } catch (err) {
    const latencyMs = Math.round(performance.now() - start);
    return {
      ok: false,
      latencyMs,
      message: err instanceof Error ? err.message : 'Lỗi kết nối không xác định'
    };
  }
}

async function readSseFromProxy(opts: {
  targetUrl: string;
  headers: Record<string, string>;
  body: unknown;
  signal?: AbortSignal;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  extractChunk: (json: any) => string;
  onToken: (fullText: string) => void;
}): Promise<string> {
  const response = await fetch('/api/ai-proxy', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    signal: opts.signal,
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
