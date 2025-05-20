"use client";

import Link from 'next/link';
import { useAuth } from '@/contexts/AuthContext';
import { useRouter, usePathname } from 'next/navigation';

export default function Header() {
  const { isAuthenticated, user, logout, isLoading, setIsLoggingOut } = useAuth();
  const router = useRouter();
  const pathname = usePathname();

  const noHeaderPaths = ['/login', '/register'];

  if (noHeaderPaths.includes(pathname)) {
    return null;
  }

  const handleLogout = () => {
    if (setIsLoggingOut) {
        setIsLoggingOut(true);
    }
    router.push('/login');
    logout();
  };

  return (
    <header className="bg-slate-800 text-white shadow-md w-full">
      {/* Навигация с отступами, но без container mx-auto */}
      <nav className="px-6 py-3 flex justify-between items-center">
        <Link href="/" className="text-xl font-bold hover:text-sky-400 transition-colors">
          DeadlineMaster
        </Link>
        <div className="flex items-center space-x-4">
          {/* <Link href="/courses" className="hover:text-sky-400 transition-colors">
            Предметы
          </Link> */}
          {!isLoading && (
            <>
              {isAuthenticated && user ? (
                <>
                  <span className="text-sm text-slate-300 mr-3">{user.email}</span>
                  {/* <Link href="/profile" className="text-sm hover:text-sky-400 transition-colors">
                    Профиль
                  </Link> */}
                  <button
                    onClick={handleLogout}
                    className="bg-sky-600 hover:bg-sky-700 text-white py-2 px-4 rounded-md transition-colors text-sm"
                  >
                    Выйти
                  </button>
                </>
              ) : (
                <>
                  <Link href="/login" className="hover:text-sky-400 transition-colors">
                    Войти
                  </Link>
                  <Link
                    href="/register"
                    className="bg-sky-600 hover:bg-sky-700 text-white py-2 px-3 rounded-md transition-colors text-sm"
                  >
                    Регистрация
                  </Link>
                </>
              )}
            </>
          )}
           {isLoading && <div className="text-sm text-slate-400">Загрузка...</div>}
        </div>
      </nav>
    </header>
  );
} 