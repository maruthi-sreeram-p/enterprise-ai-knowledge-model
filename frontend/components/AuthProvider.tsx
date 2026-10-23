"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from "react";
import { useRouter } from "next/navigation";
import { api, tokenStorage, userStorage } from "../lib/api";
import type { AuthResponse, Role, User } from "../lib/types";

interface AuthState {
  user: User | null;
  organizationName: string | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (
    organizationName: string,
    name: string,
    email: string,
    password: string,
  ) => Promise<void>;
  logout: () => void;
  hasRole: (...roles: Role[]) => boolean;
}

const AuthContext = createContext<AuthState | null>(null);

const ORGANIZATION_STORAGE_KEY = "knowledge_base_organization";

/**
 * Where to go after signing in: the page the user was sent away from, if any.
 * Only same-site paths are honoured, so a crafted `?next=` cannot bounce someone
 * to another origin.
 */
function postLoginPath(): string {
  const next = new URLSearchParams(window.location.search).get("next");
  if (next && next.startsWith("/") && !next.startsWith("//")) {
    return next;
  }
  return "/";
}

/**
 * Holds the signed-in user for the whole app.
 *
 * The user object is cached in localStorage purely so the first paint after a
 * reload is not a spinner. It is treated as a hint, not as truth: the token is
 * verified on the server for every request, and `/api/auth/me` re-reads the
 * real user on mount.
 */
export default function AuthProvider({
  children,
}: {
  children: React.ReactNode;
}) {
  const router = useRouter();
  const [user, setUser] = useState<User | null>(null);
  const [organizationName, setOrganizationName] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    // With no token there is nothing to verify. Every state update below happens
    // in a promise callback, never synchronously in the effect body.
    const session = tokenStorage.get()
      ? api.get<User>("/api/auth/me")
      : Promise.resolve(null);

    session
      .then((response) => {
        if (!response) return;
        setUser(response.data);
        setOrganizationName(
          window.localStorage.getItem(ORGANIZATION_STORAGE_KEY),
        );
        userStorage.set(response.data);
      })
      .catch(() => {
        // The interceptor already redirects on a 401. Anything else here means
        // the session cannot be trusted, so it is dropped.
        tokenStorage.clear();
        setUser(null);
      })
      .finally(() => setLoading(false));
  }, []);

  const applySession = useCallback((data: AuthResponse) => {
    tokenStorage.set(data.accessToken);
    userStorage.set(data.user);
    window.localStorage.setItem(
      ORGANIZATION_STORAGE_KEY,
      data.organizationName,
    );
    setUser(data.user);
    setOrganizationName(data.organizationName);
  }, []);

  const login = useCallback(
    async (email: string, password: string) => {
      const response = await api.post<AuthResponse>("/api/auth/login", {
        email,
        password,
      });
      applySession(response.data);
      router.push(postLoginPath());
    },
    [applySession, router],
  );

  const register = useCallback(
    async (
      organizationName: string,
      name: string,
      email: string,
      password: string,
    ) => {
      const response = await api.post<AuthResponse>("/api/auth/register", {
        organizationName,
        name,
        email,
        password,
      });
      applySession(response.data);
      router.push("/");
    },
    [applySession, router],
  );

  const logout = useCallback(() => {
    tokenStorage.clear();
    window.localStorage.removeItem(ORGANIZATION_STORAGE_KEY);
    setUser(null);
    setOrganizationName(null);
    router.push("/welcome");
  }, [router]);

  const hasRole = useCallback(
    (...roles: Role[]) => (user ? roles.includes(user.role) : false),
    [user],
  );

  const value = useMemo<AuthState>(
    () => ({
      user,
      organizationName,
      loading,
      login,
      register,
      logout,
      hasRole,
    }),
    [user, organizationName, loading, login, register, logout, hasRole],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used inside AuthProvider");
  }
  return context;
}
