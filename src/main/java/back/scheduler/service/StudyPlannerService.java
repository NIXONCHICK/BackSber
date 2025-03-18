package back.scheduler.service;

import back.dto.StudyPlanResponse;
import back.entities.Person;
import back.entities.Task;
import back.entities.TaskSource;
import back.repositories.TaskRepository;
import back.scheduler.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.optaplanner.core.api.solver.SolverManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class StudyPlannerService {

    private final TaskRepository taskRepository;
    private final TaskPartitioningService taskPartitioningService;
    
    @Autowired
    private SolverManager<StudySchedule, Long> solverManager;
    

    public StudyPlanResponse createStudyPlan(Person currentUser, LocalDate date) {
        log.info("Создание учебного плана для пользователя {} на семестр с датой {}", currentUser.getId(), date);
        
        LocalDate semesterStart;
        LocalDate semesterEnd;
        
        int year = date.getYear();
        int month = date.getMonthValue();
        
        if (month >= 9 && month <= 12) {
            semesterStart = LocalDate.of(year, 9, 1);
            semesterEnd = LocalDate.of(year, 12, 31);
        } else if (month >= 1 && month <= 6) {
            semesterStart = LocalDate.of(year, 2, 1);
            semesterEnd = LocalDate.of(year, 6, 30);
        } else {
            semesterStart = LocalDate.of(year, 6, 1);
            semesterEnd = LocalDate.of(year, 8, 31);
        }
        
        log.info("Определен семестр с {} по {}", semesterStart, semesterEnd);
        
        return createStudyPlan(currentUser, semesterStart, semesterEnd);
    }
    

    public StudyPlanResponse createStudyPlan(Person currentUser, LocalDate semesterStart, LocalDate semesterEnd) {
        log.info("Создание учебного плана для пользователя {} на семестр с {} по {}", 
                 currentUser.getId(), semesterStart, semesterEnd);
        
        List<Task> tasks = getTasksForSemester(currentUser, semesterStart, semesterEnd);
        log.info("Найдено {} заданий для планирования", tasks.size());
        
        if (tasks.isEmpty()) {
            return StudyPlanResponse.builder()
                    .message("У вас нет заданий для планирования в этом семестре")
                    .totalTasks(0)
                    .plannedTasks(0)
                    .build();
        }
        
        List<StudyDay> studyDays = createStudyDays(semesterStart, semesterEnd);
        
        List<TaskChain> taskChains = taskPartitioningService.createTaskChains(tasks);
        log.info("Создано {} цепей задач", taskChains.size());
        
        List<TaskPart> taskParts = taskPartitioningService.createTaskParts(taskChains);
        log.info("Создано {} частей задач для планирования", taskParts.size());
        
        List<TaskTimeWarning> timeWarnings = checkTimeWarnings(tasks, semesterStart, semesterEnd);
        
        List<StudyAssignment> assignments = createAssignments(tasks);
        
        StudySchedule problem = new StudySchedule();
        problem.setId(1L);
        problem.setSemesterStartDate(semesterStart);
        problem.setSemesterEndDate(semesterEnd);
        problem.setAvailableDays(studyDays);
        problem.setTasks(tasks);
        problem.setAssignments(assignments);
        problem.setTaskChains(taskChains);
        problem.setTaskParts(taskParts);
        problem.setTimeWarnings(timeWarnings);
        
        StudySchedule solution = solveStudySchedule(problem);
        
        return createResponseFromTaskParts(solution);
    }
    
    private List<Task> getTasksForSemester(Person currentUser, LocalDate semesterStart, LocalDate semesterEnd) {
        List<Task> allTasks = taskRepository.findTasksByPersonId(currentUser.getId());
        
        return allTasks.stream()
                .filter(task -> {
                    if (task.getDeadline() != null) {
                        LocalDate deadlineDate = new java.sql.Date(task.getDeadline().getTime()).toLocalDate();
                        return !deadlineDate.isBefore(semesterStart) && !deadlineDate.isAfter(semesterEnd);
                    }
                    
                    if (task.getSubject() != null && task.getSubject().getSemesterDate() != null) {
                        LocalDate subjectSemesterDate = task.getSubject().getSemesterDate().toLocalDate();
                        return subjectSemesterDate.getYear() == semesterStart.getYear() &&
                               ((subjectSemesterDate.getMonthValue() >= 9 && semesterStart.getMonthValue() >= 9) ||
                                (subjectSemesterDate.getMonthValue() >= 1 && subjectSemesterDate.getMonthValue() <= 6 &&
                                 semesterStart.getMonthValue() >= 1 && semesterStart.getMonthValue() <= 6));
                    }
                    
                    return false;
                })
                .filter(task -> task.getSource() == TaskSource.PARSED)
                .filter(task -> task.getEstimatedMinutes() != null && task.getEstimatedMinutes() > 0)
                .collect(Collectors.toList());
    }
    

    private List<StudyDay> createStudyDays(LocalDate semesterStart, LocalDate semesterEnd) {
        List<StudyDay> days = new ArrayList<>();
        LocalDate currentDate = semesterStart;
        
        while (!currentDate.isAfter(semesterEnd)) {
            days.add(new StudyDay(currentDate));
            currentDate = currentDate.plusDays(1);
        }
        
        return days;
    }
    

    private List<StudyAssignment> createAssignments(List<Task> tasks) {
        List<StudyAssignment> assignments = new ArrayList<>();
        
        for (int i = 0; i < tasks.size(); i++) {
            Task task = tasks.get(i);
            assignments.add(new StudyAssignment((long) (i + 1), task));
        }
        
        return assignments;
    }
    

    private List<TaskTimeWarning> checkTimeWarnings(List<Task> tasks, LocalDate semesterStart, LocalDate currentDate) {
        List<TaskTimeWarning> warnings = new ArrayList<>();
        
        for (Task task : tasks) {
            if (task.getDeadline() == null || task.getEstimatedMinutes() == null) {
                continue;
            }
            
            LocalDate deadlineDate = new java.sql.Date(task.getDeadline().getTime()).toLocalDate();
            long daysUntilDeadline = ChronoUnit.DAYS.between(currentDate, deadlineDate);
            
            if (daysUntilDeadline <= 0) {
                continue;
            }
            
            int requiredMinutes = task.getEstimatedMinutes();
            int availableMinutes = (int) daysUntilDeadline * 180;
            
            if (requiredMinutes > availableMinutes) {
                warnings.add(new TaskTimeWarning(
                    task, deadlineDate, requiredMinutes, availableMinutes, (int) daysUntilDeadline
                ));
            }
        }
        
        return warnings;
    }
    

    private StudySchedule solveStudySchedule(StudySchedule problem) {
        try {
            return solverManager.solve(1L, problem).getFinalBestSolution();
        } catch (ExecutionException | InterruptedException e) {
            log.error("Ошибка при планировании учебного расписания", e);
            throw new RuntimeException("Не удалось создать оптимальный учебный план: " + e.getMessage());
        }
    }
    

    private StudyPlanResponse createResponseFromTaskParts(StudySchedule solution) {
        int totalTasks = solution.getTasks().size();
        int plannedTasks = 0;
        Set<Long> plannedTaskIds = new HashSet<>();
        
        Map<LocalDate, List<TaskPart>> partsByDay = new HashMap<>();
        
        for (TaskPart part : solution.getTaskParts()) {
            if (part.getAssignedDay() != null) {
                LocalDate date = part.getAssignedDay().getDate();
                partsByDay.computeIfAbsent(date, k -> new ArrayList<>()).add(part);
            }
        }
        
        List<StudyPlanResponse.DailyPlan> dailyPlans = new ArrayList<>();
        
        for (Map.Entry<LocalDate, List<TaskPart>> entry : partsByDay.entrySet()) {
            LocalDate date = entry.getKey();
            List<TaskPart> dayParts = entry.getValue();
            
            List<StudyPlanResponse.TaskPlan> taskPlans = new ArrayList<>();
            int totalMinutes = 0;
            
            for (TaskPart part : dayParts) {
                TaskChain chain = part.getTaskChain();
                if (chain == null) continue;
                
                Task task = chain.getTask();
                
                if (!plannedTaskIds.contains(task.getId())) {
                    plannedTaskIds.add(task.getId());
                    plannedTasks++;
                }
                
                String taskName = task.getName();
                if (part.getPartIndex() > 1) {
                    taskName += " (часть " + part.getPartIndex() + ")";
                }
                
                taskPlans.add(StudyPlanResponse.TaskPlan.builder()
                        .taskId(task.getId())
                        .taskName(taskName)
                        .subjectName(task.getSubject() != null ? task.getSubject().getName() : "")
                        .deadline(task.getDeadline())
                        .durationMinutes(part.getDurationMinutes())
                        .build());
                
                totalMinutes += part.getDurationMinutes();
            }
            
            dailyPlans.add(StudyPlanResponse.DailyPlan.builder()
                    .date(date)
                    .tasks(taskPlans)
                    .totalMinutes(totalMinutes)
                    .maxMinutes(180) // 3 часа
                    .build());
        }
        
        dailyPlans.sort(Comparator.comparing(StudyPlanResponse.DailyPlan::getDate));
        
        List<String> warnings = new ArrayList<>();
        
        if (solution.getTimeWarnings() != null) {
            for (TaskTimeWarning warning : solution.getTimeWarnings()) {
                warnings.add(warning.getWarningMessage());
            }
        }
        
        String message;
        if (plannedTasks == totalTasks) {
            message = "Создан оптимальный план изучения для всех ваших заданий";
        } else {
            message = String.format("Создан частичный план изучения для %d из %d заданий. " +
                    "Некоторые задания невозможно запланировать из-за ограничений времени.",
                    plannedTasks, totalTasks);
        }
        
        return StudyPlanResponse.builder()
                .message(message)
                .dailyPlans(dailyPlans)
                .warnings(warnings)
                .totalTasks(totalTasks)
                .plannedTasks(plannedTasks)
                .build();
    }
} 