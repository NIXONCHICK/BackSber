package back.scheduler.constraint;

import back.scheduler.domain.SemesterAssignment;
import org.optaplanner.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import org.optaplanner.core.api.score.stream.Constraint;
import org.optaplanner.core.api.score.stream.ConstraintFactory;
import org.optaplanner.core.api.score.stream.ConstraintProvider;
import org.optaplanner.core.api.score.stream.Joiners;

import java.time.temporal.ChronoUnit;

public class SemesterScheduleConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory constraintFactory) {
        return new Constraint[] {
                // Жесткие ограничения
                deadlineConstraint(constraintFactory),
                noOverlappingAssignmentsConstraint(constraintFactory),
                taskOrderingByIdConstraint(constraintFactory),
                
                // Средние ограничения
                
                // Мягкие ограничения
                spreadTasksEvenly(constraintFactory)
        };
    }
    
    // Жесткое ограничение: задания должны быть выполнены до дедлайна
    private Constraint deadlineConstraint(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(SemesterAssignment.class)
                .filter(assignment -> assignment.hasDeadline() && assignment.getAssignedDay() != null)
                .filter(assignment -> assignment.getAssignedDay().isAfter(assignment.getTaskDeadline()))
                .penalize(HardMediumSoftScore.ONE_HARD, assignment -> {
                    // Значительно увеличиваем штраф за нарушение дедлайнов
                    long daysLate = ChronoUnit.DAYS.between(assignment.getTaskDeadline(), assignment.getAssignedDay());
                    return 2000 + (int)(Math.min(daysLate, Integer.MAX_VALUE / 500) * 500);
                })
                .asConstraint("Задание после дедлайна");
    }
    
    // Жесткое ограничение: предотвращение перекрытия заданий
    private Constraint noOverlappingAssignmentsConstraint(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(SemesterAssignment.class)
                .join(SemesterAssignment.class, 
                      Joiners.equal(SemesterAssignment::getAssignedDay),
                      Joiners.lessThan(SemesterAssignment::getId))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint("Перекрывающиеся задания");
    }
    
    // Жесткое ограничение: задания одного предмета должны выполняться в порядке возрастания ID
    private Constraint taskOrderingByIdConstraint(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(SemesterAssignment.class)
            .join(SemesterAssignment.class,
                  // Задания одного предмета
                  Joiners.equal(a -> a.getTask().getSubject().getName()),
                  // с разными ID 
                  Joiners.lessThan(a -> a.getTask().getId()))
            // Проверяем только назначенные задания
            .filter((assignment1, assignment2) -> 
                assignment1.getAssignedDay() != null && 
                assignment2.getAssignedDay() != null)
            // Задание с большим ID не должно выполняться раньше задания с меньшим ID
            .filter((assignment1, assignment2) -> 
                assignment1.getAssignedDay().isAfter(assignment2.getAssignedDay()))
            .penalize(HardMediumSoftScore.ONE_HARD, (assignment1, assignment2) -> 100)
            .asConstraint("Порядок выполнения заданий одного предмета по ID");
    }
    
    // Мягкое ограничение: равномерное распределение заданий
    private Constraint spreadTasksEvenly(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(SemesterAssignment.class)
                .join(SemesterAssignment.class, 
                      Joiners.equal(SemesterAssignment::getAssignedDay),
                      Joiners.lessThan(SemesterAssignment::getId))
                .penalize(HardMediumSoftScore.ONE_SOFT, (a, b) -> 10)
                .asConstraint("Равномерное распределение заданий");
    }
} 