import React, { createContext, useContext, useState, useEffect, useCallback, ReactNode } from 'react';
import { UserResponse, LoginRequest, RegisterRequest } from '../api/types';
import { authApi } from '../api/authApi';
import { userApi } from '../api/userApi';
import { authEvents } from './authEvents';
import {
  saveAccessToken,
  getAccessToken,
  saveRefreshToken,
  getRefreshToken,
  clearTokens,
} from './authStorage';

export interface AuthContextValue {
  user: UserResponse | null;
  accessToken: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (credentials: LoginRequest) => Promise<void>;
  register: (data: RegisterRequest) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export const AuthProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<UserResponse | null>(null);
  const [accessToken, setAccessToken] = useState<string | null>(getAccessToken());
  const [isLoading, setIsLoading] = useState<boolean>(true);

  const logout = useCallback(() => {
    const refreshToken = getRefreshToken();
    if (refreshToken) {
      authApi.logout(refreshToken);
    }
    clearTokens();
    setUser(null);
    setAccessToken(null);
  }, []);

  // Initialize session from stored token and listen for global 401 expiration events
  useEffect(() => {
    let isMounted = true;

    async function restoreSession() {
      const storedToken = getAccessToken();
      if (!storedToken) {
        if (isMounted) setIsLoading(false);
        return;
      }

      try {
        const currentUser = await userApi.getCurrentUser();
        if (isMounted) {
          setUser(currentUser);
          setAccessToken(storedToken);
        }
      } catch (err) {
        console.warn('Session restoration failed, clearing token:', err);
        clearTokens();
        if (isMounted) {
          setUser(null);
          setAccessToken(null);
        }
      } finally {
        if (isMounted) {
          setIsLoading(false);
        }
      }
    }

    // Subscribe to decoupled 401 session expiration event
    const unsubscribe = authEvents.onUnauthorized(() => {
      logout();
    });

    restoreSession();

    return () => {
      isMounted = false;
      unsubscribe();
    };
  }, [logout]);

  const login = async (credentials: LoginRequest): Promise<void> => {
    const response = await authApi.login(credentials);
    saveAccessToken(response.accessToken);
    if (response.refreshToken) {
      saveRefreshToken(response.refreshToken);
    }
    setAccessToken(response.accessToken);
    setUser(response.user);
  };

  const register = async (data: RegisterRequest): Promise<void> => {
    const response = await authApi.register(data);
    saveAccessToken(response.accessToken);
    if (response.refreshToken) {
      saveRefreshToken(response.refreshToken);
    }
    setAccessToken(response.accessToken);
    setUser(response.user);
  };

  const isAuthenticated = Boolean(user && accessToken);

  return (
    <AuthContext.Provider
      value={{
        user,
        accessToken,
        isAuthenticated,
        isLoading,
        login,
        register,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
