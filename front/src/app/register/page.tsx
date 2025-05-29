"use client";

import Link from 'next/link';
import { useState, type FormEvent } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/contexts/AuthContext';

export default function RegisterPage() {
  const router = useRouter();
  const { login } = useAuth();
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [loadingMessage, setLoadingMessage] = useState('Регистрация...');

  const validateEmail = (email: string) => {
    const emailRegex = /^[\w-\.]+@sfedu\.ru$/;
    return emailRegex.test(email);
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setLoadingMessage('Проверяем ваши данные SFEDU...');
    setIsLoading(true);
    setError(null);
    setSuccessMessage(null);

    if (!validateEmail(email)) {
      setError('Пожалуйста, используйте email адрес с доменом sfedu.ru.');
      setIsLoading(false);
      return;
    }

    if (password !== confirmPassword) {
      setError('Пароли не совпадают.');
      setIsLoading(false);
      return;
    }

    try {
      const response = await fetch('/api/auth/register', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ email, password, role: 'STUDENT' }),
      });

      const data = await response.json();

      if (!response.ok) {
        const errorMessage = data.message || 'Ошибка регистрации. Пожалуйста, проверьте свои данные.';
        setError(errorMessage);
        setIsLoading(false);
        return;
      }
      
      setLoadingMessage('Завершаем регистрацию...');
      login(data.token, { id: data.id, email: data.email, role: data.role });
      
      localStorage.setItem('needsInitialParsing', 'true');

      router.push('/');

    } catch (err) {
      console.error('Ошибка при попытке регистрации:', err);
      setError('Произошла ошибка сети. Попробуйте снова.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-900 to-slate-800 flex flex-col justify-center items-center p-4 relative">
      <div className="absolute top-6 left-6">
        <Link href="/" className="text-xl font-bold text-white hover:text-sky-400 transition-colors">
          DeadlineMaster
        </Link>
      </div>
      <div className="w-full max-w-lg bg-slate-800 shadow-2xl rounded-xl p-8">
        <h1 className="text-4xl font-bold text-center text-sky-400 mb-2">
          Создание Аккаунта
        </h1>
        <p className="text-center text-sm text-slate-400 mb-6 px-4">
          Пожалуйста, используйте данные (email и пароль) от вашего личного кабинета студента SFEDU.
          Это необходимо для сбора информации о вашем учебном плане.
        </p>
        <form onSubmit={handleSubmit} className="space-y-6">
          <div>
            <label
              htmlFor="email-register"
              className="block text-sm font-medium text-sky-300 mb-1"
            >
              Email
            </label>
            <input
              type="email"
              name="email"
              id="email-register"
              autoComplete="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              disabled={isLoading}
              className="w-full px-4 py-3 bg-slate-700 border border-slate-600 rounded-lg text-gray-100 focus:ring-2 focus:ring-sky-500 focus:border-sky-500 outline-none transition-colors duration-300 disabled:opacity-50"
              placeholder="you@example.com"
            />
          </div>
          <div>
            <label
              htmlFor="password-register"
              className="block text-sm font-medium text-sky-300 mb-1"
            >
              Пароль
            </label>
            <input
              type="password"
              name="password"
              id="password-register"
              autoComplete="new-password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              disabled={isLoading}
              className="w-full px-4 py-3 bg-slate-700 border border-slate-600 rounded-lg text-gray-100 focus:ring-2 focus:ring-sky-500 focus:border-sky-500 outline-none transition-colors duration-300 disabled:opacity-50"
              placeholder="••••••••"
            />
          </div>
          <div>
            <label
              htmlFor="confirmPassword"
              className="block text-sm font-medium text-sky-300 mb-1"
            >
              Подтвердите Пароль
            </label>
            <input
              type="password"
              name="confirmPassword"
              id="confirmPassword"
              autoComplete="new-password"
              required
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              disabled={isLoading}
              className="w-full px-4 py-3 bg-slate-700 border border-slate-600 rounded-lg text-gray-100 focus:ring-2 focus:ring-sky-500 focus:border-sky-500 outline-none transition-colors duration-300 disabled:opacity-50"
              placeholder="••••••••"
            />
          </div>

          {error && (
            <p className="text-sm text-red-400 bg-red-900/30 border border-red-700 p-3 rounded-md">
              {error}
            </p>
          )}
          {successMessage && (
             <p className="text-sm text-green-400 bg-green-900/30 border border-green-700 p-3 rounded-md">
              {successMessage}
            </p>
          )}

          <div>
            <button
              type="submit"
              disabled={isLoading}
              className="w-full bg-sky-600 hover:bg-sky-700 text-white font-semibold py-3 px-4 rounded-lg focus:outline-none focus:ring-2 focus:ring-sky-500 focus:ring-offset-2 focus:ring-offset-slate-800 transition-all duration-300 ease-in-out transform hover:scale-105 disabled:opacity-70 disabled:cursor-not-allowed disabled:transform-none cursor-pointer"
            >
              {isLoading ? (
                <span className="flex items-center justify-center">
                  <svg className="animate-spin -ml-1 mr-3 h-5 w-5 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                  </svg>
                  {loadingMessage}
                </span>
              ) : (
                'Зарегистрироваться'
              )}
            </button>
          </div>
        </form>
        <p className="mt-8 text-center text-sm text-slate-400">
          Уже есть аккаунт?{' '}
          <Link
            href="/login"
            className={`font-medium text-sky-500 hover:text-sky-400 transition-colors duration-300 ${isLoading ? 'pointer-events-none text-slate-600' : ''}`}
          >
            Войдите
          </Link>
        </p>
      </div>
    </div>
  );
} 