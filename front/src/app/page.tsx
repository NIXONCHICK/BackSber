"use client"; // HomePage теперь тоже клиентский компонент из-за useAuth и ProtectedRoute

import Link from 'next/link';
import ProtectedRoute from '@/components/auth/ProtectedRoute';
import { useAuth } from '@/contexts/AuthContext';
import { useEffect, useState } from 'react';

function HomeComponent() {
  const { user, logout, token } = useAuth();
  const [isParsing, setIsParsing] = useState(false);
  const [parsingStatusMessage, setParsingStatusMessage] = useState<string | null>(null);

  useEffect(() => {
    console.log("HomeComponent useEffect triggered. Token:", token);
    const needsParsing = localStorage.getItem('needsInitialParsing');
    console.log("useEffect: needsInitialParsing from localStorage:", needsParsing);

    if (needsParsing === 'true' && token) {
      console.log("useEffect: Condition MET (needsParsing is true AND token exists). Removing item and initiating parsing.");
      localStorage.removeItem('needsInitialParsing');
      setIsParsing(true);
      setParsingStatusMessage("Идет сбор ваших учебных данных... Это может занять некоторое время.");

      const initiateParsingInternal = async () => {
        console.log("initiateParsingInternal: Attempting to fetch /api/user/initiate-parsing. Token:", token);
        try {
          const response = await fetch('/api/user/initiate-parsing', { 
            method: 'POST',
            headers: {
              'Authorization': `Bearer ${token}`,
            },
          });
          console.log("initiateParsingInternal: Fetch response received", response.status);
          const data = await response.json();
          console.log("initiateParsingInternal: Fetch response data:", data);

          if (response.ok) {
            setParsingStatusMessage(data.message || "Сбор данных успешно завершен!");
          } else {
            setParsingStatusMessage(data.message || "Ошибка при сборе данных. Пожалуйста, попробуйте обновить страницу.");
          }
        } catch (error) {
          console.error("Ошибка при инициации парсинга:", error);
          setParsingStatusMessage("Сетевая ошибка при сборе данных. Пожалуйста, проверьте ваше подключение и попробуйте обновить страницу.");
        } finally {
          setIsParsing(false);
        }
      };

      initiateParsingInternal();
      
    } else if (needsParsing === 'true' && !token) {
      console.log("useEffect: Condition NOT MET (needsParsing is true BUT token is missing). Waiting for token.");
      console.log("Ожидание токена для инициации парсинга...");
    } else {
      console.log("useEffect: Condition NOT MET (needsParsing is not 'true' or already processed). needsParsing:", needsParsing, "Token:", token);
    }

  }, [token]);

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-900 to-slate-800 text-slate-100 flex flex-col items-center justify-center p-6 relative">
      {parsingStatusMessage && (
        <div 
          className={`fixed top-5 left-1/2 -translate-x-1/2 z-50 p-4 rounded-md shadow-lg text-white ${
            isParsing 
              ? 'bg-sky-500' 
              : parsingStatusMessage.includes("успешно") 
              ? 'bg-green-500' 
              : 'bg-red-500'
          }`}
        >
          {parsingStatusMessage}
          {isParsing && <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white inline-block ml-2"></div>}
        </div>
      )}

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

      <main className="text-center pt-16">
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
