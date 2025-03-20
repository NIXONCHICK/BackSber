package back.scheduler.domain;

import back.entities.Task;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class TaskTimeWarning {
    
    private Task task;
    private LocalDate deadline;
    private int requiredMinutes;
    private int availableMinutes;
    private int daysUntilDeadline;
    private int recommendedDailyMinutes;

    public TaskTimeWarning(Task task, LocalDate deadline, int requiredMinutes, int availableMinutes,
                          int daysUntilDeadline) {
        this.task = task;
        this.deadline = deadline;
        this.requiredMinutes = requiredMinutes;
        this.availableMinutes = availableMinutes;
        this.daysUntilDeadline = daysUntilDeadline;
        
        this.recommendedDailyMinutes = (int) Math.ceil((double) requiredMinutes / daysUntilDeadline);
    }
    

    public String getWarningMessage() {
        return String.format(
            "Внимание: для задания '%s' требуется %d минут, но до дедлайна осталось только %d дней (%d минут при норме 180 минут в день). " +
            "Рекомендуется уделять этому заданию не менее %d минут в день до дедлайна.",
            task.getName(), requiredMinutes, daysUntilDeadline, availableMinutes, recommendedDailyMinutes
        );
    }
} 