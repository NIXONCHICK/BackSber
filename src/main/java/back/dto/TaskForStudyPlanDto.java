package back.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime; // Используем LocalDateTime для дедлайна, как в PlannedTaskDto
import java.util.Date;

@Data
@Builder
public class TaskForStudyPlanDto {
    private Long id;
    private String name;
    private Date originalDeadline;
    private LocalDateTime deadlineForPlanning;
    private Integer estimatedMinutes;
    private String subjectName;
    private String status;
}