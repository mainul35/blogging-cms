export type UserRole = 'ADMIN' | 'AUTHOR' | 'VIEWER';

export interface User {
  id: number;
  username: string;
  email: string;
  role: UserRole;
}

export interface AuthRequest {
  email: string;
  password: string;
}

export interface AuthResponse {
  token: string;
  email: string;
  role: UserRole;
  name?: string;
  avatarUrl?: string;
}

export interface Profile {
  email: string;
  name?: string;
  avatarUrl?: string;
  role: UserRole;
}

export interface UpdateProfileRequest {
  email: string;
  name?: string;
  avatarUrl?: string;
}

export interface ForgotPasswordRequest {
  email: string;
}

export interface ResetPasswordRequest {
  token: string;
  newPassword: string;
}
