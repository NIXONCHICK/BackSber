"use client";

import { createContext, useContext, useState, useEffect, ReactNode, Dispatch, SetStateAction } from 'react';
import { useRouter } from 'next/navigation';

// Тип для данных пользователя, которые мы получаем от бэкенда
interface User {
  id: number;
  email: string;
  role: 'STUDENT' | 'ELDER'; 
}

interface AuthState {
  token: string | null;
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean; // Для отслеживания первоначальной загрузки токена
  isLoggingOut: boolean; // Новое состояние для отслеживания процесса выхода
}

interface AuthContextType extends AuthState {
  login: (token: string, userData: User) => void;
  logout: () => void;
  setAuthState: Dispatch<SetStateAction<AuthState>>; // Для более гибкого управления состоянием при необходимости
  setIsLoggingOut: (status: boolean) => void; // Новая функция
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

interface AuthProviderProps {
  children: ReactNode;
}

const AUTH_TOKEN_KEY = 'authToken';
const USER_DATA_KEY = 'userData';

export const AuthProvider = ({ children }: AuthProviderProps) => {
  const [authState, setAuthState] = useState<AuthState>({
    token: null,
    user: null,
    isAuthenticated: false,
    isLoading: true, // Начинаем с true, пока не проверим localStorage
    isLoggingOut: false, // Инициализируем новое состояние
  });
  const router = useRouter();

  useEffect(() => {
    // При монтировании компонента пытаемся загрузить токен и данные пользователя из localStorage
    try {
      const storedToken = localStorage.getItem(AUTH_TOKEN_KEY);
      const storedUserDataString = localStorage.getItem(USER_DATA_KEY);
      
      if (storedToken && storedUserDataString) {
        const storedUser = JSON.parse(storedUserDataString) as User;
        setAuthState(prev => ({ // Используем prev, чтобы не затереть isLoggingOut если он был изменен до завершения этого useEffect
          ...prev,
          token: storedToken,
          user: storedUser,
          isAuthenticated: true,
          isLoading: false,
        }));
      } else {
        setAuthState(prev => ({ ...prev, isLoading: false }));
      }
    } catch (error) {
      console.error("Error loading auth data from localStorage:", error);
      localStorage.removeItem(AUTH_TOKEN_KEY);
      localStorage.removeItem(USER_DATA_KEY);
      setAuthState(prev => ({ ...prev, isLoading: false, token: null, user: null, isAuthenticated: false }));
    }
  }, []);

  const login = (token: string, userData: User) => {
    try {
      localStorage.setItem(AUTH_TOKEN_KEY, token);
      localStorage.setItem(USER_DATA_KEY, JSON.stringify(userData));
      setAuthState(prev => ({ // При логине сбрасываем isLoggingOut
        ...prev,
        token,
        user: userData,
        isAuthenticated: true,
        isLoading: false,
        isLoggingOut: false, 
      }));
    } catch (error) {
      console.error("Error saving auth data to localStorage:", error);
    }
  };

  const logout = () => {
    try {
      localStorage.removeItem(AUTH_TOKEN_KEY);
      localStorage.removeItem(USER_DATA_KEY);
      // isLoggingOut устанавливается в true из Header.tsx до вызова этой функции
      // Здесь мы просто сбрасываем аутентификацию
      setAuthState(prev => ({
        ...prev, // Сохраняем isLoggingOut который был установлен ранее
        token: null,
        user: null,
        isAuthenticated: false,
        isLoading: false, // Загрузка завершена
      }));
    } catch (error) {
      console.error("Error removing auth data from localStorage:", error);
    }
  };

  const setIsLoggingOut = (status: boolean) => {
    setAuthState(prev => ({ ...prev, isLoggingOut: status }));
  };

  return (
    <AuthContext.Provider value={{ ...authState, login, logout, setAuthState, setIsLoggingOut }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}; 