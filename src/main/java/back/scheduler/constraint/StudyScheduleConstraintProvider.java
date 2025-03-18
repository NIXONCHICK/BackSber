package back.scheduler.constraint;

import back.scheduler.domain.StudyAssignment;
import back.scheduler.domain.StudyDay;
import org.optaplanner.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import org.optaplanner.core.api.score.stream.Constraint;
import org.optaplanner.core.api.score.stream.ConstraintFactory;
import org.optaplanner.core.api.score.stream.ConstraintProvider;
import org.optaplanner.core.api.score.stream.Joiners;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static org.optaplanner.core.api.score.stream.ConstraintCollectors.count;
import static org.optaplanner.core.api.score.stream.ConstraintCollectors.sum;

public class StudyScheduleConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory constraintFactory) {
        return new Constraint[] {
            deadlineConstraint(constraintFactory),
            maxDailyStudyTimeConstraint(constraintFactory),
            taskOrderingByIdConstraint(constraintFactory),
            taskPartsOrderingConstraint(constraintFactory),
            
            evenDistributionConstraint(constraintFactory),
            preventTaskClustering(constraintFactory),
            
            earlierDeadlinePreferenceConstraint(constraintFactory),
            prioritizeAllTasksCompletion(constraintFactory),
            spreadTasksOverSemester(constraintFactory)
        };
    }
    
    private Constraint deadlineConstraint(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(StudyAssignment.class)
            .filter(assignment -> assignment.hasDeadline() && assignment.getAssignedDate() != null)
            .filter(assignment -> assignment.getAssignedDate().isAfter(assignment.getTaskDeadline()))
            .penalize(HardMediumSoftScore.ONE_HARD, assignment -> {
                long daysLate = ChronoUnit.DAYS.between(assignment.getTaskDeadline(), assignment.getAssignedDate());
                return 2000 + (int)(Math.min(daysLate, Integer.MAX_VALUE / 500) * 500);
            })
            .asConstraint("Задание после дедлайна");
    }
    
    private Constraint maxDailyStudyTimeConstraint(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(StudyAssignment.class)
            .groupBy(StudyAssignment::getAssignedDay, sum(StudyAssignment::getDurationMinutes))
            .filter((day, totalMinutes) -> day != null && totalMinutes > day.getMaxStudyMinutes())
            .penalize(HardMediumSoftScore.ONE_HARD, (day, totalMinutes) -> totalMinutes - day.getMaxStudyMinutes())
            .asConstraint("Превышение максимального ежедневного времени учебы");
    }
    
    private Constraint taskOrderingByIdConstraint(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(StudyAssignment.class)
            .join(StudyAssignment.class,
                  // Задания одного предмета
                  Joiners.equal(a -> a.getTask().getSubject().getName()),
                  // с разными ID 
                  Joiners.lessThan(StudyAssignment::getId))
            .filter((assignment1, assignment2) ->
                assignment1.getAssignedDate() != null && 
                assignment2.getAssignedDate() != null)
            .filter((assignment1, assignment2) ->
                assignment1.getAssignedDate().isAfter(assignment2.getAssignedDate()))
            .penalize(HardMediumSoftScore.ONE_HARD, (assignment1, assignment2) -> 100)
            .asConstraint("Порядок выполнения заданий одного предмета по ID");
    }
    
    private Constraint taskPartsOrderingConstraint(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(StudyAssignment.class)
            .join(StudyAssignment.class,
                  Joiners.equal(a -> a.getTask().getName()),
                  Joiners.lessThan(StudyAssignment::getId))
            .filter((assignment1, assignment2) ->
                assignment1.getAssignedDate() != null && 
                assignment2.getAssignedDate() != null)
            .filter((assignment1, assignment2) ->
                assignment1.getAssignedDate().isAfter(assignment2.getAssignedDate()))
            .penalize(HardMediumSoftScore.ONE_HARD, (assignment1, assignment2) -> 300)
            .asConstraint("Порядок выполнения частей одного задания");
    }
    
    private Constraint evenDistributionConstraint(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(StudyAssignment.class)
            .groupBy(StudyAssignment::getAssignedDay, sum(StudyAssignment::getDurationMinutes))
            .filter((day, totalMinutes) -> day != null)
            .penalize(HardMediumSoftScore.ONE_MEDIUM, (day, totalMinutes) -> {
                int deviation = Math.abs(totalMinutes - 180);
                return (int) Math.pow(deviation, 2) / 5;
            })
            .asConstraint("Равномерное распределение заданий");
    }
    
    private Constraint preventTaskClustering(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(StudyAssignment.class)
            .groupBy(StudyAssignment::getAssignedDay, count())
            .filter((day, count) -> day != null && count > 3)
            .penalize(HardMediumSoftScore.ONE_MEDIUM, (day, count) -> (count - 3) * 100)
            .asConstraint("Предотвращение скопления заданий");
    }
    
    private Constraint earlierDeadlinePreferenceConstraint(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(StudyAssignment.class)
            .filter(assignment -> assignment.hasDeadline() && assignment.getAssignedDate() != null)
            .penalize(HardMediumSoftScore.ONE_SOFT, assignment -> {
                LocalDate deadline = assignment.getTaskDeadline();
                LocalDate assigned = assignment.getAssignedDate();
                long daysBeforeDeadline = ChronoUnit.DAYS.between(assigned, deadline);
                return (int) Math.max(0, 30 - daysBeforeDeadline);
            })
            .asConstraint("Приоритет заданиям с ранними дедлайнами");
    }
    
    private Constraint prioritizeAllTasksCompletion(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(StudyAssignment.class)
            .filter(assignment -> assignment.getAssignedDay() == null)
            .penalize(HardMediumSoftScore.ONE_SOFT, assignment -> assignment.getDurationMinutes() * 10)
            .asConstraint("Приоритет назначению всех заданий");
    }
    
    private Constraint spreadTasksOverSemester(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(StudyAssignment.class)
            .join(StudyAssignment.class,
                  Joiners.equal(StudyAssignment::getAssignedDay))
            .filter((a, b) -> a.getId() < b.getId())
            .penalize(HardMediumSoftScore.ONE_SOFT, (a, b) -> 5)
            .asConstraint("Распределение заданий по семестру");
    }
} 