"use client";

import { useEffect, ReactNode } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/contexts/AuthContext';

interface ProtectedRouteProps {
  children: ReactNode;
}

export default function ProtectedRoute({ children }: ProtectedRouteProps) {
  const { isAuthenticated, isLoading } = useAuth();
  const router = useRouter();

  useEffect(() => {
    // Не выполняем никаких действий, пока идет первоначальная загрузка данных аутентификации
    if (isLoading) {
      return;
    }

    // Если загрузка завершена и пользователь не аутентифицирован, перенаправляем на страницу входа
    if (!isAuthenticated) {
      router.push('/login');
    }
  }, [isAuthenticated, isLoading, router]);

  // Если идет загрузка, можно показать заглушку/лоадер
  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-900 text-sky-400">
        Загрузка...
      </div>
    );
  }

  // Если пользователь аутентифицирован, отображаем дочерний компонент (защищенную страницу)
  if (isAuthenticated) {
    return <>{children}</>;
  }

  // Если пользователь не аутентифицирован и загрузка завершена (но еще не произошел редирект из useEffect),
  // можно ничего не отображать или показать заглушку, пока происходит редирект.
  // useEffect сработает и выполнит редирект.
  return null; 
} 