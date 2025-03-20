package back.scheduler.service;

import back.entities.Subject;
import back.entities.Task;
import back.entities.TaskSource;
import back.repositories.TaskRepository;
import back.scheduler.domain.SemesterAssignment;
import back.scheduler.domain.SemesterSchedule;
import back.scheduler.dto.ScheduleResponse;
import back.scheduler.dto.TaskScheduleDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.optaplanner.core.api.solver.SolverJob;
import org.optaplanner.core.api.solver.SolverManager;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SemesterSchedulerService {

    private final TaskRepository taskRepository;
    private final SolverManager<SemesterSchedule, Long> solverManager;

    public ScheduleResponse generateSemesterSchedule(Long userId, int year, int month) {
        LocalDate semesterStart;
        LocalDate semesterEnd;

        if (month >= 9 && month <= 12) {
            semesterStart = LocalDate.of(year, 9, 1);
            semesterEnd = LocalDate.of(year, 12, 31);
        } else if (month >= 1 && month <= 6) {
            semesterStart = LocalDate.of(year, 2, 1);
            semesterEnd = LocalDate.of(year, 6, 30);
        } else {
            throw new IllegalArgumentException("Недопустимый месяц для начала семестра: " + month);
        }

        log.info("Generating schedule for userId={} from {} to {}", userId, semesterStart, semesterEnd);

        List<Task> userTasks = getUserTasks(userId, semesterStart, semesterEnd);
        
        if (userTasks.isEmpty()) {
            log.warn("No tasks found for user {} in the specified semester period", userId);
            return new ScheduleResponse(new ArrayList<>(), "Нет заданий для планирования в указанном семестре");
        }

        List<SemesterAssignment> assignments = new ArrayList<>();
        for (int i = 0; i < userTasks.size(); i++) {
            assignments.add(new SemesterAssignment((long) i, userTasks.get(i)));
        }

        SemesterSchedule problem = new SemesterSchedule(semesterStart, semesterEnd, userId, assignments);

        try {
            SolverJob<SemesterSchedule, Long> solverJob = solverManager.solve(userId, problem);
            SemesterSchedule solution = solverJob.getFinalBestSolution();

            List<TaskScheduleDto> scheduledTasks = solution.getAssignments().stream()
                    .filter(assignment -> assignment.getAssignedDay() != null)
                    .map(this::convertToTaskScheduleDto)
                    .collect(Collectors.toList());

            log.info("Generated schedule with {} tasks for user {}", scheduledTasks.size(), userId);
            return new ScheduleResponse(scheduledTasks, "План семестра успешно составлен");
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error solving semester scheduling problem", e);
            return new ScheduleResponse(new ArrayList<>(), "Ошибка при составлении плана: " + e.getMessage());
        }
    }

    private List<Task> getUserTasks(Long userId, LocalDate semesterStart, LocalDate semesterEnd) {
        Date startDate = Date.from(semesterStart.atStartOfDay(ZoneId.systemDefault()).toInstant());
        List<Task> tasks = taskRepository.findTasksWithDeadlinesForUser(startDate, userId);

        return tasks.stream()
                .filter(task -> task.getSource() == TaskSource.PARSED && 
                        task.getSubject() != null &&
                        isInSemester(task.getSubject(), semesterStart))
                .collect(Collectors.toList());
    }

    private boolean isInSemester(Subject subject, LocalDate semesterStart) {
        if (subject.getSemesterDate() == null) {
            return false;
        }
        
        LocalDate subjectSemesterDate = subject.getSemesterDate().toLocalDate();
        
        if (subjectSemesterDate.getYear() == semesterStart.getYear()) {
            int subjectMonth = subjectSemesterDate.getMonthValue();
            int startMonth = semesterStart.getMonthValue();
            
            return subjectMonth >= 9 && startMonth >= 9 || subjectMonth <= 6 && startMonth <= 6;
        }
        
        return false;
    }
    
    private TaskScheduleDto convertToTaskScheduleDto(SemesterAssignment assignment) {
        Task task = assignment.getTask();
        return TaskScheduleDto.builder()
                .taskId(task.getId())
                .taskName(task.getName())
                .subjectName(task.getSubject() != null ? task.getSubject().getName() : "Без предмета")
                .deadline(task.getDeadline())
                .assignedDate(assignment.getAssignedDay())
                .durationMinutes(assignment.getDurationMinutes())
                .build();
    }
} 