'use client';

import { useRef, useState } from 'react';

type Handle = 'nw' | 'n' | 'ne' | 'e' | 'se' | 's' | 'sw' | 'w';

const HANDLES: Handle[] = ['nw', 'n', 'ne', 'e', 'se', 's', 'sw', 'w'];
const CURSORS: Record<Handle, string> = {
  nw: 'nwse-resize', n: 'ns-resize', ne: 'nesw-resize', e: 'ew-resize',
  se: 'nwse-resize', s: 'ns-resize', sw: 'nesw-resize', w: 'ew-resize',
};
const MIN_SIZE = 40;

function handlePosition(handle: Handle): React.CSSProperties {
  const edge = -5;
  switch (handle) {
    case 'nw': return { top: edge, left: edge };
    case 'n':  return { top: edge, left: '50%', transform: 'translateX(-50%)' };
    case 'ne': return { top: edge, right: edge };
    case 'e':  return { top: '50%', right: edge, transform: 'translateY(-50%)' };
    case 'se': return { bottom: edge, right: edge };
    case 's':  return { bottom: edge, left: '50%', transform: 'translateX(-50%)' };
    case 'sw': return { bottom: edge, left: edge };
    case 'w':  return { top: '50%', left: edge, transform: 'translateY(-50%)' };
  }
}

// Serializes back into the markdown source, replacing whatever the original
// image syntax was (bare <img>, one with a stale size, or legacy ![](url)).
export function replaceImageTag(
  content: string,
  id: string | undefined,
  src: string,
  alt: string | undefined,
  width: number,
  height: number
): { content: string; id: string } {
  const finalId = id || `img-${Math.random().toString(36).slice(2, 9)}`;
  const serialized = `<img src="${src}"${alt ? ` alt="${alt}"` : ''} width="${width}" height="${height}" id="${finalId}" />`;

  const replaceTagAt = (markerIndex: number): string | null => {
    const tagStart = content.lastIndexOf('<img', markerIndex);
    if (tagStart === -1) return null;
    const selfCloseEnd = content.indexOf('/>', markerIndex);
    const closeEnd = content.indexOf('>', markerIndex);
    const tagEnd = selfCloseEnd !== -1 && (closeEnd === -1 || selfCloseEnd <= closeEnd)
      ? selfCloseEnd + 2
      : closeEnd + 1;
    if (tagEnd <= tagStart) return null;
    return content.slice(0, tagStart) + serialized + content.slice(tagEnd);
  };

  if (id) {
    const idIdx = content.indexOf(`id="${id}"`);
    if (idIdx !== -1) {
      const replaced = replaceTagAt(idIdx);
      if (replaced !== null) return { content: replaced, id };
    }
  }

  // Legacy plain markdown image, never resized before.
  const markdownSyntax = `![${alt ?? ''}](${src})`;
  const mdIdx = content.indexOf(markdownSyntax);
  if (mdIdx !== -1) {
    const content2 = content.slice(0, mdIdx) + serialized + content.slice(mdIdx + markdownSyntax.length);
    return { content: content2, id: finalId };
  }

  // Bare <img src="..."> without an id yet.
  const srcIdx = content.indexOf(`src="${src}"`);
  if (srcIdx !== -1) {
    const replaced = replaceTagAt(srcIdx);
    if (replaced !== null) return { content: replaced, id: finalId };
  }

  return { content, id: finalId };
}

interface ResizableImageProps {
  src?: string;
  alt?: string;
  width?: string | number;
  height?: string | number;
  id?: string;
  content: string;
  onContentChange: (value: string) => void;
}

// rehype-sanitize prefixes all `id` attributes with "user-content-" as a DOM-clobbering
// protection (borrowed from GitHub's sanitize rules) — the markdown source stores the
// bare id, so it must be stripped back off before matching against `content`.
const CLOBBER_PREFIX = 'user-content-';
function bareId(id?: string): string | undefined {
  return id?.startsWith(CLOBBER_PREFIX) ? id.slice(CLOBBER_PREFIX.length) : id;
}

export default function ResizableImage({ src, alt, width, height, id: rawId, content, onContentChange }: ResizableImageProps) {
  const id = bareId(rawId);
  const imgRef = useRef<HTMLImageElement>(null);
  const [size, setSize] = useState<{ width: number; height: number } | null>(
    width && height ? { width: Number(width), height: Number(height) } : null
  );
  const liveSize = useRef(size);
  const drag = useRef<{
    handle: Handle; startX: number; startY: number; startWidth: number; startHeight: number; aspect: number;
  } | null>(null);

  const onDrag = (e: MouseEvent) => {
    const d = drag.current;
    if (!d) return;
    const dx = e.clientX - d.startX;
    const dy = e.clientY - d.startY;

    let newWidth = d.startWidth;
    let newHeight = d.startHeight;
    if (d.handle.includes('e')) newWidth = d.startWidth + dx;
    if (d.handle.includes('w')) newWidth = d.startWidth - dx;
    if (d.handle.includes('s')) newHeight = d.startHeight + dy;
    if (d.handle.includes('n')) newHeight = d.startHeight - dy;

    const isCorner = d.handle.length === 2;
    if (isCorner) {
      if (Math.abs(dx) > Math.abs(dy)) newHeight = newWidth / d.aspect;
      else newWidth = newHeight * d.aspect;
    }

    newWidth = Math.max(MIN_SIZE, Math.round(newWidth));
    newHeight = Math.max(MIN_SIZE, Math.round(newHeight));

    liveSize.current = { width: newWidth, height: newHeight };
    setSize(liveSize.current);
  };

  const endDrag = () => {
    window.removeEventListener('mousemove', onDrag);
    window.removeEventListener('mouseup', endDrag);
    drag.current = null;
    const finalSize = liveSize.current;
    if (!finalSize || !src) return;
    const { content: newContent } = replaceImageTag(content, id, src, alt, finalSize.width, finalSize.height);
    onContentChange(newContent);
  };

  const startDrag = (handle: Handle) => (e: React.MouseEvent) => {
    e.preventDefault();
    const rect = imgRef.current?.getBoundingClientRect();
    if (!rect) return;
    drag.current = {
      handle,
      startX: e.clientX,
      startY: e.clientY,
      startWidth: rect.width,
      startHeight: rect.height,
      aspect: rect.width / rect.height,
    };
    window.addEventListener('mousemove', onDrag);
    window.addEventListener('mouseup', endDrag);
  };

  return (
    <span
      style={
        size
          ? { position: 'relative', display: 'inline-block', width: size.width, height: size.height }
          : { position: 'relative', display: 'inline-block' }
      }
    >
      <img
        ref={imgRef}
        src={src}
        alt={alt}
        style={
          size
            ? { width: '100%', height: '100%', display: 'block' }
            : { maxWidth: '100%', height: 'auto', display: 'block' }
        }
      />
      {HANDLES.map(handle => (
        // A <span>, not a <div> — markdown wraps a lone image in a <p>, and
        // block-level children there triggers a hydration error.
        <span
          key={handle}
          onMouseDown={startDrag(handle)}
          style={{
            position: 'absolute',
            ...handlePosition(handle),
            width: 10,
            height: 10,
            cursor: CURSORS[handle],
            background: 'white',
            border: '1px solid #3b82f6',
            borderRadius: 2,
          }}
        />
      ))}
    </span>
  );
}
