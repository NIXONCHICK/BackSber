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
                deadlineConstraint(constraintFactory),
                
                spreadTasksEvenly(constraintFactory)
        };
    }

    private Constraint deadlineConstraint(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(SemesterAssignment.class)
                .filter(assignment -> assignment.hasDeadline() && 
                        assignment.getAssignedDay() != null && 
                        assignment.getTaskDeadline() != null &&
                        assignment.getAssignedDay().isAfter(assignment.getTaskDeadline()))
                .penalize("Task after deadline", HardMediumSoftScore.ofHard(1));
    }

    private Constraint spreadTasksEvenly(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(SemesterAssignment.class)
                .join(SemesterAssignment.class, 
                      Joiners.equal(SemesterAssignment::getAssignedDay),
                      Joiners.lessThan(SemesterAssignment::getId))
                .penalize("Spread tasks evenly", HardMediumSoftScore.ofSoft(1));
    }
} 