'use client';

import { useEffect, useRef, useState } from 'react';
import { authLib } from '@/lib/auth';
import { uploadImage } from '@/lib/upload';

function ProfileForm() {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [email, setEmail] = useState('');
  const [name, setName] = useState('');
  const [avatarUrl, setAvatarUrl] = useState('');
  const [loadError, setLoadError] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);
  const [loading, setLoading] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    authLib.getProfile()
      .then(profile => {
        setEmail(profile.email);
        setName(profile.name ?? '');
        setAvatarUrl(profile.avatarUrl ?? '');
      })
      .catch(() => setLoadError('Failed to load profile.'))
      .finally(() => setReady(true));
  }, []);

  const handleAvatarSelected = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = ''; // allow picking the same file again later
    if (!file) return;
    setError('');
    setUploading(true);
    try {
      setAvatarUrl(await uploadImage(file));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Avatar upload failed. Please try again.');
    } finally {
      setUploading(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccess(false);
    setLoading(true);
    try {
      await authLib.updateProfile({ email, name, avatarUrl });
      setSuccess(true);
      // UserMenu (top-right avatar) fetched its own copy of the profile on
      // mount and has no reference to this component -- without this it
      // would keep showing the old name/avatar until a full page reload.
      window.dispatchEvent(new Event('profile-updated'));
    } catch {
      setError('Could not update profile. The email may already be in use.');
    } finally {
      setLoading(false);
    }
  };

  if (!ready) return <p className="text-gray-400 text-sm">Loading…</p>;
  if (loadError) return <p className="text-sm text-red-500">{loadError}</p>;

  return (
    <div className="bg-white rounded-xl shadow-sm p-6">
      <h2 className="text-lg font-semibold mb-4">Profile</h2>
      {error && <p className="mb-4 text-sm text-red-500">{error}</p>}
      {success && <p className="mb-4 text-sm text-green-600">Profile updated successfully.</p>}
      <form onSubmit={handleSubmit} className="space-y-4">
        <div className="flex items-center gap-4">
          <div className="relative w-16 h-16 shrink-0 rounded-full overflow-hidden bg-gray-100 border">
            {avatarUrl ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img src={avatarUrl} alt="Avatar preview" className="w-full h-full object-cover" />
            ) : (
              <div className="w-full h-full flex items-center justify-center text-xs text-gray-400">No image</div>
            )}
            {uploading && (
              <div className="absolute inset-0 flex items-center justify-center bg-white/70 text-[10px] font-medium text-gray-700">
                Uploading…
              </div>
            )}
          </div>
          <div className="flex gap-2">
            <button
              type="button"
              onClick={() => fileInputRef.current?.click()}
              disabled={uploading}
              className="px-3 py-1.5 text-xs font-medium border rounded-lg hover:bg-gray-50 transition-colors disabled:opacity-50"
            >
              {avatarUrl ? 'Change' : 'Upload'} photo
            </button>
            {avatarUrl && (
              <button
                type="button"
                onClick={() => setAvatarUrl('')}
                disabled={uploading}
                className="px-3 py-1.5 text-xs font-medium border border-red-300 text-red-600 rounded-lg hover:bg-red-50 transition-colors disabled:opacity-50"
              >
                Remove
              </button>
            )}
          </div>
          <input
            ref={fileInputRef}
            type="file"
            accept="image/png,image/jpeg,image/gif,image/webp"
            className="hidden"
            onChange={handleAvatarSelected}
          />
        </div>
        <div>
          <label className="block text-sm font-medium mb-1">Email</label>
          <input
            type="email"
            value={email}
            onChange={e => setEmail(e.target.value)}
            required
            className="w-full border rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-black"
          />
        </div>
        <div>
          <label className="block text-sm font-medium mb-1">Name</label>
          <input
            value={name}
            onChange={e => setName(e.target.value)}
            placeholder="Display name"
            className="w-full border rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-black"
          />
        </div>
        <button
          type="submit"
          disabled={loading || uploading}
          className="w-full py-2 bg-black text-white rounded-lg hover:bg-gray-800 disabled:opacity-50 transition-colors"
        >
          {loading ? 'Saving…' : 'Save Profile'}
        </button>
      </form>
    </div>
  );
}

function PasswordForm() {
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccess(false);

    if (newPassword !== confirmPassword) {
      setError('New password and confirmation do not match.');
      return;
    }

    setLoading(true);
    try {
      await authLib.changePassword(currentPassword, newPassword);
      setSuccess(true);
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
    } catch {
      setError('Could not change password. Check your current password and try again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="bg-white rounded-xl shadow-sm p-6">
      <h2 className="text-lg font-semibold mb-4">Change Password</h2>
      {error && <p className="mb-4 text-sm text-red-500">{error}</p>}
      {success && <p className="mb-4 text-sm text-green-600">Password updated successfully.</p>}
      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label className="block text-sm font-medium mb-1">Current Password</label>
          <input
            type="password"
            value={currentPassword}
            onChange={e => setCurrentPassword(e.target.value)}
            required
            className="w-full border rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-black"
          />
        </div>
        <div>
          <label className="block text-sm font-medium mb-1">New Password</label>
          <input
            type="password"
            value={newPassword}
            onChange={e => setNewPassword(e.target.value)}
            required
            minLength={8}
            className="w-full border rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-black"
          />
          <p className="text-xs text-gray-400 mt-1">Minimum 8 characters</p>
        </div>
        <div>
          <label className="block text-sm font-medium mb-1">Confirm New Password</label>
          <input
            type="password"
            value={confirmPassword}
            onChange={e => setConfirmPassword(e.target.value)}
            required
            minLength={8}
            className="w-full border rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-black"
          />
        </div>
        <button
          type="submit"
          disabled={loading}
          className="w-full py-2 bg-black text-white rounded-lg hover:bg-gray-800 disabled:opacity-50 transition-colors"
        >
          {loading ? 'Updating…' : 'Update Password'}
        </button>
      </form>
    </div>
  );
}

export default function ProfileSettingsPage() {
  return (
    <>
      <ProfileForm />
      <PasswordForm />
    </>
  );
}
