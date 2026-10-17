import type { ProblemDetail, TokenResponse } from './types';

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';
let accessToken: string | null = null;
let refreshPromise: Promise<boolean> | null = null;

export function setAccessToken(token: string | null) {
  accessToken = token;
}

export function hasAccessToken() {
  return accessToken !== null;
}

async function refreshAccessToken() {
  if (!refreshPromise)
    refreshPromise = fetch(`${API_BASE}/api/auth/refresh`, {
      method: 'POST',
      credentials: 'include'
    })
      .then(async (response) => {
        if (!response.ok) return false;
        const token = (await response.json()) as TokenResponse;
        setAccessToken(token.accessToken);
        return true;
      })
      .finally(() => {
        refreshPromise = null;
      });
  return refreshPromise;
}

export async function apiFetch<T>(path: string, init: RequestInit = {}, retry = true): Promise<T> {
  const headers = new Headers(init.headers);
  if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`);
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
  const response = await fetch(`${API_BASE}${path}`, { ...init, headers, credentials: 'include' });
  if (response.status === 401 && retry && (await refreshAccessToken()))
    return apiFetch<T>(path, init, false);
  if (!response.ok) {
    const problem = (await response.json().catch(() => ({}))) as ProblemDetail;
    throw new Error(problem.detail ?? `Request failed (${response.status})`);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export async function establishSession(
  path: '/api/auth/login' | '/api/auth/register',
  body: unknown
) {
  const token = await apiFetch<TokenResponse>(
    path,
    { method: 'POST', body: JSON.stringify(body) },
    false
  );
  setAccessToken(token.accessToken);
  return token;
}

export async function restoreSession() {
  return hasAccessToken() || refreshAccessToken();
}
export async function logout() {
  try {
    await apiFetch<void>('/api/auth/logout', { method: 'POST' });
  } finally {
    setAccessToken(null);
  }
}
