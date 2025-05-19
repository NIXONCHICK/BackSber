'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/contexts/AuthContext';
import ProtectedRoute from '@/components/auth/ProtectedRoute';
import { CoursesPageComponent } from '@/app/courses/page';

// Content of the page, not exported directly
function PageContent() { 
    const { user, token, isLoading: authLoading } = useAuth();
    const router = useRouter();
    const [parsingMessage, setParsingMessage] = useState<string | null>(null);
    const [parsingError, setParsingError] = useState<string | null>(null);

    useEffect(() => {
        const needsParsing = localStorage.getItem('needsInitialParsing');
        if (needsParsing === 'true' && token && user) {
            localStorage.removeItem('needsInitialParsing');
            setParsingMessage("Идет сбор ваших учебных данных...");
            setParsingError(null);

            fetch('/api/user/initiate-parsing', {
                method: 'POST',
                headers: {
                    'Authorization': `Bearer ${token}`,
                },
            })
            .then(async response => {
                if (response.ok) {
                    const result = await response.json();
                    if (result) { 
                        setParsingMessage("Сбор данных успешно завершен!");
                    } else {
                        setParsingError("Не удалось собрать данные. Пожалуйста, попробуйте позже или обратитесь в поддержку.");
                        setParsingMessage(null);
                    }
                } else {
                    const errorData = await response.text();
                    console.error('Parsing error response:', errorData);
                    setParsingError(`Ошибка при сборе данных: ${response.status} ${response.statusText}. Попробуйте позже.`);
                    setParsingMessage(null);
                }
            })
            .catch(error => {
                console.error('Parsing fetch error:', error);
                setParsingError("Произошла ошибка при отправке запроса на сбор данных. Проверьте ваше интернет-соединение.");
                setParsingMessage(null);
            });
        }
    }, [token, user]); 

    if (authLoading) {
        return (
            <div className="flex items-center justify-center min-h-screen">
                <p>Загрузка...</p>
            </div>
        );
    }
    
    return (
        <div className="min-h-screen flex flex-col items-center pt-8 bg-gradient-to-br from-slate-900 to-slate-800 text-slate-100 w-full">
            {parsingMessage && <p className="text-green-400 bg-slate-700 p-3 rounded-md mb-4 text-center shadow-lg">{parsingMessage}</p>}
            {parsingError && <p className="text-red-400 bg-slate-700 p-3 rounded-md mb-4 text-center shadow-lg">{parsingError}</p>}
            
            {user && !parsingMessage && !parsingError ? (
                 <CoursesPageComponent />
            ) : !user && !authLoading && !parsingMessage && !parsingError ? (
                <div className="space-y-4 text-center mt-10">
                    <h1 className="text-4xl font-bold">Добро пожаловать в DeadlineMaster!</h1>
                    <p className="text-lg text-slate-300">Войдите в систему, чтобы получить доступ к вашим предметам и заданиям.</p>
                </div>
            ) : null}
        </div>
    );
}

// Single default export for the page
export default function HomePage() {
  return (
    <ProtectedRoute>
      <PageContent />
    </ProtectedRoute>
  );
}
