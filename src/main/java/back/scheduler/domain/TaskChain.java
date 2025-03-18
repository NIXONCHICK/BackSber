package back.scheduler.domain;

import back.entities.Task;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;


@Getter
@Setter
public class TaskChain implements TaskStep {
    
    private Long id;
    private Task task;
    
    private Integer totalDurationMinutes;
    
    private LocalDate startDate;
    
    private Integer minDailyDurationMinutes = 30;
    
    private Integer maxDailyDurationMinutes = 180;
    
    public TaskChain() {
    }
    
    public TaskChain(Long id, Task task) {
        this.id = id;
        this.task = task;
        this.totalDurationMinutes = task.getEstimatedMinutes() != null ? task.getEstimatedMinutes() : 60;
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
    

    public String getTaskName() {
        return task.getName();
    }
    
    @Override
    public String toString() {
        return "TaskChain{" +
                "id=" + id +
                ", task=" + (task != null ? task.getName() : "null") +
                ", totalDurationMinutes=" + totalDurationMinutes +
                ", startDate=" + startDate +
                '}';
    }
    
    @Override
    public StudyDay getAssignedDay() {
        return null;
    }
} 