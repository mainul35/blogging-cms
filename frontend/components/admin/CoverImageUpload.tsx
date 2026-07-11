'use client';

import { useRef, useState } from 'react';
import { uploadImage } from '@/lib/upload';

interface CoverImageUploadProps {
  value: string;
  onChange: (url: string) => void;
}

export default function CoverImageUpload({ value, onChange }: CoverImageUploadProps) {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState('');

  const handleFileSelected = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = ''; // allow picking the same file again later
    if (!file) return;
    setError('');
    setUploading(true);
    try {
      onChange(await uploadImage(file));
    } catch {
      setError('Cover image upload failed. Please try again.');
    } finally {
      setUploading(false);
    }
  };

  return (
    <div>
      <div className="relative w-full h-48 rounded-lg overflow-hidden bg-gray-50">
        {value ? (
          // object-cover crops/stretches to fill the box, matching how the
          // cover image is displayed everywhere it's actually shown to readers.
          <img src={value} alt="Cover" className="w-full h-full object-cover" />
        ) : (
          <button
            type="button"
            onClick={() => fileInputRef.current?.click()}
            disabled={uploading}
            className="w-full h-full flex items-center justify-center border-2 border-dashed rounded-lg text-sm text-gray-500 hover:border-gray-400 hover:text-gray-700 transition-colors disabled:opacity-50"
          >
            Click to upload a cover image
          </button>
        )}

        {uploading && (
          <div className="absolute inset-0 flex items-center justify-center bg-white/70 text-sm font-medium text-gray-700">
            Uploading…
          </div>
        )}

        {value && !uploading && (
          <div className="absolute top-2 right-2 flex gap-2">
            <button
              type="button"
              onClick={() => fileInputRef.current?.click()}
              className="px-3 py-1.5 text-xs font-medium bg-white/90 border rounded-lg hover:bg-white transition-colors"
            >
              Change
            </button>
            <button
              type="button"
              onClick={() => onChange('')}
              className="px-3 py-1.5 text-xs font-medium bg-white/90 border border-red-300 text-red-600 rounded-lg hover:bg-red-50 transition-colors"
            >
              Remove
            </button>
          </div>
        )}
      </div>

      {error && <p className="mt-1 text-sm text-red-500">{error}</p>}

      <input
        ref={fileInputRef}
        type="file"
        accept="image/png,image/jpeg,image/gif,image/webp"
        className="hidden"
        onChange={handleFileSelected}
      />
    </div>
  );
}
