package back.scheduler.domain;

import lombok.Getter;
import lombok.Setter;
import org.optaplanner.core.api.domain.entity.PlanningEntity;
import org.optaplanner.core.api.domain.lookup.PlanningId;
import org.optaplanner.core.api.domain.variable.PlanningVariable;
import org.optaplanner.core.api.domain.variable.PlanningVariableGraphType;

import java.time.LocalDate;


@PlanningEntity
@Getter
@Setter
public class TaskPart implements TaskStep {
    
    @PlanningId
    private Long id;
    
    @PlanningVariable(valueRangeProviderRefs = "taskStepRange", graphType = PlanningVariableGraphType.CHAINED)
    private TaskStep previousTaskStep;
    
    @PlanningVariable(valueRangeProviderRefs = "availableDays")
    private StudyDay assignedDay;
    
    private int partIndex;
    
    private Integer durationMinutes;
    
    private TaskPart nextTaskPart;

    public TaskPart() {
    }
    
    public TaskPart(Long id, int partIndex, Integer durationMinutes) {
        this.id = id;
        this.partIndex = partIndex;
        this.durationMinutes = durationMinutes;
    }
    

    public TaskChain getTaskChain() {
        TaskStep step = previousTaskStep;
        while (step != null && !(step instanceof TaskChain)) {
            step = ((TaskPart) step).getPreviousTaskStep();
        }
        return (TaskChain) step;
    }


    public LocalDate getTaskDeadline() {
        TaskChain chain = getTaskChain();
        return chain != null ? chain.getTaskDeadline() : null;
    }
    

    public boolean hasDeadline() {
        TaskChain chain = getTaskChain();
        return chain != null && chain.hasDeadline();
    }
    
    @Override
    public String toString() {
        return "TaskPart{" +
                "id=" + id +
                ", partIndex=" + partIndex +
                ", taskChain=" + (getTaskChain() != null ? getTaskChain().getTaskName() : "null") +
                ", assignedDay=" + (assignedDay != null ? assignedDay.getDate() : "null") +
                ", durationMinutes=" + durationMinutes +
                '}';
    }
} 