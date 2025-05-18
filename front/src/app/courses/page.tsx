"use client";

import { useState, useEffect, useCallback } from 'react';
import ProtectedRoute from '@/components/auth/ProtectedRoute';
import { useAuth } from '@/contexts/AuthContext'; // Если понадобится для API-запросов в будущем
import { ChevronDownIcon, ChevronUpIcon } from '@heroicons/react/24/solid'; // Иконки для аккордеона
import { useRouter } from 'next/navigation';

// Типы данных, соответствующие DTO с бэкенда
interface TaskDto {
  id: number; 
  name: string;
  deadline: string | null;
  status: string;
  grade: string | null;
  description?: string;
  estimatedMinutes?: number | null;      // Добавлено для оценки времени
  timeEstimateExplanation?: string | null; // Добавлено для объяснения оценки
}

interface SubjectDto {
  id: number; 
  name: string;
  tasks?: TaskDto[]; 
  tasksLoading?: boolean;
  tasksError?: string | null;
}

interface SemesterDto {
  id: string; 
  name: string;
  subjects?: SubjectDto[]; 
  subjectsLoading?: boolean;
  subjectsError?: string | null;
}

// Примерные мок-данные
const mockSemestersData: SemesterDto[] = [
  {
    id: "sem1",
    name: "Осень 2023-2024",
    subjects: [
      {
        id: 101,
        name: "Математический анализ",
        tasks: [
          { id: 10101, name: "Контрольная работа №1", deadline: "2023-10-15", status: "Оценено", grade: "5/5" },
          { id: 10102, name: "Домашнее задание №3", deadline: "2023-11-01", status: "Сдано", grade: null },
          { id: 10103, name: "Коллоквиум", deadline: "2023-12-10", status: "Не сдано", grade: null },
        ],
      },
      {
        id: 102,
        name: "Основы программирования",
        tasks: [
          { id: 10201, name: "Лабораторная работа №1: Алгоритмы", deadline: "2023-09-30", status: "Оценено", grade: "10/10" },
          { id: 10202, name: "Лабораторная работа №2: Структуры данных", deadline: "2023-10-20", status: "Сдано", grade: "8/10" },
          { id: 10203, name: "Курсовой проект: Этап 1", deadline: "2023-11-15", status: "Не сдано", grade: null },
        ],
      },
    ],
  },
  {
    id: "sem2",
    name: "Весна 2024",
    subjects: [
      {
        id: 201,
        name: "Физика: Механика",
        tasks: [
          { id: 20101, name: "Отчет по лабораторной №2", deadline: "2024-03-10", status: "Сдано", grade: null },
          { id: 20102, name: "Расчетно-графическая работа", deadline: "2024-04-05", status: "Не сдано", grade: null },
        ],
      },
       {
        id: 202,
        name: "Английский язык B2",
        tasks: [
          { id: 20201, name: "Эссе: My Future Career", deadline: "2024-03-20", status: "Оценено", grade: "Зачет" },
          { id: 20202, name: "Презентация: Innovations", deadline: "2024-04-15", status: "Сдано", grade: null },
        ],
      },
    ],
  },
];

