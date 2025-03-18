package back.scheduler.constraint;

import back.scheduler.domain.StudyDay;
import back.scheduler.domain.TaskChain;
import back.scheduler.domain.TaskPart;
import org.optaplanner.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import org.optaplanner.core.api.score.stream.Constraint;
import org.optaplanner.core.api.score.stream.ConstraintFactory;
import org.optaplanner.core.api.score.stream.ConstraintProvider;
import org.optaplanner.core.api.score.stream.Joiners;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static org.optaplanner.core.api.score.stream.ConstraintCollectors.count;
import static org.optaplanner.core.api.score.stream.ConstraintCollectors.sum;

/**
 * Провайдер ограничений для планирования частей заданий с последовательным выполнением
 */
public class TaskPartConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory constraintFactory) {
        return new Constraint[] {
            deadlineConstraint(constraintFactory),
            sequenceConstraint(constraintFactory),
            continuousTaskExecution(constraintFactory),
            maxDailyStudyTimeHardConstraint(constraintFactory),
            onlyOneSubjectPerDay(constraintFactory),
            preventTaskPartsOnSameDay(constraintFactory),
            taskOrderingByIdConstraint(constraintFactory),
            enforceSameTaskPartsOrder(constraintFactory),
            
            evenDistributionConstraint(constraintFactory),
            preventTaskClustering(constraintFactory),
            mediumDailyStudyTimeConstraint(constraintFactory),
            
            earlierDeadlinePreferenceConstraint(constraintFactory),
            maxDailyStudyTimeConstraint(constraintFactory),
            prioritizeAllTasksCompletion(constraintFactory),
            spreadTasksWithoutDeadlines(constraintFactory),
            fillLowWorkloadDays(constraintFactory)
        };
    }
    
    private Constraint deadlineConstraint(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(TaskPart.class)
            .filter(part -> part.hasDeadline() && part.getAssignedDay() != null)
            .filter(part -> part.getAssignedDay().getDate().isAfter(part.getTaskDeadline()))
            .penalize(HardMediumSoftScore.ONE_HARD, part -> {
                long daysLate = ChronoUnit.DAYS.between(part.getTaskDeadline(), part.getAssignedDay().getDate());
                return 2000 + (int)(Math.min(daysLate, Integer.MAX_VALUE / 500) * 500);
            })
            .asConstraint("Задание после дедлайна");
    }
    
    private Constraint sequenceConstraint(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(TaskPart.class)
            .join(TaskPart.class,
                 Joiners.equal(TaskPart::getTaskChain),
                 Joiners.lessThan(TaskPart::getPartIndex))
            .filter((part1, part2) -> part1.getAssignedDay() != null && part2.getAssignedDay() != null)
            .filter((part1, part2) -> !part1.getAssignedDay().getDate().isAfter(part2.getAssignedDay().getDate()))
            .penalize(HardMediumSoftScore.ONE_HARD, (part1, part2) -> 10000) // Значительно усиливаем штраф
            .asConstraint("Нарушение последовательности частей задания");
    }
    
    private Constraint continuousTaskExecution(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(TaskPart.class)
            .filter(part -> part.getNextTaskPart() != null)
            .filter(part -> part.getAssignedDay() != null && part.getNextTaskPart().getAssignedDay() != null)
            .filter(part -> {
                LocalDate currentDate = part.getAssignedDay().getDate();
                LocalDate nextDate = part.getNextTaskPart().getAssignedDay().getDate();
                long daysBetween = ChronoUnit.DAYS.between(currentDate, nextDate);
                return daysBetween != 1 && daysBetween != 0;
            })
            .penalize(HardMediumSoftScore.ONE_HARD, part -> {
                LocalDate currentDate = part.getAssignedDay().getDate();
                LocalDate nextDate = part.getNextTaskPart().getAssignedDay().getDate();
                long daysBetween = ChronoUnit.DAYS.between(currentDate, nextDate);
                return (int)Math.abs(daysBetween) * 1000;
            })
            .asConstraint("Прерывание последовательности выполнения задачи");
    }
    
    private Constraint maxDailyStudyTimeHardConstraint(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(TaskPart.class)
            .groupBy(TaskPart::getAssignedDay, sum(TaskPart::getDurationMinutes))
            .filter((day, totalMinutes) -> day != null && totalMinutes > 300) // Абсолютный лимит 5 часов
            .penalize(HardMediumSoftScore.ONE_HARD, (day, totalMinutes) -> (totalMinutes - 300) * 1000)
            .asConstraint("Превышение абсолютного максимума ежедневного времени учебы");
    }
    
    private Constraint taskOrderingByIdConstraint(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(TaskPart.class)
            .join(TaskPart.class,
                  Joiners.equal(part -> part.getTaskChain().getTask().getSubject().getName()),
                  Joiners.lessThan(part -> part.getTaskChain().getTask().getId()))
            .filter((part1, part2) ->
                part1.getAssignedDay() != null && 
                part2.getAssignedDay() != null)
            .filter((part1, part2) ->
                part1.getAssignedDay().getDate().isAfter(part2.getAssignedDay().getDate()))
            .penalize(HardMediumSoftScore.ONE_HARD, (part1, part2) -> 100)
            .asConstraint("Порядок выполнения заданий одного предмета по ID");
    }
    
    private Constraint enforceSameTaskPartsOrder(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(TaskPart.class)
            .join(TaskPart.class,
                  Joiners.equal(part -> part.getTaskChain().getId()),
                  Joiners.lessThan(TaskPart::getPartIndex))
            .filter((part1, part2) ->
                part1.getAssignedDay() != null && 
                part2.getAssignedDay() != null)
            .filter((part1, part2) ->
                part1.getAssignedDay().getDate().isAfter(part2.getAssignedDay().getDate()))
            .penalize(HardMediumSoftScore.ONE_HARD, (part1, part2) -> 300)
            .asConstraint("Порядок выполнения частей одного задания");
    }
    
    private Constraint evenDistributionConstraint(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(TaskPart.class)
            .groupBy(TaskPart::getAssignedDay, sum(TaskPart::getDurationMinutes))
            .filter((day, totalMinutes) -> day != null)
            .penalize(HardMediumSoftScore.ONE_MEDIUM, (day, totalMinutes) -> {
                return (int) Math.pow(Math.abs(totalMinutes - 180), 2) / 8; // Увеличен штраф
            })
            .asConstraint("Равномерное распределение заданий");
    }
    
    private Constraint preventTaskClustering(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(TaskPart.class)
            .groupBy(TaskPart::getAssignedDay, count())
            .filter((day, count) -> day != null && count > 2)
            .penalize(HardMediumSoftScore.ONE_MEDIUM, (day, count) -> (count - 2) * 120)
            .asConstraint("Предотвращение скопления заданий");
    }
    
    private Constraint mediumDailyStudyTimeConstraint(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(TaskPart.class)
            .groupBy(TaskPart::getAssignedDay, sum(TaskPart::getDurationMinutes))
            .filter((day, totalMinutes) -> day != null && totalMinutes > 240) // 4 часа
            .penalize(HardMediumSoftScore.ONE_MEDIUM, (day, totalMinutes) -> (totalMinutes - 240) * 30)
            .asConstraint("Превышение среднего лимита ежедневного времени учебы");
    }
    
    private Constraint earlierDeadlinePreferenceConstraint(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(TaskPart.class)
            .filter(part -> part.hasDeadline() && part.getAssignedDay() != null)
            .penalize(HardMediumSoftScore.ONE_SOFT, part -> {
                LocalDate deadline = part.getTaskDeadline();
                LocalDate assigned = part.getAssignedDay().getDate();
                long daysBeforeDeadline = ChronoUnit.DAYS.between(assigned, deadline);
                return (int) Math.max(0, 30 - daysBeforeDeadline);
            })
            .asConstraint("Приоритет заданиям с ранними дедлайнами");
    }
    
    // Мягкое ограничение: стараться не превышать 180 минут в день, но это допустимо
    private Constraint maxDailyStudyTimeConstraint(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(TaskPart.class)
            .groupBy(TaskPart::getAssignedDay, sum(TaskPart::getDurationMinutes))
            .filter((day, totalMinutes) -> day != null && totalMinutes > day.getMaxStudyMinutes())
            .penalize(HardMediumSoftScore.ONE_SOFT, (day, totalMinutes) -> (totalMinutes - day.getMaxStudyMinutes()) * 50)
            .asConstraint("Превышение максимального ежедневного времени учебы");
    }
    
    private Constraint prioritizeAllTasksCompletion(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(TaskPart.class)
            .filter(part -> part.getAssignedDay() == null)
            .penalize(HardMediumSoftScore.ONE_SOFT, part -> part.getDurationMinutes() * 10)
            .asConstraint("Приоритет назначению всех заданий");
    }
    
    private Constraint spreadTasksWithoutDeadlines(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(TaskPart.class)
            .filter(part -> !part.hasDeadline() && part.getAssignedDay() != null)
            .join(TaskPart.class)
            .filter((part1, part2) -> 
                    !part2.hasDeadline() && 
                    part2.getAssignedDay() != null && 
                    !part1.equals(part2) &&
                    !part1.getTaskChain().equals(part2.getTaskChain()))
            .filter((part1, part2) -> {
                LocalDate date1 = part1.getAssignedDay().getDate();
                LocalDate date2 = part2.getAssignedDay().getDate();
                return Math.abs(ChronoUnit.DAYS.between(date1, date2)) < 4;
            })
            .penalize(HardMediumSoftScore.ONE_SOFT, (part1, part2) -> 40)
            .asConstraint("Распределение заданий без дедлайнов");
    }
    
    private Constraint fillLowWorkloadDays(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(TaskPart.class)
            .filter(part -> !part.hasDeadline() && part.getAssignedDay() != null)
            .join(constraintFactory.forEach(StudyDay.class))
            .filter((part, day) -> part.getAssignedDay().equals(day))
            .groupBy((part, day) -> day, (part, day) -> part,
                     sum((part, day) -> part.getDurationMinutes()))
            .reward(HardMediumSoftScore.ONE_SOFT, (day, part, totalMinutes) -> {
                if (totalMinutes < 120) return 40;
                if (totalMinutes < 180) return 20;
                return 0;
            })
            .asConstraint("Заполнение дней с низкой нагрузкой");
    }

    private Constraint onlyOneSubjectPerDay(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(TaskPart.class)
            .filter(part -> part.getAssignedDay() != null && 
                    part.getTaskChain() != null && 
                    part.getTaskChain().getTask() != null &&
                    part.getTaskChain().getTask().getSubject() != null)
            .join(TaskPart.class,
                  Joiners.equal(part -> part.getAssignedDay()),
                  Joiners.lessThan(part -> part.getId()))
            .filter((part1, part2) -> 
                part1.getTaskChain().getTask().getSubject() != null && 
                part2.getTaskChain().getTask().getSubject() != null &&
                !part1.getTaskChain().getTask().getSubject().equals(part2.getTaskChain().getTask().getSubject())
            )
            .penalize(HardMediumSoftScore.ONE_HARD, (part1, part2) -> 1000)
            .asConstraint("Разные предметы в один день");
    }

    private Constraint preventTaskPartsOnSameDay(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(TaskPart.class)
            .join(TaskPart.class,
                  Joiners.equal(part -> part.getAssignedDay()),
                  Joiners.equal(part -> part.getTaskChain()),
                  Joiners.lessThan(part -> part.getId()))
            .penalize(HardMediumSoftScore.ONE_HARD, (part1, part2) -> 2000)
            .asConstraint("Несколько частей одной задачи в один день");
    }
}