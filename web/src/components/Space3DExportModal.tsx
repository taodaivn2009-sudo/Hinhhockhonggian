import React, { useRef, useState } from 'react';
import {
  Box,
  Check,
  Download,
  FileCode2,
  FolderOpen,
  Image as ImageIcon,
  Link2,
  Save,
  Trash2,
  Upload,
  X
} from 'lucide-react';
import { Scene3DSnapshot } from '../engine/geo3d';

export interface SavedProject3D {
  id: string;
  name: string;
  updatedAt: number;
  snapshot: Scene3DSnapshot;
}

interface Space3DExportModalProps {
  open: boolean;
  onClose: () => void;
  shapeTitle: string;
  transparentPng: boolean;
  onChangeTransparentPng: (v: boolean) => void;
  onExportPng: () => void;
  onExportSvg: () => void;
  onExportObj: () => void;
  onExportJson: () => void;
  onImportJson: (text: string) => void;
  onCopyShareLink: () => void;
  savedProjects: SavedProject3D[];
  onSaveProject: (name: string) => void;
  onLoadProject: (proj: SavedProject3D) => void;
  onDeleteProject: (id: string) => void;
}

export const Space3DExportModal: React.FC<Space3DExportModalProps> = ({
  open,
  onClose,
  shapeTitle,
  transparentPng,
  onChangeTransparentPng,
  onExportPng,
  onExportSvg,
  onExportObj,
  onExportJson,
  onImportJson,
  onCopyShareLink,
  savedProjects,
  onSaveProject,
  onLoadProject,
  onDeleteProject
}) => {
  const [slotName, setSlotName] = useState('');
  const [copiedLink, setCopiedLink] = useState(false);
  const fileRef = useRef<HTMLInputElement | null>(null);

  if (!open) return null;

  const handleSave = () => {
    const clean = slotName.trim() || `${shapeTitle} (${new Date().toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })})`;
    onSaveProject(clean);
    setSlotName('');
  };

  const handleCopyLink = () => {
    onCopyShareLink();
    setCopiedLink(true);
    window.setTimeout(() => setCopiedLink(false), 2000);
  };

  return (
    <div className="studio-modal-backdrop" onClick={onClose}>
      <div className="glass-surface studio-modal-card" onClick={(e) => e.stopPropagation()}>
        <div className="studio-inspector-header">
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <Save size={17} color="var(--accent)" />
            <span style={{ fontWeight: 800, fontSize: 15 }}>Lưu trữ, Chia sẻ & Xuất File 3D</span>
          </div>
          <button type="button" className="icon-btn" onClick={onClose} aria-label="Đóng">
            <X size={18} />
          </button>
        </div>

        <div className="studio-inspector-body scroll-area">
          {/* 1-Click Share Link */}
          <div className="studio-section-card">
            <div className="studio-section-title">
              <span>Chia sẻ liên kết trực tiếp</span>
            </div>
            <button type="button" className="primary-btn glass-press" onClick={handleCopyLink} style={{ width: '100%' }}>
              {copiedLink ? <Check size={16} /> : <Link2 size={16} />}
              {copiedLink ? 'Đã sao chép liên kết 3D!' : 'Sao chép link chia sẻ hình này'}
            </button>
          </div>

          {/* Export Files */}
          <div className="studio-section-card">
            <div className="studio-section-title">
              <span>Xuất hình ảnh & Mô hình 3D</span>
            </div>
            <label className="studio-switch-row">
              <span>Xuất ảnh PNG nền trong suốt (Chèn Word/Slide)</span>
              <input
                type="checkbox"
                checked={transparentPng}
                onChange={(e) => onChangeTransparentPng(e.target.checked)}
              />
            </label>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
              <button type="button" className="chip-btn glass-press" onClick={onExportPng}>
                <ImageIcon size={15} /> Ảnh .PNG
              </button>
              <button type="button" className="chip-btn glass-press" onClick={onExportSvg}>
                <FileCode2 size={15} /> Vector .SVG
              </button>
              <button type="button" className="chip-btn glass-press" onClick={onExportObj}>
                <Box size={15} /> Mô hình .OBJ
              </button>
              <button type="button" className="chip-btn glass-press" onClick={onExportJson}>
                <Download size={15} /> Dự án .JSON
              </button>
            </div>
            <button
              type="button"
              className="chip-btn glass-press"
              onClick={() => fileRef.current?.click()}
              style={{ width: '100%' }}
            >
              <Upload size={15} /> Nhập tệp dự án .JSON từ máy
            </button>
            <input
              ref={fileRef}
              type="file"
              accept=".json,application/json"
              style={{ display: 'none' }}
              onChange={(e) => {
                const f = e.target.files?.[0];
                if (!f) return;
                const reader = new FileReader();
                reader.onload = () => {
                  if (typeof reader.result === 'string') {
                    onImportJson(reader.result);
                    onClose();
                  }
                };
                reader.readAsText(f);
                e.target.value = '';
              }}
            />
          </div>

          {/* Saved Project Slots */}
          <div className="studio-section-card">
            <div className="studio-section-title">
              <span>Bản vẽ đã lưu trên trình duyệt ({savedProjects.length})</span>
            </div>
            <div style={{ display: 'flex', gap: 6 }}>
              <input
                className="glass-input"
                placeholder={`Tên bản vẽ (vd: ${shapeTitle})`}
                value={slotName}
                onChange={(e) => setSlotName(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && handleSave()}
              />
              <button type="button" className="primary-btn glass-press" onClick={handleSave} style={{ flexShrink: 0 }}>
                <Save size={15} /> Lưu
              </button>
            </div>
            {savedProjects.length === 0 ? (
              <div style={{ fontSize: 12, color: 'var(--text-secondary)', textAlign: 'center', padding: '10px 0' }}>
                Chưa có bản vẽ 3D nào được lưu.
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                {savedProjects.map((proj) => (
                  <div
                    key={proj.id}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      gap: 8,
                      padding: '8px 10px',
                      borderRadius: 12,
                      background: 'rgba(148,163,184,0.1)',
                      border: '1px solid var(--border-subtle)'
                    }}
                  >
                    <div style={{ minWidth: 0 }}>
                      <div style={{ fontSize: 13, fontWeight: 700, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        {proj.name}
                      </div>
                      <div style={{ fontSize: 11, color: 'var(--text-secondary)' }}>
                        {new Date(proj.updatedAt).toLocaleString('vi-VN')}
                      </div>
                    </div>
                    <div style={{ display: 'flex', gap: 4, flexShrink: 0 }}>
                      <button
                        type="button"
                        className="chip-btn"
                        style={{ minHeight: 30, padding: '4px 10px' }}
                        onClick={() => {
                          onLoadProject(proj);
                          onClose();
                        }}
                      >
                        <FolderOpen size={13} /> Mở
                      </button>
                      <button
                        type="button"
                        className="icon-btn"
                        style={{ width: 30, height: 30, color: '#f43f5e' }}
                        onClick={() => onDeleteProject(proj.id)}
                        aria-label="Xóa"
                      >
                        <Trash2 size={14} />
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
