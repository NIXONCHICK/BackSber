"use client";

import { useEffect, ReactNode } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/contexts/AuthContext';

interface ProtectedRouteProps {
  children: ReactNode;
}

export default function ProtectedRoute({ children }: ProtectedRouteProps) {
  const { isAuthenticated, isLoading, isLoggingOut } = useAuth();
  const router = useRouter();

  console.log('[ProtectedRoute] Render. isLoading:', isLoading, 'isAuthenticated:', isAuthenticated, 'isLoggingOut:', isLoggingOut);

  useEffect(() => {
    console.log('[ProtectedRoute] useEffect. isLoading:', isLoading, 'isAuthenticated:', isAuthenticated, 'isLoggingOut:', isLoggingOut);
    if (isLoading || isLoggingOut) {
      console.log('[ProtectedRoute] useEffect: isLoading or isLoggingOut is true, returning (no redirect from here).');
      return;
    }
    if (!isAuthenticated) {
      console.log('[ProtectedRoute] useEffect: Not authenticated (and not loading/logging out), pushing to /login.');
      router.push('/login');
    }
  }, [isAuthenticated, isLoading, isLoggingOut, router]);

  if (isLoading) {
    console.log('[ProtectedRoute] Render: isLoading is true, rendering Loader.');
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-900 text-sky-400">
        Загрузка...
      </div>
    );
  }

  if (isAuthenticated || isLoggingOut) {
    console.log('[ProtectedRoute] Render: Authenticated OR isLoggingOut is true, rendering children.');
    return <>{children}</>;
  }

  console.log('[ProtectedRoute] Render: !isAuthenticated AND !isLoggingOut, returning null.');
  return null; 
} 