package back.services;

import back.dto.PlannedDayDto;
import back.dto.PlannedTaskDto;
import back.dto.StudyPlanResponse;
import back.dto.StudyPlanWarningDto;
import back.entities.Task;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudyPlanService {

    private static final int DEFAULT_MAX_MINUTES_PER_DAY = 3 * 60; // 180 минут
    private static final int DEADLINE_MAX_MINUTES_PER_DAY = 6 * 60; // 360 минут
    private static final int DAYS_BEFORE_DEADLINE_TO_INCREASE_HOURS = 3;

    public StudyPlanResponse generateStudyPlan(List<Task> tasks, LocalDate semesterStartDate, LocalDate planStartDate) {
        List<PlannedDayDto> plannedDays = new ArrayList<>();
        List<StudyPlanWarningDto> warnings = new ArrayList<>();

        List<Task> validTasks = tasks.stream()
                .filter(task -> task.getEstimatedMinutes() != null && task.getEstimatedMinutes() > 0)
                .collect(Collectors.toList());

        validTasks.sort(Comparator
            .comparing((Task task) -> task.getDeadline() == null)
            .thenComparing(task -> {
                if (task.getDeadline() != null) {
                    return task.getDeadline().toInstant();
                }
                return null;
            }, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Task::getSource, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Task::getId, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Task::getEstimatedMinutes, Comparator.nullsLast(Comparator.naturalOrder()))
        );

        LocalDate currentPlanningDate = planStartDate;
        int dayNumber = 1;

        List<Integer> remainingMinutesForTasks = validTasks.stream()
                .map(Task::getEstimatedMinutes)
                .collect(Collectors.toList());

        for (int i = 0; i < validTasks.size(); i++) {
            Task currentTask = validTasks.get(i);
            int totalMinutesForCurrentTask = remainingMinutesForTasks.get(i);
            if (totalMinutesForCurrentTask <= 0) continue;

            LocalDateTime taskDeadline = null;
            if (currentTask.getDeadline() != null) {
                taskDeadline = LocalDateTime.ofInstant(currentTask.getDeadline().toInstant(), ZoneId.systemDefault());
            }

            while (totalMinutesForCurrentTask > 0) {
                PlannedDayDto currentDayPlan = findOrCreateDayPlan(plannedDays, currentPlanningDate, dayNumber);
                if (currentDayPlan == null) {
                    currentDayPlan = PlannedDayDto.builder()
                            .date(currentPlanningDate)
                            .dayNumber(dayNumber)
                            .tasks(new ArrayList<>())
                            .totalMinutesScheduledThisDay(0)
                            .build();
                    plannedDays.add(currentDayPlan);
                }

                int maxMinutesForToday = DEFAULT_MAX_MINUTES_PER_DAY;
                long daysUntilDeadline = Long.MAX_VALUE;

                if (taskDeadline != null) {
                    daysUntilDeadline = Duration.between(currentPlanningDate.atStartOfDay(), taskDeadline).toDays();
                    if (daysUntilDeadline <= DAYS_BEFORE_DEADLINE_TO_INCREASE_HOURS && daysUntilDeadline >= 0) {
                        int minutesNeededPerDayToMeetDeadline = (int) Math.ceil((double) totalMinutesForCurrentTask / (daysUntilDeadline + 1));
                        if (minutesNeededPerDayToMeetDeadline > maxMinutesForToday) {
                            maxMinutesForToday = Math.min(DEADLINE_MAX_MINUTES_PER_DAY, Math.max(maxMinutesForToday, minutesNeededPerDayToMeetDeadline));
                        }
                    } else if (daysUntilDeadline < 0) {
                        maxMinutesForToday = DEFAULT_MAX_MINUTES_PER_DAY;
                    }
                }
                
                int availableMinutesToday = maxMinutesForToday - currentDayPlan.getTotalMinutesScheduledThisDay();
                if (availableMinutesToday <= 0) {
                    currentPlanningDate = currentPlanningDate.plusDays(1);
                    dayNumber++;
                    continue; 
                }

                int minutesToScheduleForTaskToday = Math.min(totalMinutesForCurrentTask, availableMinutesToday);

                String taskName = currentTask.getName();
                String suffixToRemove = " Задание";
                if (taskName != null && taskName.endsWith(suffixToRemove)) {
                    taskName = taskName.substring(0, taskName.length() - suffixToRemove.length());
                }

                PlannedTaskDto plannedTaskDto = PlannedTaskDto.builder()
                        .taskId(currentTask.getId())
                        .taskName(taskName)
                        .subjectName(currentTask.getSubject() != null ? currentTask.getSubject().getName() : "Без предмета")
                        .minutesScheduledToday(minutesToScheduleForTaskToday)
                        .deadline(taskDeadline)
                        .build();

                currentDayPlan.getTasks().add(plannedTaskDto);
                currentDayPlan.setTotalMinutesScheduledThisDay(currentDayPlan.getTotalMinutesScheduledThisDay() + minutesToScheduleForTaskToday);
                totalMinutesForCurrentTask -= minutesToScheduleForTaskToday;

                remainingMinutesForTasks.set(i, totalMinutesForCurrentTask);

                if (totalMinutesForCurrentTask <= 0 || currentDayPlan.getTotalMinutesScheduledThisDay() >= maxMinutesForToday) {
                    if (currentDayPlan.getTotalMinutesScheduledThisDay() >= maxMinutesForToday && totalMinutesForCurrentTask > 0) {
                        currentPlanningDate = currentPlanningDate.plusDays(1);
                        dayNumber++;
                    }
                }
            }
            if (taskDeadline != null && currentPlanningDate.atStartOfDay().isAfter(taskDeadline) && remainingMinutesForTasks.get(i) <=0) {
                 warnings.add(StudyPlanWarningDto.builder()
                                .taskId(currentTask.getId())
                                .taskName(currentTask.getName())
                                .message("Задача была завершена " + currentPlanningDate +
                                         ", но ее дедлайн был " + taskDeadline.toLocalDate())
                                .build());
            }
        }

        List<Integer> finalRemainingMinutes = tasks.stream()
                .map(task -> (task.getEstimatedMinutes() != null ? task.getEstimatedMinutes() : 0))
                .collect(Collectors.toList());

        for (PlannedDayDto day : plannedDays) {
            for (PlannedTaskDto plannedTask : day.getTasks()) {
                int taskIndex = -1;
                for(int k=0; k < tasks.size(); k++) {
                    if(tasks.get(k).getId().equals(plannedTask.getTaskId())) {
                        taskIndex = k;
                        break;
                    }
                }
                if (taskIndex != -1) {
                    int alreadyScheduledOnThisDayInMinutes = plannedTask.getMinutesScheduledToday();
                    plannedTask.setMinutesRemainingForTask(Math.max(0, finalRemainingMinutes.get(taskIndex) - alreadyScheduledOnThisDayInMinutes));
                    finalRemainingMinutes.set(taskIndex, Math.max(0, finalRemainingMinutes.get(taskIndex) - alreadyScheduledOnThisDayInMinutes));
                }
            }
        }
        plannedDays.removeIf(day -> day.getTasks().isEmpty());

        return StudyPlanResponse.builder()
                .semesterStartDate(semesterStartDate)
                .plannedDays(plannedDays)
                .warnings(warnings)
                .totalTasksConsideredForPlanning(validTasks.size())
                .build();
    }

    private PlannedDayDto findOrCreateDayPlan(List<PlannedDayDto> plannedDays, LocalDate date, int dayNumber) {
        return plannedDays.stream()
                .filter(day -> day.getDate().equals(date))
                .findFirst()
                .orElse(null); // Если не нашли, вернем null, чтобы создать новый
    }
} 