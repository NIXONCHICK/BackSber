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
public class StudyAssignment {
    
    @PlanningId
    private Long id;
    
    private Task task;
    
    @PlanningVariable(valueRangeProviderRefs = "availableDays")
    private StudyDay assignedDay;
    
    private Integer durationMinutes;
    
    public StudyAssignment() {
    }
    
    public StudyAssignment(Long id, Task task) {
        this.id = id;
        this.task = task;
        this.durationMinutes = task.getEstimatedMinutes() != null ? task.getEstimatedMinutes() : 60; // По умолчанию 60 минут
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
    
    public LocalDate getAssignedDate() {
        return assignedDay != null ? assignedDay.getDate() : null;
    }
    
    @Override
    public String toString() {
        return "StudyAssignment{" +
                "id=" + id +
                ", task=" + (task != null ? task.getName() : "null") +
                ", assignedDay=" + (assignedDay != null ? assignedDay.getDate() : "null") +
                ", durationMinutes=" + durationMinutes +
                '}';
    }
} 