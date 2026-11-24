import { Scene3DSnapshot } from '../engine/geo3d';
import { Geo2DSnapshot } from '../engine/geo2d';

function toBase64Url(json: string): string {
  const bytes = new TextEncoder().encode(json);
  let binary = '';
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function fromBase64Url(encoded: string): string {
  const padded = encoded.replace(/-/g, '+').replace(/_/g, '/') + '==='.slice((encoded.length + 3) % 4);
  const binary = atob(padded);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return new TextDecoder().decode(bytes);
}

export function buildShareUrl3D(snapshot: Scene3DSnapshot): string {
  const payload = toBase64Url(JSON.stringify(snapshot));
  const base = `${window.location.origin}${window.location.pathname}`;
  return `${base}#share3d=${payload}`;
}

export function buildShareUrl2D(snapshot: Geo2DSnapshot): string {
  const payload = toBase64Url(JSON.stringify(snapshot));
  const base = `${window.location.origin}${window.location.pathname}`;
  return `${base}#share2d=${payload}`;
}

export interface ParsedShareHash {
  kind: 'space3d' | 'plane2d';
  snapshot3d?: Scene3DSnapshot;
  snapshot2d?: Geo2DSnapshot;
}

export function parseShareHashFromUrl(hash: string): ParsedShareHash | null {
  if (!hash) return null;
  const cleaned = hash.startsWith('#') ? hash.slice(1) : hash;
  try {
    if (cleaned.startsWith('share3d=')) {
      const json = fromBase64Url(cleaned.slice('share3d='.length));
      const parsed = JSON.parse(json) as Scene3DSnapshot;
      if (parsed && typeof parsed.shapeKey === 'string') {
        return { kind: 'space3d', snapshot3d: parsed };
      }
    } else if (cleaned.startsWith('share2d=')) {
      const json = fromBase64Url(cleaned.slice('share2d='.length));
      const parsed = JSON.parse(json) as Geo2DSnapshot;
      if (parsed && Array.isArray(parsed.functions)) {
        return { kind: 'plane2d', snapshot2d: parsed };
      }
    }
  } catch {
    return null;
  }
  return null;
}
