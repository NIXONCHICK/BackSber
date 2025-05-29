"use client";

import { useState, useEffect, useCallback } from 'react';
import ProtectedRoute from '@/components/auth/ProtectedRoute';
import { useAuth } from '@/contexts/AuthContext';
import { ChevronDownIcon, ChevronUpIcon } from '@heroicons/react/24/solid';
import { useRouter, useSearchParams } from 'next/navigation';
import StudyPlanComponent from '@/components/features/StudyPlanComponent';

interface TaskDto {
  id: number; 
  name: string;
  deadline: string | null;
  status: string;
  grade: string | null;
  description?: string;
  estimatedMinutes?: number | null;
  timeEstimateExplanation?: string | null;
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
  lastAiRefreshTimestamp?: string | null;
}

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
  const router = useRouter();
  const searchParams = useSearchParams();

  const [semesters, setSemesters] = useState<SemesterDto[]>([]);
  const [selectedSemesterId, setSelectedSemesterId] = useState<string | null>(null);
  const [expandedSubjectId, setExpandedSubjectId] = useState<number | null>(null);

  const [semestersLoading, setSemestersLoading] = useState(true);
  const [semestersError, setSemestersError] = useState<string | null>(null);

  const [estimatesRefreshing, setEstimatesRefreshing] = useState(false);
  const [estimatesRefreshError, setEstimatesRefreshError] = useState<string | null>(null);
  const [estimatesRefreshSuccess, setEstimatesRefreshSuccess] = useState<string | null>(null);
  const [isAutoRefreshing, setIsAutoRefreshing] = useState(false);

  const getSemesterDates = (semesterId: string | null): { startDate?: string; endDate?: string } => {
    if (!semesterId) return {};

    const startDate = semesterId.split('T')[0];
    const year = parseInt(startDate.substring(0, 4));
    const month = parseInt(startDate.substring(5, 7));

    let endDate;
    if (month >= 9) {
      endDate = `${year + 1}-01-31`;
    } else if (month <= 2 && month > 0) {
        endDate = `${year}-06-30`;
    } else if (month > 2 && month <= 6) {
         endDate = `${year}-06-30`;
    } else {
      const dateObj = new Date(startDate);
      dateObj.setMonth(dateObj.getMonth() + 5);
      const endYear = dateObj.getFullYear();
      const endMonth = (dateObj.getMonth() + 1).toString().padStart(2, '0');
      const endDay = new Date(endYear, parseInt(endMonth, 10), 0).getDate().toString().padStart(2, '0'); // Последний день месяца
      endDate = `${endYear}-${endMonth}-${endDay}`;
    }
    return { startDate, endDate };
  };

  const formatSemesterName = (name: string) => {
    const parts = name.split(' ');
    if (parts.length < 2) return name;

    const yearPart = parts.slice(0, parts.length - 1).join(' ');
    const seasonPart = parts[parts.length - 1];

    if (yearPart.includes('-')) {
      const firstYear = yearPart.split('-')[0];
      return `${firstYear} ${seasonPart}`;
    }
    return name;
  };

  const handleRefreshTaskEstimates = useCallback(async () => {
    if (!selectedSemesterId || !token) {
      setEstimatesRefreshError("Не выбран семестр или отсутствует аутентификация.");
      return;
    }
    console.log(`CoursesPageComponent: handleRefreshTaskEstimates called for semesterId: ${selectedSemesterId}`);
    const isManualCall = !isAutoRefreshing;
    if (isManualCall) {
        setEstimatesRefreshing(true);
    }


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
      
      const updatedEstimates: TaskTimeEstimateResponseDto[] = await response.json(); 
      console.log("CoursesPageComponent: Received updated estimates:", updatedEstimates);

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
              lastAiRefreshTimestamp: new Date().toISOString(),
            };
          }
          return semester;
        })
      );

      if (isManualCall) {
        const semesterName = semesters.find(s => s.id === selectedSemesterId)?.name || "";
        const displaySemesterName = formatSemesterName(semesterName);
        setEstimatesRefreshSuccess(`Информация по задачам в семестре "${displaySemesterName}" успешно актуализирована! Откройте предмет, чтобы увидеть детали.`);
      } else {
        console.log(`Автоматическое обновление для семестра "${selectedSemesterId}" успешно завершено в фоновом режиме.`);
      }

    } catch (error) {
      console.error("CoursesPageComponent: Error refreshing task estimates:", error);
      if (isManualCall) {
        setEstimatesRefreshError(error instanceof Error ? error.message : "Неизвестная ошибка при обновлении оценок.");
      }
    } finally {
      if (isManualCall) {
        setEstimatesRefreshing(false);
      }
    }
  }, [selectedSemesterId, token, semesters, formatSemesterName, isAutoRefreshing ]);

  console.log("CoursesPageComponent: Initial render. Token:", token);

  useEffect(() => {
    console.log("CoursesPageComponent: Semesters useEffect triggered. Token:", token, "Current selectedSemesterId:", selectedSemesterId);
    if (!token) {
      console.log("CoursesPageComponent: No token, skipping semester fetch.");
      setSemestersLoading(false);
      return;
    }

    let isMounted = true; 

    const fetchSemestersData = async () => {
      console.log("CoursesPageComponent: fetchSemestersData called.");
      
      if (semesters.length === 0 && !semestersLoading) {
           setSemestersLoading(true);
      }
      setSemestersError(null);

      try {
        console.log("CoursesPageComponent: Fetching /api/semesters...");
        const response = await fetch('/api/semesters', {
          headers: { 'Authorization': `Bearer ${token}` },
        });
        
        if (!response.ok) {
          if (response.status === 401) {
            if (isMounted) {
              console.error("CoursesPageComponent: Ошибка 401 при загрузке семестров. Перенаправление на /login");
              localStorage.removeItem('token');
              localStorage.removeItem('user');
              localStorage.removeItem('needsInitialParsing');
              router.push('/login');
            }
            return;
          }
          const errorText = await response.text();
          console.error("CoursesPageComponent: Error loading semesters - Response not OK:", response.status, errorText);
          throw new Error(`Ошибка загрузки семестров: ${response.status} ${errorText}`);
        }
        const data: SemesterDto[] = await response.json();
        
        if (isMounted) {
          console.log("CoursesPageComponent: Semesters data received:", data);
          setSemesters(data);

          if (data.length > 0) {
            const initialParse = searchParams.get('initialParseCompleted') === 'true';
            const currentSelectedIsValid = selectedSemesterId && data.some(s => s.id === selectedSemesterId);

            if (initialParse || !currentSelectedIsValid) {
              console.log(`CoursesPageComponent: Setting selectedSemesterId to first available: ${data[0].id} (initialParse: ${initialParse}, currentSelectedIsValid: ${currentSelectedIsValid})`);
              setSelectedSemesterId(data[0].id);
            } 
            else if (initialParse && currentSelectedIsValid && selectedSemesterId !== data[0].id) {
                 console.log(`CoursesPageComponent: initialParse=true, forcing selectedSemesterId to first available: ${data[0].id} even if current was valid.`);
                 setSelectedSemesterId(data[0].id);
            }
          } else {
            console.log("CoursesPageComponent: No semester data, setting selectedSemesterId to null.");
            setSelectedSemesterId(null);
          }

          setSemestersLoading(false); 
        }
      } catch (error) {
        if (isMounted) {
          console.error("CoursesPageComponent: Catch block - Error loading semesters:", error);
          setSemestersError(error instanceof Error ? error.message : String(error));
          setSemestersLoading(false);
        }
      }
    };

    fetchSemestersData();

    return () => {
      isMounted = false; 
    };
  }, [token, searchParams, router]);

  useEffect(() => {
    console.log("CoursesPageComponent: Subjects useEffect triggered. SelectedSemesterId:", selectedSemesterId, "Token:", token);
    if (!token) {
        console.log("CoursesPageComponent: No token, skipping subject fetch.");
        return;
    }

    if (!selectedSemesterId) {
        console.log("CoursesPageComponent: No selectedSemesterId, clearing subjects for all semesters and skipping fetch.");
        setSemesters(prevSemesters => prevSemesters.map(s =>
            s.subjects !== undefined ? { ...s, subjects: undefined, subjectsLoading: false, subjectsError: null } : s
        ));
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

  const STALE_THRESHOLD_MS = 24 * 60 * 60 * 1000; // 24 часа

  useEffect(() => {
    if (!selectedSemesterId || !token || estimatesRefreshing || isAutoRefreshing || semesters.length === 0) {
      return;
    }

    const latestSemester = semesters[0];
  
    if (selectedSemesterId !== latestSemester.id) {
      return;
    }

    const currentSemesterData = latestSemester;
    const lastRefreshString = currentSemesterData.lastAiRefreshTimestamp;
    const lastRefreshTime = lastRefreshString ? new Date(lastRefreshString).getTime() : 0;
    const now = new Date().getTime();

    if (!lastRefreshTime || (now - lastRefreshTime > STALE_THRESHOLD_MS)) {
      console.log(`Данные для ПОСЛЕДНЕГО семестра "${currentSemesterData.name}" устарели (ID: ${currentSemesterData.id}, последнее обновление: ${lastRefreshString || 'никогда'}). Запуск автоматического обновления.`);
      setIsAutoRefreshing(true);
      handleRefreshTaskEstimates().finally(() => {
        setIsAutoRefreshing(false);
        console.log(`Автоматическое обновление для ПОСЛЕДНЕГО семестра "${currentSemesterData.name}" завершено.`);
      });
    }
  }, [selectedSemesterId, semesters, token, estimatesRefreshing, isAutoRefreshing, handleRefreshTaskEstimates]);

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
  }, [expandedSubjectId, selectedSemesterId, token]);

  const getStatusColor = (status: string) => {
    if (!status) return 'text-slate-400';
    const lowerStatus = status.toLowerCase();
    if (lowerStatus.includes('оценено') || lowerStatus.includes('зачет')) return 'text-green-400';
    if (lowerStatus.includes('сдано')) return 'text-yellow-400';
    if (lowerStatus.includes('не сдано')) return 'text-red-400';
    return 'text-slate-400';
  };

  interface TaskTimeEstimateResponseDto {
    taskId: number;
    taskName?: string;
    estimatedMinutes: number | null;
    explanation: string | null;
  }

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
        {}
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

  const selectedSemesterDetails = getSemesterDates(selectedSemesterId);
  const formattedSelectedSemesterName = selectedSemesterId ? 
    semesters.find(s => s.id === selectedSemesterId)?.name || selectedSemesterId :
    null;
  
  const studyPlanSemesterDisplayId = selectedSemesterId ? selectedSemesterId.split('T')[0] : null;
  const studyPlanFormattedSemesterName = formattedSelectedSemesterName;

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-900 to-slate-800 text-slate-100 px-4 md:px-6 pt-2 md:pt-3 pb-4 md:pb-6 w-full">
      <nav className="mb-6 md:mb-8 flex justify-center space-x-1 sm:space-x-2 md:space-x-4 flex-wrap">
        {semesters.map((semester) => (
          <button
            key={semester.id}
            onClick={() => {
              setSelectedSemesterId(semester.id);
              setExpandedSubjectId(null); 
              setEstimatesRefreshError(null);
              setEstimatesRefreshSuccess(null);
            }}
            disabled={currentSelectedSemesterData?.subjectsLoading && selectedSemesterId === semester.id}
            className={`mb-2 px-2 py-1 sm:px-4 sm:py-2 md:px-6 md:py-3 text-xs sm:text-sm md:text-base font-medium rounded-lg transition-all duration-300 disabled:opacity-50 disabled:transform-none 
              ${
                selectedSemesterId === semester.id 
                  ? 'bg-sky-600 text-white shadow-lg transform scale-105' 
                  : 'bg-slate-700 hover:bg-slate-600 text-slate-300 hover:text-sky-300 cursor-pointer'
              }`}
          >
            {formatSemesterName(semester.name)}
          </button>
        ))}
      </nav>


      <div className="flex flex-col lg:flex-row gap-4 md:gap-6">
        <div className="lg:w-1/2 flex flex-col">
          {selectedSemesterId && currentSelectedSemesterData?.subjectsLoading && (
            <p className="text-center text-sky-400 text-lg my-4">Загрузка предметов для семестра "{currentSelectedSemesterData?.name}"...</p>
          )}
          {selectedSemesterId && currentSelectedSemesterData?.subjectsError && (
            <div className="text-center text-red-500 text-lg my-4 p-4 bg-red-900/30 rounded-md">
                <p className="font-semibold">Ошибка загрузки предметов:</p>
                <p>{currentSelectedSemesterData.subjectsError}</p>
            </div>
          )}

          {currentSelectedSemesterData && !currentSelectedSemesterData.subjectsLoading && !currentSelectedSemesterData.subjectsError && (
            <div className="space-y-4 md:space-y-6 flex-grow">
              {!currentSelectedSemesterData.subjects || currentSelectedSemesterData.subjects.length === 0 && (
                <div className="p-4 bg-slate-800 rounded-lg shadow-lg h-full flex items-center justify-center">
                   <p className="text-center text-slate-400 text-lg">В семестре "{currentSelectedSemesterData.name}" нет предметов.</p>
                </div>
              )}
              {currentSelectedSemesterData.subjects?.map((subject) => (
                <div key={subject.id} className="bg-slate-800 shadow-xl rounded-lg overflow-hidden">
                  <button
                    onClick={() => handleToggleSubject(subject.id)}
                    className="w-full flex justify-between items-center p-4 sm:p-5 text-left hover:bg-slate-700/50 transition-colors duration-200 focus:outline-none cursor-pointer"
                  >
                    <h2 className="text-lg sm:text-xl font-semibold text-sky-400">{subject.name}</h2>
                    {subject.tasksLoading ? (
                        <div className="animate-spin rounded-full h-5 w-5 border-b-2 border-sky-400"></div>
                    ) : expandedSubjectId === subject.id ? (
                      <ChevronUpIcon className="h-5 w-5 sm:h-6 sm:w-6 text-sky-500" />
                    ) : (
                      <ChevronDownIcon className="h-5 w-5 sm:h-6 sm:w-6 text-sky-500" />
                    )}
                  </button>
                  
                  {expandedSubjectId === subject.id && (
                    <div className="border-t border-slate-700 px-4 sm:px-5 py-3 sm:py-4 bg-slate-800/50">
                      {subject.tasksLoading && <p className="text-sky-400">Загрузка заданий...</p>}
                      {subject.tasksError && <p className="text-red-500">Ошибка загрузки заданий: {subject.tasksError}</p>}
                      {!subject.tasksLoading && !subject.tasksError && (
                        !subject.tasks || subject.tasks.length === 0 ? (
                          <p className="text-slate-400">По этому предмету пока нет заданий.</p>
                        ) : (
                          <ul className="space-y-2 sm:space-y-3">
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
                                displayedStatusText = "Не оценено";
                                statusColorClass = getStatusColor("Не сдано");
                              } else if (gradeFromBackend.toLowerCase() === "оценено" || gradeFromBackend.toLowerCase() === "зачет") {
                                displayedStatusText = "Не оценено";
                                statusColorClass = getStatusColor("Не сдано");
                              } else {
                                displayedStatusText = originalBackendStatus;
                                statusColorClass = getStatusColor(originalBackendStatus);
                                displayedGradeText = gradeFromBackend;
                              }

                              return (
                                <li key={task.id} className="p-2 sm:p-3 bg-slate-700/70 rounded-md shadow">
                                  <div className="flex flex-col sm:flex-row justify-between items-start mb-1">
                                    <h3 className="text-base sm:text-lg font-medium text-slate-100 mb-1 sm:mb-0">
                                      {task.name.endsWith(" Задание") ? task.name.slice(0, -8) : task.name}
                                    </h3>
                                    {task.deadline && (
                                      <span className="text-xs sm:text-sm text-slate-400 whitespace-nowrap">
                                        Срок: {new Date(task.deadline + 'T00:00:00').toLocaleDateString('ru-RU')}
                                      </span>
                                    )}
                                  </div>
                                  <div className="flex flex-col sm:flex-row justify-between items-start sm:items-end text-xs sm:text-sm mt-1">
                                    <span className={`font-semibold ${statusColorClass}`}>
                                      {displayedStatusText}
                                    </span>
                                    {estimatedTimeText && (
                                      <span className="text-slate-400 mt-1 sm:mt-0 sm:ml-2 md:ml-4 italic">
                                        Оценка ИИ: ~{estimatedTimeText}
                                      </span>
                                    )}
                                    {displayedGradeText && (
                                      <span className="text-slate-300 mt-1 sm:mt-0">
                                         {estimatedTimeText ? ' / ' : ''}Оценка: {displayedGradeText}
                                      </span>
                                    )}
                                  </div>
                                  {task.timeEstimateExplanation && (
                                    <div className="mt-1.5 sm:mt-2 text-xs sm:text-sm text-slate-400 pl-2 border-l-2 border-slate-600">
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

              {currentSelectedSemesterData?.subjects && currentSelectedSemesterData.subjects.length > 0 && (
                <div className="mt-6 md:mt-8 pt-4 md:pt-6 border-t border-slate-700 text-center">
                  <button
                    onClick={handleRefreshTaskEstimates}
                    disabled={estimatesRefreshing}
                    className="w-full max-w-md mx-auto flex justify-center bg-purple-600 hover:bg-purple-700 text-white font-semibold py-2.5 px-5 md:py-3 md:px-6 rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500 focus:ring-offset-2 focus:ring-offset-slate-900 transition-all duration-300 ease-in-out transform hover:scale-105 disabled:opacity-70 disabled:cursor-not-allowed cursor-pointer"
                  >
                    {estimatesRefreshing ? (
                      <span className="flex items-center">
                        <svg className="animate-spin -ml-1 mr-3 h-5 w-5 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                        </svg>
                        Обновляем информацию...
                      </span>
                    ) : (
                      'Актуализировать семестр (ИИ)'
                    )}
                  </button>
                  {estimatesRefreshError && (
                    <div className="mt-4 md:mt-6 p-3 md:p-4 bg-red-900/30 border border-red-700 rounded-md text-red-400 max-w-md mx-auto">
                      <p className="font-semibold">Ошибка обновления оценок:</p>
                      <p>{estimatesRefreshError}</p> 
                    </div>
                  )}
                  {estimatesRefreshSuccess && !estimatesRefreshError && (
                    <div className="mt-4 md:mt-6 p-3 md:p-4 bg-green-900/30 border border-green-700 rounded-md text-green-400 max-w-md mx-auto">
                      <p>{estimatesRefreshSuccess}</p>
                    </div>
                  )}
                </div>
              )}
            </div>
          )}
           {!selectedSemesterId && semesters.length > 0 && !semestersLoading && !currentSelectedSemesterData?.subjectsLoading && (
             <div className="p-4 bg-slate-800 rounded-lg shadow-lg h-full flex items-center justify-center">
                <p className="text-center text-slate-400 text-lg">Выберите семестр для просмотра предметов и плана.</p>
             </div>
           )}
        </div>

        <div className="lg:w-1/2 flex flex-col">
          <StudyPlanComponent
            semesterId={studyPlanSemesterDisplayId}
            formattedSemesterName={studyPlanFormattedSemesterName}
            semesterStartDate={selectedSemesterDetails.startDate}
            semesterEndDate={selectedSemesterDetails.endDate}
          />
        </div>
      </div>
    </div>
  );
}

export { CoursesPageComponent };

export default function CoursesPage() {
  return (
    <ProtectedRoute>
      <CoursesPageComponent />
    </ProtectedRoute>
  );
} 