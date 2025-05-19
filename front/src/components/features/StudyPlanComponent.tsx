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
  planStartDate?: string;
  plannedDays: PlannedDay[];
  warnings: string[]; // Предполагаем, что предупреждения - это массив строк
  totalTasksConsideredForPlanning: number;
}

export default function StudyPlanComponent({ semesterId, formattedSemesterName }: StudyPlanComponentProps) {
  const { token } = useAuth();
  const [studyPlan, setStudyPlan] = useState<StudyPlanData | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Состояния для настроек плана
  const [ignoreCompleted, setIgnoreCompleted] = useState<boolean>(true);
  const [dailyHours, setDailyHours] = useState<string>("3"); // Дефолтное значение, например, 3 часа
  const [customPlanStartDate, setCustomPlanStartDate] = useState<string>(""); // ГГГГ-ММ-ДД

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

    let apiUrl = `/api/tasks/time-estimate/study-plan/semester?requestDate=${semesterId}`;
    apiUrl += `&ignoreCompleted=${ignoreCompleted}`;
    if (dailyHours && parseInt(dailyHours, 10) > 0) {
      apiUrl += `&dailyHours=${dailyHours}`;
    }
    if (customPlanStartDate) {
      apiUrl += `&customPlanStartDate=${customPlanStartDate}`;
    }
    
    console.log("API URL for study plan:", apiUrl);

    try {
      const response = await fetch(apiUrl, {
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
  }, [semesterId, token, ignoreCompleted, dailyHours, customPlanStartDate]);

  useEffect(() => {
    if (semesterId && token) {
      handleFetchStudyPlan();
    } else {
      setStudyPlan(null);
      setError(null);
    }
  }, [semesterId, token, handleFetchStudyPlan, ignoreCompleted, dailyHours, customPlanStartDate]);

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

  // JSX для настроек плана
  const renderSettingsForm = () => (
    <div className="mb-4 p-3 bg-slate-800/50 rounded-lg shadow">
      <h3 className="text-lg font-semibold text-sky-300 mb-3">Настройки плана</h3>
      <div className="flex flex-wrap gap-4 items-end">
        <div className="flex-1 min-w-[180px]">
          <label htmlFor="customPlanStartDate" className="block text-sm font-medium text-slate-300 mb-1">
            Начать план с:
          </label>
          <input
            type="date"
            id="customPlanStartDate"
            value={customPlanStartDate}
            onChange={(e) => setCustomPlanStartDate(e.target.value)}
            className="w-full p-2 rounded bg-slate-700 border-slate-600 text-slate-200 focus:ring-sky-500 focus:border-sky-500"
          />
        </div>
        <div className="flex-1 min-w-[120px]">
          <label htmlFor="dailyHours" className="block text-sm font-medium text-slate-300 mb-1">
            Часов в день:
          </label>
          <input
            type="number"
            id="dailyHours"
            value={dailyHours}
            onChange={(e) => setDailyHours(e.target.value)}
            min="1"
            max="24"
            className="w-full p-2 rounded bg-slate-700 border-slate-600 text-slate-200 focus:ring-sky-500 focus:border-sky-500"
          />
        </div>
        <div className="flex items-center pt-5">
          <label htmlFor="ignoreCompleted" className="flex items-center space-x-2 cursor-pointer">
            <input
              type="checkbox"
              id="ignoreCompleted"
              checked={ignoreCompleted}
              onChange={(e) => setIgnoreCompleted(e.target.checked)}
              className="form-checkbox h-5 w-5 text-sky-600 bg-slate-700 border-slate-600 rounded focus:ring-sky-500"
            />
            <span className="text-slate-300 whitespace-nowrap">Не учитывать оцененные/зачтенные</span>
          </label>
        </div>
      </div>
    </div>
  );

  // Основной контейнер для правой панели (плана и его состояний)
  // Обернем все состояния (загрузка, ошибка, нет плана, есть план) и настройки в общий div,
  // чтобы заголовок и настройки были всегда видны, если выбран семестр.
  return (
    <div className="bg-slate-800 rounded-lg shadow-lg h-full text-slate-300 overflow-y-auto" style={{maxHeight: 'calc(100vh - 150px)'}}>
      {/* Заголовок всегда наверху */} 
      <h2 className="text-xl font-semibold text-sky-400 sticky top-0 bg-slate-800 z-20 px-4 pt-4 pb-3">
        {formattedSemesterName ? `План на семестр ${formattedSemesterName}` : semesterId ? `План на семестр (Семестр: ${semesterId})` : 'План на семестр'}
      </h2>
      
      {/* Контейнер для настроек и контента плана */} 
      <div className="px-4 pb-4">
        {/* Форма настроек рендерится здесь, если выбран семестр (проверка semesterId уже была выше) */}
        {renderSettingsForm()}

        {/* Теперь условные блоки для состояний плана */} 
        {isLoading && (
          <div className="p-4 flex flex-col justify-center items-center">
            <p className="text-sky-300 animate-pulse text-center">Построение плана...</p>
          </div>
        )}

        {!isLoading && error && (
          <div className="p-4">
            <p className="text-red-400">Ошибка загрузки плана: {error}</p>
            <button
                onClick={handleFetchStudyPlan}
                className="mt-4 bg-sky-600 hover:bg-sky-700 text-white font-semibold py-2 px-4 rounded-lg transition-colors"
            >
                Попробовать снова
            </button>
          </div>
        )}

        {!isLoading && !error && (!studyPlan || !studyPlan.plannedDays || studyPlan.plannedDays.length === 0) && (
          // Этот блок теперь будет отображаться ПОД настройками, если условия совпали
          <div className="p-4">
            <p className="text-slate-400">
              {studyPlan && studyPlan.warnings && studyPlan.warnings.length > 0 ? (
                <>
                  <span>План для этого семестра с текущими настройками не удалось сформировать.</span>
                  <div className="mt-2 text-yellow-400">
                    <h4 className="font-semibold">Предупреждения:</h4>
                    <ul className="list-disc list-inside text-sm">
                      {studyPlan.warnings.map((warning, idx) => <li key={idx}>{warning}</li>)}
                    </ul>
                  </div>
                </>
              ) : (
                "План для этого семестра не найден или не содержит дней с учетом текущих настроек."
              )}
            </p>
            {/* Кнопка "Попробовать построить снова" здесь может быть избыточной, 
                т.к. есть общая кнопка "Перестроить план" ниже, 
                но оставим ее для явного действия в этом состоянии, если warnings не пустые 
                или если studyPlan вообще отсутствует (первичная загрузка/ошибка до получения studyPlan) */}
             {(!studyPlan || (studyPlan.warnings && studyPlan.warnings.length > 0)) && (
                <button 
                    onClick={handleFetchStudyPlan}
                    disabled={isLoading}
                    className="mt-4 bg-sky-600 hover:bg-sky-700 text-white font-semibold py-2 px-4 rounded-lg transition-colors disabled:opacity-50"
                >
                    {isLoading ? "Повтор..." : "Попробовать построить снова"}
                </button>
             )}
          </div>
        )}

        {!isLoading && !error && studyPlan && studyPlan.plannedDays && studyPlan.plannedDays.length > 0 && (
          // Этот блок также будет под настройками
          <>
            <div className="mb-4 text-sm">
              <p><strong>Начало семестра:</strong> {formatDate(studyPlan.semesterStartDate)}</p>
              {studyPlan.planStartDate && studyPlan.planStartDate !== studyPlan.semesterStartDate && (
                <p><strong>План построен с:</strong> {formatDate(studyPlan.planStartDate)}</p>
              )}
              <p><strong>Всего задач в плане:</strong> {studyPlan.totalTasksConsideredForPlanning}</p>
              {studyPlan.warnings && studyPlan.warnings.length > 0 && (
                <div className="mt-2 p-3 bg-yellow-900/30 border border-yellow-700 rounded-md text-yellow-300">
                  <h4 className="font-semibold">Предупреждения:</h4>
                  <ul className="list-disc list-inside text-xs">
                    {studyPlan.warnings.map((warning, idx) => <li key={idx}>{warning}</li>)}
                  </ul>
                </div>
              )}
            </div>

            <div className="space-y-4">
              {studyPlan.plannedDays.map((day) => (
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
              disabled={isLoading}
              className="mt-6 w-full bg-sky-700 hover:bg-sky-800 text-white font-semibold py-2 px-4 rounded-lg transition-colors disabled:opacity-50"
            >
              {isLoading ? "Перестроение..." : "Перестроить план с текущими настройками"}
            </button>
          </>
        )}
      </div>
    </div>
  );
} 