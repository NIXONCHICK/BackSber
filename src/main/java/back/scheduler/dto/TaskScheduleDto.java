package back.scheduler.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Date;


@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TaskScheduleDto {
    
    private Long taskId;
    private String taskName;
    private String subjectName;
    private Date deadline;
    private LocalDate assignedDate;
    private Integer durationMinutes;
    
} 