"use client"; // HomePage теперь тоже клиентский компонент из-за useAuth и ProtectedRoute

import Link from 'next/link';
import ProtectedRoute from '@/components/auth/ProtectedRoute';
import { useAuth } from '@/contexts/AuthContext';

function HomeComponent() {
  const { user, logout } = useAuth();

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-900 to-slate-800 text-slate-100 flex flex-col items-center justify-center p-6">
      <header className="absolute top-0 left-0 right-0 p-6 flex justify-between items-center">
        <Link href="/" className="text-2xl font-bold text-sky-400">
          DeadlineMaster {user?.role === 'ELDER' && "(Староста)"}
        </Link>
        <nav className="space-x-4">
          <span className="text-slate-300">Привет, {user?.email}!</span>
          <Link href="/profile" className="text-slate-300 hover:text-sky-400 transition-colors duration-300">
            Профиль
          </Link>
          <button 
            onClick={logout} 
            className="text-slate-300 hover:text-sky-400 transition-colors duration-300 bg-transparent border-none cursor-pointer p-0"
          >
            Выйти
          </button>
        </nav>
      </header>

      <main className="text-center">
        <h1 className="text-5xl font-extrabold text-sky-400 mb-6">
          Добро пожаловать в StudentHub!
        </h1>
        <p className="text-xl text-slate-300 mb-8 max-w-2xl mx-auto">
          Это ваша главная страница. В будущем здесь будет много полезного для студентов.
          {user?.role === 'ELDER' && " Как староста, у вас будут дополнительные возможности."}
        </p>
        <div className="space-x-4">
          <Link href="/courses" className="bg-sky-600 hover:bg-sky-700 text-white font-semibold py-3 px-6 rounded-lg transition-all duration-300 ease-in-out transform hover:scale-105">
            Курсы
          </Link>
          <Link href="/schedule" className="bg-slate-700 hover:bg-slate-600 text-sky-300 font-semibold py-3 px-6 rounded-lg transition-all duration-300 ease-in-out transform hover:scale-105">
            Расписание
          </Link>
        </div>
      </main>

      <footer className="absolute bottom-0 left-0 right-0 p-6 text-center text-slate-500 text-sm">
        &copy; {new Date().getFullYear()} DeadlineMaster. Все права защищены.
      </footer>
    </div>
  );
}

export default function HomePage() {
  return (
    <ProtectedRoute>
      <HomeComponent />
    </ProtectedRoute>
  );
}
