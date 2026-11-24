import React from 'react';
import {
  Box,
  Compass,
  BookOpen,
  Sparkles,
  Settings,
  ChevronUp
} from 'lucide-react';
import { MathWorkspace } from '../types/app';

interface FloatingNavBarProps {
  active: MathWorkspace;
  onSelect: (tab: MathWorkspace) => void;
}

const NAV_ITEMS: { id: MathWorkspace; label: string; icon: React.ComponentType<{ size?: number; strokeWidth?: number }> }[] = [
  { id: 'space3d', label: '3D', icon: Box },
  { id: 'plane2d', label: '2D', icon: Compass },
  { id: 'docs', label: 'Tài liệu', icon: BookOpen },
  { id: 'ai', label: 'AI', icon: Sparkles },
  { id: 'settings', label: 'Cài đặt', icon: Settings }
];

export const FloatingNavBar: React.FC<FloatingNavBarProps> = ({ active, onSelect }) => {
  const activeIdx = Math.max(0, NAV_ITEMS.findIndex((item) => item.id === active));

  return (
    <nav className="floating-nav-wrap" aria-label="Điều hướng chính">
      <div className="floating-nav-pill glass-surface">
        <div
          className="floating-nav-indicator"
          style={{ transform: `translateX(${activeIdx * 100}%)` }}
          aria-hidden="true"
        />
        {NAV_ITEMS.map((item) => {
          const Icon = item.icon;
          const isActive = item.id === active;
          return (
            <button
              key={item.id}
              type="button"
              onClick={() => onSelect(item.id)}
              className={`floating-nav-btn glass-press${isActive ? ' active' : ''}`}
              aria-current={isActive ? 'page' : undefined}
            >
              <Icon size={19} strokeWidth={isActive ? 2.4 : 1.9} />
              <span>{item.label}</span>
            </button>
          );
        })}
      </div>
    </nav>
  );
};

export const ImmersiveRestorePill: React.FC<{ onRestore: () => void }> = ({ onRestore }) => (
  <div
    style={{
      position: 'fixed',
      left: '50%',
      bottom: 'max(14px, env(safe-area-inset-bottom))',
      transform: 'translateX(-50%)',
      zIndex: 45
    }}
  >
    <button
      type="button"
      onClick={onRestore}
      className="glass-surface glass-press"
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 6,
        padding: '8px 16px',
        borderRadius: 999,
        color: 'var(--text-primary)',
        fontSize: 12,
        fontWeight: 700,
        cursor: 'pointer'
      }}
    >
      <ChevronUp size={16} />
      <span>Hiện thanh công cụ</span>
    </button>
  </div>
);
