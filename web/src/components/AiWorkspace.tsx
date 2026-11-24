import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  BookOpen,
  Box,
  Check,
  Copy,
  Image as ImageIcon,
  MessageSquarePlus,
  Send,
  Settings2,
  Sparkles,
  Square,
  Trash2,
  TrendingUp
} from 'lucide-react';
import {
  AI_PROVIDERS,
  AiChatMessage,
  AiChatSession,
  AiDrawingPlan,
  AiProviderId,
  AiSavedProfile,
  AiTokenUsage,
  CustomAiEndpoint
} from '../types/app';
import { extractDrawingPlan, extractPdfCitations, streamAiResponse } from '../services/aiClient';
import { MathMarkdown } from './MathMarkdown';

const SESSIONS_KEY = 'tooltoan_ai_sessions_v2';

interface AiWorkspaceProps {
  accentHex: string;
  activeProvider: AiProviderId;
  onChangeProvider: (id: AiProviderId) => void;
  profiles: Record<AiProviderId, AiSavedProfile>;
  customEndpoints: CustomAiEndpoint[];
  activeCustomEndpointId: string | null;
  onSelectCustomEndpoint: (id: string | null) => void;
  tokenUsage: AiTokenUsage;
  onRecordTokens: (promptTokens: number, completionTokens: number) => void;
  prefillPrompt: string | null;
  onPrefillConsumed: () => void;
  onOpenSettings: () => void;
  onApplyDrawingPlan: (plan: AiDrawingPlan) => void;
  onOpenPdfPage: (page: number) => void;
}

function makeSession(drawMode: boolean): AiChatSession {
  return {
    id: `chat_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`,
    title: drawMode ? 'Đọc đề & Dựng hình mới' : 'Hỏi đáp Toán mới',
    drawMode,
    messages: [],
    updatedAt: Date.now()
  };
}

