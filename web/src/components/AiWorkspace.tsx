import React, { useEffect, useRef, useState } from 'react';
import {
  Box,
  Compass,
  ImagePlus,
  Send,
  Settings,
  Sparkles,
  Trash2,
  X
} from 'lucide-react';
import { extractDrawingPlan, streamAiResponse } from '../services/aiClient';
import {
  AI_PROVIDERS,
  AiChatMessage,
  AiDrawingPlan,
  AiProviderId,
  AiSavedProfile
} from '../types/app';

interface AiWorkspaceProps {
  accentHex: string;
  activeProvider: AiProviderId;
  onChangeProvider: (id: AiProviderId) => void;
  profiles: Record<AiProviderId, AiSavedProfile>;
  prefillPrompt: string | null;
  onPrefillConsumed: () => void;
  onOpenSettings: () => void;
  onApplyDrawingPlan: (plan: AiDrawingPlan) => void;
}

const SAMPLE_PROMPTS_CHAT = [
  'Khảo sát và tìm cực trị của hàm số y = x^3 - 3x + 2',
  'Cho hình chóp S.ABCD đáy vuông cạnh a, SA ⊥ đáy, SA = a√2. Tính thể tích V.',
  'Tổng hợp công thức tính khoảng cách từ điểm đến mặt phẳng trong Oxyz'
];

const SAMPLE_PROMPTS_DRAW = [
  'Vẽ hình chóp tứ giác đều S.ABCD cạnh đáy a=4, chiều cao h=5',
  'Vẽ tứ diện vuông O.ABC với OA=4, OB=3, OC=5',
  'Vẽ đồ thị hàm số y = x^3 - 3*x và y = 2*x - 1 trên hệ trục Oxy'
];

