package back.scheduler.domain;

import back.entities.Task;
import lombok.Getter;
import lombok.Setter;
import org.optaplanner.core.api.domain.entity.PlanningEntity;
import org.optaplanner.core.api.domain.lookup.PlanningId;
import org.optaplanner.core.api.domain.variable.PlanningVariable;

import java.time.LocalDate;

@PlanningEntity
@Getter
@Setter
public class SemesterAssignment {
    
    @PlanningId
    private Long id;
    
    private Task task;
    
    @PlanningVariable(valueRangeProviderRefs = "availableDays")
    private LocalDate assignedDay;
    
    private Integer durationMinutes;

    public SemesterAssignment() {
    }
    
    public SemesterAssignment(Long id, Task task) {
        this.id = id;
        this.task = task;
        this.durationMinutes = task.getEstimatedMinutes() != null ? task.getEstimatedMinutes() : 60;
    }
    
    public LocalDate getTaskDeadline() {
        if (task.getDeadline() == null) {
            return null;
        }
        return new java.sql.Date(task.getDeadline().getTime()).toLocalDate();
    }
    
    public boolean hasDeadline() {
        return task.getDeadline() != null;
    }
    
    @Override
    public String toString() {
        return "SemesterAssignment{" +
                "id=" + id +
                ", task=" + (task != null ? task.getName() : "null") +
                ", assignedDay=" + assignedDay +
                ", durationMinutes=" + durationMinutes +
                '}';
    }
} 