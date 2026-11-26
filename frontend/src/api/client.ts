import axios, { AxiosError } from 'axios';
import type { ApiErrorPayload } from '../types/api';

const TOKEN_KEY = 'docintel_access_token';
const baseURL = import.meta.env.VITE_API_BASE_URL || '/api/v1';

export const apiClient = axios.create({
  baseURL,
  timeout: 120000,
});

apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiErrorPayload>) => {
    if (error.response?.status === 401 && !error.config?.url?.includes('/auth/login')) {
      localStorage.removeItem(TOKEN_KEY);
      window.dispatchEvent(new Event('auth:unauthorized'));
    }
    return Promise.reject(error);
  }
);

export function getStoredToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setStoredToken(token: string | null): void {
  if (token) {
    localStorage.setItem(TOKEN_KEY, token);
  } else {
    localStorage.removeItem(TOKEN_KEY);
  }
}

export function extractApiErrorMessage(err: unknown, fallback = 'An unexpected error occurred'): string {
  if (axios.isAxiosError<ApiErrorPayload>(err)) {
    const data = err.response?.data;
    if (data?.validationErrors) {
      const firstError = Object.values(data.validationErrors)[0];
      if (firstError) return firstError;
    }
    if (data?.message) return data.message;
    if (err.code === 'ECONNABORTED') return 'Request timed out while waiting for the server or AI model.';
    if (!err.response) return 'Cannot reach backend server. Please verify the API is running.';
  }
  return fallback;
}