function CoursesPageComponent() {
  const { token } = useAuth();
  const [semesters, setSemesters] = useState<SemesterDto[]>([]);
  const [selectedSemesterId, setSelectedSemesterId] = useState<string | null>(null);
  const [expandedSubjectId, setExpandedSubjectId] = useState<number | null>(null);

  const [semestersLoading, setSemestersLoading] = useState(true);
  const [semestersError, setSemestersError] = useState<string | null>(null);

  // Состояния для обновления оценок времени задач семестра
  const [estimatesRefreshing, setEstimatesRefreshing] = useState(false);
  const [estimatesRefreshError, setEstimatesRefreshError] = useState<string | null>(null);
  const [estimatesRefreshSuccess, setEstimatesRefreshSuccess] = useState<string | null>(null);

  const router = useRouter();

  console.log("CoursesPageComponent: Initial render. Token:", token);

  // Загрузка семестров
  useEffect(() => {
    console.log("CoursesPageComponent: Semesters useEffect triggered. Token:", token);
    if (!token) {
      console.log("CoursesPageComponent: No token, skipping semester fetch.");
      setSemestersLoading(false);
      return;
    }
    const fetchSemesters = async () => {
      console.log("CoursesPageComponent: fetchSemesters called.");
      setSemestersLoading(true);
      setSemestersError(null);
      setSelectedSemesterId(null);
      setSemesters(prevSemesters => prevSemesters.map(s => ({ ...s, subjects: undefined, subjectsLoading: true, subjectsError: null })));
      setEstimatesRefreshing(false);
      setEstimatesRefreshError(null);
      setEstimatesRefreshSuccess(null);

      try {
        console.log("CoursesPageComponent: Fetching /api/semesters with token:", token ? 'present' : 'absent');
        const response = await fetch('/api/semesters', {
          headers: { 'Authorization': `Bearer ${token}` },
        });
        console.log("CoursesPageComponent: /api/semesters response status:", response.status);
        if (!response.ok) {
          if (response.status === 401) {
            console.error("CoursesPageComponent: Ошибка 401 при загрузке семестров. Перенаправление на /login");
            localStorage.removeItem('token');
            localStorage.removeItem('user');
            localStorage.removeItem('needsInitialParsing');
            router.push('/login');
            return;
          }
          const errorText = await response.text();
          console.error("CoursesPageComponent: Error loading semesters - Response not OK:", response.status, errorText);
          throw new Error(`Ошибка загрузки семестров: ${response.status} ${errorText}`);
        }
        const data: SemesterDto[] = await response.json();
        console.log("CoursesPageComponent: Semesters data received:", data);
        setSemesters(data);
        if (data.length > 0 && !semesters.find(s => s.id === selectedSemesterId)) {
          console.log("CoursesPageComponent: Setting selectedSemesterId to first semester:", data[0].id);
          setSelectedSemesterId(data[0].id);
        } else if (data.length === 0) {
          setSelectedSemesterId(null);
        }
      } catch (error) {
        console.error("CoursesPageComponent: Catch block - Error loading semesters:", error);
        setSemestersError(error instanceof Error ? error.message : String(error));
      } finally {
        console.log("CoursesPageComponent: fetchSemesters finally block. Setting semestersLoading to false.");
        setSemestersLoading(false);
      }
    };
    fetchSemesters();
  }, [token, router]);

  // Загрузка предметов для выбранного семестра
  useEffect(() => {
    console.log("CoursesPageComponent: Subjects useEffect triggered. SelectedSemesterId:", selectedSemesterId, "Token:", token);
    if (!selectedSemesterId || !token) {
        console.log("CoursesPageComponent: No selectedSemesterId or token, skipping subject fetch.");
        return;
    }
    const fetchSubjects = async () => {
      console.log(`CoursesPageComponent: fetchSubjects called for semesterId: ${selectedSemesterId}.`);
      setSemesters(prevSemesters => prevSemesters.map(s => 
        s.id === selectedSemesterId ? { ...s, subjectsLoading: true, subjectsError: null, subjects: undefined } : s
      ));
      try {
        console.log(`CoursesPageComponent: Fetching /api/semesters/${selectedSemesterId}/subjects`);
        const response = await fetch(`/api/semesters/${selectedSemesterId}/subjects`, {
          headers: { 'Authorization': `Bearer ${token}` },
        });
        console.log(`CoursesPageComponent: /api/semesters/${selectedSemesterId}/subjects response status:`, response.status);
        if (!response.ok) {
          const errorText = await response.text();
          console.error("CoursesPageComponent: Error loading subjects - Response not OK:", response.status, errorText);
          throw new Error(`Ошибка загрузки предметов: ${response.status} ${errorText}`);
        }
        const data: SubjectDto[] = await response.json();
        console.log(`CoursesPageComponent: Subjects data received for semester ${selectedSemesterId}:`, data);
        setSemesters(prevSemesters => prevSemesters.map(s => 
          s.id === selectedSemesterId ? { ...s, subjects: data, subjectsLoading: false } : s
        ));
      } catch (error) {
        console.error(`CoursesPageComponent: Catch block - Error loading subjects for semester ${selectedSemesterId}:`, error);
        setSemesters(prevSemesters => prevSemesters.map(s => 
          s.id === selectedSemesterId ? { ...s, subjectsLoading: false, subjectsError: error instanceof Error ? error.message : String(error) } : s
        ));
      }
    };
    fetchSubjects();
  }, [selectedSemesterId, token]);

   // Загрузка заданий для раскрытого предмета (ленивая)
  const handleToggleSubject = useCallback(async (subjectId: number) => {
    console.log(`CoursesPageComponent: handleToggleSubject called for subjectId: ${subjectId}. Current expanded: ${expandedSubjectId}`);
    if (expandedSubjectId === subjectId) {
      setExpandedSubjectId(null);
      return;
    }
    setExpandedSubjectId(subjectId);
    if (!selectedSemesterId || !token) {
        console.log("CoursesPageComponent: No selectedSemesterId or token in handleToggleSubject.");
        return;
    }
    setSemesters(prevSemesters => prevSemesters.map(s => {
      if (s.id === selectedSemesterId) {
        return {
          ...s,
          subjects: s.subjects?.map(sub => 
            sub.id === subjectId ? { ...sub, tasksLoading: true, tasksError: null, tasks: sub.tasksLoading ? sub.tasks : undefined } : sub
          ),
        };
      }
      return s;
    }));
    
    try {
      console.log(`CoursesPageComponent: Fetching /api/subjects/${subjectId}/tasks`);
      const response = await fetch(`/api/subjects/${subjectId}/tasks`, {
        headers: { 'Authorization': `Bearer ${token}` },
      });
      console.log(`CoursesPageComponent: /api/subjects/${subjectId}/tasks response status:`, response.status);
      if (!response.ok) {
        const errorText = await response.text();
        console.error("CoursesPageComponent: Error loading tasks - Response not OK:", response.status, errorText);
        throw new Error(`Ошибка загрузки заданий: ${response.status} ${errorText}`);
      }
      const tasksData: TaskDto[] = await response.json();
      console.log(`CoursesPageComponent: Tasks data received for subject ${subjectId}:`, tasksData);
      setSemesters(prevSemesters => prevSemesters.map(s => {
        if (s.id === selectedSemesterId) {
          return {
            ...s,
            subjects: s.subjects?.map(sub => 
              sub.id === subjectId ? { ...sub, tasks: tasksData, tasksLoading: false } : sub
            ),
          };
        }
        return s;
      }));
    } catch (error) {
      console.error(`CoursesPageComponent: Catch block - Error loading tasks for subject ${subjectId}:`, error);
      setSemesters(prevSemesters => prevSemesters.map(s => {
        if (s.id === selectedSemesterId) {
          return {
            ...s,
            subjects: s.subjects?.map(sub => 
              sub.id === subjectId ? { ...sub, tasksLoading: false, tasksError: error instanceof Error ? error.message : String(error) } : sub
            ),
          };
        }
        return s;
      }));
    }
  }, [expandedSubjectId, selectedSemesterId, token]); // semesters убрано из зависимостей

  const getStatusColor = (status: string) => {
    if (!status) return 'text-slate-400';
    const lowerStatus = status.toLowerCase();
    if (lowerStatus.includes('оценено') || lowerStatus.includes('зачет')) return 'text-green-400';
    if (lowerStatus.includes('сдано')) return 'text-yellow-400';
    if (lowerStatus.includes('не сдано')) return 'text-red-400';
    return 'text-slate-400';
  };

  // Интерфейс для ответа от API оценки времени
  interface TaskTimeEstimateResponseDto {
    taskId: number;
    taskName?: string; // Не используется для обновления, но есть в ответе
    estimatedMinutes: number | null;
    explanation: string | null;
    // createdAt: string; // Не используется для обновления
    // fromCache: boolean; // Не используется для обновления
  }

  // Функция для обновления оценок времени задач семестра
  const handleRefreshTaskEstimates = async () => {
    if (!selectedSemesterId || !token) {
      setEstimatesRefreshError("Не выбран семестр или отсутствует аутентификация.");
      return;
    }
    console.log(`CoursesPageComponent: handleRefreshTaskEstimates called for semesterId: ${selectedSemesterId}`);
    setEstimatesRefreshing(true);
    setEstimatesRefreshError(null);
    setEstimatesRefreshSuccess(null);

    try {
      const response = await fetch(`/api/tasks/time-estimate/semester/refresh?date=${selectedSemesterId}`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
        },
      });

      if (!response.ok) {
        const errorData = await response.json().catch(() => ({ message: 'Не удалось получить детали ошибки при обновлении оценок.' }));
        throw new Error(errorData.message || `Ошибка ${response.status}`);
      }
      
      // Ожидаем List<TaskTimeEstimateResponseDto>
      const updatedEstimates: TaskTimeEstimateResponseDto[] = await response.json(); 
      console.log("CoursesPageComponent: Received updated estimates:", updatedEstimates);

      // Обновляем состояние `semesters` новыми оценками
      setSemesters(prevSemesters => 
        prevSemesters.map(semester => {
          if (semester.id === selectedSemesterId) {
            return {
              ...semester,
              subjects: semester.subjects?.map(subject => ({
                ...subject,
                tasks: subject.tasks?.map(task => {
                  const updatedEstimate = updatedEstimates.find(est => est.taskId === task.id);
                  if (updatedEstimate) {
                    return {
                      ...task,
                      estimatedMinutes: updatedEstimate.estimatedMinutes,
                      timeEstimateExplanation: updatedEstimate.explanation,
                    };
                  }
                  return task;
                }),
              })),
            };
          }
          return semester;
        })
      );
      setEstimatesRefreshSuccess(`Оценки времени для задач в семестре "${semesters.find(s=>s.id === selectedSemesterId)?.name}" успешно обновлены! Откройте предмет, чтобы увидеть их.`);

    } catch (error) {
      console.error("CoursesPageComponent: Error refreshing task estimates:", error);
      setEstimatesRefreshError(error instanceof Error ? error.message : "Неизвестная ошибка при обновлении оценок.");
    } finally {
      setEstimatesRefreshing(false);
    }
  };

  // --- Отображение состояний загрузки и ошибок ---
  if (semestersLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-900 text-sky-400 text-xl">
        Загрузка семестров...
      </div>
    );
  }
  
  if (semestersError) {
    return (
      <div className="min-h-screen flex flex-col items-center justify-center bg-gradient-to-br from-slate-900 to-slate-800 text-slate-100 p-6">
        <h1 className="text-3xl font-bold text-red-500 mb-4">Ошибка при загрузке семестров</h1>
        <p className="text-slate-300 text-lg">{semestersError}</p>
        {/* Можно добавить кнопку "Попробовать снова" */}
      </div>
    );
  }

  if (semesters.length === 0) {
    return (
      <div className="min-h-screen flex flex-col items-center justify-center bg-gradient-to-br from-slate-900 to-slate-800 text-slate-100 p-6">
        <h1 className="text-3xl font-bold text-sky-400 mb-4">Мои Курсы</h1>
        <p className="text-slate-300 text-lg">
          Нет данных о ваших семестрах. Убедитесь, что сбор данных был произведен.
        </p>
      </div>
    );
  }
  
  const currentSelectedSemesterData = semesters.find(s => s.id === selectedSemesterId);

  // --- Основная разметка страницы --- 
  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-900 to-slate-800 text-slate-100 p-6">
      <header className="mb-8">
        <div className="mb-6 text-center">
          <h1 className="text-4xl font-bold text-sky-400">Предметы</h1>
        </div>
      </header>

      {/* Табы семестров */}
      <nav className="mb-8 flex justify-center space-x-2 sm:space-x-4 flex-wrap">
        {semesters.map((semester) => (
          <button
            key={semester.id}
            onClick={() => {
              console.log("CoursesPageComponent: Semester tab clicked:", semester.id);
              setSelectedSemesterId(semester.id);
              setExpandedSubjectId(null); 
              setEstimatesRefreshError(null); // Сбрасываем ошибку и сообщение об успехе
              setEstimatesRefreshSuccess(null);
            }}
            disabled={currentSelectedSemesterData?.subjectsLoading && selectedSemesterId === semester.id} // Блокируем активный таб во время загрузки его предметов
            className={`mb-2 px-3 py-2 sm:px-6 sm:py-3 text-sm sm:text-base font-medium rounded-lg transition-all duration-300 disabled:opacity-50 disabled:transform-none
              ${selectedSemesterId === semester.id 
                ? 'bg-sky-600 text-white shadow-lg transform scale-105' 
                : 'bg-slate-700 hover:bg-slate-600 text-slate-300 hover:text-sky-300'
              }`}
          >
            {semester.name}
          </button>
        ))}
      </nav>

      {/* Индикаторы загрузки/ошибок для предметов */}
      {selectedSemesterId && currentSelectedSemesterData?.subjectsLoading && (
        <p className="text-center text-sky-400 text-lg my-4">Загрузка предметов для семестра "{currentSelectedSemesterData?.name}"...</p>
      )}
      {selectedSemesterId && currentSelectedSemesterData?.subjectsError && (
        <div className="text-center text-red-500 text-lg my-4 p-4 bg-red-900/30 rounded-md">
            <p className="font-semibold">Ошибка загрузки предметов:</p>
            <p>{currentSelectedSemesterData.subjectsError}</p>
        </div>
      )}

      {/* Список предметов */}
      {currentSelectedSemesterData && !currentSelectedSemesterData.subjectsLoading && !currentSelectedSemesterData.subjectsError && (
        <div className="space-y-6 max-w-4xl mx-auto">
          {!currentSelectedSemesterData.subjects || currentSelectedSemesterData.subjects.length === 0 && (
            <p className="text-center text-slate-400 text-lg">В семестре "{currentSelectedSemesterData.name}" нет предметов.</p>
          )}
          {currentSelectedSemesterData.subjects?.map((subject) => (
            <div key={subject.id} className="bg-slate-800 shadow-xl rounded-lg overflow-hidden">
              <button
                onClick={() => handleToggleSubject(subject.id)}
                className="w-full flex justify-between items-center p-5 sm:p-6 text-left hover:bg-slate-700/50 transition-colors duration-200 focus:outline-none"
              >
                <h2 className="text-xl sm:text-2xl font-semibold text-sky-400">{subject.name}</h2>
                {subject.tasksLoading ? (
                    <div className="animate-spin rounded-full h-5 w-5 border-b-2 border-sky-400"></div>
                ) : expandedSubjectId === subject.id ? (
                  <ChevronUpIcon className="h-6 w-6 text-sky-500" />
                ) : (
                  <ChevronDownIcon className="h-6 w-6 text-sky-500" />
                )}
              </button>
              
              {/* Аккордеон с заданиями */}
              {expandedSubjectId === subject.id && (
                <div className="border-t border-slate-700 px-5 sm:px-6 py-4 bg-slate-800/50">
                  {subject.tasksLoading && <p className="text-sky-400">Загрузка заданий...</p>}
                  {subject.tasksError && <p className="text-red-500">Ошибка загрузки заданий: {subject.tasksError}</p>}
                  {!subject.tasksLoading && !subject.tasksError && (
                    !subject.tasks || subject.tasks.length === 0 ? (
                      <p className="text-slate-400">По этому предмету пока нет заданий.</p>
                    ) : (
                      <ul className="space-y-3">
                        {subject.tasks?.map((task) => {
                          let displayedStatusText: string;
                          let displayedGradeText: string | null = null;
                          let statusColorClass: string;

                          const originalBackendStatus = task.status;
                          const gradeFromBackend = task.grade ? String(task.grade).trim() : null;

                          const estimatedTimeText = task.estimatedMinutes 
                            ? `${Math.floor(task.estimatedMinutes / 60)} ч ${task.estimatedMinutes % 60} мин`
                            : null;

                          if (gradeFromBackend === null) {
                            // Случай 1: Оценки нет вообще (null)
                            displayedStatusText = "Не оценено";
                            statusColorClass = getStatusColor("Не сдано"); // Используем цвет для "Не сдано" (красный)
                          } else if (gradeFromBackend.toLowerCase() === "оценено" || gradeFromBackend.toLowerCase() === "зачет") {
                            // Случай 2: Оценка - это "Оценено" или "Зачет" (нет конкретного балла)
                            displayedStatusText = "Не оценено";
                            statusColorClass = getStatusColor("Не сдано"); // Используем цвет для "Не сдано" (красный)
                            // displayedGradeText остается null, чтобы не показывать "Оценка: Оценено"
                          } else {
                            // Случай 3: Есть конкретная оценка (например, "5/5")
                            displayedStatusText = originalBackendStatus; // Используем статус с бэкенда
                            statusColorClass = getStatusColor(originalBackendStatus); // Цвет согласно статусу с бэкенда
                            displayedGradeText = gradeFromBackend; // Отображаем эту конкретную оценку
                          }

                          return (
                            <li key={task.id} className="p-3 bg-slate-700/70 rounded-md shadow">
                              <div className="flex flex-col sm:flex-row justify-between items-start mb-1">
                                <h3 className="text-lg font-medium text-slate-100 mb-1 sm:mb-0">
                                  {task.name.endsWith(" Задание") ? task.name.slice(0, -8) : task.name}
                                </h3>
                                {task.deadline && (
                                  <span className="text-sm text-slate-400 whitespace-nowrap">
                                    Срок: {new Date(task.deadline + 'T00:00:00').toLocaleDateString('ru-RU')}
                                  </span>
                                )}
                              </div>
                              <div className="flex flex-col sm:flex-row justify-between items-start sm:items-end text-sm mt-1">
                                <span className={`font-semibold ${statusColorClass}`}>
                                  {displayedStatusText}
                                </span>
                                {estimatedTimeText && (
                                  <span className="text-slate-400 mt-1 sm:mt-0 sm:ml-4 italic">
                                    Оценка ИИ: ~{estimatedTimeText}
                                  </span>
                                )}
                                {displayedGradeText && (
                                  <span className="text-slate-300 mt-1 sm:mt-0">
                                     {estimatedTimeText ? ' / ' : ''}Оценка: {displayedGradeText}
                                  </span>
                                )}
                              </div>
                              {/* Добавляем отображение объяснения оценки от ИИ */} 
                              {task.timeEstimateExplanation && (
                                <div className="mt-2 text-sm text-slate-400 pl-2 border-l-2 border-slate-600">
                                  <p className="italic">{task.timeEstimateExplanation}</p>
                                </div>
                              )}
                            </li>
                          );
                        })}
                      </ul>
                    )
                  )}
                </div>
              )}
            </div>
          ))}

          {/* Кнопка и область для анализа семестра -> Обновления оценок */}
          {currentSelectedSemesterData?.subjects && currentSelectedSemesterData.subjects.length > 0 && (
            <div className="mt-8 pt-6 border-t border-slate-700 text-center">
              <button
                onClick={handleRefreshTaskEstimates}
                disabled={estimatesRefreshing}
                className="w-full max-w-md mx-auto flex justify-center bg-purple-600 hover:bg-purple-700 text-white font-semibold py-3 px-6 rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500 focus:ring-offset-2 focus:ring-offset-slate-900 transition-all duration-300 ease-in-out transform hover:scale-105 disabled:opacity-70 disabled:cursor-not-allowed"
              >
                {estimatesRefreshing ? (
                  <span className="flex items-center">
                    <svg className="animate-spin -ml-1 mr-3 h-5 w-5 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                    </svg>
                    Обновляем оценки...
                  </span>
                ) : (
                  'Обновить оценки времени задач нейросетью'
                )}
              </button>

              {estimatesRefreshError && (
                <div className="mt-6 p-4 bg-red-900/30 border border-red-700 rounded-md text-red-400 max-w-md mx-auto">
                  <p className="font-semibold">Ошибка обновления оценок:</p>
                  <p>{estimatesRefreshError}</p> 
                </div>
              )}
              {estimatesRefreshSuccess && !estimatesRefreshError && (
                <div className="mt-6 p-4 bg-green-900/30 border border-green-700 rounded-md text-green-400 max-w-md mx-auto">
                  <p>{estimatesRefreshSuccess}</p>
                </div>
              )}
            </div>
          )}
        </div>
      )}
       {!selectedSemesterId && semesters.length > 0 && !semestersLoading && (
         <p className="text-center text-slate-400 text-lg">Выберите семестр для просмотра предметов.</p>
       )}
    </div>
  );
}

export default function CoursesPage() {
  return (
    <ProtectedRoute>
      <CoursesPageComponent />
    </ProtectedRoute>
  );
} 