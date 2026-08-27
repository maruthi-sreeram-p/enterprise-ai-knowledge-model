import axios, { AxiosError } from "axios";
import type { ApiErrorBody } from "./types";

const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

const TOKEN_STORAGE_KEY = "knowledge_base_token";
const USER_STORAGE_KEY = "knowledge_base_user";

// The token is kept in localStorage. An httpOnly cookie would be less exposed to
// cross-site scripting, but it needs the API to set cookies and to carry CSRF
// protection, which the stateless bearer-token design deliberately avoids. The
// trade-off is written down in the README rather than left implicit.
export const tokenStorage = {
  get(): string | null {
    if (typeof window === "undefined") return null;
    return window.localStorage.getItem(TOKEN_STORAGE_KEY);
  },
  set(token: string) {
    window.localStorage.setItem(TOKEN_STORAGE_KEY, token);
  },
  clear() {
    window.localStorage.removeItem(TOKEN_STORAGE_KEY);
    window.localStorage.removeItem(USER_STORAGE_KEY);
  },
};

export const userStorage = {
  get<T>(): T | null {
    if (typeof window === "undefined") return null;
    const raw = window.localStorage.getItem(USER_STORAGE_KEY);
    if (!raw) return null;
    try {
      return JSON.parse(raw) as T;
    } catch {
      return null;
    }
  },
  set(user: unknown) {
    window.localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(user));
  },
};

export const api = axios.create({
  baseURL: API_BASE_URL,
  headers: { "Content-Type": "application/json" },
});

api.interceptors.request.use((config) => {
  const token = tokenStorage.get();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// A 401 means the token is gone, expired, or the account was disabled. Clearing
// it and sending the user to the login page is the only useful response, and
// doing it here means no individual screen has to handle it.
api.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    const isAuthEndpoint = error.config?.url?.startsWith("/api/auth/");
    if (error.response?.status === 401 && !isAuthEndpoint) {
      tokenStorage.clear();
      if (typeof window !== "undefined" && window.location.pathname !== "/login") {
        window.location.href = "/login";
      }
    }
    return Promise.reject(error);
  },
);

/**
 * Turns any failure into a message worth showing.
 *
 * The API always returns the same error shape, so its `message` is used when
 * present. A network failure has no response at all and gets its own wording,
 * because "an error occurred" would leave the user unable to tell a server
 * problem from a disconnected laptop.
 */
export function errorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const body = error.response?.data as ApiErrorBody | undefined;

    if (body?.fieldErrors) {
      const messages = Object.values(body.fieldErrors).flat();
      if (messages.length > 0) return messages.join(" ");
    }
    if (body?.message) return body.message;
    if (!error.response) {
      return "Could not reach the server. Check that the backend is running.";
    }
    return `Request failed with status ${error.response.status}.`;
  }
  return "Something went wrong. Please try again.";
}