export const AiWorkspace: React.FC<AiWorkspaceProps> = ({
  accentHex,
  activeProvider,
  onChangeProvider,
  profiles,
  prefillPrompt,
  onPrefillConsumed,
  onOpenSettings,
  onApplyDrawingPlan
}) => {
  const [mode, setMode] = useState<'chat' | 'draw'>('chat');
  const [messages, setMessages] = useState<AiChatMessage[]>([]);
  const [input, setInput] = useState<string>('');
  const [attachedImage, setAttachedImage] = useState<string | null>(null);
  const [isStreaming, setIsStreaming] = useState<boolean>(false);
  const [errorText, setErrorText] = useState<string | null>(null);

  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const endRef = useRef<HTMLDivElement | null>(null);

  const providerSpec = AI_PROVIDERS.find((p) => p.id === activeProvider) || AI_PROVIDERS[0];
  const currentProfile = profiles[activeProvider];
  const hasKey = Boolean(currentProfile?.apiKey?.trim()) || activeProvider === 'custom_openai';

  useEffect(() => {
    if (!prefillPrompt) return;
    setMode('chat');
    setInput(prefillPrompt);
    onPrefillConsumed();
  }, [prefillPrompt, onPrefillConsumed]);

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, isStreaming]);

  const handleImagePick = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      if (typeof reader.result === 'string') setAttachedImage(reader.result);
    };
    reader.readAsDataURL(file);
    e.target.value = '';
  };

  const handleSend = async (overrideText?: string) => {
    const prompt = (overrideText ?? input).trim();
    if ((!prompt && !attachedImage) || isStreaming) return;

    setErrorText(null);
    const userMsg: AiChatMessage = {
      id: `u-${Date.now()}`,
      role: 'user',
      content: prompt || 'Hãy phân tích hình ảnh bài toán này.',
      imageDataUrl: attachedImage || undefined,
      createdAt: Date.now()
    };
    const assistantId = `a-${Date.now() + 1}`;
    const assistantPlaceholder: AiChatMessage = {
      id: assistantId,
      role: 'assistant',
      content: '',
      createdAt: Date.now()
    };

    setMessages((prev) => [...prev, userMsg, assistantPlaceholder]);
    setInput('');
    const imageToSend = attachedImage || undefined;
    setAttachedImage(null);
    setIsStreaming(true);

    try {
      const fullReply = await streamAiResponse({
        providerId: activeProvider,
        profile: currentProfile,
        prompt: userMsg.content,
        drawMode: mode === 'draw',
        imageDataUrl: imageToSend,
        onToken: (text) => {
          setMessages((prev) =>
            prev.map((m) => (m.id === assistantId ? { ...m, content: text } : m))
          );
        }
      });

      const plan = extractDrawingPlan(fullReply);
      setMessages((prev) =>
        prev.map((m) => (m.id === assistantId ? { ...m, content: fullReply, plan } : m))
      );
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Lỗi không xác định khi gọi AI.';
      setErrorText(msg);
      setMessages((prev) =>
        prev.map((m) =>
          m.id === assistantId ? { ...m, content: `Không thể nhận phản hồi: ${msg}` } : m
        )
      );
    } finally {
      setIsStreaming(false);
    }
  };

  const samples = mode === 'draw' ? SAMPLE_PROMPTS_DRAW : SAMPLE_PROMPTS_CHAT;

  return (
    <div
      style={{
        width: '100%',
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        maxWidth: 880,
        margin: '0 auto',
        padding: '12px 12px 86px 12px',
        gap: 10
      }}
    >
      {/* Top Bar: Mode Switch + Provider Selector */}
      <header
        className="glass-surface"
        style={{
          borderRadius: 20,
          padding: '10px 14px',
          display: 'flex',
          flexWrap: 'wrap',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 10
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          <button
            type="button"
            className={`chip-btn glass-press${mode === 'chat' ? ' active' : ''}`}
            onClick={() => setMode('chat')}
          >
            <Sparkles size={14} />
            <span>Hỏi đáp Toán AI</span>
          </button>
          <button
            type="button"
            className={`chip-btn glass-press${mode === 'draw' ? ' active' : ''}`}
            onClick={() => setMode('draw')}
          >
            <Box size={14} />
            <span>Đọc đề & Vẽ 3D/2D</span>
          </button>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          <select
            className="glass-select"
            style={{ width: 'auto', padding: '6px 10px', fontSize: 12 }}
            value={activeProvider}
            onChange={(e) => onChangeProvider(e.target.value as AiProviderId)}
            aria-label="Chọn nhà cung cấp AI"
          >
            {AI_PROVIDERS.map((p) => (
              <option key={p.id} value={p.id} style={{ color: '#0f172a' }}>
                {p.shortTitle}
              </option>
            ))}
          </select>

          <button
            type="button"
            className="icon-btn glass-press"
            onClick={onOpenSettings}
            title="Cài đặt API Key AI"
            aria-label="Cài đặt API Key AI"
          >
            <Settings size={16} />
          </button>

          {messages.length > 0 && (
            <button
              type="button"
              className="icon-btn glass-press"
              onClick={() => setMessages([])}
              title="Xóa hội thoại"
              aria-label="Xóa hội thoại"
            >
              <Trash2 size={15} />
            </button>
          )}
        </div>
      </header>

      {/* Missing Key Notice */}
      {!hasKey && (
        <div
          className="glass-surface"
          style={{
            borderRadius: 16,
            padding: '10px 14px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 10,
            borderColor: 'rgba(245, 158, 11, 0.55)'
          }}
        >
          <span style={{ fontSize: 12 }}>
            Chưa cấu hình API Key cho <b>{providerSpec.shortTitle}</b>. Khóa chỉ lưu cục bộ trên trình duyệt của bạn.
          </span>
          <button type="button" className="chip-btn active" onClick={onOpenSettings}>
            Dán Key ngay
          </button>
        </div>
      )}

      {/* Message Stream */}
      <div
        className="glass-surface scroll-area"
        style={{
          flex: 1,
          minHeight: 0,
          borderRadius: 22,
          padding: 16,
          display: 'flex',
          flexDirection: 'column',
          gap: 12
        }}
      >
        {messages.length === 0 ? (
          <div
            style={{
              margin: 'auto',
              textAlign: 'center',
              maxWidth: 540,
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              gap: 12
            }}
          >
            <div
              style={{
                width: 48,
                height: 48,
                borderRadius: 16,
                background: `${accentHex}24`,
                border: `1px solid ${accentHex}66`,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center'
              }}
            >
              <Sparkles size={22} color={accentHex} />
            </div>
            <div style={{ fontSize: 16, fontWeight: 800 }}>
              {mode === 'draw' ? 'AI Đọc Đề & Dựng Hình Tự Động' : 'Trợ lý Toán học THPT'}
            </div>
            <div style={{ fontSize: 12.5, color: 'var(--text-secondary)', lineHeight: 1.5 }}>
              {mode === 'draw'
                ? 'Nhập hoặc tải ảnh đề bài hình không gian / hàm số. AI sẽ phân tích và tạo nút dựng thẳng sang bảng 3D hoặc 2D.'
                : `Đang dùng ${providerSpec.shortTitle} (${currentProfile.model || providerSpec.defaultModel}). Gửi câu hỏi hoặc tải ảnh bài toán để giải từng bước.`}
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 6, width: '100%', marginTop: 4 }}>
              {samples.map((s) => (
                <button
                  key={s}
                  type="button"
                  className="glass-press"
                  onClick={() => handleSend(s)}
                  style={{
                    textAlign: 'left',
                    padding: '10px 14px',
                    borderRadius: 14,
                    border: '1px solid var(--border-subtle)',
                    background: 'rgba(148,163,184,0.10)',
                    color: 'var(--text-primary)',
                    fontSize: 12.5,
                    cursor: 'pointer'
                  }}
                >
                  {s}
                </button>
              ))}
            </div>
          </div>
        ) : (
          messages.map((msg) => (
            <div
              key={msg.id}
              style={{
                alignSelf: msg.role === 'user' ? 'flex-end' : 'flex-start',
                maxWidth: '88%',
                padding: '11px 14px',
                borderRadius: 18,
                background:
                  msg.role === 'user'
                    ? `linear-gradient(135deg, ${accentHex}, var(--accent-indigo))`
                    : 'rgba(148, 163, 184, 0.14)',
                color: msg.role === 'user' ? '#ffffff' : 'var(--text-primary)',
                border: msg.role === 'user' ? 'none' : '1px solid var(--border-subtle)',
                fontSize: 13.5,
                lineHeight: 1.55,
                whiteSpace: 'pre-wrap',
                wordBreak: 'break-word'
              }}
            >
              {msg.imageDataUrl && (
                <img
                  src={msg.imageDataUrl}
                  alt="Ảnh đề bài đính kèm"
                  style={{
                    maxWidth: '100%',
                    maxHeight: 220,
                    borderRadius: 12,
                    display: 'block',
                    marginBottom: 8
                  }}
                />
              )}
              <div>{msg.content || (isStreaming ? 'Đang suy luận...' : '')}</div>

              {msg.plan && (
                <button
                  type="button"
                  onClick={() => onApplyDrawingPlan(msg.plan!)}
                  className="primary-btn glass-press"
                  style={{ marginTop: 10, minHeight: 38, fontSize: 12.5 }}
                >
                  {msg.plan.target === 'space3d' ? <Box size={15} /> : <Compass size={15} />}
                  <span>
                    Mở trên không gian {msg.plan.target === 'space3d' ? '3D' : '2D'}: {msg.plan.title}
                  </span>
                </button>
              )}
            </div>
          ))
        )}
        <div ref={endRef} />
      </div>

      {errorText && (
        <div style={{ fontSize: 12, color: '#f43f5e', padding: '0 6px' }}>{errorText}</div>
      )}

      {/* Composer */}
      <form
        className="glass-surface"
        onSubmit={(e) => {
          e.preventDefault();
          handleSend();
        }}
        style={{
          borderRadius: 20,
          padding: 10,
          display: 'flex',
          flexDirection: 'column',
          gap: 8
        }}
      >
        {attachedImage && (
          <div style={{ position: 'relative', alignSelf: 'flex-start' }}>
            <img
              src={attachedImage}
              alt="Ảnh chuẩn bị gửi"
              style={{ height: 64, borderRadius: 10, border: '1px solid var(--border-subtle)' }}
            />
            <button
              type="button"
              onClick={() => setAttachedImage(null)}
              style={{
                position: 'absolute',
                top: -6,
                right: -6,
                width: 22,
                height: 22,
                borderRadius: '50%',
                border: 'none',
                background: '#f43f5e',
                color: '#fff',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                cursor: 'pointer'
              }}
              aria-label="Xóa ảnh"
            >
              <X size={12} />
            </button>
          </div>
        )}

        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <button
            type="button"
            className="icon-btn glass-press"
            onClick={() => fileInputRef.current?.click()}
            title="Đính kèm ảnh đề toán"
            aria-label="Đính kèm ảnh đề toán"
          >
            <ImagePlus size={18} />
          </button>
          <input ref={fileInputRef} type="file" accept="image/*" style={{ display: 'none' }} onChange={handleImagePick} />

          <input
            type="text"
            className="glass-input"
            placeholder={
              mode === 'draw'
                ? 'Nhập đề bài để AI dựng hình 3D hoặc đồ thị 2D...'
                : 'Nhập câu hỏi Toán 10–11–12...'
            }
            value={input}
            onChange={(e) => setInput(e.target.value)}
            disabled={isStreaming}
          />

          <button
            type="submit"
            className="primary-btn glass-press"
            style={{ minHeight: 40, padding: '0 16px' }}
            disabled={isStreaming || (!input.trim() && !attachedImage)}
            aria-label="Gửi"
          >
            <Send size={16} />
          </button>
        </div>
      </form>
    </div>
  );
};
