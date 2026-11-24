import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Bookmark,
  BookOpen,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  ChevronUp,
  Link2,
  Maximize2,
  Minimize2,
  Moon,
  Search,
  Sparkles,
  Sun,
  Upload,
  ZoomIn,
  ZoomOut
} from 'lucide-react';
import * as pdfjsLib from 'pdfjs-dist';
import pdfWorkerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url';
import {
  PDF_BOOK_SUBTITLE,
  PDF_BOOK_TITLE,
  PDF_GRADES,
  PDF_TOC_TOPICS,
  PDF_TOTAL_PAGES,
  PdfGradeId,
  searchPdfTopics,
  sectionLabelForPage
} from '../data/pdfToc';

pdfjsLib.GlobalWorkerOptions.workerSrc = pdfWorkerUrl;

const BOOKMARKS_KEY = 'tooltoan_pdf_bookmarks_v1';
const PDF_URL_KEY = 'tooltoan_pdf_custom_url_v1';
const LAST_PAGE_KEY = 'tooltoan_pdf_last_page_v1';
const LAST_TAB_KEY = 'tooltoan_pdf_last_tab_v1';
const NIGHT_FILTER_KEY = 'tooltoan_pdf_night_filter_v1';

type DocsTab = PdfGradeId | 'bookmarks';

interface DocsWorkspaceProps {
  accentHex: string;
  externalPage: number | null;
  onExternalPageConsumed: () => void;
  onAskAiAboutPage: (prompt: string) => void;
}

