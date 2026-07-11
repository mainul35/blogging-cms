import { AuthRequest, AuthResponse, Profile, UpdateProfileRequest } from '@/types/user';
import { api } from './api';

const TOKEN_KEY = 'blog_token';

function storeSession(response: AuthResponse) {
  if (typeof window !== 'undefined') {
    localStorage.setItem(TOKEN_KEY, response.token);
    // Also set a cookie so Next.js middleware can read it for route protection
    document.cookie = `token=${response.token}; path=/; max-age=86400; SameSite=Strict`;
  }
}

export const authLib = {
  login: async (credentials: AuthRequest): Promise<AuthResponse> => {
    const response = await api.post<AuthResponse>('/api/auth/login', credentials);
    storeSession(response);
    return response;
  },

  changePassword: async (currentPassword: string, newPassword: string): Promise<void> => {
    await api.put<void>('/api/admin/account/password', { currentPassword, newPassword });
  },

  getProfile: async (): Promise<Profile> => {
    return api.get<Profile>('/api/admin/account/profile');
  },

  // The backend re-issues a token here since the JWT subject is the email,
  // which may have just changed — the old token would stop matching the user.
  updateProfile: async (data: UpdateProfileRequest): Promise<AuthResponse> => {
    const response = await api.put<AuthResponse>('/api/admin/account/profile', data);
    storeSession(response);
    return response;
  },

  logout: () => {
    if (typeof window !== 'undefined') {
      localStorage.removeItem(TOKEN_KEY);
      document.cookie = 'token=; path=/; max-age=0';
    }
  },

  getToken: (): string | null => {
    if (typeof window !== 'undefined') {
      return localStorage.getItem(TOKEN_KEY);
    }
    return null;
  },

  isAuthenticated: (): boolean => !!authLib.getToken(),
};
