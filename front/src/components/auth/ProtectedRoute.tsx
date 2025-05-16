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

  console.log('[ProtectedRoute] Render. isLoading:', isLoading, 'isAuthenticated:', isAuthenticated);

  useEffect(() => {
    console.log('[ProtectedRoute] useEffect run. isLoading:', isLoading, 'isAuthenticated:', isAuthenticated);
    if (isLoading) {
      console.log('[ProtectedRoute] useEffect: Still loading, returning.');
      return;
    }
    if (!isAuthenticated) {
      console.log('[ProtectedRoute] useEffect: Not authenticated, pushing to /login.');
      router.push('/login');
    } else {
      console.log('[ProtectedRoute] useEffect: Authenticated.');
    }
  }, [isAuthenticated, isLoading, router]);

  if (isLoading) {
    console.log('[ProtectedRoute] Render: isLoading is true, rendering Loader.');
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-900 text-sky-400">
        Загрузка (isLoading)...
      </div>
    );
  }

  if (!isAuthenticated) {
    console.log('[ProtectedRoute] Render: !isAuthenticated (and not loading), rendering Loader for redirect.');
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-900 text-sky-400">
        Перенаправление...
      </div>
    );
  }
  
  console.log('[ProtectedRoute] Render: Authenticated, rendering children.');
  return <>{children}</>;
} 