export const DocsWorkspace: React.FC<DocsWorkspaceProps> = ({
  accentHex,
  externalPage,
  onExternalPageConsumed,
  onAskAiAboutPage
}) => {
  const [pdfDoc, setPdfDoc] = useState<pdfjsLib.PDFDocumentProxy | null>(null);
  const [numPages, setNumPages] = useState<number>(PDF_TOTAL_PAGES);
  const [currentPage, setCurrentPage] = useState<number>(() => {
    const saved = parseInt(localStorage.getItem(LAST_PAGE_KEY) || '1', 10);
    return Number.isFinite(saved) && saved >= 1 && saved <= PDF_TOTAL_PAGES ? saved : 1;
  });
  const [zoom, setZoom] = useState<number>(1.2);
  const [loading, setLoading] = useState<boolean>(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [tocOpen, setTocOpen] = useState<boolean>(typeof window !== 'undefined' ? window.innerWidth >= 900 : true);
  const [activeTab, setActiveTab] = useState<DocsTab>(() => {
    const saved = localStorage.getItem(LAST_TAB_KEY) as DocsTab | null;
    if (saved && ['grade_10', 'grade_11', 'grade_12', 'bookmarks'].includes(saved)) return saved;
    return 'grade_12';
  });
  const [expandedTopics, setExpandedTopics] = useState<Record<string, boolean>>({});
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [nightFilter, setNightFilter] = useState<boolean>(() => localStorage.getItem(NIGHT_FILTER_KEY) === '1');
  const [immersiveReader, setImmersiveReader] = useState<boolean>(false);
  const [bookmarks, setBookmarks] = useState<number[]>(() => {
    try {
      const raw = localStorage.getItem(BOOKMARKS_KEY);
      return raw ? JSON.parse(raw) : [];
    } catch {
      return [];
    }
  });
  const [customUrl, setCustomUrl] = useState<string>(() => {
    return localStorage.getItem(PDF_URL_KEY) || import.meta.env.VITE_PDF_URL || '/docs/toan_10_11_12_dpad.pdf';
  });
  const [urlInput, setUrlInput] = useState<string>(customUrl);
  const [jumpInput, setJumpInput] = useState<string>(String(currentPage));

  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const renderTaskRef = useRef<pdfjsLib.RenderTask | null>(null);

  useEffect(() => {
    localStorage.setItem(BOOKMARKS_KEY, JSON.stringify(bookmarks));
  }, [bookmarks]);

  useEffect(() => {
    localStorage.setItem(LAST_PAGE_KEY, String(currentPage));
    setJumpInput(String(currentPage));
  }, [currentPage]);

  useEffect(() => {
    localStorage.setItem(LAST_TAB_KEY, activeTab);
  }, [activeTab]);

  useEffect(() => {
    localStorage.setItem(NIGHT_FILTER_KEY, nightFilter ? '1' : '0');
  }, [nightFilter]);

  // Handle direct PDF page jumps from AI citations
  useEffect(() => {
    if (externalPage && externalPage >= 1 && externalPage <= PDF_TOTAL_PAGES) {
      setCurrentPage(externalPage);
      onExternalPageConsumed();
    }
  }, [externalPage, onExternalPageConsumed]);

  const loadPdfFromUrl = useCallback(async (url: string) => {
    if (!url.trim()) return;
    setLoading(true);
    setErrorMsg(null);
    try {
      const task = pdfjsLib.getDocument({ url, cMapPacked: true });
      const doc = await task.promise;
      setPdfDoc(doc);
      setNumPages(doc.numPages);
      setCurrentPage((p) => Math.max(1, Math.min(doc.numPages, p)));
    } catch {
      setPdfDoc(null);
      setErrorMsg(
        'Chưa tải được file PDF trực tuyến (do PDF 29.5 MB vượt giới hạn 25 MB Cloudflare Pages). ' +
          'Bạn vẫn có thể tra cứu toàn bộ 59 chuyên đề bên trái, tải file PDF từ máy lên, hoặc dán link Cloudflare R2.'
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadPdfFromUrl(customUrl);
  }, [customUrl, loadPdfFromUrl]);

  useEffect(() => {
    if (!pdfDoc || !canvasRef.current) return;
    let cancelled = false;
    (async () => {
      try {
        const page = await pdfDoc.getPage(Math.max(1, Math.min(pdfDoc.numPages, currentPage)));
        if (cancelled || !canvasRef.current) return;
        const dpr = Math.min(window.devicePixelRatio || 1, 2);
        const viewport = page.getViewport({ scale: zoom * dpr });
        const canvas = canvasRef.current;
        canvas.width = viewport.width;
        canvas.height = viewport.height;
        canvas.style.width = `${viewport.width / dpr}px`;
        canvas.style.height = `${viewport.height / dpr}px`;
        const ctx = canvas.getContext('2d');
        if (!ctx) return;
        if (renderTaskRef.current) {
          try {
            renderTaskRef.current.cancel();
          } catch {
            // ignore
          }
        }
        const task = page.render({ canvasContext: ctx, viewport });
        renderTaskRef.current = task;
        await task.promise;
      } catch {
        // ignore cancelled render
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [pdfDoc, currentPage, zoom]);

  const handleLocalFile = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setLoading(true);
    setErrorMsg(null);
    try {
      const buf = await file.arrayBuffer();
      const doc = await pdfjsLib.getDocument({ data: buf }).promise;
      setPdfDoc(doc);
      setNumPages(doc.numPages);
      setCurrentPage((p) => Math.max(1, Math.min(doc.numPages, p)));
    } catch {
      setErrorMsg('Không đọc được file PDF đã chọn.');
    } finally {
      setLoading(false);
    }
  };

  const toggleBookmark = () => {
    setBookmarks((prev) =>
      prev.includes(currentPage) ? prev.filter((p) => p !== currentPage) : [...prev, currentPage].sort((a, b) => a - b)
    );
  };

  const goToPage = (p: number) => {
    const clamped = Math.max(1, Math.min(numPages, Math.round(p)));
    setCurrentPage(clamped);
    if (window.innerWidth < 900) setTocOpen(false);
  };

  const searchHits = useMemo(() => searchPdfTopics(searchQuery), [searchQuery]);
  const visibleGradeTopics = useMemo(
    () => (activeTab === 'bookmarks' ? [] : PDF_TOC_TOPICS.filter((t) => t.grade === activeTab)),
    [activeTab]
  );
  const currentSection = useMemo(() => sectionLabelForPage(currentPage), [currentPage]);
  const isBookmarked = bookmarks.includes(currentPage);

  return (
    <div style={{ width: '100%', height: '100%', display: 'flex', flexDirection: 'column', paddingBottom: immersiveReader ? 0 : 76 }}>
      {/* Top Toolbar */}
      {!immersiveReader && (
        <div
          className="glass-surface"
          style={{
            margin: '10px 12px 0',
            borderRadius: 18,
            padding: '8px 12px',
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            flexWrap: 'wrap',
            zIndex: 10
          }}
        >
          <button className={`chip-btn glass-press ${tocOpen ? 'active' : ''}`} onClick={() => setTocOpen((v) => !v)}>
            <BookOpen size={15} /> Mục lục (59)
          </button>

          <div style={{ flex: 1, minWidth: 160 }}>
            <div style={{ fontSize: 13, fontWeight: 800, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
              {currentSection}
            </div>
            <div style={{ fontSize: 11, color: 'var(--text-secondary)' }}>
              {PDF_BOOK_SUBTITLE}
            </div>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
            <button className="icon-btn" title="Trang trước" onClick={() => goToPage(currentPage - 1)}>
              <ChevronLeft size={16} />
            </button>
            <form
              onSubmit={(e) => {
                e.preventDefault();
                const p = parseInt(jumpInput, 10);
                if (Number.isFinite(p)) goToPage(p);
              }}
              style={{ display: 'flex', alignItems: 'center', gap: 4 }}
            >
              <input
                className="glass-input mono"
                value={jumpInput}
                onChange={(e) => setJumpInput(e.target.value)}
                style={{ width: 56, padding: '5px 8px', textAlign: 'center', fontSize: 12 }}
              />
              <span className="mono" style={{ fontSize: 11.5, color: 'var(--text-secondary)' }}>/{numPages}</span>
            </form>
            <button className="icon-btn" title="Trang sau" onClick={() => goToPage(currentPage + 1)}>
              <ChevronRight size={16} />
            </button>
          </div>

          <button className="icon-btn" title="Thu nhỏ" onClick={() => setZoom((z) => Math.max(0.6, +(z - 0.15).toFixed(2)))}>
            <ZoomOut size={16} />
          </button>
          <span className="mono desktop-only" style={{ fontSize: 11.5 }}>{Math.round(zoom * 100)}%</span>
          <button className="icon-btn" title="Phóng to" onClick={() => setZoom((z) => Math.min(2.6, +(z + 0.15).toFixed(2)))}>
            <ZoomIn size={16} />
          </button>

          <button
            className={`icon-btn ${nightFilter ? 'active' : ''}`}
            style={{ color: nightFilter ? '#fbbf24' : 'var(--text-primary)' }}
            title="Chế độ Đọc Ban Đêm (Đảo màu dịu mắt)"
            onClick={() => setNightFilter((v) => !v)}
          >
            {nightFilter ? <Sun size={16} /> : <Moon size={16} />}
          </button>

          <button
            className="icon-btn"
            style={{ color: isBookmarked ? '#f59e0b' : 'var(--text-primary)' }}
            title="Đánh dấu trang này"
            onClick={toggleBookmark}
          >
            <Bookmark size={16} fill={isBookmarked ? '#f59e0b' : 'none'} />
          </button>

          <button
            className="icon-btn"
            title="Đọc toàn màn hình"
            onClick={() => setImmersiveReader(true)}
          >
            <Maximize2 size={16} />
          </button>

          <button
            className="chip-btn active glass-press"
            onClick={() =>
              onAskAiAboutPage(
                `Hãy giải thích ngắn gọn, dễ hiểu kèm ví dụ minh họa cho phần kiến thức tại trang ${currentPage} (${currentSection}) trong tài liệu Toán THPT.`
              )
            }
          >
            <Sparkles size={14} /> Hỏi AI trang {currentPage}
          </button>
        </div>
      )}

      {/* Main Content */}
      <div style={{ flex: 1, display: 'flex', gap: 12, padding: immersiveReader ? 0 : '10px 12px 0', overflow: 'hidden', position: 'relative' }}>
        {/* Left TOC Sidebar (All 59 Hierarchical Topics) */}
        {tocOpen && !immersiveReader && (
          <aside
            className="glass-surface scroll-area"
            style={{
              width: 340,
              maxWidth: '88vw',
              borderRadius: 18,
              padding: 12,
              display: 'flex',
              flexDirection: 'column',
              gap: 10,
              flexShrink: 0
            }}
          >
            <div style={{ fontSize: 13.5, fontWeight: 800 }}>{PDF_BOOK_TITLE}</div>

            {/* Search */}
            <div style={{ position: 'relative' }}>
              <Search size={14} style={{ position: 'absolute', left: 10, top: 12, color: 'var(--text-secondary)' }} />
              <input
                className="glass-input"
                placeholder="Tìm 59 chuyên đề (vd: tich phan, thiet dien...)"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                style={{ paddingLeft: 32, fontSize: 12.5 }}
              />
            </div>

            {/* Grade Tabs */}
            {!searchQuery.trim() && (
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 4 }}>
                {PDF_GRADES.map((g) => (
                  <button
                    key={g.id}
                    className={`chip-btn ${activeTab === g.id ? 'active' : ''}`}
                    style={{ padding: '4px 6px', fontSize: 11.5 }}
                    onClick={() => setActiveTab(g.id)}
                  >
                    {g.shortLabel}
                  </button>
                ))}
                <button
                  className={`chip-btn ${activeTab === 'bookmarks' ? 'active' : ''}`}
                  style={{ padding: '4px 6px', fontSize: 11.5 }}
                  onClick={() => setActiveTab('bookmarks')}
                >
                  Lưu ({bookmarks.length})
                </button>
              </div>
            )}

            {/* Search Results */}
            {searchQuery.trim() ? (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                <div style={{ fontSize: 11.5, color: 'var(--text-secondary)' }}>
                  Tìm thấy {searchHits.length} mục khớp
                </div>
                {searchHits.map((hit, idx) => (
                  <button
                    key={`${hit.topic.id}_${idx}`}
                    onClick={() => goToPage(hit.targetPage)}
                    style={{
                      textAlign: 'left',
                      padding: '9px 10px',
                      borderRadius: 12,
                      border: `1px solid ${hit.topic.accentHex}44`,
                      background: 'rgba(148,163,184,0.08)',
                      color: 'var(--text-primary)',
                      cursor: 'pointer'
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', gap: 6 }}>
                      <span style={{ fontSize: 12.5, fontWeight: 700 }}>{hit.topic.title}</span>
                      <span className="mono" style={{ fontSize: 11, color: hit.topic.accentHex }}>Tr. {hit.targetPage}</span>
                    </div>
                    {hit.matchedSubTopic && (
                      <div style={{ fontSize: 11.5, color: accentHex, marginTop: 2 }}>↳ {hit.matchedSubTopic.title}</div>
                    )}
                    <div style={{ fontSize: 11, color: 'var(--text-secondary)', marginTop: 3 }}>{hit.topic.summary}</div>
                  </button>
                ))}
              </div>
            ) : activeTab === 'bookmarks' ? (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                {bookmarks.length === 0 ? (
                  <div style={{ fontSize: 12, color: 'var(--text-secondary)', padding: 12, textAlign: 'center' }}>
                    Chưa đánh dấu trang nào. Nhấn biểu tượng Bookmark ở thanh trên để lưu trang quan trọng.
                  </div>
                ) : (
                  bookmarks.map((p) => (
                    <button
                      key={p}
                      onClick={() => goToPage(p)}
                      style={{
                        textAlign: 'left',
                        padding: '9px 10px',
                        borderRadius: 12,
                        border: '1px solid var(--border-subtle)',
                        background: currentPage === p ? `${accentHex}22` : 'rgba(148,163,184,0.08)',
                        color: 'var(--text-primary)',
                        cursor: 'pointer',
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center'
                      }}
                    >
                      <span style={{ fontSize: 12.5, fontWeight: 700 }}>{sectionLabelForPage(p)}</span>
                      <span className="mono" style={{ fontSize: 11.5, color: '#f59e0b' }}>Tr. {p}</span>
                    </button>
                  ))
                )}
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
                {visibleGradeTopics.map((topic) => {
                  const isCurrent = currentPage >= topic.startPage && currentPage <= topic.endPage;
                  const isExpanded = !!expandedTopics[topic.id];
                  return (
                    <div
                      key={topic.id}
                      style={{
                        borderRadius: 12,
                        border: `1px solid ${isCurrent ? topic.accentHex : 'var(--border-subtle)'}`,
                        background: isCurrent ? `${topic.accentHex}18` : 'rgba(148,163,184,0.06)',
                        overflow: 'hidden'
                      }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', padding: '8px 10px', gap: 6 }}>
                        <button
                          onClick={() => goToPage(topic.startPage)}
                          style={{
                            flex: 1,
                            textAlign: 'left',
                            background: 'transparent',
                            border: 'none',
                            color: 'var(--text-primary)',
                            cursor: 'pointer',
                            padding: 0
                          }}
                        >
                          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 6 }}>
                            <span style={{ fontSize: 10.5, fontWeight: 700, color: topic.accentHex }}>
                              {topic.indexLabel}
                            </span>
                            <span className="mono" style={{ fontSize: 10.5, color: 'var(--text-secondary)' }}>
                              Tr. {topic.startPage}{topic.endPage > topic.startPage ? `–${topic.endPage}` : ''}
                            </span>
                          </div>
                          <div style={{ fontSize: 12.5, fontWeight: 700, marginTop: 2 }}>{topic.title}</div>
                          <div style={{ fontSize: 11, color: 'var(--text-secondary)', marginTop: 2, lineHeight: 1.4 }}>
                            {topic.summary}
                          </div>
                        </button>
                        {topic.subTopics.length > 0 && (
                          <button
                            className="icon-btn"
                            style={{ width: 28, height: 28 }}
                            title="Mở mục con"
                            onClick={() => setExpandedTopics((prev) => ({ ...prev, [topic.id]: !prev[topic.id] }))}
                          >
                            {isExpanded ? <ChevronUp size={15} /> : <ChevronDown size={15} />}
                          </button>
                        )}
                      </div>
                      {isExpanded && topic.subTopics.length > 0 && (
                        <div style={{ borderTop: '1px solid var(--border-subtle)', padding: '4px 8px 8px', display: 'flex', flexDirection: 'column', gap: 4 }}>
                          {topic.subTopics.map((sub, i) => (
                            <button
                              key={i}
                              onClick={() => goToPage(sub.page)}
                              style={{
                                display: 'flex',
                                justifyContent: 'space-between',
                                alignItems: 'center',
                                padding: '5px 8px',
                                borderRadius: 8,
                                border: 'none',
                                background: currentPage === sub.page ? `${topic.accentHex}28` : 'transparent',
                                color: 'var(--text-primary)',
                                cursor: 'pointer',
                                fontSize: 11.5,
                                textAlign: 'left'
                              }}
                            >
                              <span>↳ {sub.title}</span>
                              <span className="mono" style={{ color: topic.accentHex, fontSize: 11 }}>Tr. {sub.page}</span>
                            </button>
                          ))}
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            )}
          </aside>
        )}

        {/* Right PDF Canvas Viewport */}
        <section
          className="glass-surface"
          style={{
            flex: 1,
            borderRadius: immersiveReader ? 0 : 18,
            display: 'flex',
            flexDirection: 'column',
            overflow: 'hidden',
            position: 'relative'
          }}
        >
          {immersiveReader && (
            <button
              className="chip-btn active glass-press"
              style={{ position: 'absolute', top: 14, right: 14, zIndex: 20 }}
              onClick={() => setImmersiveReader(false)}
            >
              <Minimize2 size={14} /> Thoát toàn màn hình · Tr. {currentPage}/{numPages}
            </button>
          )}

          <div
            className="scroll-area"
            onClick={() => {
              if (pdfDoc) setImmersiveReader((v) => !v);
            }}
            style={{
              flex: 1,
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: pdfDoc ? 'flex-start' : 'center',
              padding: immersiveReader ? 10 : 18,
              cursor: pdfDoc ? 'pointer' : 'default'
            }}
          >
            {loading && (
              <div style={{ padding: 24, fontSize: 13, color: 'var(--text-secondary)' }}>
                Đang tải Sổ tay Toán học PDF...
              </div>
            )}

            {pdfDoc ? (
              <canvas
                ref={canvasRef}
                className={nightFilter ? 'pdf-night-filter' : undefined}
                style={{
                  borderRadius: 10,
                  boxShadow: '0 12px 34px rgba(0,0,0,0.45)',
                  background: '#ffffff',
                  maxWidth: '100%'
                }}
              />
            ) : (
              !loading && (
                <div
                  onClick={(e) => e.stopPropagation()}
                  style={{ maxWidth: 460, width: '100%', textAlign: 'center', display: 'flex', flexDirection: 'column', gap: 12 }}
                >
                  <BookOpen size={42} style={{ margin: '0 auto', color: accentHex }} />
                  <div style={{ fontSize: 16, fontWeight: 800 }}>{PDF_BOOK_TITLE}</div>
                  {errorMsg && (
                    <div style={{ fontSize: 12.5, color: 'var(--text-secondary)', lineHeight: 1.5 }}>{errorMsg}</div>
                  )}

                  <input
                    ref={fileInputRef}
                    type="file"
                    accept="application/pdf"
                    style={{ display: 'none' }}
                    onChange={handleLocalFile}
                  />
                  <button className="primary-btn glass-press" onClick={() => fileInputRef.current?.click()}>
                    <Upload size={16} /> Mở file PDF từ máy (toan_10_11_12_dpad.pdf)
                  </button>

                  <div style={{ display: 'flex', gap: 6, marginTop: 4 }}>
                    <input
                      className="glass-input"
                      placeholder="Hoặc dán link PDF Cloudflare R2 (https://...)"
                      value={urlInput}
                      onChange={(e) => setUrlInput(e.target.value)}
                    />
                    <button
                      className="chip-btn active"
                      onClick={() => {
                        localStorage.setItem(PDF_URL_KEY, urlInput.trim());
                        setCustomUrl(urlInput.trim());
                      }}
                    >
                      <Link2 size={14} /> Tải
                    </button>
                  </div>
                </div>
              )
            )}
          </div>

          {/* Fast Page Scrubber Bar */}
          {!immersiveReader && (
            <div
              style={{
                borderTop: '1px solid var(--border-subtle)',
                padding: '8px 14px',
                display: 'flex',
                alignItems: 'center',
                gap: 10,
                background: 'rgba(2,6,23,0.22)'
              }}
            >
              <span className="mono" style={{ fontSize: 11.5, color: accentHex, minWidth: 52 }}>
                Tr. {currentPage}
              </span>
              <input
                type="range"
                className="glass-slider"
                min={1}
                max={numPages}
                value={currentPage}
                onChange={(e) => setCurrentPage(parseInt(e.target.value, 10))}
              />
              <span className="mono" style={{ fontSize: 11.5, color: 'var(--text-secondary)', minWidth: 38, textAlign: 'right' }}>
                {numPages}
              </span>
            </div>
          )}
        </section>
      </div>
    </div>
  );
};
