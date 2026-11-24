import React, { useEffect, useMemo, useRef, useState } from 'react';
import * as pdfjsLib from 'pdfjs-dist';
import {
  Bookmark,
  BookmarkCheck,
  ChevronLeft,
  ChevronRight,
  FileUp,
  ListTree,
  Search,
  Sparkles,
  ZoomIn,
  ZoomOut,
  X
} from 'lucide-react';
import {
  GradeId,
  normalizeVietnamese,
  PDF_CURRICULUM_TOPICS,
  PDF_DEFAULT_URL,
  PDF_TOTAL_PAGES
} from '../data/pdfToc';

pdfjsLib.GlobalWorkerOptions.workerSrc = new URL(
  'pdfjs-dist/build/pdf.worker.min.mjs',
  import.meta.url
).toString();

interface DocsWorkspaceProps {
  accentHex: string;
  onAskAiAboutPage: (prompt: string) => void;
}

const BOOKMARKS_KEY = 'tooltoan_pdf_bookmarks_v1';
const CUSTOM_URL_KEY = 'tooltoan_pdf_custom_url_v1';

export const DocsWorkspace: React.FC<DocsWorkspaceProps> = ({ accentHex, onAskAiAboutPage }) => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const renderTaskRef = useRef<pdfjsLib.RenderTask | null>(null);

  const [pdfDoc, setPdfDoc] = useState<pdfjsLib.PDFDocumentProxy | null>(null);
  const [pdfSourceUrl, setPdfSourceUrl] = useState<string>(
    () => localStorage.getItem(CUSTOM_URL_KEY) || PDF_DEFAULT_URL
  );
  const [customUrlInput, setCustomUrlInput] = useState<string>(
    () => localStorage.getItem(CUSTOM_URL_KEY) || ''
  );
  const [totalPages, setTotalPages] = useState<number>(PDF_TOTAL_PAGES);
  const [pageNum, setPageNum] = useState<number>(18);
  const [pageInput, setPageInput] = useState<string>('18');
  const [zoom, setZoom] = useState<number>(1.2);
  const [loading, setLoading] = useState<boolean>(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [tocOpen, setTocOpen] = useState<boolean>(false);
  const [selectedGrade, setSelectedGrade] = useState<GradeId | 'all'>('all');
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [bookmarks, setBookmarks] = useState<number[]>(() => {
    try {
      const raw = localStorage.getItem(BOOKMARKS_KEY);
      const parsed = raw ? JSON.parse(raw) : [];
      return Array.isArray(parsed) ? parsed.filter((n: unknown): n is number => typeof n === 'number') : [];
    } catch {
      return [];
    }
  });

  // Persist bookmarks
  useEffect(() => {
    localStorage.setItem(BOOKMARKS_KEY, JSON.stringify(bookmarks));
  }, [bookmarks]);

  // Load PDF document from URL or blob URL
  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setLoadError(null);

    const loadingTask = pdfjsLib.getDocument({
      url: pdfSourceUrl,
      disableAutoFetch: true,
      disableStream: false
    });

    loadingTask.promise
      .then((doc) => {
        if (cancelled) {
          doc.destroy();
          return;
        }
        setPdfDoc(doc);
        setTotalPages(doc.numPages);
        setPageNum((prev) => Math.min(Math.max(1, prev), doc.numPages));
        setLoading(false);
      })
      .catch(() => {
        if (cancelled) return;
        setPdfDoc(null);
        setLoading(false);
        setLoadError(
          'Không tìm thấy tệp PDF mặc định trên máy chủ (có thể tệp > 25 MB nên cần host trên Cloudflare R2). Bạn có thể mở file PDF từ máy hoặc dán link R2 bên dưới.'
        );
      });

    return () => {
      cancelled = true;
      loadingTask.destroy().catch(() => undefined);
    };
  }, [pdfSourceUrl]);

  // Render active PDF page onto canvas
  useEffect(() => {
    if (!pdfDoc || !canvasRef.current) return;
    let cancelled = false;

    const renderPage = async () => {
      try {
        if (renderTaskRef.current) {
          renderTaskRef.current.cancel();
          renderTaskRef.current = null;
        }
        const page = await pdfDoc.getPage(pageNum);
        if (cancelled) return;

        const canvas = canvasRef.current;
        if (!canvas) return;
        const ctx = canvas.getContext('2d');
        if (!ctx) return;

        const dpr = Math.min(window.devicePixelRatio || 1, 2);
        const viewport = page.getViewport({ scale: zoom });
        canvas.width = Math.floor(viewport.width * dpr);
        canvas.height = Math.floor(viewport.height * dpr);
        canvas.style.width = `${Math.floor(viewport.width)}px`;
        canvas.style.height = `${Math.floor(viewport.height)}px`;

        const task = page.render({
          canvasContext: ctx,
          viewport,
          transform: dpr !== 1 ? [dpr, 0, 0, dpr, 0, 0] : undefined
        });
        renderTaskRef.current = task;
        await task.promise;
      } catch {
        // Ignore cancelled render task when switching pages quickly
      }
    };

    renderPage();
    setPageInput(String(pageNum));

    return () => {
      cancelled = true;
      if (renderTaskRef.current) {
        renderTaskRef.current.cancel();
        renderTaskRef.current = null;
      }
    };
  }, [pdfDoc, pageNum, zoom]);

  const currentTopic = useMemo(
    () => PDF_CURRICULUM_TOPICS.find((t) => pageNum >= t.startPage && pageNum <= t.endPage) || null,
    [pageNum]
  );

  const filteredTopics = useMemo(() => {
    const q = normalizeVietnamese(searchQuery.trim());
    return PDF_CURRICULUM_TOPICS.filter((topic) => {
      if (selectedGrade !== 'all' && topic.grade !== selectedGrade) return false;
      if (!q) return true;
      const hay = normalizeVietnamese(`${topic.indexLabel} ${topic.title} ${topic.summary}`);
      return hay.includes(q);
    });
  }, [selectedGrade, searchQuery]);

  const isBookmarked = bookmarks.includes(pageNum);

  const toggleBookmark = () => {
    setBookmarks((prev) =>
      prev.includes(pageNum) ? prev.filter((p) => p !== pageNum) : [...prev, pageNum].sort((a, b) => a - b)
    );
  };

  const jumpToPage = (target: number) => {
    const clamped = Math.min(Math.max(1, target), totalPages);
    setPageNum(clamped);
    setPageInput(String(clamped));
  };

  const handleLocalFile = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const blobUrl = URL.createObjectURL(file);
    setPdfSourceUrl(blobUrl);
    e.target.value = '';
  };

  const handleApplyCustomUrl = (e: React.FormEvent) => {
    e.preventDefault();
    const cleaned = customUrlInput.trim();
    if (!cleaned) return;
    localStorage.setItem(CUSTOM_URL_KEY, cleaned);
    setPdfSourceUrl(cleaned);
  };

  const handleAskAi = () => {
    const topicHint = currentTopic
      ? `chuyên đề "${currentTopic.title}" (${currentTopic.indexLabel})`
      : 'nội dung Toán THPT';
    onAskAiAboutPage(
      `Tôi đang học trang ${pageNum} thuộc ${topicHint} trong Sổ tay Toán 10-11-12. Hãy tóm tắt các công thức trọng tâm, dạng bài thường gặp và cho 1 ví dụ minh họa có lời giải chi tiết.`
    );
  };

  return (
    <div
      style={{
        position: 'relative',
        width: '100%',
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        padding: '12px 12px 86px 12px',
        gap: 10
      }}
    >
      {/* Top Toolbar */}
      <header
        className="glass-surface"
        style={{
          borderRadius: 20,
          padding: '8px 12px',
          display: 'flex',
          flexWrap: 'wrap',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 8,
          zIndex: 10
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <button
            type="button"
            onClick={() => setTocOpen((v) => !v)}
            className={`chip-btn glass-press${tocOpen ? ' active' : ''}`}
          >
            <ListTree size={15} />
            <span>Mục lục 10–11–12</span>
          </button>

          <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
            <button
              type="button"
              className="icon-btn glass-press"
              onClick={() => jumpToPage(pageNum - 1)}
              disabled={pageNum <= 1}
              aria-label="Trang trước"
            >
              <ChevronLeft size={17} />
            </button>
            <form
              onSubmit={(e) => {
                e.preventDefault();
                const parsed = parseInt(pageInput, 10);
                if (!Number.isNaN(parsed)) jumpToPage(parsed);
              }}
              style={{ display: 'flex', alignItems: 'center', gap: 4 }}
            >
              <input
                type="text"
                className="glass-input mono"
                style={{ width: 54, padding: '5px 8px', textAlign: 'center', fontSize: 12 }}
                value={pageInput}
                onChange={(e) => setPageInput(e.target.value)}
                aria-label="Số trang"
              />
              <span className="mono" style={{ fontSize: 12, color: 'var(--text-secondary)' }}>
                / {totalPages}
              </span>
            </form>
            <button
              type="button"
              className="icon-btn glass-press"
              onClick={() => jumpToPage(pageNum + 1)}
              disabled={pageNum >= totalPages}
              aria-label="Trang sau"
            >
              <ChevronRight size={17} />
            </button>
          </div>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          <button
            type="button"
            className="icon-btn glass-press"
            onClick={() => setZoom((z) => Math.max(0.6, +(z - 0.15).toFixed(2)))}
            title="Thu nhỏ"
            aria-label="Thu nhỏ"
          >
            <ZoomOut size={16} />
          </button>
          <span className="mono desktop-only" style={{ fontSize: 11.5, minWidth: 42, textAlign: 'center' }}>
            {Math.round(zoom * 100)}%
          </span>
          <button
            type="button"
            className="icon-btn glass-press"
            onClick={() => setZoom((z) => Math.min(2.6, +(z + 0.15).toFixed(2)))}
            title="Phóng to"
            aria-label="Phóng to"
          >
            <ZoomIn size={16} />
          </button>

          <button
            type="button"
            className="icon-btn glass-press"
            onClick={toggleBookmark}
            title={isBookmarked ? 'Bỏ đánh dấu trang' : 'Đánh dấu trang này'}
            aria-label="Đánh dấu trang"
          >
            {isBookmarked ? <BookmarkCheck size={17} color="#f59e0b" /> : <Bookmark size={17} />}
          </button>

          <button
            type="button"
            className="icon-btn glass-press"
            onClick={() => fileInputRef.current?.click()}
            title="Mở tệp PDF từ máy"
            aria-label="Mở tệp PDF từ máy"
          >
            <FileUp size={16} />
          </button>
          <input ref={fileInputRef} type="file" accept="application/pdf" style={{ display: 'none' }} onChange={handleLocalFile} />

          <button type="button" className="primary-btn glass-press" style={{ minHeight: 36, padding: '6px 12px', fontSize: 12 }} onClick={handleAskAi}>
            <Sparkles size={14} />
            <span>Hỏi AI trang {pageNum}</span>
          </button>
        </div>
      </header>

      {/* Current Topic Context Pill */}
      {currentTopic && (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            padding: '0 4px',
            fontSize: 12,
            color: 'var(--text-secondary)'
          }}
        >
          <span>
            <b style={{ color: accentHex }}>{currentTopic.indexLabel}:</b> {currentTopic.title}
          </span>
          <span className="mono desktop-only">
            Tr. {currentTopic.startPage}–{currentTopic.endPage}
          </span>
        </div>
      )}

      {/* Main Reader Container */}
      <div style={{ position: 'relative', flex: 1, minHeight: 0, display: 'flex', gap: 10 }}>
        {/* TOC Side Panel (Overlay on mobile, split pane when opened) */}
        {tocOpen && (
          <aside
            className="glass-surface scroll-area"
            style={{
              width: 'min(330px, 100%)',
              borderRadius: 20,
              padding: 14,
              display: 'flex',
              flexDirection: 'column',
              gap: 10,
              flexShrink: 0,
              zIndex: 12
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ fontSize: 13.5, fontWeight: 800 }}>Mục lục Toán 10–11–12</span>
              <button type="button" className="icon-btn" style={{ width: 28, height: 28 }} onClick={() => setTocOpen(false)}>
                <X size={15} />
              </button>
            </div>

            <div style={{ position: 'relative' }}>
              <Search
                size={14}
                style={{ position: 'absolute', left: 10, top: '50%', transform: 'translateY(-50%)', color: 'var(--text-secondary)' }}
              />
              <input
                type="text"
                className="glass-input"
                style={{ paddingLeft: 32, fontSize: 12 }}
                placeholder="Tìm không dấu (vd: tich phan, oxyz)..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
              />
            </div>

            <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
              {[
                { id: 'all' as const, label: 'Tất cả' },
                { id: 'grade_10' as const, label: 'Lớp 10' },
                { id: 'grade_11' as const, label: 'Lớp 11' },
                { id: 'grade_12' as const, label: 'Lớp 12' }
              ].map((g) => (
                <button
                  key={g.id}
                  type="button"
                  className={`chip-btn${selectedGrade === g.id ? ' active' : ''}`}
                  style={{ minHeight: 28, padding: '3px 10px', fontSize: 11 }}
                  onClick={() => setSelectedGrade(g.id)}
                >
                  {g.label}
                </button>
              ))}
            </div>

            {bookmarks.length > 0 && (
              <div>
                <div style={{ fontSize: 11, fontWeight: 700, color: '#f59e0b', marginBottom: 4 }}>
                  Trang đã đánh dấu ({bookmarks.length})
                </div>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 5 }}>
                  {bookmarks.map((p) => (
                    <button
                      key={p}
                      type="button"
                      className="chip-btn"
                      style={{ minHeight: 26, padding: '2px 9px', fontSize: 11 }}
                      onClick={() => jumpToPage(p)}
                    >
                      Tr. {p}
                    </button>
                  ))}
                </div>
              </div>
            )}

            <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              {filteredTopics.map((topic) => {
                const isActive = pageNum >= topic.startPage && pageNum <= topic.endPage;
                return (
                  <button
                    key={topic.id}
                    type="button"
                    onClick={() => {
                      jumpToPage(topic.startPage);
                      if (window.innerWidth < 760) setTocOpen(false);
                    }}
                    className="glass-press"
                    style={{
                      textAlign: 'left',
                      padding: '9px 11px',
                      borderRadius: 14,
                      cursor: 'pointer',
                      border: `1px solid ${isActive ? accentHex : 'var(--border-subtle)'}`,
                      background: isActive ? `${accentHex}22` : 'rgba(148,163,184,0.08)',
                      color: 'var(--text-primary)'
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11, color: accentHex, fontWeight: 700 }}>
                      <span>{topic.indexLabel}</span>
                      <span className="mono">Tr. {topic.startPage}–{topic.endPage}</span>
                    </div>
                    <div style={{ fontSize: 12.5, fontWeight: 700, marginTop: 2 }}>{topic.title}</div>
                    <div style={{ fontSize: 11, color: 'var(--text-secondary)', marginTop: 2 }}>{topic.summary}</div>
                  </button>
                );
              })}
            </div>
          </aside>
        )}

        {/* PDF Canvas Viewport */}
        <div
          className="glass-surface scroll-area"
          style={{
            flex: 1,
            borderRadius: 22,
            padding: 16,
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: pdfDoc ? 'flex-start' : 'center'
          }}
        >
          {loading && <div style={{ fontSize: 13, color: 'var(--text-secondary)' }}>Đang tải tài liệu PDF...</div>}

          {!loading && loadError && (
            <div
              style={{
                maxWidth: 460,
                textAlign: 'center',
                display: 'flex',
                flexDirection: 'column',
                gap: 12,
                padding: 16
              }}
            >
              <div style={{ fontSize: 15, fontWeight: 800 }}>Sổ tay Toán 10–11–12</div>
              <div style={{ fontSize: 12.5, color: 'var(--text-secondary)', lineHeight: 1.5 }}>{loadError}</div>

              <button
                type="button"
                className="primary-btn glass-press"
                onClick={() => fileInputRef.current?.click()}
              >
                <FileUp size={16} />
                <span>Mở file PDF trực tiếp từ thiết bị</span>
              </button>

              <form onSubmit={handleApplyCustomUrl} style={{ display: 'flex', gap: 6 }}>
                <input
                  type="url"
                  className="glass-input"
                  placeholder="Hoặc dán link PDF (Cloudflare R2 / CDN)..."
                  value={customUrlInput}
                  onChange={(e) => setCustomUrlInput(e.target.value)}
                />
                <button type="submit" className="chip-btn active">
                  Tải
                </button>
              </form>
            </div>
          )}

          {!loading && pdfDoc && (
            <canvas
              ref={canvasRef}
              style={{
                borderRadius: 12,
                boxShadow: '0 10px 30px rgba(0, 0, 0, 0.32)',
                background: '#ffffff',
                maxWidth: '100%'
              }}
            />
          )}
        </div>
      </div>
    </div>
  );
};
