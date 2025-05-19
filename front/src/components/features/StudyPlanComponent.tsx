'use client';

import { useEffect, useState, useCallback } from 'react';
import { useAuth } from '@/contexts/AuthContext';

interface StudyPlanComponentProps {
  semesterId: string | null;
  formattedSemesterName?: string | null;
}

// Определяем интерфейсы на основе структуры данных из логов
interface PlannedTask {
  taskId: number;
  taskName: string;
  subjectName: string;
  minutesScheduledToday: number;
  minutesRemainingForTask: number;
  deadline: string;
}

interface PlannedDay {
  dayNumber: number;
  date: string;
  totalMinutesScheduledThisDay: number;
  tasks: PlannedTask[];
}

interface StudyPlanData {
  semesterStartDate: string;
  plannedDays: PlannedDay[];
  warnings: string[]; // Предполагаем, что предупреждения - это массив строк
  totalTasksConsideredForPlanning: number;
}

export default function StudyPlanComponent({ semesterId, formattedSemesterName }: StudyPlanComponentProps) {
  const { token } = useAuth();
  const [studyPlan, setStudyPlan] = useState<StudyPlanData | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setStudyPlan(null);
    setError(null);
    setIsLoading(false);
  }, [semesterId]);

  const handleFetchStudyPlan = useCallback(async () => {
    if (!semesterId || !token) {
      return;
    }

    setIsLoading(true);
    setError(null);
    setStudyPlan(null); 
    console.log(`Fetching study plan for semester (requestDate): ${semesterId}`);

    try {
      const response = await fetch(`/api/tasks/time-estimate/study-plan/semester?requestDate=${semesterId}`, {
        headers: {
          'Authorization': `Bearer ${token}`,
        },
      });

      if (!response.ok) {
        const errorData = await response.json().catch(() => ({ message: 'Не удалось получить детали ошибки' }));
        console.error("Study plan fetch error:", response.status, errorData);
        throw new Error(errorData.message || `Ошибка загрузки плана: ${response.status}`);
      }

      const data: StudyPlanData = await response.json();
      setStudyPlan(data);
      console.log("Study plan data received:", data);

    } catch (err) {
      console.error("Failed to fetch study plan:", err);
      setError(err instanceof Error ? err.message : 'Произошла неизвестная ошибка');
    } finally {
      setIsLoading(false);
    }
  }, [semesterId, token]);

  useEffect(() => {
    if (semesterId && token) {
      handleFetchStudyPlan();
    }
  }, [semesterId, token, handleFetchStudyPlan]);

  const formatMinutes = (minutes: number) => {
    if (minutes < 60) return `${minutes} мин`;
    const hours = Math.floor(minutes / 60);
    const mins = minutes % 60;
    return `${hours} ч ${mins > 0 ? `${mins} мин` : ''}`.trim();
  };

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleDateString('ru-RU', {
      year: 'numeric',
      month: 'long',
      day: 'numeric'
    });
  };
  
  const formatDeadline = (deadlineString: string) => {
    return new Date(deadlineString).toLocaleDateString('ru-RU', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  };

  if (!semesterId) {
    return (
      <div className="p-4 bg-slate-800 rounded-lg shadow-lg h-full text-slate-300 flex flex-col justify-center items-center">
        <h2 className="text-xl font-semibold text-sky-400 mb-3 text-center">План на семестр</h2>
        <p className="text-center">Выберите семестр, чтобы построить или просмотреть план.</p>
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="p-4 bg-slate-800 rounded-lg shadow-lg h-full text-slate-300 flex flex-col justify-center items-center">
        <h2 className="text-xl font-semibold text-sky-400 mb-3 text-center">План на семестр</h2>
        <p className="text-sky-300 animate-pulse text-center">Построение плана...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="p-4 bg-slate-800 rounded-lg shadow-lg h-full text-slate-300">
        <h2 className="text-xl font-semibold text-sky-400 mb-3">План на семестр</h2>
        <p className="text-red-400">Ошибка загрузки плана: {error}</p>
        <button
            onClick={handleFetchStudyPlan}
            className="mt-4 bg-sky-600 hover:bg-sky-700 text-white font-semibold py-2 px-4 rounded-lg transition-colors"
        >
            Попробовать снова
        </button>
      </div>
    );
  }

  if (!studyPlan || !studyPlan.plannedDays || studyPlan.plannedDays.length === 0) {
    return (
      <div className="p-4 bg-slate-800 rounded-lg shadow-lg h-full text-slate-300">
        <h2 className="text-xl font-semibold text-sky-400 mb-3">План на семестр</h2>
        <p className="text-slate-400">
          {studyPlan && studyPlan.warnings && studyPlan.warnings.length > 0 ? (
            <>
              <span>План для этого семестра не удалось сформировать.</span>
              <div className="mt-2 text-yellow-400">
                <h4 className="font-semibold">Предупреждения:</h4>
                <ul className="list-disc list-inside text-sm">
                  {studyPlan.warnings.map((warning, idx) => <li key={idx}>{warning}</li>)}
                </ul>
              </div>
            </>
          ) : (
            "План для этого семестра не найден или не содержит дней."
          )}
        </p>
        <button 
            onClick={handleFetchStudyPlan}
            className="mt-4 bg-sky-600 hover:bg-sky-700 text-white font-semibold py-2 px-4 rounded-lg transition-colors"
        >
            Попробовать построить снова
        </button>
      </div>
    );
  }

  return (
    <div className="bg-slate-800 rounded-lg shadow-lg h-full text-slate-300 overflow-y-auto" style={{maxHeight: 'calc(100vh - 200px)'}}> 
      <h2 className="text-xl font-semibold text-sky-400 sticky top-0 bg-slate-800 z-10 px-4 pt-4 pb-3">
        {formattedSemesterName ? `План на семестр ${formattedSemesterName}` : semesterId ? `План на семестр (Семестр: ${semesterId})` : 'План на семестр'}
      </h2>
      
      <div className="px-4 pb-4"> 
        <div className="mb-4 text-sm">
          <p><strong>Начало семестра:</strong> {studyPlan && formatDate(studyPlan.semesterStartDate)}</p>
          <p><strong>Всего задач в плане:</strong> {studyPlan && studyPlan.totalTasksConsideredForPlanning}</p>
          {studyPlan && studyPlan.warnings && studyPlan.warnings.length > 0 && (
            <div className="mt-2 p-3 bg-yellow-900/30 border border-yellow-700 rounded-md text-yellow-300">
              <h4 className="font-semibold">Предупреждения:</h4>
              <ul className="list-disc list-inside text-xs">
                {studyPlan.warnings.map((warning, idx) => <li key={idx}>{warning}</li>)}
              </ul>
            </div>
          )}
        </div>

        <div className="space-y-4">
          {studyPlan && studyPlan.plannedDays.map((day) => (
            <div key={day.dayNumber} className="p-3 bg-slate-700/50 rounded-md">
              <div className="flex justify-between items-start mb-2">
                <h3 className="text-lg font-semibold text-sky-300">
                  День {day.dayNumber} ({formatDate(day.date)})
                </h3>
                <p className="text-sm text-slate-400 whitespace-nowrap pl-2">
                  Общее: {formatMinutes(day.totalMinutesScheduledThisDay)}
                </p>
              </div>
              
              {day.tasks && day.tasks.length > 0 ? (
                <ul className="space-y-3">
                  {day.tasks.map((task) => (
                    <li key={task.taskId} className="p-3 bg-slate-700/60 rounded-md shadow">
                      <p className="font-semibold text-slate-100 text-base mb-1">{task.taskName}</p>
                      <div className="flex justify-between items-center text-sm">
                        <span className="text-sky-300">На сегодня: {formatMinutes(task.minutesScheduledToday)}</span>
                        <span className="text-xs text-slate-400 pl-2 whitespace-nowrap">
                          (всего по задаче: {formatMinutes(task.minutesScheduledToday + task.minutesRemainingForTask)})
                        </span>
                      </div>
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="text-slate-500 text-sm italic">Нет задач на этот день.</p>
              )}
            </div>
          ))}
        </div>
        <button
          onClick={handleFetchStudyPlan}
          className="mt-6 w-full bg-sky-700 hover:bg-sky-800 text-white font-semibold py-2 px-4 rounded-lg transition-colors"
        >
          Перестроить план для текущего семестра
        </button>
      </div>
    </div>
  );
} 