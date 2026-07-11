'use client';

import { useEffect, useRef, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import { Bold, Italic, Strikethrough, Code, Code2, Quote, List, ListOrdered, Link2, Image as ImageIcon } from 'lucide-react';
import { uploadImage } from '@/lib/upload';
import { remarkPlugins, rehypePlugins } from '@/lib/markdown';
import ResizableImage from './ResizableImage';

interface EditorProps {
  value: string;
  onChange: (value: string) => void;
}

interface Selection {
  text: string;
  selectionStart: number;
  selectionEnd: number;
}

// Wraps the selection in markers (e.g. **bold**). With no selection, inserts
// a placeholder between the markers and selects it, so typing replaces it.
function wrapTool(before: string, after: string, placeholder: string) {
  return (value: string, start: number, end: number): Selection => {
    const selected = value.slice(start, end);
    const inner = selected || placeholder;
    const text = value.slice(0, start) + before + inner + after + value.slice(end);
    const selectionStart = start + before.length;
    return { text, selectionStart, selectionEnd: selectionStart + inner.length };
  };
}

// Prefixes every line touched by the selection (e.g. "- " for a bullet list).
// With no selection, prefixes just the current line and drops the cursor after it.
function linePrefixTool(prefix: string) {
  return (value: string, start: number, end: number): Selection => {
    const lineStart = value.lastIndexOf('\n', start - 1) + 1;
    const lineEnd = value.indexOf('\n', end) === -1 ? value.length : value.indexOf('\n', end);

    const block = value.slice(lineStart, lineEnd);
    const lines = block.split('\n').map(line => prefix + line);
    const text = value.slice(0, lineStart) + lines.join('\n') + value.slice(lineEnd);

    const lineIndexOf = (pos: number) => block.slice(0, pos - lineStart).split('\n').length - 1;
    const selectionStart = start + prefix.length * (lineIndexOf(start) + 1);
    const selectionEnd = end + prefix.length * (lineIndexOf(end) + 1);
    return { text, selectionStart, selectionEnd };
  };
}

// Sets the block-level style of every line touched by the selection: a heading
// level 1-6, or 0 for plain paragraph. Replaces any existing heading prefix
// rather than stacking a new one on top of it.
function setHeadingLevel(level: number) {
  return (value: string, start: number, end: number): Selection => {
    const lineStart = value.lastIndexOf('\n', start - 1) + 1;
    const lineEnd = value.indexOf('\n', end) === -1 ? value.length : value.indexOf('\n', end);

    const prefix = level > 0 ? '#'.repeat(level) + ' ' : '';
    const newBlock = value
      .slice(lineStart, lineEnd)
      .split('\n')
      .map(line => prefix + line.replace(/^#{1,6}\s+/, ''))
      .join('\n');

    const text = value.slice(0, lineStart) + newBlock + value.slice(lineEnd);
    return { text, selectionStart: lineStart, selectionEnd: lineStart + newBlock.length };
  };
}

const ALIGN_WRAPPER = /^<div align="[a-z]+">\n\n([\s\S]*)\n\n<\/div>$/;

// Wraps the touched block in an aligned <div> (or unwraps back to plain text for
// 'left', the default). Existing wrapper is replaced rather than nested, so
// switching alignment twice doesn't produce <div><div>...</div></div>.
function setAlignment(align: 'left' | 'center' | 'right' | 'justify') {
  return (value: string, start: number, end: number): Selection => {
    const lineStart = value.lastIndexOf('\n', start - 1) + 1;
    const lineEnd = value.indexOf('\n', end) === -1 ? value.length : value.indexOf('\n', end);

    const block = value.slice(lineStart, lineEnd);
    const existing = block.match(ALIGN_WRAPPER);
    const inner = existing ? existing[1] : block;

    const newBlock = align === 'left' ? inner : `<div align="${align}">\n\n${inner}\n\n</div>`;
    const text = value.slice(0, lineStart) + newBlock + value.slice(lineEnd);
    return { text, selectionStart: lineStart, selectionEnd: lineStart + newBlock.length };
  };
}

function linkTool(value: string, start: number, end: number): Selection {
  const selected = value.slice(start, end) || 'link text';
  const before = `[${selected}](`;
  const text = value.slice(0, start) + before + ')' + value.slice(end);
  const cursor = start + before.length;
  return { text, selectionStart: cursor, selectionEnd: cursor };
}

// Inserts an image at the given position, replacing any selection, and moves
// the cursor just past it so writing can continue. Uses a raw <img> tag (with
// a stable id) rather than plain markdown syntax so it can later be resized
// in the preview — see ResizableImage.tsx.
function insertImageTool(url: string) {
  return (value: string, start: number, end: number): Selection => {
    const id = `img-${Math.random().toString(36).slice(2, 9)}`;
    const markdown = `<img src="${url}" id="${id}" />`;
    const text = value.slice(0, start) + markdown + value.slice(end);
    const cursor = start + markdown.length;
    return { text, selectionStart: cursor, selectionEnd: cursor };
  };
}

const TOOLS = [
  { icon: Bold, title: 'Bold', apply: wrapTool('**', '**', 'bold text') },
  { icon: Italic, title: 'Italic', apply: wrapTool('*', '*', 'italic text') },
  { icon: Strikethrough, title: 'Strikethrough', apply: wrapTool('~~', '~~', 'strikethrough text') },
  { icon: Code, title: 'Inline code', apply: wrapTool('`', '`', 'code') },
  { icon: Code2, title: 'Code block', apply: wrapTool('```\n', '\n```', 'code') },
  { icon: Quote, title: 'Quote', apply: linePrefixTool('> ') },
  { icon: List, title: 'Bullet list', apply: linePrefixTool('- ') },
  { icon: ListOrdered, title: 'Numbered list', apply: linePrefixTool('1. ') },
  { icon: Link2, title: 'Link', apply: linkTool },
];

export default function Editor({ value, onChange }: EditorProps) {
  const [tab, setTab] = useState<'write' | 'preview'>('write');
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState('');
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const pendingSelection = useRef<{ start: number; end: number } | null>(null);
  // Captured before the native file-picker dialog opens (or on paste), since
  // focus/selection on the textarea isn't reliably preserved across it.
  const capturedSelection = useRef<{ start: number; end: number } | null>(null);

  // Restore focus + selection after `value` is re-rendered into the textarea,
  // so the cursor ends up where the applied tool left it, not at the end.
  useEffect(() => {
    const pending = pendingSelection.current;
    if (pending && textareaRef.current) {
      textareaRef.current.focus();
      textareaRef.current.setSelectionRange(pending.start, pending.end);
      pendingSelection.current = null;
    }
  }, [value]);

  const commit = (result: Selection) => {
    pendingSelection.current = { start: result.selectionStart, end: result.selectionEnd };
    onChange(result.text);
  };

  const runTool = (apply: (value: string, start: number, end: number) => Selection) => {
    const textarea = textareaRef.current;
    if (!textarea) return;
    const { selectionStart, selectionEnd } = textarea;
    commit(apply(value, selectionStart, selectionEnd));
  };

  const uploadAndInsert = async (file: File, at: { start: number; end: number } | null) => {
    setUploadError('');
    setUploading(true);
    try {
      const url = await uploadImage(file);
      const { start, end } = at ?? { start: value.length, end: value.length };
      commit(insertImageTool(url)(value, start, end));
    } catch {
      setUploadError('Image upload failed. Please try again.');
    } finally {
      setUploading(false);
    }
  };

  // Selects can't use onMouseDown->preventDefault like the toolbar buttons do
  // (that would block the dropdown from opening), so the textarea's selection
  // has to be captured on mousedown — the last instant it's guaranteed to still
  // have focus — and used later once the actual change (onChange) fires.
  const captureSelection = () => {
    const textarea = textareaRef.current;
    capturedSelection.current = textarea
      ? { start: textarea.selectionStart, end: textarea.selectionEnd }
      : null;
  };

  const runToolAtCapturedSelection = (apply: (value: string, start: number, end: number) => Selection) => {
    const sel = capturedSelection.current ?? { start: value.length, end: value.length };
    commit(apply(value, sel.start, sel.end));
  };

  const openImagePicker = () => {
    captureSelection();
    fileInputRef.current?.click();
  };

  const handleFileSelected = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = ''; // allow picking the same file again later
    if (file) uploadAndInsert(file, capturedSelection.current);
  };

  const handlePaste = (e: React.ClipboardEvent<HTMLTextAreaElement>) => {
    const item = [...e.clipboardData.items].find(i => i.type.startsWith('image/'));
    if (!item) return; // let normal text paste proceed
    e.preventDefault();
    const file = item.getAsFile();
    const textarea = textareaRef.current;
    const selection = textarea ? { start: textarea.selectionStart, end: textarea.selectionEnd } : null;
    if (file) uploadAndInsert(file, selection);
  };

  return (
    <div className="border rounded-lg overflow-hidden">
      <div className="flex flex-wrap items-center justify-between gap-y-1 border-b bg-gray-50 px-1 pt-1">
        <div className="flex gap-1">
          {(['write', 'preview'] as const).map(t => (
            <button
              key={t}
              type="button"
              onClick={() => setTab(t)}
              className={`px-4 py-1.5 text-sm font-medium rounded-t transition-colors capitalize ${
                tab === t
                  ? 'bg-white border border-b-white text-black'
                  : 'text-gray-500 hover:text-gray-700'
              }`}
            >
              {t}
            </button>
          ))}
        </div>

        {tab === 'write' && (
          <div className="flex flex-wrap items-center gap-1 pb-1 pr-1">
            <select
              title="Text style"
              defaultValue=""
              onMouseDown={captureSelection}
              onChange={e => {
                runToolAtCapturedSelection(setHeadingLevel(Number(e.target.value)));
                e.target.value = '';
              }}
              className="text-xs border rounded px-1.5 py-1.5 text-gray-600 bg-white hover:border-gray-400 focus:outline-none transition-colors"
            >
              <option value="" disabled>Style</option>
              <option value="0">Paragraph</option>
              <option value="1">Heading 1</option>
              <option value="2">Heading 2</option>
              <option value="3">Heading 3</option>
              <option value="4">Heading 4</option>
              <option value="5">Heading 5</option>
              <option value="6">Heading 6</option>
            </select>
            <select
              title="Alignment"
              defaultValue=""
              onMouseDown={captureSelection}
              onChange={e => {
                runToolAtCapturedSelection(setAlignment(e.target.value as 'left' | 'center' | 'right' | 'justify'));
                e.target.value = '';
              }}
              className="text-xs border rounded px-1.5 py-1.5 text-gray-600 bg-white hover:border-gray-400 focus:outline-none transition-colors"
            >
              <option value="" disabled>Align</option>
              <option value="left">Left</option>
              <option value="center">Center</option>
              <option value="right">Right</option>
              <option value="justify">Justify</option>
            </select>
            <div className="w-px self-stretch bg-gray-200 mx-0.5" />
            {TOOLS.map(({ icon: Icon, title, apply }) => (
              <button
                key={title}
                type="button"
                title={title}
                onMouseDown={e => e.preventDefault()}
                onClick={() => runTool(apply)}
                className="p-1.5 rounded text-gray-500 hover:text-black hover:bg-gray-200 transition-colors"
              >
                <Icon size={16} />
              </button>
            ))}
            <button
              type="button"
              title="Upload image"
              disabled={uploading}
              onMouseDown={e => e.preventDefault()}
              onClick={openImagePicker}
              className="p-1.5 rounded text-gray-500 hover:text-black hover:bg-gray-200 transition-colors disabled:opacity-50"
            >
              <ImageIcon size={16} />
            </button>
            <input
              ref={fileInputRef}
              type="file"
              accept="image/png,image/jpeg,image/gif,image/webp"
              className="hidden"
              onChange={handleFileSelected}
            />
          </div>
        )}
      </div>

      {(uploading || uploadError) && (
        <div className={`px-4 py-1.5 text-xs border-b ${uploadError ? 'text-red-500 bg-red-50' : 'text-gray-500 bg-gray-50'}`}>
          {uploadError || 'Uploading image…'}
        </div>
      )}

      {tab === 'write' ? (
        <textarea
          ref={textareaRef}
          value={value}
          onChange={e => onChange(e.target.value)}
          onPaste={handlePaste}
          placeholder="Write your post content in Markdown… (you can also paste an image)"
          className="w-full h-80 p-4 font-mono text-sm resize-none focus:outline-none"
        />
      ) : (
        <div className="h-80 overflow-auto p-4 prose prose-sm max-w-none
          prose-code:bg-gray-100 prose-code:px-1 prose-code:rounded
          prose-code:before:content-none prose-code:after:content-none
          prose-pre:bg-gray-900 prose-pre:text-gray-100">
          <ReactMarkdown
            remarkPlugins={remarkPlugins}
            rehypePlugins={rehypePlugins}
            components={{
              img: ({ src, alt, width, height, id }) => (
                <ResizableImage
                  src={src}
                  alt={alt}
                  width={width}
                  height={height}
                  id={id}
                  content={value}
                  onContentChange={onChange}
                />
              ),
            }}
          >
            {value || '_Nothing to preview yet._'}
          </ReactMarkdown>
        </div>
      )}
    </div>
  );
}
