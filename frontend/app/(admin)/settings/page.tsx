'use client';

import { useEffect, useState } from 'react';
import { authLib } from '@/lib/auth';

function ProfileForm() {
  const [email, setEmail] = useState('');
  const [name, setName] = useState('');
  const [avatarUrl, setAvatarUrl] = useState('');
  const [loadError, setLoadError] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);
  const [loading, setLoading] = useState(false);
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

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccess(false);
    setLoading(true);
    try {
      await authLib.updateProfile({ email, name, avatarUrl });
      setSuccess(true);
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
        {avatarUrl && (
          // eslint-disable-next-line @next/next/no-img-element
          <img src={avatarUrl} alt="Avatar preview" className="w-16 h-16 rounded-full object-cover border" />
        )}
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
        <div>
          <label className="block text-sm font-medium mb-1">Avatar URL</label>
          <input
            value={avatarUrl}
            onChange={e => setAvatarUrl(e.target.value)}
            placeholder="https://example.com/avatar.jpg"
            className="w-full border rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-black"
          />
        </div>
        <button
          type="submit"
          disabled={loading}
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

export default function AdminSettingsPage() {
  return (
    <div className="max-w-md space-y-6">
      <h1 className="text-2xl font-bold">Settings</h1>
      <ProfileForm />
      <PasswordForm />
    </div>
  );
}
