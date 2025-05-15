package back.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PlannedTaskDto {
    private Long taskId;
    private String taskName;
    private String subjectName;
    private int minutesScheduledToday;
    private int minutesRemainingForTask;
    private LocalDateTime deadline;
} 