export const AiWorkspace: React.FC<AiWorkspaceProps> = ({
  accentHex,
  activeProvider,
  onChangeProvider,
  profiles,
  customEndpoints,
  activeCustomEndpointId,
  onSelectCustomEndpoint,
  tokenUsage,
  onRecordTokens,
  prefillPrompt,
  onPrefillConsumed,
  onOpenSettings,
  onApplyDrawingPlan,
  onOpenPdfPage
}) => {
  const [sessions, setSessions] = useState<AiChatSession[]>(() => {
    try {
      const raw = localStorage.getItem(SESSIONS_KEY);
      if (raw) {
        const parsed = JSON.parse(raw) as AiChatSession[];
        if (Array.isArray(parsed) && parsed.length > 0) return parsed;
      }
    } catch {
      // ignore
    }
    return [makeSession(false)];
  });
  const [activeSessionId, setActiveSessionId] = useState<string>(() => sessions[0]?.id || '');
  const [input, setInput] = useState<string>('');
  const [attachedImage, setAttachedImage] = useState<string | null>(null);
  const [isStreaming, setIsStreaming] = useState<boolean>(false);
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const [showSessions, setShowSessions] = useState<boolean>(false);

  const abortRef = useRef<AbortController | null>(null);
  const listRef = useRef<HTMLDivElement | null>(null);
  const fileRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    localStorage.setItem(SESSIONS_KEY, JSON.stringify(sessions.slice(0, 20)));
  }, [sessions]);

  const currentSession = useMemo(
    () => sessions.find((s) => s.id === activeSessionId) || sessions[0],
    [sessions, activeSessionId]
  );

  useEffect(() => {
    if (prefillPrompt) {
      setInput(prefillPrompt);
      onPrefillConsumed();
    }
  }, [prefillPrompt, onPrefillConsumed]);

  useEffect(() => {
    listRef.current?.scrollTo({ top: listRef.current.scrollHeight, behavior: 'smooth' });
  }, [currentSession?.messages.length, isStreaming]);

  const effectiveProfile: AiSavedProfile = useMemo(() => {
    if (activeCustomEndpointId) {
      const custom = customEndpoints.find((e) => e.id === activeCustomEndpointId);
      if (custom) return { apiKey: custom.apiKey, model: custom.model, baseUrl: custom.baseUrl };
    }
    return profiles[activeProvider];
  }, [activeCustomEndpointId, customEndpoints, profiles, activeProvider]);

  const effectiveProviderId: AiProviderId = activeCustomEndpointId ? 'custom_openai' : activeProvider;

  const updateCurrentSession = (updater: (s: AiChatSession) => AiChatSession) => {
    setSessions((prev) =>
      prev.map((s) => (s.id === currentSession.id ? updater(s) : s))
    );
  };

  const handleNewSession = (drawMode: boolean) => {
    const next = makeSession(drawMode);
    setSessions((prev) => [next, ...prev]);
    setActiveSessionId(next.id);
    setShowSessions(false);
  };

  const handleDeleteSession = (id: string) => {
    setSessions((prev) => {
      const remaining = prev.filter((s) => s.id !== id);
      if (remaining.length === 0) {
        const fresh = makeSession(false);
        setActiveSessionId(fresh.id);
        return [fresh];
      }
      if (id === activeSessionId) setActiveSessionId(remaining[0].id);
      return remaining;
    });
  };

  const handlePickImage = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      if (typeof reader.result === 'string') setAttachedImage(reader.result);
    };
    reader.readAsDataURL(file);
    e.target.value = '';
  };

  const handleStop = () => {
    abortRef.current?.abort();
    abortRef.current = null;
    setIsStreaming(false);
  };

  const handleSend = async (customPrompt?: string) => {
    const prompt = (customPrompt ?? input).trim();
    if ((!prompt && !attachedImage) || isStreaming || !currentSession) return;

    const userMsg: AiChatMessage = {
      id: `u_${Date.now()}`,
      role: 'user',
      content: prompt || 'Hãy giải và dựng hình từ ảnh đề toán này.',
      imageDataUrl: attachedImage || undefined,
      createdAt: Date.now()
    };
    const asstId = `a_${Date.now() + 1}`;
    const placeholderAsst: AiChatMessage = {
      id: asstId,
      role: 'assistant',
      content: '',
      createdAt: Date.now() + 1
    };

    const historyBefore = currentSession.messages;
    const nextTitle =
      currentSession.messages.length === 0 ? prompt.slice(0, 42) || 'Bài toán mới' : currentSession.title;

    updateCurrentSession((s) => ({
      ...s,
      title: nextTitle,
      updatedAt: Date.now(),
      messages: [...s.messages, userMsg, placeholderAsst]
    }));

    setInput('');
    const imgToSend = attachedImage || undefined;
    setAttachedImage(null);
    setIsStreaming(true);

    const controller = new AbortController();
    abortRef.current = controller;

    try {
      const result = await streamAiResponse({
        providerId: effectiveProviderId,
        profile: effectiveProfile,
        history: historyBefore,
        prompt: userMsg.content,
        drawMode: currentSession.drawMode,
        imageDataUrl: imgToSend,
        signal: controller.signal,
        onToken: (fullText) => {
          updateCurrentSession((s) => ({
            ...s,
            messages: s.messages.map((m) => (m.id === asstId ? { ...m, content: fullText } : m))
          }));
        }
      });

      const plan = extractDrawingPlan(result.text);
      const inlineCitations = extractPdfCitations(result.text);
      const mergedCitations = [...(plan?.pdfCitations || [])];
      for (const c of inlineCitations) {
        if (!mergedCitations.some((x) => x.page === c.page)) mergedCitations.push(c);
      }

      onRecordTokens(result.promptTokens, result.completionTokens);
      updateCurrentSession((s) => ({
        ...s,
        updatedAt: Date.now(),
        messages: s.messages.map((m) =>
          m.id === asstId ? { ...m, content: result.text, plan, citations: mergedCitations } : m
        )
      }));
    } catch (err) {
      const aborted = err instanceof DOMException && err.name === 'AbortError';
      const errMsg = aborted
        ? '⏹ Đã dừng sinh câu trả lời.'
        : err instanceof Error
          ? `⚠️ ${err.message}`
          : '⚠️ Lỗi không xác định khi gọi AI.';
      updateCurrentSession((s) => ({
        ...s,
        messages: s.messages.map((m) =>
          m.id === asstId ? { ...m, content: m.content ? `${m.content}\n\n${errMsg}` : errMsg } : m
        )
      }));
    } finally {
      abortRef.current = null;
      setIsStreaming(false);
    }
  };

  const handleCopy = async (id: string, text: string) => {
    try {
      await navigator.clipboard.writeText(text);
      setCopiedId(id);
      setTimeout(() => setCopiedId((prev) => (prev === id ? null : prev)), 1800);
    } catch {
      // ignore
    }
  };

  const activeProviderSpec = AI_PROVIDERS.find((p) => p.id === activeProvider) || AI_PROVIDERS[0];
  const hasApiKey = Boolean(effectiveProfile.apiKey.trim()) || effectiveProviderId === 'custom_openai';

  return (
    <div style={{ width: '100%', height: '100%', display: 'flex', flexDirection: 'column', padding: '10px 12px 78px', gap: 10 }}>
      {/* Top Bar */}
      <div
        className="glass-surface"
        style={{
          borderRadius: 18,
          padding: '8px 12px',
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          flexWrap: 'wrap'
        }}
      >
        <button
          className={`chip-btn glass-press ${!currentSession?.drawMode ? 'active' : ''}`}
          onClick={() => updateCurrentSession((s) => ({ ...s, drawMode: false }))}
        >
          <Sparkles size={14} /> Hỏi đáp Toán
        </button>
        <button
          className={`chip-btn glass-press ${currentSession?.drawMode ? 'active' : ''}`}
          onClick={() => updateCurrentSession((s) => ({ ...s, drawMode: true }))}
        >
          <Box size={14} /> Đọc đề & Dựng 3D/2D
        </button>

        <button className="chip-btn glass-press" onClick={() => setShowSessions((v) => !v)}>
          Lịch sử ({sessions.length})
        </button>
        <button className="chip-btn glass-press" onClick={() => handleNewSession(currentSession?.drawMode || false)}>
          <MessageSquarePlus size={14} /> Phiên mới
        </button>

        <div style={{ flex: 1 }} />

        {/* Provider & Custom Endpoint selector */}
        <select
          className="glass-select"
          style={{ width: 'auto', minWidth: 150, padding: '6px 10px', fontSize: 12 }}
          value={activeCustomEndpointId ? `custom:${activeCustomEndpointId}` : activeProvider}
          onChange={(e) => {
            const val = e.target.value;
            if (val.startsWith('custom:')) {
              onSelectCustomEndpoint(val.slice(7));
            } else {
              onSelectCustomEndpoint(null);
              onChangeProvider(val as AiProviderId);
            }
          }}
        >
          {AI_PROVIDERS.map((p) => (
            <option key={p.id} value={p.id}>
              {p.shortTitle} ({profiles[p.id].model})
            </option>
          ))}
          {customEndpoints.length > 0 && (
            <optgroup label="Custom Endpoints đã lưu">
              {customEndpoints.map((ce) => (
                <option key={ce.id} value={`custom:${ce.id}`}>
                  ★ {ce.name} ({ce.model})
                </option>
              ))}
            </optgroup>
          )}
        </select>

        <span
          className="mono desktop-only"
          style={{
            fontSize: 11,
            padding: '4px 8px',
            borderRadius: 999,
            background: 'rgba(148,163,184,0.14)',
            color: 'var(--text-secondary)'
          }}
          title="Tổng ước lượng Token đã dùng"
        >
          {tokenUsage.totalTokens.toLocaleString()} tok
        </span>

        <button className="icon-btn" title="Cài đặt AI Key & Model" onClick={onOpenSettings}>
          <Settings2 size={17} />
        </button>
      </div>

      {/* Main area with optional saved sessions drawer */}
      <div style={{ flex: 1, display: 'flex', gap: 10, overflow: 'hidden' }}>
        {showSessions && (
          <aside
            className="glass-surface scroll-area"
            style={{ width: 250, borderRadius: 16, padding: 10, display: 'flex', flexDirection: 'column', gap: 6, flexShrink: 0 }}
          >
            <div style={{ fontSize: 12, fontWeight: 800, color: 'var(--text-secondary)' }}>Các cuộc trò chuyện</div>
            {sessions.map((s) => (
              <div
                key={s.id}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                  padding: '7px 8px',
                  borderRadius: 10,
                  border: `1px solid ${s.id === currentSession?.id ? accentHex : 'var(--border-subtle)'}`,
                  background: s.id === currentSession?.id ? `${accentHex}1c` : 'rgba(148,163,184,0.06)'
                }}
              >
                <button
                  onClick={() => setActiveSessionId(s.id)}
                  style={{
                    flex: 1,
                    textAlign: 'left',
                    border: 'none',
                    background: 'transparent',
                    color: 'var(--text-primary)',
                    cursor: 'pointer',
                    fontSize: 12,
                    fontWeight: 600,
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap'
                  }}
                >
                  {s.drawMode ? '📐 ' : '💬 '}
                  {s.title}
                </button>
                <button className="icon-btn" style={{ width: 26, height: 26 }} onClick={() => handleDeleteSession(s.id)}>
                  <Trash2 size={13} />
                </button>
              </div>
            ))}
          </aside>
        )}

        {/* Chat Stream */}
        <div
          ref={listRef}
          className="glass-surface scroll-area"
          style={{
            flex: 1,
            borderRadius: 18,
            padding: 14,
            display: 'flex',
            flexDirection: 'column',
            gap: 12
          }}
        >
          {!hasApiKey && (
            <div
              style={{
                padding: '10px 12px',
                borderRadius: 12,
                background: 'rgba(245,158,11,0.14)',
                border: '1px solid rgba(245,158,11,0.45)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                gap: 8,
                fontSize: 12.5
              }}
            >
              <span>Chưa cấu hình API Key cho <b>{activeProviderSpec.shortTitle}</b>.</span>
              <button className="chip-btn active" onClick={onOpenSettings}>Mở Cài đặt AI</button>
            </div>
          )}

          {currentSession?.messages.length === 0 ? (
            <div style={{ margin: 'auto', textAlign: 'center', maxWidth: 520, display: 'flex', flexDirection: 'column', gap: 10 }}>
              <Sparkles size={36} style={{ margin: '0 auto', color: accentHex }} />
              <div style={{ fontSize: 16, fontWeight: 800 }}>
                {currentSession.drawMode ? 'AI Đọc Đề & Dựng Hình 3D / 2D Tự Động' : 'Trợ lý Toán học THPT (Chuẩn LaTeX)'}
              </div>
              <div style={{ fontSize: 12.5, color: 'var(--text-secondary)', lineHeight: 1.55 }}>
                {currentSession.drawMode
                  ? 'Dán đề bài hoặc tải ảnh đề thi. AI sẽ tự dựng khối 3D, chấm điểm trung điểm/tỉ lệ k, kẻ đường phụ, cắt thiết diện động hoặc tô miền tích phân trên Oxy.'
                  : 'Hỗ trợ công thức LaTeX sắc nét, nhớ ngữ cảnh nhiều lượt hỏi và tự động gắn liên kết mở thẳng trang Sổ tay Toán 10–11–12.'}
              </div>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, justifyContent: 'center', marginTop: 4 }}>
                {(currentSession.drawMode
                  ? [
                      'Cho chóp S.ABCD đáy vuông cạnh 4, SA vuông góc đáy SA=5. Lấy M là trung điểm SC, N trên SB sao cho SN=1/3 SB. Dựng thiết diện (AMN) và mặt cầu ngoại tiếp.',
                      'Vẽ đồ thị y = x^2 và y = 2x, tô màu và tính diện tích hình phẳng giới hạn trên [0; 2].'
                    ]
                  : [
                      'Chứng minh công thức khoảng cách giữa 2 đường thẳng chéo nhau trong Oxyz và lấy ví dụ.',
                      'Tổng hợp các dạng cực trị hàm bậc 3 chứa tham số m kèm công thức giải nhanh.'
                    ]
                ).map((sample, i) => (
                  <button
                    key={i}
                    className="chip-btn glass-press"
                    style={{ whiteSpace: 'normal', textAlign: 'left', borderRadius: 12, padding: '8px 11px' }}
                    onClick={() => handleSend(sample)}
                  >
                    {sample}
                  </button>
                ))}
              </div>
            </div>
          ) : (
            currentSession?.messages.map((msg) => (
              <div
                key={msg.id}
                style={{
                  alignSelf: msg.role === 'user' ? 'flex-end' : 'flex-start',
                  maxWidth: '88%',
                  borderRadius: 16,
                  padding: '10px 14px',
                  background: msg.role === 'user' ? `${accentHex}24` : 'rgba(148,163,184,0.10)',
                  border: `1px solid ${msg.role === 'user' ? `${accentHex}55` : 'var(--border-subtle)'}`
                }}
              >
                {msg.imageDataUrl && (
                  <img
                    src={msg.imageDataUrl}
                    alt="Ảnh đề toán"
                    style={{ maxWidth: 260, maxHeight: 180, borderRadius: 10, marginBottom: 8, display: 'block' }}
                  />
                )}

                {msg.role === 'assistant' ? (
                  <MathMarkdown text={msg.content || (isStreaming ? 'Đang suy nghĩ...' : '')} />
                ) : (
                  <div style={{ fontSize: 13.5, lineHeight: 1.55, whiteSpace: 'pre-wrap' }}>{msg.content}</div>
                )}

                {/* Rich Drawing Plan Card */}
                {msg.plan && (
                  <div
                    style={{
                      marginTop: 10,
                      padding: '10px 12px',
                      borderRadius: 12,
                      background: `${accentHex}18`,
                      border: `1px solid ${accentHex}66`,
                      display: 'flex',
                      flexDirection: 'column',
                      gap: 6
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8, flexWrap: 'wrap' }}>
                      <div style={{ fontSize: 12.5, fontWeight: 800 }}>
                        {msg.plan.target === 'space3d' ? '🧊 Mô hình 3D: ' : '📈 Đồ thị 2D: '}
                        {msg.plan.title}
                      </div>
                      <button className="primary-btn glass-press" style={{ minHeight: 34, padding: '5px 12px', fontSize: 12 }} onClick={() => onApplyDrawingPlan(msg.plan!)}>
                        {msg.plan.target === 'space3d' ? <Box size={14} /> : <TrendingUp size={14} />}
                        Mở trên {msg.plan.target === 'space3d' ? 'Không gian 3D' : 'Mặt phẳng 2D'}
                      </button>
                    </div>
                    <div style={{ fontSize: 11.5, color: 'var(--text-secondary)' }}>
                      {msg.plan.target === 'space3d'
                        ? [
                            msg.plan.shape3dKey && `Khối: ${msg.plan.shape3dKey}`,
                            msg.plan.edgePoints?.length ? `${msg.plan.edgePoints.length} điểm trên cạnh` : '',
                            msg.plan.segments?.length ? `${msg.plan.segments.length} đoạn/vectơ` : '',
                            msg.plan.crossSection ? `Thiết diện (${msg.plan.crossSection.join('')})` : '',
                            msg.plan.showCircumSphere ? 'Mặt cầu ngoại tiếp' : ''
                          ]
                            .filter(Boolean)
                            .join(' • ')
                        : [
                            msg.plan.expressions2d?.join(', '),
                            msg.plan.integral2d ? `Tích phân [${msg.plan.integral2d.a}; ${msg.plan.integral2d.b}]` : ''
                          ]
                            .filter(Boolean)
                            .join(' • ')}
                    </div>
                  </div>
                )}

                {/* Clickable PDF Citations */}
                {msg.citations && msg.citations.length > 0 && (
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginTop: 8 }}>
                    {msg.citations.map((c, idx) => (
                      <button
                        key={idx}
                        className="chip-btn glass-press"
                        style={{ minHeight: 28, padding: '3px 9px', fontSize: 11 }}
                        onClick={() => onOpenPdfPage(c.page)}
                      >
                        <BookOpen size={12} /> Mở Tr. {c.page}: {c.label}
                      </button>
                    ))}
                  </div>
                )}

                {/* Copy action for assistant */}
                {msg.role === 'assistant' && msg.content && (
                  <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 6 }}>
                    <button
                      className="chip-btn"
                      style={{ minHeight: 26, padding: '2px 8px', fontSize: 11 }}
                      onClick={() => handleCopy(msg.id, msg.content)}
                    >
                      {copiedId === msg.id ? <Check size={12} /> : <Copy size={12} />}
                      {copiedId === msg.id ? 'Đã chép' : 'Sao chép'}
                    </button>
                  </div>
                )}
              </div>
            ))
          )}
        </div>
      </div>

      {/* Composer */}
      <div className="glass-surface" style={{ borderRadius: 18, padding: '8px 10px', display: 'flex', flexDirection: 'column', gap: 6 }}>
        {attachedImage && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <img src={attachedImage} alt="Đính kèm" style={{ height: 48, borderRadius: 8 }} />
            <button className="chip-btn" onClick={() => setAttachedImage(null)}>Xóa ảnh</button>
          </div>
        )}
        <div style={{ display: 'flex', alignItems: 'flex-end', gap: 8 }}>
          <input ref={fileRef} type="file" accept="image/*" style={{ display: 'none' }} onChange={handlePickImage} />
          <button className="icon-btn" title="Tải ảnh đề toán" onClick={() => fileRef.current?.click()}>
            <ImageIcon size={18} />
          </button>
          <textarea
            className="glass-textarea"
            rows={1}
            placeholder={
              currentSession?.drawMode
                ? 'Nhập đề bài để AI dựng hình 3D / đồ thị 2D tự động...'
                : 'Hỏi bài Toán lớp 10–11–12 (hỗ trợ LaTeX & nhớ ngữ cảnh)...'
            }
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                handleSend();
              }
            }}
            style={{ flex: 1, resize: 'none', maxHeight: 110 }}
          />
          {isStreaming ? (
            <button
              className="primary-btn glass-press"
              style={{ background: '#ef4444', minHeight: 40, padding: '0 14px' }}
              onClick={handleStop}
            >
              <Square size={14} /> Dừng
            </button>
          ) : (
            <button className="primary-btn glass-press" style={{ minHeight: 40, padding: '0 14px' }} onClick={() => handleSend()}>
              <Send size={15} /> Gửi
            </button>
          )}
        </div>
      </div>
    </div>
  );
};
