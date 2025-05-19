package back.services;

import back.dto.PlannedDayDto;
import back.dto.PlannedTaskDto;
import back.dto.StudyPlanResponse;
import back.dto.StudyPlanWarningDto;
import back.dto.TaskForStudyPlanDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudyPlanService {

    private static final int DEFAULT_DAILY_MINUTES_PER_DAY = 3 * 60; // 180 минут (дефолтное значение, если пользователь не указал)
    private static final int DEADLINE_INCREASE_MAX_MINUTES_PER_DAY = 6 * 60; // Максимальный потолок для дней перед дедлайном
    private static final int DAYS_BEFORE_DEADLINE_TO_INCREASE_HOURS = 3;

    public StudyPlanResponse generateStudyPlan(
            List<TaskForStudyPlanDto> tasksDtos,
            LocalDate semesterStartDate, 
            LocalDate planStartDate, 
            Boolean ignoreCompleted, 
            Integer dailyHours) {
        List<PlannedDayDto> plannedDays = new ArrayList<>();
        List<StudyPlanWarningDto> warnings = new ArrayList<>();

        final int actualDailyMinutes = (dailyHours != null && dailyHours > 0) ? dailyHours * 60 : DEFAULT_DAILY_MINUTES_PER_DAY;

        List<TaskForStudyPlanDto> filteredTasks = tasksDtos.stream()
                .filter(taskDto -> taskDto.getEstimatedMinutes() != null && taskDto.getEstimatedMinutes() > 0)
                .filter(taskDto -> {
                    if (Boolean.TRUE.equals(ignoreCompleted)) {
                        return !("Оценено".equalsIgnoreCase(taskDto.getStatus()) || "Зачет".equalsIgnoreCase(taskDto.getStatus()));
                    }
                    return true;
                })
                .filter(taskDto -> {
                    if (taskDto.getDeadlineForPlanning() != null) {
                        return !taskDto.getDeadlineForPlanning().toLocalDate().isBefore(planStartDate);
                    }
                    return true;
                })
                .collect(Collectors.toList());

        filteredTasks.sort(Comparator
            .comparing((TaskForStudyPlanDto taskDto) -> taskDto.getDeadlineForPlanning() == null)
            .thenComparing(TaskForStudyPlanDto::getDeadlineForPlanning, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(TaskForStudyPlanDto::getId, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(TaskForStudyPlanDto::getEstimatedMinutes, Comparator.nullsLast(Comparator.naturalOrder()))
        );

        LocalDate currentPlanningDate = planStartDate;
        int dayNumber = 1;

        List<Integer> remainingMinutesForTasks = filteredTasks.stream()
                .map(TaskForStudyPlanDto::getEstimatedMinutes)
                .collect(Collectors.toList());

        for (int i = 0; i < filteredTasks.size(); i++) {
            TaskForStudyPlanDto currentTaskDto = filteredTasks.get(i);
            int totalMinutesForCurrentTask = remainingMinutesForTasks.get(i);
            if (totalMinutesForCurrentTask <= 0) continue;

            LocalDateTime taskDeadline = currentTaskDto.getDeadlineForPlanning();

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

                int maxMinutesForThisDay = actualDailyMinutes;
                long daysUntilDeadline = Long.MAX_VALUE;

                if (taskDeadline != null) {
                    daysUntilDeadline = Duration.between(currentPlanningDate.atStartOfDay(), taskDeadline).toDays();
                    if (daysUntilDeadline <= DAYS_BEFORE_DEADLINE_TO_INCREASE_HOURS && daysUntilDeadline >= 0) {
                        int minutesNeededPerDayToMeetDeadline = (int) Math.ceil((double) totalMinutesForCurrentTask / (daysUntilDeadline + 1));
                        if (minutesNeededPerDayToMeetDeadline > actualDailyMinutes) {
                           maxMinutesForThisDay = Math.min(DEADLINE_INCREASE_MAX_MINUTES_PER_DAY, Math.max(actualDailyMinutes, minutesNeededPerDayToMeetDeadline));
                        } else {
                           maxMinutesForThisDay = Math.max(actualDailyMinutes, minutesNeededPerDayToMeetDeadline); 
                        }
                        maxMinutesForThisDay = Math.max(maxMinutesForThisDay, Math.min(actualDailyMinutes, minutesNeededPerDayToMeetDeadline));
                        maxMinutesForThisDay = Math.min(maxMinutesForThisDay, DEADLINE_INCREASE_MAX_MINUTES_PER_DAY);

                    } else if (daysUntilDeadline < 0) {
                        warnings.add(StudyPlanWarningDto.builder()
                                .taskId(currentTaskDto.getId())
                                .taskName(currentTaskDto.getName())
                                .message("Дедлайн задачи ('" + taskDeadline.toLocalDate() + "') уже прошел, но она все еще планируется.")
                                .build());
                    }
                }
                
                int availableMinutesToday = maxMinutesForThisDay - currentDayPlan.getTotalMinutesScheduledThisDay();
                
                if (availableMinutesToday <= 0) {
                    currentPlanningDate = currentPlanningDate.plusDays(1);
                    dayNumber++;
                    continue; 
                }

                int minutesToScheduleForTaskToday = Math.min(totalMinutesForCurrentTask, availableMinutesToday);

                String taskName = currentTaskDto.getName();
                String suffixToRemove = " Задание";
                if (taskName != null && taskName.endsWith(suffixToRemove)) {
                    taskName = taskName.substring(0, taskName.length() - suffixToRemove.length());
                }

                PlannedTaskDto plannedTaskDto = PlannedTaskDto.builder()
                        .taskId(currentTaskDto.getId())
                        .taskName(taskName)
                        .subjectName(currentTaskDto.getSubjectName())
                        .minutesScheduledToday(minutesToScheduleForTaskToday)
                        .deadline(taskDeadline)
                        .build();

                currentDayPlan.getTasks().add(plannedTaskDto);
                currentDayPlan.setTotalMinutesScheduledThisDay(currentDayPlan.getTotalMinutesScheduledThisDay() + minutesToScheduleForTaskToday);
                totalMinutesForCurrentTask -= minutesToScheduleForTaskToday;

                remainingMinutesForTasks.set(i, totalMinutesForCurrentTask);

                if (currentDayPlan.getTotalMinutesScheduledThisDay() >= maxMinutesForThisDay && totalMinutesForCurrentTask > 0) {
                    currentPlanningDate = currentPlanningDate.plusDays(1);
                    dayNumber++;
                } else if (currentDayPlan.getTotalMinutesScheduledThisDay() >= maxMinutesForThisDay && totalMinutesForCurrentTask <= 0){
                    currentPlanningDate = currentPlanningDate.plusDays(1);
                    dayNumber++;
                }
            }
        }

        List<Integer> initialEstimatedMinutes = filteredTasks.stream()
            .map(dto -> dto.getEstimatedMinutes() != null ? dto.getEstimatedMinutes() : 0)
            .collect(Collectors.toList());

        List<Integer> totalScheduledMinutesPerTask = new ArrayList<>(Collections.nCopies(filteredTasks.size(), 0));

        for (PlannedDayDto day : plannedDays) {
            for (PlannedTaskDto plannedTask : day.getTasks()) {
                int taskIndex = -1;
                for(int k=0; k < filteredTasks.size(); k++) {
                    if(filteredTasks.get(k).getId().equals(plannedTask.getTaskId())) {
                        taskIndex = k;
                        break;
                    }
                }
                if (taskIndex != -1) {
                    totalScheduledMinutesPerTask.set(taskIndex, totalScheduledMinutesPerTask.get(taskIndex) + plannedTask.getMinutesScheduledToday());
                }
            }
        }
        
        for (PlannedDayDto day : plannedDays) {
            for (PlannedTaskDto plannedTask : day.getTasks()) {
                int taskIndex = -1;
                 for(int k=0; k < filteredTasks.size(); k++) {
                    if(filteredTasks.get(k).getId().equals(plannedTask.getTaskId())) {
                        taskIndex = k;
                        break;
                    }
                }
                if (taskIndex != -1) {
                    int totalEstimate = initialEstimatedMinutes.get(taskIndex);
                    int totalScheduled = totalScheduledMinutesPerTask.get(taskIndex);
                    plannedTask.setMinutesRemainingForTask(Math.max(0, totalEstimate - totalScheduled));
                }
            }
        }

        plannedDays.removeIf(day -> day.getTasks().isEmpty());
        
        return StudyPlanResponse.builder()
                .semesterStartDate(semesterStartDate)
                .planStartDate(planStartDate)
                .plannedDays(plannedDays)
                .warnings(warnings)
                .totalTasksConsideredForPlanning(filteredTasks.size())
                .build();
    }

    private PlannedDayDto findOrCreateDayPlan(List<PlannedDayDto> plannedDays, LocalDate date, int dayNumber) {
        return plannedDays.stream()
                .filter(day -> day.getDate().equals(date))
                .findFirst()
                .orElse(null);
    }
} 